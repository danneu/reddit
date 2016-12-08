package com.danneu.reddit


// a little tuple to dry up some repetition between Comment/Submission
class Subreddit(override val id: String, val name: String) : RedditThing(ThingPrefix.Subreddit) {
    override fun url() = "https://www.reddit.com/r/$name"
}
