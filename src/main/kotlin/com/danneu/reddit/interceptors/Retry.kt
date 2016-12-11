package com.danneu.reddit.interceptors

import okhttp3.Interceptor
import okhttp3.Response
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.time.Duration

/**
 * If the request fails (e.g. connection timeout), then keep retrying after waiting some duration.
 *
 * Note: Must go at the end of the interceptor chain.
 */
class Retry(val sleep: Duration = Duration.ofMillis(5000)) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        while(true) {
            try {
                return chain.proceed(request)
            } catch(e: ConnectException) {
                System.err.println(":: Retrying connect failure for ${Thread.currentThread().name}\n::")
                Thread.sleep(sleep.toMillis())
            } catch(e: SocketTimeoutException) {
                System.err.println(":: Retrying timeout failure for ${Thread.currentThread().name}\n::")
                Thread.sleep(sleep.toMillis())
            } catch(e: Exception) {
                System.err.println(":: [Unhandled Error] Retrying connection failure for ${Thread.currentThread().name}\n::")
                e.printStackTrace()
                Thread.sleep(sleep.toMillis())
            }
        }
    }

}
