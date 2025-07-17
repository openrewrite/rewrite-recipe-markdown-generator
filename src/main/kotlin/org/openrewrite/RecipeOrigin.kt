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
    var repositoryUrl: String = ""
    var license: License = Licenses.Unknown
    /**
     * The build plugins automatically have dependencies on the core libraries.
     * It isn't necessary to explicitly take dependencies on the core libraries to access their recipes.
     * So explicit dependencies are only necessary when this returns "false"
     */
    fun isFromCoreLibrary() = groupId == "org.openrewrite" && coreLibs.contains(artifactId)

    private fun convertNameToJavaPath(recipeName: String): String {
        // These recipes are not Refaster recipes and should keep
        // Recipe or Recipes in their name when linking to them.
        val recipesToNotReplace = listOf(
            "org.openrewrite.config.CompositeRecipe",
            "org.openrewrite.java.migrate.util.OptionalStreamRecipe",
            "org.openrewrite.java.recipes.FindRecipes",
            "org.openrewrite.java.recipes.NoMutableStaticFieldsInRecipes",
            "org.openrewrite.java.spring.boot2.search.IntegrationSchedulerPoolRecipe"
        )

        val updatedRecipeName = recipeName.replace('.', '/')

        return if (recipesToNotReplace.contains(recipeName)) {
            "$updatedRecipeName.java"
        } else {
            updatedRecipeName
                .replace(Regex("\\$.*"), "")
                .replace(Regex("Recipes?$"), "") + ".java"
        }
    }

    fun githubUrl(recipeName: String, source: URI): String {
        //todo we can remove this I think, as third party recipes can now define there very own License
        if (artifactId == "rewrite-third-party") {
            return "https://github.com/search?type=code&q=$recipeName"
        }

        val sourceString = source.toString()

        // YAML recipes will have a source that ends with META-INF/rewrite/something.yml
        return if (sourceString.substring(sourceString.length - 3) == "yml") {
            val ymlPath = sourceString.substring(source.toString().lastIndexOf("META-INF"))
            "${repositoryUrl.removeSuffix("/")}/src/main/resources/${ymlPath.removePrefix("/")}"
        } else {
            val javaPath = convertNameToJavaPath(recipeName)
            "${repositoryUrl.removeSuffix("/")}/src/main/java/${javaPath.removePrefix("/")}"
        }
    }

    fun versionPlaceholderKey() = "VERSION_${groupId}_${artifactId}"
        .uppercase()
        .replace('-', '_')
        .replace('.', '_')

    fun issueTrackerUrl() = repositoryUrl.replace(Regex("/blob/main/.*"), "/issues")

    companion object {
        private val parsePattern = Pattern.compile("([^:]+):([^:]+):([^:]+):(.+)")
        private val coreLibs = setOf(
            "rewrite-core",
            "rewrite-gradle",
            "rewrite-groovy",
            "rewrite-hcl",
            "rewrite-java",
            "rewrite-java-test",
            "rewrite-json",
            "rewrite-kotlin",
            "rewrite-maven",
            "rewrite-properties",
            "rewrite-protobuf",
            "rewrite-test",
            "rewrite-toml",
            "rewrite-xml",
            "rewrite-yaml"
        )

        fun fromString(encoded: String): RecipeOrigin {
            val m = parsePattern.matcher(encoded)
            require(m.matches()) { "Couldn't parse as a RecipeOrigin: $encoded" }
            return RecipeOrigin(m.group(1), m.group(2), m.group(3), Paths.get(m.group(4)).toUri())
        }

        fun parse(text: String): Map<URI, RecipeOrigin> {
            if (text.isBlank()) {
                return emptyMap()
            }
            return text.split(";").asSequence()
                .filter { it.isNotBlank() }
                .map(Companion::fromString)
                .sortedWith(compareBy({ it.groupId }, { it.artifactId }, { it.version }))
                .associateBy { it.jarLocation }
        }
    }
}
