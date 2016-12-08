package com.danneu.reddit

import org.junit.Assert.*
import org.junit.Test
import java.net.URI


class HtmlParserTests {
    @Test
    fun testRelativeUrls() {
        val html = """
            <div>
                <a href="#top"></a>
                <a href="/r/Futurology"></a>
                <a href="r/Futurology"></a>
                <a href="https://www.reddit.com/r/Futurology"></a>
            </div>
        """

        assertEquals(
            "ignores relative urls",
            listOf(URI("https://www.reddit.com/r/Futurology")), HtmlParser.urls(html))
    }
}
