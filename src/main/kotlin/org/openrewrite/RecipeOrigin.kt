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
    private val artifactIdToBaseGithubUrl = mapOf(
        "rewrite-circleci" to "https://github.com/openrewrite/rewrite-circleci/blob/main/src/main",
        "rewrite-cloud-suitability-analyzer" to "https://github.com/openrewrite/rewrite-cloud-suitability-analyzer/blob/main/src/main",
        "rewrite-concourse" to "https://github.com/openrewrite/rewrite-concourse/blob/main/src/main",
        "rewrite-core" to "https://github.com/openrewrite/rewrite/blob/main/rewrite-core/src/main",
        "rewrite-github-actions" to "https://github.com/openrewrite/rewrite-github-actions/blob/main/src/main",
        "rewrite-gradle" to "https://github.com/openrewrite/rewrite/blob/main/rewrite-gradle/src/main",
        "rewrite-groovy" to "https://github.com/openrewrite/rewrite/blob/main/rewrite-groovy/src/main",
        "rewrite-hcl" to "https://github.com/openrewrite/rewrite/blob/main/rewrite-hcl/src/main",
        "rewrite-java" to "https://github.com/openrewrite/rewrite/blob/main/rewrite-java/src/main",
        "rewrite-java-security" to "https://github.com/openrewrite/rewrite-java-security/blob/main/src/main",
        "rewrite-jhipster" to "https://github.com/openrewrite/rewrite-jhipster/blob/main/src/main",
        "rewrite-json" to "https://github.com/openrewrite/rewrite/blob/main/rewrite-json/src/main",
        "rewrite-kubernetes" to "https://github.com/openrewrite/rewrite-kubernetes/blob/main/src/main",
        "rewrite-logging-frameworks" to "https://github.com/openrewrite/rewrite-logging-frameworks/blob/main/src/main",
        "rewrite-maven" to "https://github.com/openrewrite/rewrite/blob/main/rewrite-maven/src/main",
        "rewrite-micronaut" to "https://github.com/openrewrite/rewrite-micronaut/blob/main/src/main",
        "rewrite-migrate-java" to "https://github.com/openrewrite/rewrite-migrate-java/blob/main/src/main",
        "rewrite-properties" to "https://github.com/openrewrite/rewrite/blob/main/rewrite-properties/src/main",
        "rewrite-quarkus" to "https://github.com/openrewrite/rewrite-quarkus/blob/main/src/main",
        "rewrite-spring" to "https://github.com/openrewrite/rewrite-spring/blob/main/src/main",
        "rewrite-terraform" to "https://github.com/openrewrite/rewrite-terraform/blob/main/src/main",
        "rewrite-testing-frameworks" to "https://github.com/openrewrite/rewrite-testing-frameworks/blob/main/src/main",
        "rewrite-xml" to "https://github.com/openrewrite/rewrite/blob/main/rewrite-xml/src/main",
        "rewrite-yaml" to "https://github.com/openrewrite/rewrite/blob/main/rewrite-yaml/src/main",
    )

    /**
     * The build plugins automatically have dependencies on the core libraries.
     * It isn't necessary to explicitly take dependencies on the core libraries to access their recipes.
     * So explicit dependencies are only necessary when this returns "false"
     */
    fun isFromCoreLibrary() = groupId == "org.openrewrite" && coreLibs.contains(artifactId)

    private fun convertNameToJavaPath(recipeName: String): String =
        recipeName.replace('.', '/') + ".java"

    fun githubUrl(recipeName: String, source: URI): String {
        val sourceString = source.toString()

        // YAML recipes will have a source that ends with META-INF/rewrite/something.yml
        if (sourceString.substring(sourceString.length - 3) == "yml") {
            val ymlPath = sourceString.substring(source.toString().lastIndexOf("META-INF"))
            return artifactIdToBaseGithubUrl[artifactId].toString() + "/resources/" + ymlPath
        } else {
            return artifactIdToBaseGithubUrl[artifactId].toString() + "/java/" + convertNameToJavaPath(recipeName)
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
