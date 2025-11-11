package org.openrewrite

import org.openrewrite.config.DeclarativeRecipe
import org.openrewrite.config.RecipeDescriptor
import picocli.CommandLine
import picocli.CommandLine.*
import picocli.CommandLine.Option
import java.io.BufferedWriter
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import kotlin.system.exitProcess

@Command(
    name = "rewrite-recipe-markdown-generator",
    mixinStandardHelpOptions = true,
    description = ["Generates documentation for OpenRewrite recipes in markdown format"],
    version = ["1.0.0-SNAPSHOT"]
)
class RecipeMarkdownGenerator : Runnable {
    @Parameters(index = "0", description = ["Destination directory for generated recipe markdown"])
    lateinit var destinationDirectoryName: String

    @Parameters(
        index = "1", defaultValue = "", description = ["A ';' delineated list of coordinates to search for recipes. " +
                "Each entry in the list must be of format groupId:artifactId:version:path where 'path' is a file path to the jar"]
    )
    lateinit var recipeSources: String

    @Parameters(
        index = "2", defaultValue = "", description = ["A ';' delineated list of jars that provide the full " +
                "transitive dependency list for the recipeSources"]
    )
    lateinit var recipeClasspath: String

    @Parameters(
        index = "3",
        defaultValue = "latest.release",
        description = ["The version of the rewrite-bom to display on the changelog page"]
    )
    lateinit var rewriteBomVersion: String

    @Parameters(
        index = "4",
        defaultValue = "latest.release",
        description = ["The version of the rewrite-recipe-bom to display on all module versions page"]
    )
    lateinit var rewriteRecipeBomVersion: String

    @Parameters(
        index = "5",
        defaultValue = "latest.release",
        description = ["The version of the moderne-recipe-bom to display on all module versions page"]
    )
    lateinit var moderneRecipeBomVersion: String

    @Parameters(
        index = "6",
        defaultValue = "latest.release",
        description = ["The version of the Rewrite Gradle Plugin to display in relevant samples"]
    )
    lateinit var gradlePluginVersion: String

    @Parameters(
        index = "7",
        defaultValue = "",
        description = ["The version of the Rewrite Maven Plugin to display in relevant samples"]
    )
    lateinit var mavenPluginVersion: String

    @Option(names = ["--latest-versions-only"])
    var latestVersionsOnly: Boolean = false

