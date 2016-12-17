package com.danneu.reddit

import org.junit.Assert.*
import org.junit.Test
import java.net.URI


class HtmlParserTests {
    @Test
    fun testBlankHtml() {
        assertEquals("empty html parses into empty list", emptyList<URI>(), HtmlParser.urls(""))
    }

    @Test
    fun testUrls() {
        val html = """
            <div>
                <a href="#top"></a>
                <a href="/r/Futurology"></a>
                <a href="r/Futurology"></a>
                <a href="https://www.reddit.com/r/Futurology"></a>
                <a href="https://1.2.3.4/foo"></a>
                <a href="9.9.9.9"></a>
            </div>
        """

        val uris = HtmlParser.urls(html)

        assertEquals(
            "parses all urls",
            listOf(
                URI("#top"),
                URI("/r/Futurology"),
                URI("r/Futurology"),
                URI("https://www.reddit.com/r/Futurology"),
                URI("https://1.2.3.4/foo"),
                URI("9.9.9.9")
            ),
            uris
        )

        assertEquals(
            "absolute url filter works as expected",
            listOf(
                URI("https://www.reddit.com/r/Futurology"),
                URI("https://1.2.3.4/foo")
            ),
            uris.filter { it.isAbsolute }
        )
    }
}
