package com.danneu.reddit

import com.beust.klaxon.JsonObject
import com.beust.klaxon.boolean
import com.beust.klaxon.int
import com.beust.klaxon.string


/**
 * Represents a Submission entity from Reddit's API.
 */
class Submission(override val json: JsonObject, val subredditName: String) : Thing(Thing.Prefix.Link), HasContent, HasScore {
    /**
     * The user-created title of the submission
     */
    fun title(): String = json.string("title")!!

    /**
     * Is the submission stickied to the top of its subreddit?
     */
    fun stickied(): Boolean = json.boolean("stickied")!!

    /**
     * Username of the redditor that created the thing.
     */
    fun author(): String = json.string("author")!!

    /**
     * Submission is a self-post rather than a link elsewhere.
     */
    fun isSelf(): Boolean = json.boolean("is_self")!!

    // THING

    /**
     * Permalink of the submission
     */
    override fun url() = "https://www.reddit.com/r/$subredditName/comments/${id()}"

    // CONTENT

    // Note: selftext is always a string, though blank if submission is not a selfpost
    override fun text(): String = json.string("selftext")?.trim() ?: ""
    // Note: selftext_html is a nullable string unlike selftext
    override fun html(): String = json.string("selftext_html") ?: ""

    // SCORE

    override fun ups(): Int = json.int("ups")!!
    override fun downs(): Int = json.int("downs")!!
    override fun score(): Int = json.int("score")!!

    override fun toString(): String {
        return "Submission{id = ${id()}, url = ${url()}, title=\"${title()}\""
    }

    companion object {
        /**
         * Instantiate Submission from a JSON string from Reddit's API
         */
        fun from(json: JsonObject): Submission {
            val subredditName = json.string("subreddit")!!
            return Submission(json, subredditName)
        }
    }
}
