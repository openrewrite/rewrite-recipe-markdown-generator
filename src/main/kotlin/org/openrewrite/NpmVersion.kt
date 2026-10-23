package org.openrewrite

import com.fasterxml.jackson.module.kotlin.readValue

private data class NpmDistTags(val latest: String? = null)

/**
 * Resolve the version the npm registry tags `latest` for a package, or `null` if it can't be reached
 * or has no such tag.
 */
fun latestNpmVersion(packageName: String): String? =
    httpGet("https://registry.npmjs.org/-/package/$packageName/dist-tags") { body ->
        LENIENT_JSON_MAPPER.readValue<NpmDistTags>(body).latest
    }
