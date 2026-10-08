package org.openrewrite

// The Moderne CLI is published as `io.moderne:moderne-cli` to the Code Genome Project, which the
// Homebrew formula installs from and which serves this metadata without credentials. Maven Central
// stopped receiving releases at 4.7.4, and `moderne-cli-releases` on GitHub at 3.57.16.
private const val STABLE_METADATA_URL =
    "https://artifacts.codegenomeproject.org/maven/io/moderne/moderne-cli/maven-metadata.xml"

private val RELEASE_TAG = Regex("<release>([^<]+)</release>")
private val LATEST_TAG = Regex("<latest>([^<]+)</latest>")

fun getLatestStableVersion(): String? {
    // Prefer the explicit <release> (latest non-snapshot), falling back to <latest>.
    return httpGet(STABLE_METADATA_URL) { metadata ->
        (RELEASE_TAG.find(metadata) ?: LATEST_TAG.find(metadata))?.groupValues?.get(1)
    }
}
