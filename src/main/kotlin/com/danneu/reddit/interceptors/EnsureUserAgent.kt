package com.danneu.reddit.interceptors

import okhttp3.Interceptor
import okhttp3.Interceptor.Chain
import okhttp3.Response


// Reddit's API requires a user-agent, so this ensures that one is set even if user removes it.
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
