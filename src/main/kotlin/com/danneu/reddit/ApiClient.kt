package com.danneu.reddit

import com.beust.klaxon.JsonObject
import com.beust.klaxon.array
import com.beust.klaxon.int
import com.beust.klaxon.long
import com.beust.klaxon.obj
import com.beust.klaxon.string
import com.danneu.reddit.interceptors.EnsureUserAgent
import com.danneu.reddit.interceptors.Retry
import com.danneu.reddit.interceptors.Throttle
import com.google.common.collect.Iterators
import okhttp3.OkHttpClient
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.util.Collections


class Submission(val json: JsonObject, val subredditName: String, override val id: String) : Thing(Thing.Prefix.Link) {
    override fun url() = "https://www.reddit.com/r/$subredditName/comments/$id"

    fun title(): String = json.string("title")!!

    fun urls(): List<URI> {
        // selftext is always a string, selftext_html is string | null.
        val html = json.string("selftext_html") ?: return emptyList()
        return HtmlParser.urls(html)
    }

    companion object {
        fun from(json: JsonObject): Submission {
            val id = json.string("id")!!
            val subredditName = json.string("subreddit")!!
            return Submission(json, subredditName, id)
        }
    }
}


class Comment(val json: JsonObject, val submissionTitle: String) : Thing(Thing.Prefix.Comment) {
    override val id: String = json.string("id")!!
    override fun url() = "https://www.reddit.com/r/${subredditName()}/comments/${submissionId()}//$id"
    fun text(): String = json.string("body")!!.trim()
    fun html(): String = json.string("body_html")!!
    fun subredditName(): String = json.string("subreddit")!!
    fun submissionId(): String = Thing.Prefix.strip(json.string("link_id")!!)

    fun urls(): List<URI> {
        return HtmlParser.urls(html())
    }

    override fun toString(): String {
        return "Comment{id = $id, url = ${url()}, text=\"${text().replace("\n", "").take(70)}\""
    }

    companion object {
        fun from(submissionTitle: String, json: JsonObject): Comment {
            return Comment(json, submissionTitle)
        }
    }
}


sealed class Node(val apiClient: ApiClient) : Iterable<Comment> {
    // kind == t1
    class CommentTree(val submission: Submission, val json: JsonObject, apiClient: ApiClient) : Node(apiClient) {
        override fun iterator(): Iterator<Comment> {
            val iterator1 = Iterators.singletonIterator(Comment(json, submission.title()))
            val iterator2 = if (json.tryString("replies")?.isEmpty() ?: false) {
                Collections.emptyIterator<Comment>()
            } else {
                val nodes = json.obj("replies")!!.obj("data")!!.array<JsonObject>("children")!!.map { thing ->
                    Node.fromThing(submission, thing, apiClient)
                }
                LazyIteratorChain.fromIterables(nodes)
            }
            return Iterators.concat(iterator1, iterator2)

        }
    }

    // kind == more
    class More(val submission: Submission, val json: JsonObject, apiClient: ApiClient) : Node(apiClient) {
        fun count(): Int = json.int("count")!!
        fun parentId(): String = json.string("parent_id")!!

        override fun iterator(): Iterator<Comment> {
            return if (count() == 0) {
                val commentId = Thing.Prefix.strip(parentId()) // t1_abc -> abc
                apiClient.commentsOf(submission.subredditName, submissionId = submission.id, commentId = commentId)
                    // Drop first item since it's a duplicate of our last item
                    // TODO: Could maybe just drop the first childId in our MoreChildren request?
                    .apply { if (hasNext()) next() }

            } else {
                val childrenIds = json.array<String>("children")!!
                val nodes = apiClient.moreChildren(submission, childrenIds)
                LazyIteratorChain.fromIterables(nodes)
            }
        }
    }

    companion object {
        // a thing is the data envelope: { kind, data }
        // a node only wraps around the `data` object.
        // the thing envelope is only used to figure out which node to instantiate.
        fun fromThing(submission: Submission, json: JsonObject, apiClient: ApiClient): Node {
            val kind = json.string("kind")!!
            val data = json.obj("data")!!
            return when (kind) {
                "t1" -> CommentTree(submission, data, apiClient)
                "more" -> More(submission, data, apiClient)
                // TODO: This should never happen, but what's the proper way to handle this anyways?
                else -> throw RuntimeException("Expected kind: $kind")
            }
        }
    }
}





class ApiClient(
    userAgent: String = "com.danneu.reddit:0.0.1",
    throttle: Duration = Duration.ofMillis(1000),
    val utcOffset: Duration? = null
) {
    val client: OkHttpClient = _sharedClient.newBuilder()
        .addInterceptor(EnsureUserAgent(userAgent))
        .addInterceptor(Throttle(throttle))
        .addInterceptor(Retry()) // this must be the final interceptor
        .build()

    companion object {
        // all ApiClient instances share the same http client thread-pool
        val _sharedClient = OkHttpClient()
    }

    fun commentsOf(subreddit: String, submissionId: String, commentId: String? = null, limit: Int = 100): Iterator<Comment> {
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


    // submissionFullName: "t3_abc", children: ["abc", "def"]
    fun moreChildren(submission: Submission, children: List<String>): List<Node> {
        val url = urlOf("https://api.reddit.com/api/morechildren", listOf(
            "api_type" to "json",
            "link_id" to submission.fullName(),
            "children" to children.joinToString(",")

        ))
        println("[moreChildren] url = $url")
        val json = url.get(client).body().jsonObject()
        return json.obj("json")!!.obj("data")!!.array<JsonObject>("things")!!.map { thing ->
            Node.fromThing(submission, thing, this)
        }
    }


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


    // grabs the latest comment to determine reddit's offset from UTC.
    //
    // i.e. this is the duration you must add to a UTC timestamp to get reddit's server time.
    // for instance, reddit's cloudsearch api watns timestamps in reddit's time rather than utc, so
    // you can use this offset to do the conversion.
    fun fetchUtcOffset(): Duration {
        val iterator = recentComments(limit = 1, crawl = false)

        // TODO: do something real
        if (!iterator.hasNext()) throw RuntimeException("weird, reddit api returned no results")

        val json = iterator.next().json
        return Duration.ofSeconds(json.long("created")!! - json.long("created_utc")!!)
    }


    interface Page<out T> {
        // when after is null, no more pages
        val after: String?
        val data: List<T>
    }


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


    fun submissionsOf(subredditName: String, interval: Duration = Duration.ofMinutes(15)): Iterator<Submission> {
        val offset = utcOffset ?: fetchUtcOffset()
        var max = Instant.now().plus(offset)
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
    val offset = ApiClient().fetchUtcOffset()
    println("offset = ${offset.toHours()} hours")

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



