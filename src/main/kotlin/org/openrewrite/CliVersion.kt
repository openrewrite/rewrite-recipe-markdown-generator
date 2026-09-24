package org.openrewrite

import okhttp3.OkHttpClient
import okhttp3.Request

// The Moderne CLI is published as `io.moderne:moderne-cli` to the Code Genome Project, which the
// Homebrew formula installs from and which serves this metadata without credentials. Maven Central
// stopped receiving releases at 4.7.4, and `moderne-cli-releases` on GitHub at 3.57.16.
private const val STABLE_METADATA_URL =
    "https://artifacts.codegenomeproject.org/maven/io/moderne/moderne-cli/maven-metadata.xml"

private val RELEASE_TAG = Regex("<release>([^<]+)</release>")
private val LATEST_TAG = Regex("<latest>([^<]+)</latest>")

fun getLatestStableVersion(): String? {
    val metadata = getMavenMetadata(STABLE_METADATA_URL) ?: return null
    // Prefer the explicit <release> (latest non-snapshot), falling back to <latest>.
    return (RELEASE_TAG.find(metadata) ?: LATEST_TAG.find(metadata))?.groupValues?.get(1)
}

private fun getMavenMetadata(url: String): String? {
    return try {
        OkHttpClient()
            .newCall(Request.Builder().url(url).build())
            .execute()
            .use { response ->
                if (response.isSuccessful) {
                    response.body?.string()
                } else {
                    System.err.println("Failed to get latest version from $url: ${response.code}")
                    null
                }
            }
    } catch (e: Exception) {
        System.err.println("Failed to get latest version from $url: ${e.message}")
        null
    }
}
