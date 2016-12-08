package com.danneu.reddit

import com.beust.klaxon.JsonObject
import com.beust.klaxon.array
import com.beust.klaxon.int
import com.beust.klaxon.obj
import com.beust.klaxon.string
import com.google.common.collect.Iterators
import java.util.Collections


// A submission's comments are a list of comment trees that may have any number of "load more" nodes
// which will be paginated lazily.
internal sealed class Node(val apiClient: ApiClient) : Iterable<Comment> {
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

