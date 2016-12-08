package com.danneu.reddit


import org.junit.Assert.*
import org.junit.Test


class ApiClientBuilderTests {
    @Test
    fun test() {
        val client1 = ApiClient { userAgent = "a" }
        val client2 = client1.fork { userAgent = "b" }
        val client3 = client2.fork { userAgent = "c" }.fork { userAgent = "z" }
        assertEquals("client1 is set", "a", client1.userAgent)
        assertEquals("client2 is set", "b", client2.userAgent)
        assertEquals("client3 is set", "z", client3.userAgent)
    }
}
