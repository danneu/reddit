package com.danneu.reddit


class Subreddit(override val id: String, val name: String) : Thing(Thing.Prefix.Subreddit) {
    override fun url() = "https://www.reddit.com/r/$name"
}
