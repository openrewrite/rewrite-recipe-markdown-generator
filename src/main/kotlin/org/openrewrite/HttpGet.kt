package org.openrewrite

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.OkHttpClient
import okhttp3.Request

internal val LENIENT_JSON_MAPPER = jacksonObjectMapper()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

/**
 * GET [url] and [parse] its body, or return `null` (after logging why) if the request fails, the
 * response is unsuccessful or empty, or [parse] throws.
 */
internal fun <T> httpGet(url: String, parse: (String) -> T?): T? {
    return try {
        OkHttpClient()
            .newCall(Request.Builder().url(url).build())
            .execute()
            .use { response ->
                if (!response.isSuccessful) {
                    System.err.println("Failed to get $url: ${response.code}")
                    return null
                }
                response.body?.string()?.let(parse)
            }
    } catch (e: Exception) {
        System.err.println("Failed to get $url: ${e.message}")
        null
    }
}
