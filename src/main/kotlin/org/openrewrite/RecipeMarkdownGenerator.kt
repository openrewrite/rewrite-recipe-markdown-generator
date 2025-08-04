package org.openrewrite

import org.openrewrite.config.CategoryDescriptor
import org.openrewrite.config.DeclarativeRecipe
import org.openrewrite.config.RecipeDescriptor
import org.openrewrite.internal.StringUtils
import picocli.CommandLine
import picocli.CommandLine.*
import picocli.CommandLine.Option
import java.io.BufferedWriter
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
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

    // These are common in every recipe - so let's not use them when generating the list of recipes with data tables.
    private val dataTablesToIgnore = listOf(
        "org.openrewrite.table.SourcesFileResults",
        "org.openrewrite.table.SourcesFileErrors",
        "org.openrewrite.table.RecipeRunStats"
    )

    override fun run() {
        val outputPath = Paths.get(destinationDirectoryName)
        val recipesPath = outputPath.resolve("recipes")
        try {
            Files.createDirectories(recipesPath)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

        val recipeOrigins: Map<URI, RecipeOrigin> = RecipeOrigin.parse(recipeSources)

        // Write latest-versions-of-every-openrewrite-module.md, for all recipe modules
        val versionWriter = VersionWriter()
        versionWriter.createLatestVersionsJs(
            outputPath,
            recipeOrigins,
            rewriteRecipeBomVersion,
            gradlePluginVersion,
            mavenPluginVersion
        )
        versionWriter.createLatestVersionsMarkdown(
            outputPath,
            recipeOrigins,
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
        val loadResult = RecipeLoader().loadRecipes(recipeOrigins, recipeClasspath)
        val allRecipeDescriptors = loadResult.allRecipeDescriptors
        val allCategoryDescriptors = loadResult.allCategoryDescriptors
        val allRecipes = loadResult.allRecipes

        println("Found ${allRecipeDescriptors.size} descriptor(s).")

        val markdownArtifacts = TreeMap<String, MarkdownRecipeArtifact>()
        val recipesWithDataTables = ArrayList<RecipeDescriptor>()
        val moderneProprietaryRecipes = TreeMap<String, MutableList<RecipeDescriptor>>()

        // Build reverse mapping of recipe relationships (which recipes contain each recipe)
        val recipeContainedBy = mutableMapOf<String, MutableSet<RecipeDescriptor>>()
        for (parentRecipe in allRecipeDescriptors) {
            for (childRecipe in parentRecipe.recipeList) {
                recipeContainedBy.computeIfAbsent(childRecipe.name) { mutableSetOf() }.add(parentRecipe)
            }
        }

        // Create the recipe docs
        val recipeMarkdownWriter = RecipeMarkdownWriter()
        for (recipeDescriptor in allRecipeDescriptors) {
            var origin: RecipeOrigin?
            var rawUri = recipeDescriptor.source.toString()
            val exclamationIndex = rawUri.indexOf('!')
            if (exclamationIndex == -1) {
                origin = recipeOrigins[recipeDescriptor.source]
            } else {
                // The recipe origin includes the path to the recipe within a jar
                // Such URIs will look something like: jar:file:/path/to/the/recipes.jar!META-INF/rewrite/some-declarative.yml
                // Strip the "jar:" prefix and the part of the URI pointing inside the jar
                rawUri = rawUri.substring(0, exclamationIndex)
                rawUri = rawUri.substring(4)
                val jarOnlyUri = URI.create(rawUri)
                origin = recipeOrigins[jarOnlyUri]
            }
            requireNotNull(origin) { "Could not find GAV coordinates of recipe " + recipeDescriptor.name + " from " + recipeDescriptor.source }
            recipeMarkdownWriter.writeRecipe(recipeDescriptor, recipesPath, origin, recipeContainedBy)

            val filteredDataTables = recipeDescriptor.dataTables.filter { dataTable ->
                dataTable.name !in dataTablesToIgnore
            }

            if (filteredDataTables.isNotEmpty()) {
                recipesWithDataTables.add(recipeDescriptor)
            }

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
            val recipeSource = recipeDescriptor.source.toString()
            var isImperative = true

            // YAML recipes will have a source that ends with META-INF/rewrite/something.yml
            // Used to help with time spent calculations. Imperative = 12 hours, Declarative = 4 hours
            if (recipeSource.substring(recipeSource.length - 3) == "yml") {
                isImperative = false
            }

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

        // Create various additional files
        ChangelogWriter().createRecipeDescriptorsYaml(
            markdownArtifacts,
            allRecipeDescriptors.size,
            rewriteBomVersion
        )
        createModerneRecipes(outputPath, moderneProprietaryRecipes)
        createRecipesWithDataTables(recipesWithDataTables, outputPath)
        createRecipeAuthors(allRecipeDescriptors, outputPath)
        createRecipesByTag(allRecipeDescriptors, outputPath)
        createScanningRecipes(
            allRecipes.filter { it is ScanningRecipe<*> && it !is DeclarativeRecipe },
            recipeOrigins, outputPath
        )
        createStandaloneRecipes(
            allRecipeDescriptors.filterNot { recipe ->
                recipeContainedBy.contains(recipe.name)
            }, recipeOrigins, outputPath
        )

        // Write the README.md for each category
        val categories =
            Category.fromDescriptors(allRecipeDescriptors, allCategoryDescriptors).sortedBy { it.simpleName }
        for (category in categories) {
            val categoryIndexPath = outputPath.resolve("recipes/")
            category.writeCategoryIndex(categoryIndexPath)
        }
    }


    private fun createModerneRecipes(
        outputPath: Path,
        moderneProprietaryRecipesMap: TreeMap<String, MutableList<RecipeDescriptor>>
    ) {
        val moderneRecipesPath = outputPath.resolve("moderne-recipes.md")

        Files.newBufferedWriter(moderneRecipesPath, StandardOpenOption.CREATE).useAndApply {
            writeln(
                """
            ---
            description: An autogenerated list of recipes that are exclusive to Moderne.
            ---
            """.trimIndent()
            )
            writeln("\n# Moderne Recipes\n")

            writeln(
                "This doc includes every recipe that is exclusive to users of Moderne. " +
                        "For a full list of all recipes, check out our [recipe catalog](https://docs.openrewrite.org/recipes). " +
                        "For more information about how to use Moderne for automating code refactoring and analysis at scale, " +
                        "[contact us](https://www.moderne.ai/contact-us).\n"
            )

            for (entry in moderneProprietaryRecipesMap) {
                // Artifact ID
                writeln("## ${entry.key}\n")

                for (recipe in entry.value.sortedBy { it.displayName }) {
                    writeln(
                        "* [${recipe.displayNameEscaped()}](/recipes/${getRecipePath(recipe)}.md) - _${
                            recipe.descriptionEscaped()
                        }_"
                    )
                }

                writeln("")
            }
        }
    }


    data class Category(
        val simpleName: String,
        val path: String,
        val descriptor: CategoryDescriptor?,
        val recipes: List<RecipeDescriptor>,
        val subcategories: List<Category>
    ) {
        companion object {
            private data class CategoryBuilder(
                val path: String? = null,
                val recipes: MutableList<RecipeDescriptor> = mutableListOf(),
                val subcategories: LinkedHashMap<String, CategoryBuilder> = LinkedHashMap()
            ) {
                fun build(categoryDescriptors: List<CategoryDescriptor>): Category {
                    val simpleName = path!!.substring(path.lastIndexOf('/') + 1)
                    val descriptor = findCategoryDescriptor(path, categoryDescriptors)
                    // Do not consider backticks while sorting, they're formatting.
                    val finalizedSubcategories = subcategories.values.asSequence()
                        .map { it.build(categoryDescriptors) }
                        .sortedBy { it.displayName.replace("`", "") }
                        .toList()
                    return Category(
                        simpleName,
                        path,
                        descriptor,
                        recipes.sortedBy { it.displayName.replace("`", "") },
                        finalizedSubcategories
                    )
                }
            }

            fun fromDescriptors(
                recipes: Iterable<RecipeDescriptor>,
                descriptors: List<CategoryDescriptor>
            ): List<Category> {
                val result = LinkedHashMap<String, CategoryBuilder>()
                for (recipe in recipes) {
                    result.putRecipe(getRecipeCategory(recipe), recipe)
                }

                return result.mapValues { it.value.build(descriptors) }
                    .values
                    .toList()
            }

            private fun MutableMap<String, CategoryBuilder>.putRecipe(
                recipeCategory: String?,
                recipe: RecipeDescriptor
            ) {
                if (recipeCategory == null) {
                    return
                }
                val pathSegments = recipeCategory.split("/")
                var category = this
                for (i in pathSegments.indices) {
                    val pathSegment = pathSegments[i]
                    val pathToCurrent = pathSegments.subList(0, i + 1).joinToString("/")
                    if (!category.containsKey(pathSegment)) {
                        category[pathSegment] = CategoryBuilder(path = pathToCurrent)
                    }
                    if (i == pathSegments.size - 1) {
                        category[pathSegment]!!.recipes.add(recipe)
                    }
                    category = category[pathSegment]!!.subcategories
                }
            }
        }

        var displayName: String =
            if (descriptor == null) {
                StringUtils.capitalize(simpleName)
            } else {
                descriptor.displayName.replace("`", "")
            }

        /**
         * Produce the contents of the README.md file for this category.
         */
        private fun categoryIndex(): String {
            return StringBuilder().apply {
                // Docusaurus gets confused when parsing C# as the sidebar title. We need to surround it in backticks
                // so it displays correctly.
                if (displayName == "C#") {
                    appendLine("# `C#`")
                } else if (displayName == "Ai") {
                    // Ai is not capitalized by default - so let's switch it to be AI
                    appendLine("# AI")
                } else {
                    appendLine("# $displayName")
                }

                // While the description is not _supposed_ to be nullable it has happened before
                @Suppress("SENSELESS_COMPARISON")
                if (descriptor != null && descriptor.description != null) {
                    appendLine()
                    if (descriptor.description.contains("\n") || descriptor.description.contains("_")) {
                        appendLine(descriptor.description)
                    } else {
                        appendLine("_${descriptor.description}_")
                    }
                }
                appendLine()

                if (subcategories.isNotEmpty()) {
                    appendLine("## Categories")
                    appendLine()
                    for (subcategory in subcategories) {
                        appendLine("* [${subcategory.displayName}](/recipes/${subcategory.path})")
                    }
                    appendLine()
                }

                if (recipes.isNotEmpty()) {
                    val compositeRecipes: MutableList<RecipeDescriptor> = mutableListOf()
                    val normalRecipes: MutableList<RecipeDescriptor> = mutableListOf()

                    for (recipe in recipes) {
                        if (1 < recipe.recipeList.size) {
                            compositeRecipes.add(recipe)
                        } else {
                            normalRecipes.add(recipe)
                        }
                    }

                    if (compositeRecipes.isNotEmpty()) {
                        appendLine("## Composite Recipes")
                        appendLine()
                        appendLine("_Recipes that include further recipes, often including the individual recipes below._")
                        appendLine()

                        for (recipe in compositeRecipes) {
                            // Anything except a relative link ending in .md will be mangled.
                            val localPath = getRecipePath(recipe).substringAfterLast('/')
                            appendLine("* [${recipe.displayNameEscaped()}](./$localPath.md)")
                        }

                        appendLine()
                    }

                    if (normalRecipes.isNotEmpty()) {
                        appendLine("## Recipes")
                        appendLine()

                        for (recipe in normalRecipes) {
                            // Anything except a relative link ending in .md will be mangled.
                            val localPath = getRecipePath(recipe).substringAfterLast('/')
                            appendLine("* [${recipe.displayNameEscaped()}](./${localPath}.md)")
                        }

                        appendLine()
                    }
                }

            }.toString()
        }

        fun writeCategoryIndex(outputRoot: Path) {
            if (path.isBlank()) {
                // Create a core directory
                val recipesPath = outputRoot.resolve("core")
                try {
                    Files.createDirectories(recipesPath)
                } catch (e: IOException) {
                    throw RuntimeException(e)
                }

                // "Core" recipes need to be handled differently as they do not have a path like other recipes.
                val corePath = outputRoot.resolve("core/README.md")

                Files.newBufferedWriter(corePath, StandardOpenOption.CREATE).useAndApply {
                    writeln("# Core Recipes")
                    newLine()
                    writeln("_Recipes broadly applicable to all types of source files._")
                    newLine()
                    writeln("## Recipes")
                    newLine()

                    for (recipe in recipes) {
                        val relativePath = getRecipePath(recipe).substringAfterLast('/')
                        writeln("* [${recipe.displayNameEscaped()}](./$relativePath.md)")
                    }
                }

                return
            }

            val outputPath = outputRoot.resolve("$path/README.md")
            Files.newBufferedWriter(outputPath, StandardOpenOption.CREATE).useAndApply {
                writeln(categoryIndex())
            }

            for (subcategory in subcategories) {
                subcategory.writeCategoryIndex(outputRoot)
            }
        }
    }

    companion object {
        /**
         * Call Closable.use() together with apply() to avoid adding two levels of indentation
         */
        fun BufferedWriter.useAndApply(withFun: BufferedWriter.() -> Unit): Unit = use { it.apply(withFun) }

        fun BufferedWriter.writeln(text: String) {
            write(text)
            newLine()
        }

        private fun getRecipeCategory(recipe: RecipeDescriptor): String {
            val recipePath = getRecipePath(recipe)
            val slashIndex = recipePath.lastIndexOf("/")
            return if (slashIndex == -1) {
                ""
            } else {
                recipePath.substring(0, slashIndex)
            }
        }

        fun getRecipePath(recipe: RecipeDescriptor): String =
        // Docusaurus expects that if a file is called "assertj" inside of the folder "assertj" that it's the
        // README for said folder. Due to how generic we've made this recipe name, we need to change it for the
            // docs so that they parse correctly.
            if (recipePathToDocusaurusRenamedPath.containsKey(recipe.name)) {
                recipePathToDocusaurusRenamedPath[recipe.name]!!
            } else if (recipe.name == "io.moderne.java.spring.boot3.UpgradeSpringBoot_3_4") {
                // The spring boot recipes clashes with one another so let's make them distinct
                "java/spring/boot3/upgradespringboot_3_4-moderne-edition"
            } else if (recipe.name == "org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_4") {
                "java/spring/boot3/upgradespringboot_3_4-community-edition"
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


        private fun getRecipePath(recipesPath: Path, recipeDescriptor: RecipeDescriptor) =
            recipesPath.resolve(getRecipePath(recipeDescriptor) + ".md")

        private fun findCategoryDescriptor(
            categoryPathFragment: String,
            categoryDescriptors: Iterable<CategoryDescriptor>
        ): CategoryDescriptor? {
            val categoryPackage = "org.openrewrite.${categoryPathFragment.replace('/', '.')}"
            return categoryDescriptors.find { descriptor -> descriptor.packageName == categoryPackage }
        }

        @JvmStatic
        fun main(args: Array<String>) {
            val exitCode = CommandLine(RecipeMarkdownGenerator()).execute(*args)
            exitProcess(exitCode)
        }
    }

    private fun createRecipesWithDataTables(
        recipesWithDataTables: ArrayList<RecipeDescriptor>,
        outputPath: Path
    ) {
        val recipesWithDataTablesPath = outputPath.resolve("recipes-with-data-tables.md")
        Files.newBufferedWriter(recipesWithDataTablesPath, StandardOpenOption.CREATE).useAndApply {
            writeln(
                """
                ---
                description: An autogenerated list of all recipes that contain a unique data table.
                ---
                """.trimIndent()
            )
            writeln("\n# Recipes with Data Tables\n")

            //language=markdown
            writeln(
                "_This doc contains all of the recipes with **unique** data tables that have been explicitly " +
                        "added by the recipe author. If a recipe contains only the default data tables, " +
                        "it won't be included in this list._\n"
            )

            for (recipe in recipesWithDataTables) {
                writeln("### [${recipe.displayNameEscaped()}](/recipes/${getRecipePath(recipe)}.md)\n ")
                writeln("_${recipe.name}_\n")
                writeln("${recipe.descriptionEscaped()}\n")
                writeln("#### Data tables:\n")

                val filteredDataTables = recipe.dataTables.filter { dataTable ->
                    dataTable.name !in dataTablesToIgnore
                }

                for (dataTable in filteredDataTables) {
                    writeln("  * **${dataTable.name}**: *${dataTable.description.replace("\n", " ")}*")
                }

                writeln("\n")
            }
        }
    }

    private fun createScanningRecipes(
        scanningRecipes: List<Recipe>,
        recipeOrigins: Map<URI, RecipeOrigin>,
        outputPath: Path
    ) {
        val markdown = outputPath.resolve("scanning-recipes.md")
        Files.newBufferedWriter(markdown, StandardOpenOption.CREATE).useAndApply {
            writeln(
                //language=markdown
                """
                ---
                description: An autogenerated list of all scanning recipes.
                ---
                
                # Scanning Recipes
                
                _This doc contains all [scanning recipes](/concepts-and-explanations/recipes#scanning-recipes)._
                
                """.trimIndent()
            )


            val recipesByArtifact = scanningRecipes
                .groupBy { recipe -> recipeOrigins[recipe.descriptor.source]?.artifactId ?: "other" }
                .toSortedMap()
            for ((artifact, recipes) in recipesByArtifact) {
                writeln("## ${artifact}\n")

                for (recipe in recipes.sortedBy { it.displayName })
                    writeln(
                        "* [${recipe.descriptor.displayNameEscaped()}](/recipes/${getRecipePath(recipe.descriptor)}.md) - _${
                            recipe.descriptor.descriptionEscaped()
                        }_"
                    )
            }
        }
    }

    private fun createRecipeAuthors(recipeDescriptors: List<RecipeDescriptor>, outputPath: Path) {
        // Collect contributors
        val allContributors = TreeMap<String, MutableSet<RecipeDescriptor>>()
        for (recipeDescriptor in recipeDescriptors) {
            for (contributor in recipeDescriptor.contributors) {
                val recipeSet = allContributors.computeIfAbsent(contributor.name) { mutableSetOf() }
                recipeSet.add(recipeDescriptor)
            }
        }

        val recipeAuthorsPath = outputPath.resolve("recipe-authors.md")
        Files.newBufferedWriter(recipeAuthorsPath, StandardOpenOption.CREATE).useAndApply {
            writeln(
                """
                ---
                description: An autogenerated list of all recipe authors ranked by their contributions.
                ---
                """.trimIndent()
            )
            writeln("\n# Recipe Authors\n")

            writeln(
                "_This table lists all recipe authors ranked by the number of recipes they have contributed, from most to least._\n"
            )

            writeln("**Total authors:** ${allContributors.size}\n")

            // Sort authors by number of recipes (descending), then by name for ties
            val sortedAuthors = allContributors.entries.sortedWith(
                compareByDescending<Map.Entry<String, MutableSet<RecipeDescriptor>>> { it.value.size }
                    .thenBy { it.key }
            )

            // Create table header
            writeln("| Rank | Author | Number of Recipes |")
            writeln("|------|--------|-------------------|")

            // Create table rows
            var rank = 1
            var previousCount = -1
            var actualRank = 1

            for ((author, recipes) in sortedAuthors) {
                val recipeCount = recipes.size

                // Handle ties in ranking
                if (recipeCount != previousCount) {
                    actualRank = rank
                    previousCount = recipeCount
                }

                writeln("| $actualRank | $author | $recipeCount |")
                rank++
            }
        }
    }

    private fun createRecipesByTag(allRecipeDescriptors: List<RecipeDescriptor>, outputPath: Path) {
        val tagToRecipes = TreeMap<String, TreeSet<RecipeDescriptor>>(String.CASE_INSENSITIVE_ORDER)

        // Collect all tags and their associated recipes
        for (recipeDescriptor in allRecipeDescriptors) {
            for (tag in recipeDescriptor.tags) {
                tagToRecipes.computeIfAbsent(tag) { TreeSet(compareBy { it.name }) }
                    .add(recipeDescriptor)
            }
        }

        val markdown = outputPath.resolve("recipes-by-tag.md")
        Files.newBufferedWriter(markdown, StandardOpenOption.CREATE).useAndApply {
            writeln(
                //language=markdown
                """
                ---
                description: An autogenerated list of all recipe tags and the recipes within each tag.
                ---
                
                # Recipes by Tag
                
                _This doc contains all recipe tags and the recipes that are tagged with them._
                
                """.trimIndent()
            )

            if (tagToRecipes.isEmpty()) {
                writeln("No tagged recipes found.")
            } else {
                writeln("Total tags: ${tagToRecipes.size}\n")

                for ((tag, recipes) in tagToRecipes) {
                    writeln("## ${tag}")
                    writeln("\n_${recipes.size} recipe${if (recipes.size != 1) "s" else ""}_\n")

                    for (recipe in recipes.sortedBy { it.displayName }) {
                        writeln(
                            "* [${recipe.displayNameEscaped()}](/recipes/${getRecipePath(recipe)}.md) - _${
                                recipe.descriptionEscaped()
                            }_"
                        )
                    }
                    writeln("")
                }
            }
        }
    }

    private fun createStandaloneRecipes(
        standaloneRecipes: List<RecipeDescriptor>,
        recipeOrigins: Map<URI, RecipeOrigin>,
        outputPath: Path
    ) {
        // Skip if there are no recipes to process
        if (standaloneRecipes.isEmpty()) {
            return
        }

        // Write the standalone recipes file
        val standaloneRecipesPath = outputPath.resolve("standalone-recipes.md")
        Files.newBufferedWriter(standaloneRecipesPath, StandardOpenOption.CREATE).useAndApply {
            writeln(
                """
                ---
                description: An autogenerated list of recipes that are not included in any composite recipe.
                ---
                """.trimIndent()
            )
            writeln("\n# Standalone Recipes\n")

            writeln(
                "_This doc contains recipes that are not included as part of any larger composite recipe. " +
                        "These recipes can be run independently and are not bundled with other recipes._\n"
            )

            writeln("Total standalone recipes: ${standaloneRecipes.size}\n")

            // Group by package for better organization
            val recipesByArtifact = standaloneRecipes
                .groupBy { recipe -> recipeOrigins[recipe.source]?.artifactId ?: "other" }
                .toSortedMap()
            for ((artifact, recipes) in recipesByArtifact) {
                writeln("## ${artifact}\n")

                for (recipe in recipes.sortedBy { it.displayName }) {
                    writeln(
                        "* [${recipe.displayNameEscaped()}](/recipes/${getRecipePath(recipe)}.md) - _${
                            recipe.descriptionEscaped()
                        }_"
                    )
                }
            }
        }
    }
}
