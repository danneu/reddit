package com.danneu.reddit

import com.beust.klaxon.JsonObject
import com.beust.klaxon.array
import com.beust.klaxon.int
import com.beust.klaxon.obj
import com.beust.klaxon.string
import com.danneu.reddit.ThingPrefix.Link
import com.danneu.reddit.interceptors.EnsureUserAgent
import com.danneu.reddit.interceptors.Retry
import com.danneu.reddit.interceptors.Throttle
import com.google.common.collect.Iterators
import okhttp3.OkHttpClient
import java.time.Duration
import java.time.Instant
import java.util.Collections


class Submission(val json: JsonObject, val subredditName: String, override val id: String) : RedditThing(Link) {
    override fun url(): String = ""

    fun title(): String = json.string("title")!!

    companion object {
        fun from(json: JsonObject): Submission {
            val id = json.string("id")!!
            val subredditName = json.string("subreddit")!!
            return Submission(json, subredditName, id)
        }
    }
}


sealed class Node(val apiClient: ApiClient) : Iterable<Comment> {
    // kind == t1
    class CommentTree(val submission: Submission, val json: JsonObject, apiClient: ApiClient) : Node(apiClient) {
        override fun iterator(): Iterator<Comment> {
            val iterator1 = Iterators.singletonIterator(Comment(json))
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
                val commentId = ThingPrefix.strip(parentId()) // t1_abc -> abc
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


class Comment(val json: JsonObject) : RedditThing(ThingPrefix.Comment) {
    override val id: String = json.string("id")!!
    override fun url(): String = ""
    fun text(): String = json.string("body")!!
    fun html(): String = json.string("body_html")!!
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


    // TODO: Implement API hit
    fun fetchUtcOffset(): Duration {
        return Duration.ofHours(8)
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
//    ApiClient().submissionsOf("testfixtures", interval = Duration.ofDays(150)).forEach { submission ->
//        println("- ${submission.id} ${submission.title()}")
//    }

//    ApiClient().commentsOf("testfixtures", "5g3272", limit = 2).forEach { comment ->
//        println("${comment.id}: ${comment.text()}")
//    }

//    var count = 0
//    ApiClient().commentsOf("politics", "5h51z8").forEach { comment ->
//        println("${comment.id}: ${comment.text().replace("\n", "").take(30)}")
//        count += 1
//    }
//    println("count: $count")

    val client = ApiClient()

    client.submissionsOf("politics").forEach { submission ->
        println("[submission] ${submission.id}: ${submission.title()} ")
        var comments = 0
        client.commentsOf(submission.subredditName, submissionId = submission.id).forEach { comment ->
            comments += 1
        }
        println("- comments found: $comments. https://www.reddit.com/r/${submission.subredditName}/comments/${submission.id})")
    }


}



