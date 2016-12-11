package com.danneu.reddit


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
     * Every Reddit entity has an id36 which is only unique among entities of the same kind.
     */
    abstract val id: String

    /**
     * The system-wide unique Reddit ID of an entity created by combining a type prefix with an entity's id36.
     */
    fun fullName(): String = prefix.prefix + "_" + id

    /**
     * Every Reddit entity has a URL permalink.
     */
    abstract fun url(): String

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
