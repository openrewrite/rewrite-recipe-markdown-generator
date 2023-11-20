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

    private fun convertNameToJavaPath(recipeName: String): String =
        recipeName.replace('.', '/').removeSuffix("Recipe") + ".java"

    fun githubUrl(recipeName: String, source: URI): String {
        val sourceString = source.toString()
        val baseUrl = if (isFromCoreLibrary()) {
            "https://github.com/openrewrite/rewrite/blob/main/$artifactId/src/main"
        } else {
            "https://github.com/openrewrite/$artifactId/blob/main/src/main"
        }

        // YAML recipes will have a source that ends with META-INF/rewrite/something.yml
        return if (sourceString.substring(sourceString.length - 3) == "yml") {
            val ymlPath = sourceString.substring(source.toString().lastIndexOf("META-INF"))
            "$baseUrl/resources/$ymlPath"
        } else {
            baseUrl + "/java/" + convertNameToJavaPath(recipeName)
        }
    }

    fun issueTrackerUrl() =
        if (isFromCoreLibrary()) {
            "https://github.com/openrewrite/rewrite/issues"
        } else {
            "https://github.com/openrewrite/$artifactId/issues"
        }

    companion object {
        private val parsePattern = Pattern.compile("([^:]+):([^:]+):([^:]+):(.+)")
        private val coreLibs = setOf(
            "rewrite-core", "rewrite-gradle", "rewrite-groovy", "rewrite-hcl", "rewrite-java", "rewrite-json", "rewrite-maven", "rewrite-properties", "rewrite-xml", "rewrite-yaml"
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
