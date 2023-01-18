package org.openrewrite

import java.net.URI
import java.nio.file.Paths
import java.util.regex.Pattern

class RecipeOrigin(
    val groupId: String,
    val artifactId: String,
    val version: String,
    val jarLocation: URI
) {

    /**
     * The build plugins automatically have dependencies on the core libraries.
     * It isn't necessary to explicitly take dependencies on the core libraries to access their recipes.
     * So explicit dependencies are only necessary when this returns "false"
     */
    fun isFromCoreLibrary() = groupId == "org.openrewrite" && coreLibs.contains(artifactId)

    fun githubUrl() =
        if (isFromCoreLibrary()) {
            "https://github.com/openrewrite/rewrite"
        } else if (artifactId == "rewrite-gradle") {
            "https://github.com/openrewrite/rewrite/tree/main/rewrite-gradle"
        } else {
            "https://github.com/openrewrite/$artifactId"
        }

    fun issueTrackerUrl() =
        if (artifactId == "rewrite-gradle") {
            "https://github.com/openrewrite/rewrite/issues"
        } else {
            "${githubUrl()}/issues"
        }

    companion object {
        private val parsePattern = Pattern.compile("([^:]+):([^:]+):([^:]+):(.+)")
        private val coreLibs = setOf(
            "rewrite-core", "rewrite-java", "rewrite-java-11", "rewrite-java-8", "rewrite-xml",
            "rewrite-maven", "rewrite-properties", "rewrite-yaml"
        )

        fun fromString(encoded: String): RecipeOrigin {
            val m = parsePattern.matcher(encoded)
            require(m.matches()) { "Couldn't parse as a RecipeOrigin: $encoded" }
            return RecipeOrigin(m.group(1), m.group(2), m.group(3), Paths.get(m.group(4)).toUri())
        }

        fun parse(text: String): Map<URI, RecipeOrigin> {
            return text.split(";").asSequence()
                .map(Companion::fromString)
                .associateBy { it.jarLocation }
        }
    }
}
