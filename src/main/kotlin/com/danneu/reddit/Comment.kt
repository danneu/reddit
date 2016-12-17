package com.danneu.reddit

import com.beust.klaxon.JsonObject
import com.beust.klaxon.int
import com.beust.klaxon.string
import java.net.URI


// Must pass in submissionTitle since Comment json doesn't always include it from reddit's API.
// Rather, it's only included in reddit API requests where you don't provide a specific submission ID,
// like reddit's `api.reddit.com/comments` endpoint, where comments can belong to any submission.
class Comment(override val json: JsonObject, val submissionTitle: String) : Thing(Thing.Prefix.Comment), HasContent, HasScore {
    fun subredditName(): String = json.string("subreddit")!!
    fun submissionId(): String = Thing.Prefix.strip(json.string("link_id")!!)

    // THING

    override fun url() = "https://www.reddit.com/r/${subredditName()}/comments/${submissionId()}//${id()}"

    // CONTENT

    override fun text(): String = json.string("body")!!.trim()
    override fun html(): String = json.string("body_html")!!

    // SCORE

    override fun ups(): Int = json.int("ups")!!
    override fun downs(): Int = json.int("downs")!!
    override fun score(): Int = json.int("score")!!

    override fun toString(): String {
        return "Comment{id = ${id()}, url = ${url()}, text=\"${text().replace("\n", "").take(70)}\""
    }

    companion object {
        /**
         * Instantiate Comment from a JSON string from Reddit's API
         */
        fun from(submissionTitle: String, json: JsonObject): Comment {
            return Comment(json, submissionTitle)
        }
    }
}

