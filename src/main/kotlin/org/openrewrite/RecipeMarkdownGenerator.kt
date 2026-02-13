@file:Suppress("SENSELESS_COMPARISON")

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

    @Parameters(
        index = "8",
        defaultValue = "",
        description = ["Secondary destination directory for Moderne docs (contains all recipes including proprietary)"]
    )
    lateinit var moderneDestinationDirectoryName: String

    @Option(names = ["--latest-versions-only"])
    var latestVersionsOnly: Boolean = false

    override fun run() {
        // OpenRewrite docs output (open-source recipes only)
        val outputPath = Paths.get(destinationDirectoryName)
        val recipesPath = outputPath.resolve("recipes")

        // Moderne docs output (ALL recipes including proprietary)
        val moderneOutputPath = if (moderneDestinationDirectoryName.isNotEmpty()) {
            Paths.get(moderneDestinationDirectoryName)
        } else {
            null
        }
        val moderneRecipeCatalogPath = moderneOutputPath?.resolve("recipe-catalog")
        val moderneListsPath = moderneOutputPath?.resolve("lists")

        try {
            Files.createDirectories(recipesPath)
            moderneRecipeCatalogPath?.let { Files.createDirectories(it) }
            moderneListsPath?.let { Files.createDirectories(it) }
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

        var recipeOrigins: Map<URI, RecipeOrigin> = RecipeOrigin.parse(recipeSources)

        // Add manifest information
        val recipeLoader = RecipeLoader(recipeClasspath, recipeOrigins)
        recipeLoader.addInfosFromManifests()

        // Write latest-versions-of-every-openrewrite-module.md, for all recipe modules
        val versionWriter = VersionWriter()
        // OpenRewrite docs
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
        // Moderne docs
        if (moderneOutputPath != null) {
            versionWriter.createLatestVersionsJs(
                moderneOutputPath,
                recipeOrigins.values,
                rewriteRecipeBomVersion,
                gradlePluginVersion,
                mavenPluginVersion
            )
            versionWriter.createLatestVersionsMarkdown(
                moderneOutputPath,
                recipeOrigins.values,
                rewriteBomVersion,
                rewriteRecipeBomVersion,
                moderneRecipeBomVersion,
                gradlePluginVersion,
                mavenPluginVersion
            )
        }

        if (latestVersionsOnly) {
            return
        }

        // Load recipe details into memory
        val loadResult = recipeLoader.loadRecipes()
        val allRecipeDescriptors = loadResult.allRecipeDescriptors
        val allCategoryDescriptors = loadResult.allCategoryDescriptors
        val allRecipes = loadResult.allRecipes
        val recipeToSource = loadResult.recipeToSource

        // Merge synthetic origins from Python/TypeScript recipe loaders
        if (loadResult.additionalOrigins.isNotEmpty()) {
            recipeOrigins = recipeOrigins + loadResult.additionalOrigins
        }

        // Python recipes are always proprietary
        recipeOrigins.values
            .filter { it.artifactId in PythonRecipeLoader.PYTHON_RECIPE_MODULES }
            .forEach { it.license = Licenses.Proprietary }

        println("Found ${allRecipeDescriptors.size} descriptor(s).")

        // Detect conflicting paths between io.moderne and org.openrewrite recipes
        initializeConflictDetection(allRecipeDescriptors)

        val markdownArtifacts = TreeMap<String, MarkdownRecipeArtifact>()
        val moderneProprietaryRecipes = TreeMap<String, MutableList<RecipeDescriptor>>()

        // Build mapping from recipe name to Recipe instance (for checking if declarative)
        val recipesByName = allRecipes.associateBy { it.name }

        // Build reverse mapping of recipe relationships (which recipes contain each recipe)
        val recipeContainedBy = mutableMapOf<String, MutableSet<RecipeDescriptor>>()
        for (parentRecipe in allRecipeDescriptors) {
            if (parentRecipe.recipeList != null) {
                for (childRecipe in parentRecipe.recipeList) {
                    recipeContainedBy.computeIfAbsent(childRecipe.name) { mutableSetOf() }.add(parentRecipe)
                }
            }
        }

        // Build set of proprietary recipe names (for cross-reference link handling)
        val proprietaryRecipeNames = allRecipeDescriptors
            .filter { recipe ->
                val source = recipeToSource[recipe.name]
                val origin = findOrigin(source, recipe.name, recipeOrigins)
                origin?.license == Licenses.Proprietary ||
                    source?.toString()?.startsWith("typescript-search://") == true ||
                    source?.toString()?.startsWith("python-search://") == true
            }
            .map { it.name }
            .toSet()

        // Create the recipe docs
        // We use two writers: one for OpenRewrite docs (open-source only), one for Moderne docs (all recipes)
        val recipeMarkdownWriter = RecipeMarkdownWriter(recipeContainedBy, recipeToSource, proprietaryRecipeNames, forModerneDocs = false)
        val moderneRecipeMarkdownWriter = if (moderneRecipeCatalogPath != null) {
            RecipeMarkdownWriter(recipeContainedBy, recipeToSource, proprietaryRecipeNames, forModerneDocs = true)
        } else null

        for (recipeDescriptor in allRecipeDescriptors) {
            val recipeSource = recipeToSource[recipeDescriptor.name]
            requireNotNull(recipeSource) { "Could not find source URI for recipe " + recipeDescriptor.name }

            val origin = findOrigin(recipeSource, recipeDescriptor.name, recipeOrigins)
            requireNotNull(origin) { "Could not find GAV coordinates of recipe " + recipeDescriptor.name + " from " + recipeSource }

            // Always write to Moderne docs (ALL recipes)
            moderneRecipeMarkdownWriter?.writeRecipe(recipeDescriptor, moderneRecipeCatalogPath!!, origin)

            // Track proprietary recipes separately (for moderne-recipes.md list)
            if (origin.license == Licenses.Proprietary) {
                moderneProprietaryRecipes.computeIfAbsent(origin.artifactId) { mutableListOf() }.add(recipeDescriptor)
                // Skip writing proprietary recipes to rewrite-docs (they only go to moderne-docs)
                continue
            }

            // Write non-proprietary recipes to OpenRewrite docs
            recipeMarkdownWriter.writeRecipe(recipeDescriptor, recipesPath, origin)

            val recipeOptions = TreeSet<RecipeOption>()
            if (recipeDescriptor.options != null) {
                for (recipeOption in recipeDescriptor.options) {
                    // TypeScript recipes may have null name or type
                    val name = recipeOption.name?.toString() ?: "unknown"
                    val type = recipeOption.type ?: "String"
                    val ro = RecipeOption(name, type, recipeOption.isRequired)
                    recipeOptions.add(ro)
                }
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

        // Filter to only open-source recipes for rewrite-docs output
        val openSourceRecipeDescriptors = allRecipeDescriptors.filter { recipe ->
            val source = recipeToSource[recipe.name]
            val origin = findOrigin(source, recipe.name, recipeOrigins)
            origin?.license != Licenses.Proprietary
        }
        println("Filtered to ${openSourceRecipeDescriptors.size} open-source recipe(s) for rewrite-docs.")

        // Write the README.md for each category
        // OpenRewrite docs: open-source only (in "recipes" subdir)
        CategoryWriter(openSourceRecipeDescriptors, allCategoryDescriptors, "/recipes")
            .writeCategories(outputPath, "recipes")
        // Moderne docs: ALL recipes (in "recipe-catalog" subdir to match workflow expectations)
        if (moderneOutputPath != null) {
            CategoryWriter(allRecipeDescriptors, allCategoryDescriptors, "/user-documentation/recipes/recipe-catalog")
                .writeCategories(moderneOutputPath, "recipe-catalog")
        }

        // Create changelog markdown, and update tracking file
        ChangelogWriter().createRecipeDescriptorsYaml(
            markdownArtifacts,
            openSourceRecipeDescriptors.size,
            rewriteBomVersion,
            outputPath
        )

        // Write lists of recipes into various files
        // OpenRewrite docs: open-source recipes only (links use /recipes path)
        val listWriter = ListsOfRecipesWriter(openSourceRecipeDescriptors, outputPath, "/recipes")
        listWriter.createModerneRecipes(moderneProprietaryRecipes)
        listWriter.createRecipesWithDataTables()
        listWriter.createRecipesByTag()
        listWriter.createScanningRecipes(
            allRecipes.filter { recipe ->
                recipe is ScanningRecipe<*> && recipe !is DeclarativeRecipe &&
                openSourceRecipeDescriptors.any { it.name == recipe.name }
            },
            recipeOrigins,
            recipeToSource
        )
        listWriter.createStandaloneRecipes(recipeContainedBy, recipeOrigins, recipeToSource)
        listWriter.createAllRecipesByModule(recipeOrigins, recipeToSource)

        // Moderne docs: ALL recipes (links use /user-documentation/recipes/recipe-catalog path)
        if (moderneListsPath != null) {
            val moderneListWriter = ListsOfRecipesWriter(allRecipeDescriptors, moderneListsPath, "/user-documentation/recipes/recipe-catalog")
            moderneListWriter.createRecipesWithDataTables()
            moderneListWriter.createRecipesByTag()
            moderneListWriter.createScanningRecipes(
                allRecipes.filter { recipe ->
                    recipe is ScanningRecipe<*> && recipe !is DeclarativeRecipe
                },
                recipeOrigins,
                recipeToSource
            )
            moderneListWriter.createStandaloneRecipes(recipeContainedBy, recipeOrigins, recipeToSource)
            moderneListWriter.createAllRecipesByModule(recipeOrigins, recipeToSource)
        }

        // Generate redirects for proprietary recipes (from old OpenRewrite URLs to Moderne docs)
        val allProprietaryRecipes = moderneProprietaryRecipes.values.flatten()
        if (allProprietaryRecipes.isNotEmpty()) {
            RedirectWriter.writeRedirectConfig(
                outputPath,
                allProprietaryRecipes,
                "https://docs.moderne.io",
                "/user-documentation/recipes/recipe-catalog"
            )
        }

        // Generate redirects for categories that only exist in Moderne docs
        RedirectWriter.writeCategoryRedirects(
            outputPath,
            allRecipeDescriptors,
            openSourceRecipeDescriptors,
            "https://docs.moderne.io",
            "/user-documentation/recipes/recipe-catalog"
        )
    }


    companion object {
        // Set of base paths that have both io.moderne and org.openrewrite recipes (conflicts)
        private var conflictingBasePaths: Set<String> = emptySet()

        /**
         * Initialize conflict detection by scanning all recipe descriptors.
         * Must be called before any getRecipePath() calls.
         */
        fun initializeConflictDetection(allDescriptors: Collection<RecipeDescriptor>) {
            val moderneBasePaths = mutableSetOf<String>()
            val openrewriteBasePaths = mutableSetOf<String>()

            for (descriptor in allDescriptors) {
                val name = descriptor.name
                when {
                    name.startsWith("io.moderne") -> {
                        moderneBasePaths.add(getBasePath(name))
                    }
                    name.startsWith("org.openrewrite") -> {
                        openrewriteBasePaths.add(getBasePath(name))
                    }
                }
            }

            // Find paths that exist in both sets
            conflictingBasePaths = moderneBasePaths.intersect(openrewriteBasePaths)
        }

        /**
         * Compute the base path for a recipe name (without any edition suffix).
         * This is used for conflict detection.
         */
        private fun getBasePath(recipeName: String): String {
            return when {
                recipeName.startsWith("org.openrewrite") -> {
                    if (recipeName.count { it == '.' } == 2) {
                        "core/" + recipeName.substring(16).lowercase(Locale.getDefault())
                    } else {
                        recipeName.substring(16).replace("\\.".toRegex(), "/").lowercase(Locale.getDefault())
                    }
                }
                recipeName.startsWith("io.moderne") -> {
                    recipeName.substring(11).replace("\\.".toRegex(), "/").lowercase(Locale.getDefault())
                }
                else -> {
                    recipeName.replace("\\.".toRegex(), "/").lowercase(Locale.getDefault())
                }
            }
        }

        /**
         * Find the RecipeOrigin for a given source URI.
         * Handles TypeScript recipes (typescript-search:// scheme) and JAR recipes.
         */
        fun findOrigin(source: URI?, recipeName: String, recipeOrigins: Map<URI, RecipeOrigin>): RecipeOrigin? {
            if (source == null) return null

            val rawUri = source.toString()

            // Handle TypeScript recipes with custom URI scheme
            if (rawUri.startsWith("typescript-search://")) {
                val artifactId = rawUri.substringAfter("typescript-search://").substringBefore("/")
                return recipeOrigins.values.firstOrNull { it.artifactId == artifactId }
            }

            // Handle Python recipes with custom URI scheme
            if (rawUri.startsWith("python-search://")) {
                val artifactId = rawUri.substringAfter("python-search://").substringBefore("/")
                return recipeOrigins.values.firstOrNull { it.artifactId == artifactId }
            }

            // Handle JAR URIs (e.g., jar:file:/path/to/recipes.jar!META-INF/rewrite/some.yml)
            val exclamationIndex = rawUri.indexOf('!')
            val origin = if (exclamationIndex == -1) {
                recipeOrigins[source]
            } else {
                // Strip the "jar:" prefix and the part after the "!"
                val jarOnlyUri = URI.create(rawUri.substring(4, exclamationIndex))
                recipeOrigins[jarOnlyUri]
            }

            if (origin == null) return null

            // When multiple JARs share the same artifactId (e.g. org.openrewrite.recipe:rewrite-prethink and
            // io.moderne.recipe:rewrite-prethink), the recipe may have been loaded from the wrong JAR due to
            // classloader ordering. Prefer the origin whose groupId prefix matches the recipe name's package prefix.
            val recipePrefix = recipeName.substringBeforeLast('.')
            val originGroupPrefix = origin.groupId.substringBeforeLast('.')
            if (!recipePrefix.startsWith(originGroupPrefix)) {
                val betterOrigin = recipeOrigins.values.firstOrNull {
                    it.artifactId == origin.artifactId && recipePrefix.startsWith(it.groupId.substringBeforeLast('.'))
                }
                if (betterOrigin != null) {
                    return betterOrigin
                }
            }

            return origin
        }

        /**
         * Call Closable.use() together with apply() to avoid adding two levels of indentation
         */
        fun BufferedWriter.useAndApply(withFun: BufferedWriter.() -> Unit): Unit = use { it.apply(withFun) }

        fun BufferedWriter.writeln(text: String) {
            write(text)
            newLine()
        }

        // Docusaurus expects that if a file is called "assertj" inside of the folder "assertj" that it's the
        // README for said folder. Due to how generic we've made this recipe name, we need to change it for the
        // docs so that they parse correctly.
        fun getRecipePath(recipe: RecipeDescriptor): String {
            // Check for manual overrides first
            if (recipePathToDocusaurusRenamedPath.containsKey(recipe.name)) {
                return recipePathToDocusaurusRenamedPath[recipe.name]!!
            }

            val basePath = getBasePath(recipe.name)

            // Add edition suffix only if there's a detected conflict
            val needsSuffix = conflictingBasePaths.contains(basePath)

            return when {
                recipe.name.startsWith("org.openrewrite") -> {
                    if (needsSuffix) basePath + "-community-edition" else basePath
                }
                recipe.name.startsWith("io.moderne") -> {
                    if (needsSuffix) basePath + "-moderne-edition" else basePath
                }
                recipe.name.startsWith("ai.timefold") ||
                recipe.name.startsWith("com.google") ||
                recipe.name.startsWith("com.oracle") ||
                recipe.name.startsWith("io.quarkus") ||
                recipe.name.startsWith("io.quakus") ||
                recipe.name.startsWith("org.apache") ||
                recipe.name.startsWith("org.axonframework") ||
                recipe.name.startsWith("org.jetbrains") ||
                recipe.name.startsWith("software.amazon.awssdk") ||
                recipe.name.startsWith("tech.picnic") -> {
                    basePath
                }
                else -> {
                    throw RuntimeException("Recipe package unrecognized: ${recipe.name}")
                }
            }
        }

        private val recipePathToDocusaurusRenamedPath: Map<String, String> = mapOf(
            "org.openrewrite.java.testing.assertj.Assertj" to "java/testing/assertj/assertj-best-practices",
            "org.openrewrite.java.migrate.javaee7" to "java/migrate/javaee7-recipe",
            "org.openrewrite.java.migrate.javaee8" to "java/migrate/javaee8-recipe"
        )

        @JvmStatic
        fun main(args: Array<String>) {
            val exitCode = CommandLine(RecipeMarkdownGenerator()).execute(*args)
            exitProcess(exitCode)
        }
    }
}
