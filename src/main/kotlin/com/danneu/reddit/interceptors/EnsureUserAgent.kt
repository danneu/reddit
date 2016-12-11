package com.danneu.reddit.interceptors

import okhttp3.Interceptor
import okhttp3.Interceptor.Chain
import okhttp3.Response


/**
 * If a request does not have a user-agent header, the provided default user-agent value is set before
 * the request is sent.
 *
 * Reddit's API requires a user-agent.
 */
internal class EnsureUserAgent(val defaultUserAgent: String) : Interceptor {
    override fun intercept(chain: Chain): Response {
        var request = chain.request()

        if (request.header("User-Agent") == null) {
            request = request.newBuilder()
                .addHeader("User-Agent", defaultUserAgent)
                .build()
        }

        return chain.proceed(request)
    }
}
