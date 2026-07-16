package org.openrewrite

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import okhttp3.OkHttpClient
import okhttp3.Request

// The C# recipe modules are published only to NuGet; there is no Maven artifact to anchor their
// version the way the other ecosystems do. Their latest stable version is resolved from the NuGet
// "package base address" (flat container) index at doc-generation time.
// https://learn.microsoft.com/en-us/nuget/api/package-base-address-resource
private val NUGET_MAPPER = jacksonObjectMapper()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

private data class NuGetVersionIndex(val versions: List<String> = emptyList())

/**
 * Resolve the latest stable (non-prerelease) version of a NuGet package, or `null` if it can't be
 * reached or has no stable release. The flat-container index lists versions in ascending SemVer
 * order, so the last non-prerelease entry is the newest stable release.
 */
fun latestStableNuGetVersion(packageId: String): String? {
    val url = "https://api.nuget.org/v3-flatcontainer/${packageId.lowercase()}/index.json"
    return try {
        OkHttpClient()
            .newCall(Request.Builder().url(url).build())
            .execute()
            .use { response ->
                if (!response.isSuccessful) {
                    System.err.println("Failed to get NuGet version for $packageId from $url: ${response.code}")
                    return null
                }
                val body = response.body?.string() ?: return null
                NUGET_MAPPER.readValue<NuGetVersionIndex>(body).versions
                    .filterNot { it.contains('-') }
                    .lastOrNull()
            }
    } catch (e: Exception) {
        System.err.println("Failed to get NuGet version for $packageId from $url: ${e.message}")
        null
    }
}
