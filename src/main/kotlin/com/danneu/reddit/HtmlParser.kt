package com.danneu.reddit

import org.jsoup.Jsoup
import java.net.URI
import java.net.URISyntaxException


object HtmlParser {
    // ignores relative urls like <a href="#top"> and <a href="r/futurology">
    //
    // sponge up comment logic between Submission and Comment
    fun urls(html: String): List<URI> {
        return Jsoup.parseBodyFragment(html, "https://www.reddit.com").let { doc ->
            doc.select("a")
                .map { it.attr("href") }
                .mapNotNull { href -> try { URI(href) } catch(e: URISyntaxException) { null } }
                // ignore relative urls
                .filter { it.isAbsolute }
        }
    }
}