    override fun run() {
        val outputPath = Paths.get(destinationDirectoryName)
        val recipesPath = outputPath.resolve("recipes")
        try {
            Files.createDirectories(recipesPath)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

        val recipeOrigins: Map<URI, RecipeOrigin> = RecipeOrigin.parse(recipeSources)

        // Add manifest information
        val recipeLoader = RecipeLoader(recipeClasspath, recipeOrigins)
        recipeLoader.addInfosFromManifests()

        // Write latest-versions-of-every-openrewrite-module.md, for all recipe modules
        val versionWriter = VersionWriter()
        versionWriter.createLatestVersionsJs(
            outputPath,
            recipeOrigins.values,
            rewriteRecipeBomVersion,
            gradlePluginVersion,
            mavenPluginVersion
        )
        versionWriter.createLatestVersionsMarkdown(
            outputPath,
            recipeOrigins.values,
            rewriteBomVersion,
            rewriteRecipeBomVersion,
            moderneRecipeBomVersion,
            gradlePluginVersion,
            mavenPluginVersion
        )

        if (latestVersionsOnly) {
            return
        }

        // Load recipe details into memory
        val loadResult = recipeLoader.loadRecipes()
        val allRecipeDescriptors = loadResult.allRecipeDescriptors
        val allCategoryDescriptors = loadResult.allCategoryDescriptors
        val allRecipes = loadResult.allRecipes
        val recipeToSource = loadResult.recipeToSource

        println("Found ${allRecipeDescriptors.size} descriptor(s).")

        val markdownArtifacts = TreeMap<String, MarkdownRecipeArtifact>()
        val moderneProprietaryRecipes = TreeMap<String, MutableList<RecipeDescriptor>>()

        // Build mapping from recipe name to Recipe instance (for checking if declarative)
        val recipesByName = allRecipes.associateBy { it.name }

        // Build reverse mapping of recipe relationships (which recipes contain each recipe)
        val recipeContainedBy = mutableMapOf<String, MutableSet<RecipeDescriptor>>()
        for (parentRecipe in allRecipeDescriptors) {
            for (childRecipe in parentRecipe.recipeList) {
                recipeContainedBy.computeIfAbsent(childRecipe.name) { mutableSetOf() }.add(parentRecipe)
            }
        }

        // Create the recipe docs
        val recipeMarkdownWriter = RecipeMarkdownWriter(recipeContainedBy, recipeToSource)
        for (recipeDescriptor in allRecipeDescriptors) {
            val recipeSource = recipeToSource[recipeDescriptor.name]
            requireNotNull(recipeSource) { "Could not find source URI for recipe " + recipeDescriptor.name }

            var origin: RecipeOrigin?
            var rawUri = recipeSource.toString()
            val exclamationIndex = rawUri.indexOf('!')
            if (exclamationIndex == -1) {
                origin = recipeOrigins[recipeSource]
            } else {
                // The recipe origin includes the path to the recipe within a jar
                // Such URIs will look something like: jar:file:/path/to/the/recipes.jar!META-INF/rewrite/some-declarative.yml
                // Strip the "jar:" prefix and the part of the URI pointing inside the jar
                rawUri = rawUri.substring(0, exclamationIndex)
                rawUri = rawUri.substring(4)
                val jarOnlyUri = URI.create(rawUri)
                origin = recipeOrigins[jarOnlyUri]
            }
            requireNotNull(origin) { "Could not find GAV coordinates of recipe " + recipeDescriptor.name + " from " + recipeSource }
            recipeMarkdownWriter.writeRecipe(recipeDescriptor, recipesPath, origin)

            if (origin.license == Licenses.Proprietary) {
                moderneProprietaryRecipes.computeIfAbsent(origin.artifactId) { mutableListOf() }.add(recipeDescriptor)
            }

            val recipeOptions = TreeSet<RecipeOption>()
            for (recipeOption in recipeDescriptor.options) {
                val name = recipeOption.name as String
                val ro = RecipeOption(name, recipeOption.type, recipeOption.isRequired)
                recipeOptions.add(ro)
            }

            // Changes something like org.openrewrite.circleci.InstallOrb to https://docs.openrewrite.org/recipes/circleci/installorb
            val docLink = "https://docs.openrewrite.org/recipes/" + getRecipePath(recipeDescriptor)

            // Determine if recipe is imperative (Java) or declarative (YAML)
            // Used to help with time spent calculations. Imperative = 12 hours, Declarative = 4 hours
            val recipe = recipesByName[recipeDescriptor.name]
            val isImperative = recipe !is DeclarativeRecipe

            // Used to create changelogs
            val markdownRecipeDescriptor =
                MarkdownRecipeDescriptor(
                    recipeDescriptor.name,
                    recipeDescriptor.description,
                    docLink,
                    recipeOptions,
                    isImperative,
                    origin.artifactId
                )
            val markdownArtifact = markdownArtifacts.computeIfAbsent(origin.artifactId) {
                MarkdownRecipeArtifact(
                    origin.artifactId,
                    origin.version,
                    TreeMap<String, MarkdownRecipeDescriptor>(),
                )
            }
            markdownArtifact.markdownRecipeDescriptors[recipeDescriptor.name] = markdownRecipeDescriptor
        }

        // Write the README.md for each category
        CategoryWriter(allRecipeDescriptors, allCategoryDescriptors)
            .writeCategories(outputPath)

        // Create changelog markdown, and update tracking file
        ChangelogWriter().createRecipeDescriptorsYaml(
            markdownArtifacts,
            allRecipeDescriptors.size,
            rewriteBomVersion
        )

        // Write lists of recipes into various files
        val listWriter = ListsOfRecipesWriter(allRecipeDescriptors, outputPath)
        listWriter.createModerneRecipes(moderneProprietaryRecipes)
        listWriter.createRecipesWithDataTables()
        listWriter.createRecipesByTag()
        listWriter.createScanningRecipes(
            allRecipes.filter { it is ScanningRecipe<*> && it !is DeclarativeRecipe },
            recipeOrigins,
            recipeToSource
        )
        listWriter.createStandaloneRecipes(recipeContainedBy, recipeOrigins, recipeToSource)
    }


    companion object {
        private val SPRING_BOOT_UPGRADE_PATTERN = Regex("^(io\\.moderne|org\\.openrewrite)\\.java\\.spring\\.boot(\\d+)\\.UpgradeSpringBoot_(\\d+)_(\\d+)$")

        /**
         * Call Closable.use() together with apply() to avoid adding two levels of indentation
         */
        fun BufferedWriter.useAndApply(withFun: BufferedWriter.() -> Unit): Unit = use { it.apply(withFun) }

        fun BufferedWriter.writeln(text: String) {
            write(text)
            newLine()
        }

        fun getRecipePath(recipe: RecipeDescriptor): String =
        // Docusaurus expects that if a file is called "assertj" inside of the folder "assertj" that it's the
        // README for said folder. Due to how generic we've made this recipe name, we need to change it for the
            // docs so that they parse correctly.
            if (recipePathToDocusaurusRenamedPath.containsKey(recipe.name)) {
                recipePathToDocusaurusRenamedPath[recipe.name]!!
            } else if (isSpringBoot34OrHigher(recipe.name)) {
                // The moderne and community spring boot recipes clashes with one another (deviating since 3.4) so let's make them distinct
                generateSpringBootUpgradePath(recipe.name)
            } else if (recipe.name.startsWith("org.openrewrite")) {
                // If the recipe path only has two periods, it's part of the core recipes and should be adjusted accordingly.
                if (recipe.name.count { it == '.' } == 2) {
                    "core/" + recipe.name
                        .substring(16)
                        .lowercase(Locale.getDefault())
                } else {
                    recipe.name.substring(16).replace("\\.".toRegex(), "/").lowercase(Locale.getDefault())
                }
            } else if (recipe.name.startsWith("io.moderne")) {
                recipe.name.substring(11).replace("\\.".toRegex(), "/").lowercase(Locale.getDefault())
            } else if (
                recipe.name.startsWith("ai.timefold") ||
                recipe.name.startsWith("com.google") ||
                recipe.name.startsWith("com.oracle") ||
                recipe.name.startsWith("io.quarkus") ||
                recipe.name.startsWith("io.quakus") ||
                recipe.name.startsWith("org.apache") ||
                recipe.name.startsWith("org.axonframework") ||
                recipe.name.startsWith("software.amazon.awssdk") ||
                recipe.name.startsWith("tech.picnic")
            ) {
                recipe.name.replace("\\.".toRegex(), "/").lowercase(Locale.getDefault())
            } else {
                throw RuntimeException("Recipe package unrecognized: ${recipe.name}")
            }

        private val recipePathToDocusaurusRenamedPath: Map<String, String> = mapOf(
            "org.openrewrite.java.testing.assertj.Assertj" to "java/testing/assertj/assertj-best-practices",
            "org.openrewrite.java.migrate.javaee7" to "java/migrate/javaee7-recipe",
            "org.openrewrite.java.migrate.javaee8" to "java/migrate/javaee8-recipe"
        )

        private fun isSpringBoot34OrHigher(recipeName: String): Boolean {
            val matchResult = SPRING_BOOT_UPGRADE_PATTERN.find(recipeName) ?: return false
            val (_, _, upgradeMajor, upgradeMinor) = matchResult.destructured
            val upgradeMajorInt = upgradeMajor.toInt()
            val upgradeMinorInt = upgradeMinor.toInt()
            return upgradeMajorInt > 3 || (upgradeMajorInt == 3 && upgradeMinorInt >= 4)
        }

        private fun generateSpringBootUpgradePath(recipeName: String): String {
            val matchResult = SPRING_BOOT_UPGRADE_PATTERN.find(recipeName)

            return if (matchResult != null) {
                val (organization, majorVersion, upgradeMajor, upgradeMinor) = matchResult.destructured
                val edition = if (organization == "io.moderne") "moderne-edition" else "community-edition"
                "java/spring/boot$majorVersion/upgradespringboot_${upgradeMajor}_$upgradeMinor-$edition"
            } else {
                throw RuntimeException("Invalid Spring Boot upgrade recipe format: $recipeName")
            }
        }

        @JvmStatic
        fun main(args: Array<String>) {
            val exitCode = CommandLine(RecipeMarkdownGenerator()).execute(*args)
            exitProcess(exitCode)
        }
    }
}
