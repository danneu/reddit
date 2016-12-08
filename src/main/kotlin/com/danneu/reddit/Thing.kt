package com.danneu.reddit


// A reddit thing is an envelope around one of reddit's data types (submission, comment, subreddit, etc.).
//
// Json looks like:
//
//     {
//         kind: "t1" | "t2" | "t3" | ... | "more"
//         data: { ... }
//     }


abstract class Thing(val prefix: Prefix) {
    abstract val id: String
    fun fullName(): String = prefix.prefix + "_" + id
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
            // Reddit has a concept of an item's fullName, {typePrefix}_{itemId36}
            //
            // "t1_xxx" -> "xxx"
            // "xxx" -> "xxx"
            fun strip(id: String): String {
                return Regex("""^[^_]+_(.+)$""").find(id)?.groupValues?.last() ?: id
            }
        }
    }
}
