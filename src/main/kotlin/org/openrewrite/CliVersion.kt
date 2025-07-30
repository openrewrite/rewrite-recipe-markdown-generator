package org.openrewrite

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.OkHttpClient
import okhttp3.Request

fun getLatestStableVersion(): String? {
    val releases = getCliVersion("https://api.github.com/repos/moderneinc/moderne-cli-releases/releases/latest")
    return releases?.get("tag_name")?.asText()
}

fun getLatestStagingVersion(): String? {
    val releases = getCliVersion("https://api.github.com/repos/moderneinc/moderne-cli-releases/releases")
    if (releases != null && releases.isArray && releases.size() > 0) {
        return releases[0].get("tag_name")?.asText()
    }
    return null
}

private fun getCliVersion(url: String): JsonNode? {
    return try {
        OkHttpClient()
            .newCall(Request.Builder().url(url).build())
            .execute()
            .use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: return null
                    val mapper = ObjectMapper()
                    mapper.readTree(responseBody)
                } else {
                    System.err.println("Failed to get latest version from GitHub: ${response.code}")
                    null
                }
            }
    } catch (e: Exception) {
        System.err.println("Failed to get latest version from GitHub: ${e.message}")
        null
    }
}
