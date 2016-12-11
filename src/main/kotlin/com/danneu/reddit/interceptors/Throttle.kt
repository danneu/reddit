package com.danneu.reddit.interceptors


import okhttp3.Interceptor
import okhttp3.Response
import java.time.Duration


/**
 * Ensures that a duration has elapsed before the previous request made by this client.
 *
 * Reddit's API requires a throttle of one request per second.
 */
class Throttle(val duration: Duration) : Interceptor {
    private var lastRequestAt: Long = 0

    override fun intercept(chain: Interceptor.Chain): Response {
        val wait = Math.max(0, duration.toMillis() - (System.currentTimeMillis() - lastRequestAt))
        if (wait > 0) {
            Thread.sleep(wait)
        }
        val response = chain.proceed(chain.request())
        lastRequestAt = System.currentTimeMillis()
        return response
    }
}

