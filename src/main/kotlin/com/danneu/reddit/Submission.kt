package com.danneu.reddit

import com.beust.klaxon.JsonObject
import com.beust.klaxon.string
import java.net.URI


class Submission(val json: JsonObject, val subredditName: String, override val id: String) : Thing(Thing.Prefix.Link) {
    override fun url() = "https://www.reddit.com/r/$subredditName/comments/$id"

    fun title(): String = json.string("title")!!

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
