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
     * Only returns absolute URLs like "http://example.com".
     * Relative URLs like "#top" and "r/futurology" are ignored.
     *
     * @return list of absolute urls
     */
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
