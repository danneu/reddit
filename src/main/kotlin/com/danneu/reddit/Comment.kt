package com.danneu.reddit

import com.beust.klaxon.JsonObject
import com.beust.klaxon.string
import java.net.URI


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

