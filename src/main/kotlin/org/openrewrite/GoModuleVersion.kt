package org.openrewrite

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import okhttp3.OkHttpClient
import okhttp3.Request

// Resolves versions from the Go module proxy's `@latest` endpoint.
// https://go.dev/ref/mod#goproxy-protocol
private val GO_PROXY_MAPPER = jacksonObjectMapper()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

private data class GoModuleInfo(@JsonProperty("Version") val version: String = "")

/**
 * Resolve the latest stable version of a Go module, without its leading `v`, or `null` if it can't be
 * reached or has no stable release. `@latest` prefers the newest release tag, but falls back to a
 * prerelease or pseudo-version when there is none, so anything with a `-` suffix is rejected.
 */
fun latestStableGoModuleVersion(modulePath: String): String? {
    val url = "https://proxy.golang.org/${escapeGoModulePath(modulePath)}/@latest"
    return try {
        OkHttpClient()
            .newCall(Request.Builder().url(url).build())
            .execute()
            .use { response ->
                if (!response.isSuccessful) {
                    System.err.println("Failed to get Go module version for $modulePath from $url: ${response.code}")
                    return null
                }
                val body = response.body?.string() ?: return null
                stableGoModuleVersion(body)
            }
    } catch (e: Exception) {
        System.err.println("Failed to get Go module version for $modulePath from $url: ${e.message}")
        null
    }
}

internal fun stableGoModuleVersion(latestInfoJson: String): String? =
    GO_PROXY_MAPPER.readValue<GoModuleInfo>(latestInfoJson).version
        .removePrefix("v")
        .takeIf { it.isNotEmpty() && !it.contains('-') }

// The proxy serves case-insensitive file systems, so module paths encode each capital letter as `!` + lowercase.
internal fun escapeGoModulePath(modulePath: String): String =
    modulePath.replace(Regex("[A-Z]")) { "!" + it.value.lowercase() }
