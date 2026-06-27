@file:Suppress("SENSELESS_COMPARISON")

package org.openrewrite

import org.openrewrite.config.CategoryDescriptor
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

    @Option(
        names = ["--only-artifacts"],
        split = ",",
        description = ["Restrict recipe loading to these artifactIds (e.g. rewrite-circleci). Greatly " +
            "speeds up local testing; the full classpath is still resolved for transitive/delegatesTo lookups. " +
            "Sub-recipes from artifacts not in the list will have empty links."]
    )
    var onlyArtifacts: List<String> = emptyList()

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
        if (onlyArtifacts.isNotEmpty()) {
            val keep = onlyArtifacts.toSet()
            recipeOrigins = recipeOrigins.filterValues { it.artifactId in keep }
            println("Restricting recipe loading to ${recipeOrigins.size} artifact(s) matching $keep")
        }

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
        val crossCategoryPaths = loadResult.crossCategoryPaths

        // Merge synthetic origins from Python/TypeScript recipe loaders
        if (loadResult.additionalOrigins.isNotEmpty()) {
            recipeOrigins = recipeOrigins + loadResult.additionalOrigins
        }

        // Python recipes are always proprietary
        recipeOrigins.values
            .filter { it.artifactId in PythonRecipeLoader.PYTHON_RECIPE_MODULES }
            .forEach { it.license = Licenses.Proprietary }

        // C# recipes are always proprietary
        recipeOrigins.values
            .filter { it.artifactId in CSharpRecipeLoader.CSHARP_RECIPE_MODULES }
            .forEach { it.license = Licenses.Proprietary }

        println("Found ${allRecipeDescriptors.size} descriptor(s).")

        // Detect conflicting paths between io.moderne and org.openrewrite recipes
        initializeConflictDetection(allRecipeDescriptors)

        // Determine which root categories (e.g. com, io, org) to hide from the recipe catalog
        initializeRootCategories(allCategoryDescriptors)

        val markdownArtifacts = TreeMap<String, MarkdownRecipeArtifact>()
        val moderneOnlyRecipes = TreeMap<String, MutableList<RecipeDescriptor>>()

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
                (origin != null && isModerneDocsOnly(origin)) ||
                    source?.toString()?.startsWith("typescript-search://") == true ||
                    source?.toString()?.startsWith("python-search://") == true ||
                    source?.toString()?.startsWith("csharp-search://") == true
            }
            .map { it.name }
            .toSet()

        // Create the recipe docs
        // We use two writers: one for OpenRewrite docs (open-source only), one for Moderne docs (all recipes)
        val recipeMarkdownWriter = RecipeMarkdownWriter(recipeContainedBy, recipeToSource, proprietaryRecipeNames, forModerneDocs = false)
        val moderneRecipeMarkdownWriter = if (moderneRecipeCatalogPath != null) {
            RecipeMarkdownWriter(recipeContainedBy, recipeToSource, proprietaryRecipeNames, forModerneDocs = true)
        } else null

        // Every recipe's natural output path in the Moderne catalog (which lists ALL recipes). A
        // cross-category duplicate — e.g. java.ChangeType surfaced under python/ because it works on
        // Python code — must not overwrite a recipe that natively owns that path (python.ChangeType),
        // otherwise the two clobber each other into one file. OpenRewrite docs avoid this because the
        // native recipe is often proprietary and skipped there; the Moderne catalog writes both.
        val moderneNativeRecipePaths = allRecipeDescriptors.mapTo(HashSet()) { getRecipePath(it) }

        for (recipeDescriptor in allRecipeDescriptors) {
            val recipeSource = recipeToSource[recipeDescriptor.name]
            requireNotNull(recipeSource) { "Could not find source URI for recipe " + recipeDescriptor.name }

            val origin = findOrigin(recipeSource, recipeDescriptor.name, recipeOrigins)
            requireNotNull(origin) { "Could not find GAV coordinates of recipe " + recipeDescriptor.name + " from " + recipeSource }

            // Always write to Moderne docs (ALL recipes)
            moderneRecipeMarkdownWriter?.writeRecipe(recipeDescriptor, moderneRecipeCatalogPath!!, origin)

            // Write cross-category duplicates to Moderne docs
            val extraPaths = crossCategoryPaths[recipeDescriptor.name]
            if (extraPaths != null && moderneRecipeMarkdownWriter != null) {
                for (extraPath in extraPaths) {
                    // Don't let this duplicate clobber a recipe that natively owns the path.
                    if (extraPath in moderneNativeRecipePaths) continue
                    moderneRecipeMarkdownWriter.writeRecipeTo(recipeDescriptor, moderneRecipeCatalogPath!!, origin, extraPath)
                }
            }

            // Track moderne-docs-only recipes separately (for moderne-recipes.md list and redirects)
            if (isModerneDocsOnly(origin)) {
                moderneOnlyRecipes.computeIfAbsent(origin.artifactId) { mutableListOf() }.add(recipeDescriptor)
                // Skip writing moderne-docs-only recipes to rewrite-docs
                continue
            }

            // Write non-proprietary recipes to OpenRewrite docs
            recipeMarkdownWriter.writeRecipe(recipeDescriptor, recipesPath, origin)

            // Write cross-category duplicates to OpenRewrite docs. No native-path skip here (unlike the
            // Moderne loop above): proprietary recipes are excluded from OpenRewrite docs, so a duplicate
            // legitimately fills a slot whose native owner isn't written here. TRUNCATE_EXISTING still
            // guards against stale-tail corruption if two open-source recipes ever claim the same path.
            if (extraPaths != null) {
                for (extraPath in extraPaths) {
                    recipeMarkdownWriter.writeRecipeTo(recipeDescriptor, recipesPath, origin, extraPath)
                }
            }

            val recipeOptions = TreeSet<RecipeOption>()
            if (recipeDescriptor.options != null) {
                for (recipeOption in recipeDescriptor.options) {
                    // TypeScript recipes may have null name or type
                    val name = recipeOption.name ?: "unknown"
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

        // Filter to only rewrite-docs recipes (exclude moderne-docs-only modules and proprietary)
        val openSourceRecipeDescriptors = allRecipeDescriptors.filter { recipe ->
            val source = recipeToSource[recipe.name]
            val origin = findOrigin(source, recipe.name, recipeOrigins)
            origin != null && !isModerneDocsOnly(origin)
        }
        println("Filtered to ${openSourceRecipeDescriptors.size} open-source recipe(s) for rewrite-docs.")

        // Write the README.md for each category
        // OpenRewrite docs: open-source only (in "recipes" subdir)
        CategoryWriter(openSourceRecipeDescriptors, allCategoryDescriptors, "/recipes", crossCategoryPaths)
            .writeCategories(outputPath, "recipes")
        // Moderne docs: ALL recipes (in "recipe-catalog" subdir to match workflow expectations)
        if (moderneOutputPath != null) {
            CategoryWriter(allRecipeDescriptors, allCategoryDescriptors, "/user-documentation/recipes/recipe-catalog", crossCategoryPaths)
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
        listWriter.createModerneRecipes(moderneOnlyRecipes.values.flatten(), recipeOrigins, recipeToSource)
        listWriter.createRecipesWithDataTables(recipeOrigins, recipeToSource)
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
            moderneListWriter.createRecipesWithDataTables(recipeOrigins, recipeToSource)
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
        val allModerneOnlyRecipes = moderneOnlyRecipes.values.flatten()
        if (allModerneOnlyRecipes.isNotEmpty()) {
            RedirectWriter.writeRedirectConfig(
                outputPath,
                allModerneOnlyRecipes,
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
        /** Modules whose docs should only appear in Moderne docs, regardless of license. */
        private val MODERNE_DOCS_ONLY_MODULES = setOf("rewrite-devcenter")

        private fun isModerneDocsOnly(origin: RecipeOrigin): Boolean =
            origin.license == Licenses.Proprietary || origin.artifactId in MODERNE_DOCS_ONLY_MODULES

        // Set of base paths that have both io.moderne and org.openrewrite recipes (conflicts)
        private var conflictingBasePaths: Set<String> = emptySet()

        // Package names of categories flagged `root: true` in the loaded category metadata
        // (e.g. rewrite-core's META-INF/rewrite/core-categories.yml: com, io, org, ai, tech,
        // software). These are "hidden" container categories that should not surface in the recipe
        // catalog sidebar; their leading segment is stripped so their children become top-level
        // categories (e.g. com.google.* -> google/*). Populated by initializeRootCategories.
        private var rootCategoryPackages: Set<String> = emptySet()

        /**
         * Collect the package names of categories flagged `root: true` so that their leading
         * path segment can be stripped from recipe paths. Only single-segment roots affect path
         * computation, but multi-segment roots (org.openrewrite, io.moderne) are handled by their
         * own dedicated branches in [getBasePath]. Must be called before any getRecipePath() calls.
         */
        fun initializeRootCategories(categoryDescriptors: Collection<CategoryDescriptor>) {
            rootCategoryPackages = categoryDescriptors.filter { it.isRoot }.map { it.packageName }.toSet()
        }

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

        fun hasConflict(recipeName: String): Boolean =
            conflictingBasePaths.contains(getBasePath(recipeName))

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
                        recipeName.substring(16).replace('.', '/').lowercase(Locale.getDefault())
                    }
                }
                recipeName.startsWith("io.moderne") -> {
                    recipeName.substring(11).replace('.', '/').lowercase(Locale.getDefault())
                }
                recipeName.startsWith("OpenRewrite.") -> {
                    "csharp/" + recipeName.substring(12).replace('.', '/').lowercase(Locale.getDefault())
                }
                else -> {
                    // Strip a leading root-category segment (e.g. com, io, org) so that hidden
                    // container categories don't surface in the recipe catalog sidebar; their
                    // children become top-level categories (com.google.* -> google/*).
                    val firstSegment = recipeName.substringBefore('.')
                    val stripped = if (recipeName.contains('.') && firstSegment in rootCategoryPackages) {
                        recipeName.substring(firstSegment.length + 1)
                    } else {
                        recipeName
                    }
                    stripped.replace('.', '/').lowercase(Locale.getDefault())
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

            // Handle C# recipes with custom URI scheme
            if (rawUri.startsWith("csharp-search://")) {
                val artifactId = rawUri.substringAfter("csharp-search://").substringBefore("/")
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

            // Docusaurus treats a file with the same name as its parent directory as the
            // directory index (e.g., codequality/codequality.md -> /codequality/ route),
            // which collides with the category README.md. Rename such recipes.
            val lastSlash = basePath.lastIndexOf('/')
            if (lastSlash > 0) {
                val parentDir = basePath.substring(basePath.lastIndexOf('/', lastSlash - 1) + 1, lastSlash)
                val leaf = basePath.substring(lastSlash + 1)
                if (parentDir == leaf) {
                    return basePath + "-recipe"
                }
            }

            // Add edition suffix only if there's a detected conflict
            val needsSuffix = conflictingBasePaths.contains(basePath)

            return when {
                recipe.name.startsWith("org.openrewrite") -> {
                    if (needsSuffix) basePath + "-community-edition" else basePath
                }
                recipe.name.startsWith("io.moderne") -> {
                    if (needsSuffix) basePath + "-moderne-edition" else basePath
                }
                recipe.name.startsWith("OpenRewrite.") -> {
                    basePath
                }
                recipe.name.startsWith("ai.timefold") ||
                recipe.name.startsWith("androidx") ||
                recipe.name.startsWith("com.google") ||
                recipe.name.startsWith("com.oracle") ||
                recipe.name.startsWith("io.axoniq") ||
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
