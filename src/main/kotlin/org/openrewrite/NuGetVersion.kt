package org.openrewrite

import com.fasterxml.jackson.module.kotlin.readValue

// Resolves versions from the NuGet "package base address" (flat container) API.
// https://learn.microsoft.com/en-us/nuget/api/package-base-address-resource
private data class NuGetVersionIndex(val versions: List<String> = emptyList())

/**
 * Resolve the latest stable (non-prerelease) version of a NuGet package, or `null` if it can't be
 * reached or has no stable release. The flat-container index lists versions in ascending SemVer
 * order, so the last non-prerelease entry is the newest stable release.
 */
fun latestStableNuGetVersion(packageId: String): String? =
    httpGet("https://api.nuget.org/v3-flatcontainer/${packageId.lowercase()}/index.json") { body ->
        LENIENT_JSON_MAPPER.readValue<NuGetVersionIndex>(body).versions
            .filterNot { it.contains('-') }
            .lastOrNull()
    }
