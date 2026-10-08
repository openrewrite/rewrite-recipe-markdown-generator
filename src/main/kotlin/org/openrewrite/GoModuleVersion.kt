package org.openrewrite

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue

// Resolves versions from the Go module proxy's `@latest` endpoint.
// https://go.dev/ref/mod#goproxy-protocol
private data class GoModuleInfo(@JsonProperty("Version") val version: String = "")

/**
 * Resolve the latest stable version of a Go module, without its leading `v`, or `null` if it can't be
 * reached or has no stable release. `@latest` prefers the newest release tag, but falls back to a
 * prerelease or pseudo-version when there is none, so anything with a `-` suffix is rejected.
 */
fun latestStableGoModuleVersion(modulePath: String): String? =
    httpGet("https://proxy.golang.org/${escapeGoModulePath(modulePath)}/@latest", ::stableGoModuleVersion)

internal fun stableGoModuleVersion(latestInfoJson: String): String? =
    LENIENT_JSON_MAPPER.readValue<GoModuleInfo>(latestInfoJson).version
        .removePrefix("v")
        .takeIf { it.isNotEmpty() && !it.contains('-') }

// The proxy serves case-insensitive file systems, so module paths encode each capital letter as `!` + lowercase.
internal fun escapeGoModulePath(modulePath: String): String =
    modulePath.replace(Regex("[A-Z]")) { "!" + it.value.lowercase() }
