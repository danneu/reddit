package com.danneu.reddit


import com.beust.klaxon.JsonObject
import com.beust.klaxon.array
import com.beust.klaxon.long
import com.beust.klaxon.obj
import com.beust.klaxon.string
import com.danneu.reddit.interceptors.EnsureUserAgent
import com.danneu.reddit.interceptors.Retry
import com.danneu.reddit.interceptors.Throttle
import okhttp3.OkHttpClient
import java.time.Duration
import java.time.Instant
import java.net.Proxy


/**
 * A client that makes requests to the Reddit API.
 *
 * @property throttle How long the client will wait between each request.
 * @property userAgent Reddit requires a user-agent, else the client will generate its own.
 * @property proxy Proxy requests through an optional proxy server
 */
class ApiClient(
    val throttle: Duration,
    val userAgent: String,
    val proxy: Proxy?
) {
    val client: OkHttpClient = _sharedClient.newBuilder()
        .proxy(proxy ?: Proxy.NO_PROXY)
        .addInterceptor(EnsureUserAgent(userAgent))
        .addInterceptor(Throttle(throttle))
        .addInterceptor(Retry()) // this must be the final interceptor
        .build()

    // Can be initialized with a lambda that gets passed to builder
    constructor(block: Builder.() -> Unit = {}): this(Builder().apply(block))

    companion object {
        /**
         * The http client shared across all ApiClient instances so that they all use the same http client thread-pool.
         */
        private val _sharedClient = OkHttpClient()

        /**
         * Reddit's UTC offset.
         *
         * i.e. This is the duration that must be added to a UTC timestamp to get the Reddit's timestamp,
         * like the timestamp is will send to the cloudsearch API.
         *
         * TODO: The client used to fetch this, but I'm pretty sure I can just hard-code it. Can it change?
         *
         */
        private val utcOffset: Duration = Duration.ofHours(8)
    }

    private constructor(builder: Builder): this(
        builder.throttle,
        builder.userAgent,
        builder.proxy
    )

    ////////////////////////////////////////////////////////////

    /**
     * Create a new client based on the configuration of an existing one.
     *
     * @param block a lambda that gets applied to the client copy
     * @return a new client
     */
    fun fork(block: Builder.() -> Unit): ApiClient = Builder.from(this).apply(block).build()

    class Builder() {
        var throttle: Duration = Duration.ofMillis(1000)
        var userAgent: String = "com.danneu.reddit:0.0.1"
        var proxy: Proxy? = null

        fun build() = ApiClient(this)

        companion object {
            fun from(client: ApiClient): Builder = Builder().apply {
                throttle = client.throttle
                userAgent = client.userAgent
                proxy = client.proxy
            }
        }
    }

    ////////////////////////////////////////////////////////////

    /**
     * Load a submission from the Reddit API.
     *
     * @param subreddit the subreddit name
     * @param submissionId the submission's id36 or fullname
     * @return the submission if one was found
     */
    fun submissionAt(subreddit: String, submissionId: String): Submission? {
        @Suppress("NAME_SHADOWING")
        val submissionId = Thing.Prefix.strip(submissionId)

        val url = urlOf("https://api.reddit.com/r/$subreddit/comments/$submissionId", listOf(
            "limit" to 1,
            "depth" to 1
        ))
        val response = url.get(client)
        if (response.code() == 404) return null
        val array = response.body().jsonArray<JsonObject>()
        val json = array.first().obj("data")!!.array<JsonObject>("children")!!.first().obj("data")!!
        return Submission.from(json)
    }

    /**
     * Crawl the comments of a submission depth-first.
     *
     * @param subreddit the subreddit name
     * @param submissionId the submission's id36 or fullname
     * @param commentId optionally target a specific comment tree, else it crawls all trees
     * @param limit how many comments to fetch
     * @return a flattened iteration of all its comments
     */
    fun commentsOf(subreddit: String, submissionId: String, commentId: String? = null, limit: Int = 100): Iterator<Comment> {
        @Suppress("NAME_SHADOWING")
        val submissionId = Thing.Prefix.strip(submissionId)

        val base = "https://api.reddit.com/r/$subreddit/comments/$submissionId" +
            if (commentId == null) "" else "//$commentId"
        val url = urlOf(base, listOf(
            "limit" to limit
        ))
        println("[commentsOf] url = $url")
        val json = url.get(client).body().jsonArray<JsonObject>()
        // a thing is { data: {}, kind: 't*' }
        val submissionJson = json.first().obj("data")!!.array<JsonObject>("children")!!.first().obj("data")!!
        val nodes = json.last().obj("data")!!.array<JsonObject>("children")!!.map { thing ->
            Node.fromThing(Submission.from(submissionJson), thing, this)
        }
        return LazyIteratorChain.fromIterables(nodes)
    }


    /**
     * Convenience method for crawling a submission's comments when you have a submission instance.
     */
    fun commentsOf(submission: Submission, commentId: String? = null, limit: Int = 100): Iterator<Comment> {
        return commentsOf(submission.subredditName, submission.id, commentId, limit)
    }


    /**
     * Paginates a "Load More" or "Continue Thread" comment node.
     *
     * @param submission the submission
     * @param children a list of comment id36s
     * @return a comment iterator
     */
    fun moreChildren(submission: Submission, children: List<String>): Iterator<Comment> {
        val url = urlOf("https://api.reddit.com/api/morechildren", listOf(
            "api_type" to "json",
            "link_id" to submission.fullName(),
            "children" to children.joinToString(",")

        ))
        println("[moreChildren] url = $url")
        val json = url.get(client).body().jsonObject()
        val nodes = json.obj("json")!!.obj("data")!!.array<JsonObject>("things")!!.map { thing ->
            Node.fromThing(submission, thing, this)
        }
        return LazyIteratorChain.fromIterables(nodes)
    }


    /**
     * Paginates the latest 1,000+ comments made on reddit across all subreddits.
     *
     * @param limit how many comments to fetch each request (max: 100)
     * @param crawl whether to continue paginating after the first page or stop after one request
     * @return a comment iterator
     */
    fun recentComments(limit: Int = 100, crawl: Boolean = true): Iterator<Comment> {
        var after: String? = null
        val buffer = mutableListOf<Comment>()
        // we set this whenever a response comes back without an `after`.
        // this way we can end the iterator once the buffer is drained since there
        // are no more pages to paginate.
        var lastPage = false

        return object : Iterator<Comment> {
            override fun hasNext(): Boolean {
                // we can serve from the buffer
                if (buffer.isNotEmpty()) return true

                // buffer is drained and there are no more paginations
                if (lastPage) return false

                // refill the buffer with the next paginated request
                val url = urlOf("https://api.reddit.com/comments", listOf(
                    "limit" to limit,
                    "after" to after
                ))

                val body = url.get(client).body().jsonObject()
                buffer.addAll(body.obj("data")!!.array<JsonObject>("children")!!.map { obj ->
                    // unlike some other endpoints, comments returned by this one include the submission title
                    // in the comment json
                    val data = obj.obj("data")!!
                    val submissionTitle = data.string("link_title")!!
                    Comment.from(submissionTitle, data)
                })

                // if call-site tells us not to paginate, then end after buffer is drained
                if (!crawl) {
                    return buffer.isNotEmpty()
                }

                // update `after` for the next pagination
                after = body.obj("data")!!.string("after")

                if (after == null) {
                    lastPage = false
                }

                // we're done if the buffer is still empty
                return buffer.isNotEmpty()
            }

            override fun next(): Comment = buffer.removeAt(0)
        }
    }


    /**
     * Makes a request to determine Reddit's offset from UTC.
     *
     * i.e. The amount of time you must add to a UTC timestamp to get reddit's server time.
     * The cloudsearch API wants timestamps with Reddit's offset time rather than UTC, so we
     * add this offset to our timestamps before making cloudsearch requests.
     */
    fun fetchUtcOffset(): Duration {
        val iterator = recentComments(limit = 1, crawl = false)

        // TODO: do something real
        if (!iterator.hasNext()) throw RuntimeException("weird, reddit api returned no results")

        val json = iterator.next().json
        return Duration.ofSeconds(json.long("created")!! - json.long("created_utc")!!)
    }


    /**
     * Represents a paginated API result.
     *
     * `after` is an ID36. If it's null, then there are no more pages after this one.
     */
    interface Page<out T> {
        val after: String?
        val data: List<T>
    }


    /**
     * Makes a cloudsearch API request.
     *
     * The cloudsearch (Lucene) API is necessary since Reddit's paginated APIs only return up to 1,000 results.
     * It's going to fetch all submissions created between {max - interval} and {max}.
     *
     * @param subredditName the subreddit name
     * @param max the reddit-offset timestamp upper bound
     * @param interval the amount of time subtract from `max` to get the lower bound
     * @param after request the page after the given submission id36, else start from the first page
     * @return a page of submissions that may point to a following page
     */
    fun cloudSearch(subredditName: String, max: Instant, interval: Duration, after: String? = null): Page<Submission> {
        // ensure min is never negative
        val min = listOf(max.minus(interval), Instant.EPOCH).max()!!
        val q = "timestamp:${min.epochSecond}..${max.epochSecond}"
        val url = urlOf("https://api.reddit.com/r/$subredditName/search", listOf(
            "q" to q,
            "syntax" to "cloudsearch",
            "sort" to "new",
            "type" to "link", // "link" means submissions only
            "limit" to 100,
            "restrict_sr" to true,
            "after" to after
        ))
        println("[cloudSearch] url = $url")
        val body = url.get(client).body().jsonObject()
        val submissions = body.obj("data")!!.array<JsonObject>("children")!!.map { Submission.from(it.obj("data")!!) }

        return object : Page<Submission> {
            override val after: String? = body.obj("data")!!.string("after")
            override val data: List<Submission> = submissions
        }
    }


    /**
     * Crawl the submissions of a subreddit, attempting to hover at about one page of results per request.
     *
     * It requests all submissions between {max - interval} and {max}. If 0-50 results are found in that interval
     * (0 to half a page), then the interval is expanded. If more than one page of results are found (over 100),
     * then the interval is decayed. It then sets max to the previous request's lower bound and makes the next
     * request. It repeats this process until it has iterated back one year.
     *
     * @param subredditName the name of the subreddit
     * @param interval the min..max interval for the initial request
     * @return an interation of submissions
     */
    fun submissionsOf(subredditName: String, interval: Duration = Duration.ofMinutes(15)): Iterator<Submission> {
        var max = Instant.now().plus(utcOffset)
        val buffer = mutableListOf<Submission>()
        var after: String? = null

        @Suppress("NAME_SHADOWING")
        var interval = interval

        val minInterval = Duration.ofMinutes(10)
        val maxInterval = Duration.ofDays(365)

        return object : Iterator<Submission> {
            override fun hasNext(): Boolean {
                // we can serve from buffer
                if (buffer.isNotEmpty()) return true

                // loop until our upper-bound is older than 1 year.
                while (max.isAfter(Instant.now().minus(Duration.ofDays(365)))) {
                    val page = cloudSearch(subredditName, max, interval, after)
                    buffer.addAll(page.data)
                    after = page.after

                    // if we hit items but there are more pages, then short-circuit so that min/max are not touched
                    // and we can paginate above
                    if (after != null && buffer.isNotEmpty()) return true

                    // set max to the min used in previous request
                    // ensure this is done before updating interval
                    max = max.minus(interval)

                    // adjust interval so that we hover around 50-99 results per request and avoid pagination
                    interval = when {
                        buffer.isEmpty() ->
                            interval.plus(interval.dividedBy(2))
                        buffer.size < 50 ->
                            // increase interval if page is less than half full
                            interval.plus(interval.dividedBy(2))
                        buffer.size == 100 && after != null ->
                            // decay interval if the first page is full and there are more pages
                            interval.minus(interval.dividedBy(20))
                        else ->
                            // buffer is 50-99 so just keep it here
                            interval
                    }.clamp(minInterval, maxInterval)

                    println("INTERVAL is now ${interval.toMinutes()} minutes (${interval.toDays()} days)")

                    // stop looping once we find results
                    if (buffer.isNotEmpty()) {
                        return true
                    }

                    // else we are looping again...
                }

                // if we make it down here, we crawled back a year, so end it
                return false
            }

            override fun next(): Submission = buffer.removeAt(0)
        }
    }
}


fun main(args: Array<String>) {
//    val offset = ApiClient().fetchUtcOffset()
//    println("offset = ${offset.toHours()} hours")

    ApiClient().recentComments().forEach { comment ->
        println(comment)
    }

//    ApiClient().submissionsOf("testfixtures", interval = Duration.ofDays(150)).forEach { submission ->
//        println("- ${submission.id} ${submission.title()}")
//    }

//    ApiClient().commentsOf("testfixtures", "5g3272", limit = 2).forEach { comment ->
//        println(comment)
//    }

//    var count = 0
//    ApiClient().commentsOf("politics", "5h51z8").forEach { comment ->
//        println("${comment.id}: ${comment.text().replace("\n", "").take(30)}")
//        count += 1
//    }
//    println("count: $count")

//    val client = ApiClient()
//    client.submissionsOf("politics").forEach { submission ->
//        println("[submission] ${submission.id}: ${submission.title()} ")
//        var comments = 0
//        client.commentsOf(submission.subredditName, submissionId = submission.id).forEach { comment ->
//            comments += 1
//        }
//        println("- comments found: $comments. https://www.reddit.com/r/${submission.subredditName}/comments/${submission.id})")
//    }
}



