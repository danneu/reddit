package com.danneu.reddit

import com.beust.klaxon.JsonObject
import com.beust.klaxon.long
import com.beust.klaxon.string
import java.net.URI
import java.time.Duration
import java.time.Instant


/**
 * A Thing is an envelope around one of Reddit's data types (submission, comment, subreddit, etc.).
 *
 * Reddit's API returns these. The JSON looks like:
 *
 *     {
 *         kind: "t1" | "t2" | "t3" | ... | "more",
 *         data: { ... }
 *     }
 */
abstract class Thing(val prefix: Prefix) {
    /**
     * A thing from reddit's API wraps the response json so that the consumer can access information
     * that the crawler doesn't provide methods for.
     *
     * To remain robust, the crawler only tries to expose the most frequently useful fields from the json as methods.
     */
    abstract val json: JsonObject

    /**
     * Every Reddit entity has an id36 which is only unique among entities of the same kind.
     */
    fun id(): String = json.string("id")!!

    /**
     * The system-wide unique Reddit ID of an entity created by combining a type prefix with an entity's id36.
     */
    fun fullName(): String = prefix.prefix + "_" + id()

    /**
     * Every Reddit entity has a URL permalink.
     */
    abstract fun url(): String

    /**
     * This is the duration that you must add to a UTC timestamp to get the timestamp that reddit uses for a thing.
     */
    fun utcOffset(): Duration = Duration.ofSeconds(json.long("created")!! - json.long("created_utc")!!)

    /**
     * The UTC instant when a thing was created.
     */
    fun created(): Instant = Instant.ofEpochSecond(json.long("created_utc")!!)

    enum class Prefix(val prefix: String) {
        Comment("t1"),
        Account("t2"),
        Link("t3"),
        Message("t4"),
        Subreddit("t5"),
        Award("t6"),
        PromoCampaign("t8");

        companion object {
            /**
             * Takes a fullname or id36 and returns an id36.
             *
             * Particularly useful for letting the user pass a fullname into a function that expects an id36.
             *
             * Example:
             *
             *     "t1_xxx" -> "xxx"
             *     "xxx" -> "xxx"
             */
            fun strip(id: String): String {
                return Regex("""^[^_]+_(.+)$""").find(id)?.groupValues?.last() ?: id
            }
        }
    }
}


/**
 * Represents a Reddit Thing that has a user-generated body. i.e. Submissions and Comments.
 *
 * If a submission is a self-post, then the content methods simply return empty strings/lists
 */
interface HasContent {
    fun text(): String
    fun html(): String
    fun urls(): List<URI> = HtmlParser.urls(html())
}


/**
 * Represents a Reddit Thing that users can rate up/down.
 *
 * Score is the net value of ups - downs.
 */
interface HasScore {
    fun ups(): Int
    fun downs(): Int
    fun score(): Int
}
