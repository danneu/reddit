package com.danneu.reddit

import org.jsoup.Jsoup
import java.net.URI
import java.net.URISyntaxException


/**
 * Functions for parsing HTML
 */
object HtmlParser {
    /**
     * Parses URI objects from an HTML fragment.
     *
     * Returns both absolute and relative URLs, e.g.  "#top", "r/Futurology", and "https://www.reddit.com/r/Futurology".
     *
     * Filter for `uri.isAbsolute` to only get absolute urls.
     *
     * @return list of urls
     */
    fun urls(html: String): List<URI> {
        return Jsoup.parseBodyFragment(html, "https://www.reddit.com").let { doc ->
            doc.select("a")
                .map { it.attr("href") }
                .mapNotNull { href -> try { URI(href) } catch(e: URISyntaxException) { null } }
        }
    }
}
