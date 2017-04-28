package com.danneu.reddit

import com.beust.klaxon.JsonArray
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import com.beust.klaxon.string
import okhttp3.FormBody
import org.apache.http.client.utils.URIBuilder
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import java.time.Duration


// A junk-drawer of extensions and utilities.


// HTTP HELPERS


fun urlOf(base: String, query: List<Pair<String, Any?>> = emptyList()): String = URIBuilder(base).apply {
    query.forEach {
        // null query values are ignored since it's more convenient this way
        if (it.second != null) {
            addParameter(it.first, it.second.toString())
        }
    }
    // params to be sent on every request:
    addParameter("raw_json", "1")
}.build().toString()


fun String.get(client: OkHttpClient): Response {
    val request = Request.Builder()
        .url(this)
        .addHeader("Accept", "application/json")
        .addHeader("Content-Type", "application/json")
        .build()
    return client.newCall(request).execute()
}


fun String.postForm(client: OkHttpClient, form: Map<String, String> = emptyMap()): Response {
    val body = FormBody.Builder().apply {
        form.forEach { k, v -> this.add(k, v) }
    }.build()
    val request = Request.Builder()
        .url(this)
        .addHeader("Accept", "application/json")
        .post(body)
        .build()
    return client.newCall(request).execute()
}


fun ResponseBody.json(): Any? = try {
    Parser().parse(this.byteStream())
} catch(e: RuntimeException) {
    System.err.println("ResponseBody#json() failed to parse response body of \"${this.string()}\"")
    throw e
}


inline fun <reified T> ResponseBody.jsonArray(): List<T> = (json() as JsonArray<*>).filterIsInstance<T>()


fun ResponseBody.jsonObject(): JsonObject = json() as JsonObject


// DURATION EXTENSIONS


/**
 * If a duration is outside of min..max bounds, then min or max is returned.
 */
fun Duration.clamp(min: Duration, max: Duration): Duration = when {
    this > max -> max
    this < min -> min
    else -> this
}


// JSON EXTENSIONS


/**
 * Returns null instead of throwing if JSON field value is not a string.
 */
fun JsonObject.stringOrNull(field: String): String? = try {
    this.string(field)
} catch(e: ClassCastException) {
    null
}

