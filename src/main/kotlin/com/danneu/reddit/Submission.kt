package com.danneu.reddit

import com.beust.klaxon.JsonObject
import com.beust.klaxon.string
import java.net.URI


/**
 * Represents a Submission entity from Reddit's API.
 */
class Submission(val json: JsonObject, val subredditName: String, override val id: String) : Thing(Thing.Prefix.Link) {
    /**
     * Permalink of the submission
     */
    override fun url() = "https://www.reddit.com/r/$subredditName/comments/$id"

    /**
     * The user-created title of the submission
     */
    fun title(): String = json.string("title")!!

    /**
     * List of absolute urls linked from the submission OP.
     *
     * If submission is not a self-post, then the list is empty.
     */
    fun urls(): List<URI> {
        // selftext is always a string, selftext_html is string | null.
        val html = json.string("selftext_html") ?: return emptyList()
        return HtmlParser.urls(html)
    }

    override fun toString(): String {
        return "Submission{id = $id, url = ${url()}, title=\"${title()}\""
    }

    companion object {
        /**
         * Instantiate Submission from a JSON string from Reddit's API
         */
        fun from(json: JsonObject): Submission {
            val id = json.string("id")!!
            val subredditName = json.string("subreddit")!!
            return Submission(json, subredditName, id)
        }
    }
}
