package org.openrewrite

import com.github.difflib.DiffUtils
import com.github.difflib.patch.Patch
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
import java.util.regex.Pattern
import java.util.stream.Collectors.joining

import kotlin.system.exitProcess


// These recipes contain invalid markdown and would cause issues
// with doc generation if left in.
private val recipesToIgnore = listOf(
    // https://github.com/apache/camel-upgrade-recipes/pull/45
    "org.apache.camel.upgrade.camel45.UseExtendedCamelContextGetters",
    "org.apache.camel.upgrade.customRecipes.MoveGetterToExtendedCamelContext"
)

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
            writeRecipe(recipeDescriptor, recipesPath, origin, recipeContainedBy)

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
                            if (recipesToIgnore.contains(recipe.name)) {
                                continue;
                            }
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

    private fun writeRecipe(
        recipeDescriptor: RecipeDescriptor,
        outputPath: Path,
        origin: RecipeOrigin,
        recipeContainedBy: Map<String, MutableSet<RecipeDescriptor>>
    ) {
        if (recipesToIgnore.contains(recipeDescriptor.name)) {
            return
        }

        val editionSuffix = when (recipeDescriptor.name) {
            "io.moderne.java.spring.boot3.UpgradeSpringBoot_3_4" -> " (Moderne Edition)"
            "org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_4" -> " (Community Edition)"
            else -> ""
        }

        val formattedRecipeTitle = (recipeDescriptor.displayNameEscaped() + editionSuffix).trim()
        val formattedRecipeDescription = getFormattedRecipeDescription(recipeDescriptor.description)
        val formattedLongRecipeName = recipeDescriptor.name.replace("_".toRegex(), "\\\\_").trim()

        val recipeMarkdownPath = getRecipePath(outputPath, recipeDescriptor)
        Files.createDirectories(recipeMarkdownPath.parent)
        Files.newBufferedWriter(recipeMarkdownPath, StandardOpenOption.CREATE).useAndApply {
            write(
                """
---
sidebar_label: "${formattedRecipeTitle.replace("&#39;", "'")}"
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# $formattedRecipeTitle

**$formattedLongRecipeName**

""".trimIndent()
            )

            newLine()
            writeln(formattedRecipeDescription)
            newLine()
            if (recipeDescriptor.tags.isNotEmpty()) {
                writeln("### Tags")
                newLine()
                for (tag in recipeDescriptor.tags) {
                    if (tag.lowercase().startsWith("rspec-s")) {
                        writeln("* [$tag](https://sonarsource.github.io/rspec/#/rspec/${tag.substring(6)})")
                    } else if (tag.lowercase().startsWith("rspec-")) {
                        writeln("* [$tag](https://sonarsource.github.io/rspec/#/rspec/S${tag.substring(6)})")
                    } else {
                        val tagAnchor = tag.lowercase().replace(" ", "-")
                        writeln("* [$tag](/reference/recipes-by-tag#${tagAnchor})")
                    }
                }
                newLine()
            }

            writeSourceLinks(recipeDescriptor, origin)
            writeOptions(recipeDescriptor)
            writeDefinition(recipeDescriptor, origin)
            writeUsedBy(recipeContainedBy[recipeDescriptor.name])
            writeExamples(recipeDescriptor)
            writeUsage(recipeDescriptor, origin)
            writeModerneLink(recipeDescriptor)
            writeDataTables(recipeDescriptor)
            writeContributors(recipeDescriptor)
        }
    }

    @Suppress("UNNECESSARY_SAFE_CALL") // Recipes from third parties may lack description
    private fun getFormattedRecipeDescription(description: String): String {
        var formattedRecipeDescription = description
        val specialCharsOutsideBackticksRegex = Pattern.compile("[<>{}](?=(?:[^`]*`[^`]*`)*[^`]*\$)")

        if (formattedRecipeDescription.contains("```. [Source]")) {
            formattedRecipeDescription = formattedRecipeDescription.replace("```. [Source]", "```\n\n[Source]")
        }

        if (formattedRecipeDescription?.contains("```") == true) {
            // Assume that the recipe description is already Markdown
            return formattedRecipeDescription
        }

        if (specialCharsOutsideBackticksRegex.matcher(formattedRecipeDescription).find()) {
            // If special characters exist and are not wrapped in backticks, wrap the entire string in triple backticks
            return "```\n${formattedRecipeDescription?.replace("```", "")?.trim()}\n```\n"
        }

        // Special characters may exist here - but they are already wrapped in backticks
        return "_" + formattedRecipeDescription?.replace("\n", " ")?.trim() + "_"
    }

    private fun BufferedWriter.writeSourceLinks(recipeDescriptor: RecipeDescriptor, origin: RecipeOrigin) {
        if (origin.license == Licenses.Proprietary) {
            //language=markdown
            writeln(
                """
                ## Recipe source

                This recipe is only available to users of [Moderne](https://docs.moderne.io/).

                """.trimIndent()
            )
        } else {
            //language=markdown
            writeln(
                """
            ## Recipe source
            
            [GitHub](${origin.githubUrl(recipeDescriptor.name, recipeDescriptor.source)}), 
            [Issue Tracker](${origin.issueTrackerUrl()}), 
            [Maven Central](https://central.sonatype.com/artifact/${origin.groupId}/${origin.artifactId}/)
            """.trimIndent()
            )

            if (recipeDescriptor.recipeList.size > 1) {
                //language=markdown
                writeln(
                    """

                :::info
                This recipe is composed of more than one recipe. If you want to customize the set of recipes this is composed of, you can find and copy the GitHub source for the recipe from the link above.
                :::
                """.trimIndent()
                )
            }
        }

        writeLicense(origin)
    }

    private fun BufferedWriter.writeLicense(origin: RecipeOrigin) {
        val licenseText = when (origin.license) {
            Licenses.Unknown -> "The license for this recipe is unknown."
            Licenses.Apache2, Licenses.Proprietary, Licenses.MSAL -> "This recipe is available under the ${origin.license.markdown()}."
            else -> "This recipe is available under the ${origin.license.markdown()} License, as defined by the recipe authors."
        }

        //language=markdown
        writeln(
            """

            $licenseText

            """.trimIndent()
        )
    }

    private fun BufferedWriter.writeOptions(recipeDescriptor: RecipeDescriptor) {
        if (recipeDescriptor.options.isNotEmpty()) {
            writeln(
                """
                ## Options
                
                | Type | Name | Description | Example |
                | -- | -- | -- | -- |
                """.trimIndent()
            )
            for (option in recipeDescriptor.options) {
                var description = if (option.description == null) {
                    ""
                } else {
                    option.description.replace("\n", "<br />")

                    // Ensure that anything that matches ${variable} is wrapped in ``
                    // Otherwise Docusaurus tries to parse it as a variable.
                    val regex = Regex("(?<!`)\\$\\{[^}]+}(?!`)")
                    option.description.replace(regex) { matchResult ->
                        "`${matchResult.value}`"
                    }
                }
                description = if (option.isRequired) {
                    description
                } else {
                    "*Optional*. $description"
                }

                // Add valid options to description
                if (option.valid?.isNotEmpty() ?: false) {
                    val combinedOptions = option.valid?.joinToString(", ") {
                        // Appropriately handle empty string and a space as a valid option
                        when (it) {
                            "" -> "\"\""
                            " " -> "\" \""
                            else -> "`$it`"
                        }
                    }

                    description += " Valid options: $combinedOptions"
                }
                // Preserve table cell formatting for multiline examples
                val example = if (option.example != null) {
                    if (option.example.contains("\n")) {
                        "<pre>${option.example.replace("<", "\\<")}</pre>".replace("\n", "<br />")
                    } else {
                        "`${option.example}`"
                    }
                } else {
                    ""
                }
                writeln(
                    """
                    | `${option.type}` | ${option.name} | $description | $example |
                    """.trimIndent()
                )
            }
            newLine()
        }
    }

    private fun BufferedWriter.writeDataTables(recipeDescriptor: RecipeDescriptor) {
        if (recipeDescriptor.dataTables.isNotEmpty()) {
            writeln(
                //language=markdown
                """
                ## Data Tables

                <Tabs groupId="data-tables">
                """.trimIndent()
            )

            for (dataTable in recipeDescriptor.dataTables) {
                //language=markdown
                writeln(
                    """
                    <TabItem value="${dataTable.name}" label="${dataTable.name.substringAfterLast('.')}">

                    ### ${dataTable.displayName}
                    **${dataTable.name}**

                    _${dataTable.description}_

                    | Column Name | Description |
                    | ----------- | ----------- |
                    """.trimIndent()
                )

                for (column in dataTable.columns) {
                    //language=markdown
                    writeln(
                        """
                        | ${column.displayName} | ${column.description} |
                        """.trimIndent()
                    )
                }

                writeln(
                    """
                    
                    </TabItem>
                    """.trimIndent()
                )

                newLine()
            }

            writeln(
                //language=markdown
                """
                </Tabs>
                """.trimIndent()
            )
        }
    }

    private fun BufferedWriter.writeExamples(recipeDescriptor: RecipeDescriptor) {
        if (recipeDescriptor.examples.isNotEmpty()) {
            val subject = if (recipeDescriptor.examples.size > 1) "Examples" else "Example"
            writeln("## $subject")

            for (i in 0 until recipeDescriptor.examples.size) {
                if (i > 0) {
                    newLine()
                    writeln("---")
                    newLine()
                }

                val example = recipeDescriptor.examples[i]
                val description =
                    if (example.description.isNotEmpty()) example.description else ""

                if (recipeDescriptor.examples.size > 1) {
                    writeln("##### Example ${i + 1}")
                    if (description.isNotEmpty()) {
                        writeln(description)
                    }
                }

                newLine()

                // Parameters
                if (example.parameters.isNotEmpty() && recipeDescriptor.options.isNotEmpty()) {
                    writeln("###### Parameters")
                    writeln("| Parameter | Value |")
                    writeln("| -- | -- |")
                    for (n in 0 until recipeDescriptor.options.size) {
                        write("|")
                        write(recipeDescriptor.options[n].name)
                        write("|")
                        if (n < example.parameters.size) {
                            write("`${example.parameters[n]}`")
                        }
                        write("|")
                        newLine()
                    }
                    newLine()
                }

                // Example files
                for (sourceIndex in 0 until example.sources.size) {
                    val source = example.sources[sourceIndex]
                    val hasChange = source.after != null && source.after.isNotEmpty()
                    val beforeTitle = if (hasChange) "Before" else "Unchanged"
                    val isNewFile = source.before == null && source.after != null
                    val afterTile = if (isNewFile) "New file" else "After"


                    if (hasChange && source.before != null) {
                        newLine()
                        val tabName = source.path ?: (source.language ?: "Before / After")
                        writeln("<Tabs groupId=\"beforeAfter\">")
                        writeln("<TabItem value=\"${tabName}\" label=\"${tabName}\">\n")
                    }

                    newLine()

                    if (source.before != null) {
                        writeln("###### $beforeTitle")

                        if (source.path != null) {
                            writeln("```${source.language} title=\"${source.path}\"")
                        } else {
                            writeln("```${source.language}")
                        }

                        write(source.before)
                        if (source.before != null && !source.before.endsWith("\n")) {
                            newLine()
                        }
                        writeln("```")
                    }

                    if (hasChange) {
                        newLine()
                        writeln("###### $afterTile")

                        if (source.path != null) {
                            writeln("```${source.language} title=\"${source.path}\"")
                        } else {
                            writeln("```${source.language}")
                        }

                        write(source.after)
                        if (source.after != null && !source.after.endsWith("\n")) {
                            newLine()
                        }
                        writeln("```")

                        newLine()

                        // diff
                        if (source.before != null) {
                            writeln("</TabItem>")
                            writeln("<TabItem value=\"diff\" label=\"Diff\" >\n")

                            val diff = generateDiff(source.path, source.before, source.after)

                            writeln(
                                """
                                |```diff
                                |${diff}```
                                """.trimMargin()
                            )
                            writeln("</TabItem>")
                            writeln("</Tabs>")
                        }
                    }
                }
            }
            newLine()
        }
    }

    private fun BufferedWriter.writeUsage(
        recipeDescriptor: RecipeDescriptor,
        origin: RecipeOrigin
    ) {
        // Usage
        newLine()
        writeln("## Usage")
        newLine()

        val suppressJava = recipeDescriptor.name.contains(".csharp.") ||
                recipeDescriptor.name.contains(".dotnet.") ||
                recipeDescriptor.name.contains(".nodejs.") ||
                recipeDescriptor.name.contains(".python.") ||
                origin.license == Licenses.Proprietary
        val suppressMaven = suppressJava || recipeDescriptor.name.contains(".gradle.")
        val suppressGradle = suppressJava || recipeDescriptor.name.contains(".maven.")
        val requiresConfiguration = recipeDescriptor.options.any { it.isRequired }
        val requiresDependency = !origin.isFromCoreLibrary()

        val dataTableSnippet =
            if (recipeDescriptor.dataTables.isEmpty()) "" else "<exportDatatables>true</exportDatatables>"

        val dataTableCommandLineSnippet =
            if (recipeDescriptor.dataTables.isEmpty()) "" else "-Drewrite.exportDatatables=true"

        if (requiresConfiguration) {
            val exampleRecipeName =
                "com.yourorg." + recipeDescriptor.name.substring(recipeDescriptor.name.lastIndexOf('.') + 1) + "Example"

            if (origin.license == Licenses.Proprietary) {
                //language=markdown
                write(
                    """
                    This recipe has required configuration parameters and can only be run by users of Moderne.
                    To run this recipe, you will need to provide the Moderne CLI run command with the required options. 
                    Or, if you'd like to create a declarative recipe, please see the below example of a `rewrite.yml` file:

                    ```yaml title="rewrite.yml"
                    ---
                    type: specs.openrewrite.org/v1beta/recipe
                    name: $exampleRecipeName
                    displayName: ${recipeDescriptor.displayNameEscaped()} example
                    recipeList:
                      - ${recipeDescriptor.name}: 

                    """.trimIndent()
                )
            } else {
                write("This recipe has required configuration parameters. ")
                write("Recipes with required configuration parameters cannot be activated directly (unless you are running them via the Moderne CLI). ")
                write("To activate this recipe you must create a new recipe which fills in the required parameters. ")
                write("In your `rewrite.yml` create a new recipe with a unique name. ")
                write("For example: `$exampleRecipeName`.")
                newLine()
                writeln("Here's how you can define and customize such a recipe within your rewrite.yml:")
                //language=markdown
                write(
                    """
                ```yaml title="rewrite.yml"
                ---
                type: specs.openrewrite.org/v1beta/recipe
                name: $exampleRecipeName
                displayName: ${recipeDescriptor.displayNameEscaped()} example
                recipeList:
                  - ${recipeDescriptor.name}:
                
                """.trimIndent()
                )
            }

            var cliOptions = ""
            for (option in recipeDescriptor.options) {
                if (!option.isRequired && option.example == null) {
                    continue
                }
                val ex = if (option.example != null && option.type == "String" &&
                    (option.example.matches("^[{}\\[\\],`|=%@*!?-].*".toRegex()) ||
                            option.example.matches(".*:\\s.*".toRegex()))
                ) {
                    "'" + option.example + "'"
                } else if (option.example != null && option.type == "String" && option.example.contains('\n')) {
                    ">\n        " + option.example.replace("\n", "\n        ")
                } else if (option.type == "boolean") {
                    "false"
                } else {
                    option.example
                }
                cliOptions += " --recipe-option \"${option.name}=$ex\""
                writeln("      ${option.name}: $ex")
            }
            writeln("```")
            newLine()

            val cliSnippet = getCliSnippet(recipeDescriptor.name, cliOptions, origin)
            if (requiresDependency) {
                writeSnippetsWithConfigurationWithDependency(
                    exampleRecipeName,
                    origin,
                    suppressMaven,
                    suppressGradle,
                    cliSnippet,
                    dataTableSnippet,
                )
            } else {
                writeSnippetsWithConfigurationWithoutDependency(
                    exampleRecipeName,
                    suppressMaven,
                    suppressGradle,
                    cliSnippet,
                    dataTableSnippet,
                    origin,
                )
            }
        } else {
            val cliSnippet = getCliSnippet(recipeDescriptor.name, "", origin)
            if (origin.isFromCoreLibrary()) {
                writeSnippetsFromCoreLibrary(
                    recipeDescriptor,
                    suppressMaven,
                    suppressGradle,
                    cliSnippet,
                    dataTableSnippet,
                    dataTableCommandLineSnippet,
                )
            } else {
                writeSnippetForOtherLibrary(
                    origin,
                    recipeDescriptor,
                    suppressMaven,
                    suppressGradle,
                    cliSnippet,
                    dataTableSnippet,
                    dataTableCommandLineSnippet,
                )
            }
        }
    }

    private fun BufferedWriter.writeDefinition(recipeDescriptor: RecipeDescriptor, origin: RecipeOrigin) {
        if (recipeDescriptor.recipeList.isNotEmpty() && origin.license != Licenses.Proprietary) {
            //language=markdown
            writeln(
                """
                
                ## Definition
                
                <Tabs groupId="recipeType">
                <TabItem value="recipe-list" label="Recipe List" >
                """.trimIndent()
            )
            val recipeDepth = getRecipePath(recipeDescriptor).chars().filter { ch: Int -> ch == '/'.code }.count()
            val pathToRecipesBuilder = StringBuilder()
            for (i in 0 until recipeDepth) {
                pathToRecipesBuilder.append("../")
            }
            val pathToRecipes = pathToRecipesBuilder.toString()

            // These recipes contain other recipes that are not parseable.
            // Until we support this - let's remove links for these recipes.
            // https://github.com/openrewrite/rewrite-spring/issues/601
            val recipesThatShouldHaveLinksRemoved = listOf(
                "org.openrewrite.java.spring.boot2.MigrateDatabaseCredentials",
                "org.openrewrite.java.spring.PropertiesToKebabCase"
            )

            for (recipe in recipeDescriptor.recipeList) {
                // https://github.com/openrewrite/rewrite-docs/issues/250
                if (recipe.displayName == "Precondition bellwether") {
                    continue
                }

                if (recipesToIgnore.contains(recipe.name)) {
                    continue
                }

                if (recipesThatShouldHaveLinksRemoved.contains(recipeDescriptor.name)) {
                    writeln("* ${recipe.displayNameEscaped()}")
                } else {
                    writeln("* [" + recipe.displayNameEscaped() + "](" + pathToRecipes + getRecipePath(recipe) + ")")
                }

                if (recipe.options.isNotEmpty()) {
                    for (option in recipe.options) {
                        if (option.value != null) {
                            val formattedOptionString = printValue(option.value!!)
                                .replace("<p>", "< p >")
                                .replace("\n", " ")

                            writeln("  * " + option.name + ": `" + formattedOptionString + "`")
                        }
                    }
                }
            }
            newLine()
            //language=markdown
            writeln(
                """
                </TabItem>

                <TabItem value="yaml-recipe-list" label="Yaml Recipe List">

                ```yaml
                """.trimIndent()
            )
            writeln(recipeDescriptor.asYaml())
            //language=markdown
            writeln(
                """
                ```
                </TabItem>
                </Tabs>
                """.trimIndent()
            )
        }
    }

    private fun BufferedWriter.writeUsedBy(recipeContainedBy: MutableSet<RecipeDescriptor>?) {
        if (recipeContainedBy != null && recipeContainedBy.isNotEmpty()) {
            //language=markdown
            writeln(
                """
                
                ## Used by
                
                This recipe is used as part of the following composite recipes:
                
                """.trimIndent()
            )
            recipeContainedBy
                .map { "* [${it.displayNameEscaped()}](/recipes/${getRecipePath(it)}.md)" }
                .toSet()
                .sorted()
                .forEach { recipe -> writeln(recipe) }
            newLine()
        }
    }

    private fun BufferedWriter.writeModerneLink(recipeDescriptor: RecipeDescriptor) {
        //language=markdown
        writeln(
            """

            ## See how this recipe works across multiple open-source repositories
            
            import RecipeCallout from '@site/src/components/ModerneLink';

            <RecipeCallout link="https://app.moderne.io/recipes/${recipeDescriptor.name}" />

            The community edition of the Moderne platform enables you to easily run recipes across thousands of open-source repositories.

            Please [contact Moderne](https://moderne.io/product) for more information about safely running the recipes on your own codebase in a private SaaS.
            """.trimIndent()
        )
    }

    private fun BufferedWriter.writeContributors(recipeDescriptor: RecipeDescriptor) {
        if (recipeDescriptor.contributors.isNotEmpty()) {
            newLine()
            writeln("## Contributors")
            writeln(
                recipeDescriptor.contributors.stream()
                    .map { contributor: Contributor ->
                        if (contributor.email.contains("noreply")) {
                            contributor.name
                        } else {
                            "[" + contributor.name + "](mailto:" + contributor.email + ")"
                        }
                    }.collect(joining(", "))
            )
        }
    }

    private fun generateDiff(path: String?, original: String, revised: String): String {
        val patch: Patch<String> = DiffUtils.diff(original.lines(), revised.lines())
        val diffContent = StringBuilder()
        val contextLinesBefore = 2
        val contextLinesAfter = 1

        if (path != null) {
            diffContent.append("--- ").append(path).append("\n")
            diffContent.append("+++ ").append(path).append("\n")
        }

        for (delta in patch.deltas) {
            val originalLines = original.lines()
            val revisedLines = revised.lines()

            diffContent.append("@@ -${delta.source.position + 1},${delta.source.size()} ")
                .append("+${delta.target.position + 1},${delta.target.size()} @@")
                .append("\n")

            // print shared context
            val startIndex = maxOf(0, delta.source.position - contextLinesBefore)
            val endIndex = minOf(originalLines.size, delta.source.position + delta.source.size() + contextLinesAfter)
            for (i in startIndex until delta.source.position) {
                diffContent.append(originalLines[i]).append("\n")
            }

            for (i in delta.source.position until delta.source.position + delta.source.size()) {
                val trimmedLine =
                    if (originalLines[i].startsWith(" ")) originalLines[i].replaceFirst(" ", "") else originalLines[i]
                diffContent.append("-").append(trimmedLine).append("\n")
            }

            for (i in delta.target.position until delta.target.position + delta.target.size()) {
                val trimmedLine =
                    if (revisedLines[i].startsWith(" ")) revisedLines[i].replaceFirst(" ", "") else revisedLines[i]
                diffContent.append("+").append(trimmedLine).append("\n")
            }

            for (i in delta.source.position + delta.source.size() until endIndex) {
                diffContent.append(originalLines[i]).append("\n")
            }
        }

        return diffContent.toString()
    }

    private fun getCliSnippet(name: String, cliOptions: String, origin: RecipeOrigin): String {
        val trimmedRecipeName = name.substring(name.lastIndexOf('.') + 1)
        //language=markdown
        return """
            <TabItem value="moderne-cli" label="Moderne CLI">

            You will need to have configured the [Moderne CLI](https://docs.moderne.io/user-documentation/moderne-cli/getting-started/cli-intro) on your machine before you can run the following command.

            ```shell title="shell"
            mod run . --recipe ${trimmedRecipeName}${cliOptions}
            ```

            If the recipe is not available locally, then you can install it using:
            ```shell
            mod config recipes jar install ${origin.groupId}:${origin.artifactId}:${"{{${origin.versionPlaceholderKey()}}}"}
            ```
            </TabItem>
            """.trimIndent()
    }

    private fun BufferedWriter.writeSnippetsWithConfigurationWithoutDependency(
        exampleRecipeName: String,
        suppressMaven: Boolean,
        suppressGradle: Boolean,
        cliSnippet: String,
        dataTableSnippet: String,
        origin: RecipeOrigin,
    ) {
        //language=markdown
        val gradleSnippet = if (suppressGradle) "" else """
            <TabItem value="gradle" label="Gradle">

            1. Add the following to your `build.gradle` file:
            ```groovy title="build.gradle"
            plugins {
                id("org.openrewrite.rewrite") version("latest.release")
            }
            
            rewrite {
                activeRecipe("$exampleRecipeName")
                setExportDatatables(true)
            }
            
            repositories {
                mavenCentral()
            }
            ```
            2. Run `gradle rewriteRun` to run the recipe.
            </TabItem>
            """.trimIndent()

        //language=markdown
        val mavenSnippet = if (suppressMaven) "" else """
            <TabItem value="maven" label="Maven">

            1. Add the following to your `pom.xml` file:

            ```xml title="pom.xml"
            <project>
              <build>
                <plugins>
                  <plugin>
                    <groupId>org.openrewrite.maven</groupId>
                    <artifactId>rewrite-maven-plugin</artifactId>
                    <version>{{VERSION_REWRITE_MAVEN_PLUGIN}}</version>
                    <configuration>
                      $dataTableSnippet
                      <activeRecipes>
                        <recipe>$exampleRecipeName</recipe>
                      </activeRecipes>
                    </configuration>
                  </plugin>
                </plugins>
              </build>
            </project>
            ```
            2. Run `mvn rewrite:run` to run the recipe.
            </TabItem>
            """.trimIndent()

        if (origin.license == Licenses.Proprietary) {
            writeln(
                """
<Tabs groupId="projectType">
$cliSnippet
</Tabs>
""".trimIndent()
            )
        } else {
            writeln(
                """
Now that `$exampleRecipeName` has been defined, activate it in your build file:
<Tabs groupId="projectType">
$gradleSnippet
$mavenSnippet
$cliSnippet
</Tabs>
""".trimIndent()
            )
        }
    }

    private fun BufferedWriter.writeSnippetsWithConfigurationWithDependency(
        exampleRecipeName: String,
        origin: RecipeOrigin,
        suppressMaven: Boolean,
        suppressGradle: Boolean,
        cliSnippet: String,
        dataTableSnippet: String,
    ) {
        //language=markdown
        val gradleSnippet = if (suppressGradle) "" else """
            <TabItem value="gradle" label="Gradle">

            1. Add the following to your `build.gradle` file:

            ```groovy title="build.gradle"
            plugins {
                id("org.openrewrite.rewrite") version("latest.release")
            }
            
            rewrite {
                activeRecipe("$exampleRecipeName")
                setExportDatatables(true)
            }
            
            repositories {
                mavenCentral()
            }
            
            dependencies {
                rewrite("${origin.groupId}:${origin.artifactId}:${"{{${origin.versionPlaceholderKey()}}}"}")
            }
            ```
            2. Run `gradle rewriteRun` to run the recipe.
            </TabItem>
            """.trimIndent()

        //language=markdown
        val mavenSnippet = if (suppressMaven) "" else """
            <TabItem value="maven" label="Maven">

            1. Add the following to your `pom.xml` file:

            ```xml title="pom.xml"
            <project>
              <build>
                <plugins>
                  <plugin>
                    <groupId>org.openrewrite.maven</groupId>
                    <artifactId>rewrite-maven-plugin</artifactId>
                    <version>{{VERSION_REWRITE_MAVEN_PLUGIN}}</version>
                    <configuration>
                      $dataTableSnippet
                      <activeRecipes>
                        <recipe>$exampleRecipeName</recipe>
                      </activeRecipes>
                    </configuration>
                    <dependencies>
                      <dependency>
                        <groupId>${origin.groupId}</groupId>
                        <artifactId>${origin.artifactId}</artifactId>
                        <version>${"{{${origin.versionPlaceholderKey()}}}"}</version>
                      </dependency>
                    </dependencies>
                  </plugin>
                </plugins>
              </build>
            </project>
            ```
            2. Run `mvn rewrite:run` to run the recipe.
            </TabItem>
            """.trimIndent()

        if (origin.license == Licenses.Proprietary) {
            writeln(
                """
<Tabs groupId="projectType">
$cliSnippet
</Tabs>
""".trimIndent()
            )
        } else {
            writeln(
                """
Now that `$exampleRecipeName` has been defined, activate it and take a dependency on `${origin.groupId}:${origin.artifactId}:${"{{${origin.versionPlaceholderKey()}}}"}` in your build file:
<Tabs groupId="projectType">
$gradleSnippet
$mavenSnippet
$cliSnippet
</Tabs>
""".trimIndent()
            )
        }
    }

    private fun BufferedWriter.writeSnippetsFromCoreLibrary(
        recipeDescriptor: RecipeDescriptor,
        suppressMaven: Boolean,
        suppressGradle: Boolean,
        cliSnippet: String,
        dataTableSnippet: String,
        dataTableCommandLineSnippet: String,
    ) {
        writeln(
            "This recipe has no required configuration parameters and comes from a rewrite core library. " +
                    "It can be activated directly without adding any dependencies."
        )

        //language=markdown
        val gradleSnippet = if (suppressGradle) "" else """
            <TabItem value="gradle" label="Gradle">

            1. Add the following to your `build.gradle` file:

            ```groovy title="build.gradle"
            plugins {
                id("org.openrewrite.rewrite") version("latest.release")
            }
            
            rewrite {
                activeRecipe("${recipeDescriptor.name}")
                setExportDatatables(true)
            }
            
            repositories {
                mavenCentral()
            }
            
            ```
            2. Run `gradle rewriteRun` to run the recipe.
            </TabItem>
            
            <TabItem value="gradle-init-script" label="Gradle init script">

            1. Create a file named `init.gradle` in the root of your project.

            ```groovy title="init.gradle"
            initscript {
                repositories {
                    maven { url "https://plugins.gradle.org/m2" }
                }
                dependencies { classpath("org.openrewrite:plugin:latest.release") }
            }
            rootProject {
                plugins.apply(org.openrewrite.gradle.RewritePlugin)
                dependencies {
                    rewrite("org.openrewrite:rewrite-java")
                }
                rewrite {
                    activeRecipe("${recipeDescriptor.name}")
                    setExportDatatables(true)
                }
                afterEvaluate {
                    if (repositories.isEmpty()) {
                        repositories {
                            mavenCentral()
                        }
                    }
                }
            }
            ```

            2. Run the recipe.

            ```shell title="shell"
            gradle --init-script init.gradle rewriteRun
            ```
            </TabItem>
            """.trimIndent()

        //language=markdown
        val mavenSnippet = if (suppressMaven) "" else """
            <TabItem value="maven" label="Maven POM">

            1. Add the following to your `pom.xml` file:

            ```xml title="pom.xml"
            <project>
              <build>
                <plugins>
                  <plugin>
                    <groupId>org.openrewrite.maven</groupId>
                    <artifactId>rewrite-maven-plugin</artifactId>
                    <version>{{VERSION_REWRITE_MAVEN_PLUGIN}}</version>
                    <configuration>
                      $dataTableSnippet
                      <activeRecipes>
                        <recipe>${recipeDescriptor.name}</recipe>
                      </activeRecipes>
                    </configuration>
                  </plugin>
                </plugins>
              </build>
            </project>
            ```

            2. Run `mvn rewrite:run` to run the recipe.
            </TabItem>
            
            <TabItem value="maven-command-line" label="Maven Command Line">

            You will need to have [Maven](https://maven.apache.org/download.cgi) installed on your machine before you can run the following command.

            ```shell title="shell"
            mvn -U org.openrewrite.maven:rewrite-maven-plugin:run -Drewrite.activeRecipes=${recipeDescriptor.name} $dataTableCommandLineSnippet
            ```

            </TabItem>
            """.trimIndent()
        writeln(
            """
<Tabs groupId="projectType">
$gradleSnippet
$mavenSnippet
$cliSnippet
</Tabs>
""".trimIndent()
        )
    }

    private fun BufferedWriter.writeSnippetForOtherLibrary(
        origin: RecipeOrigin,
        recipeDescriptor: RecipeDescriptor,
        suppressMaven: Boolean,
        suppressGradle: Boolean,
        cliSnippet: String,
        dataTableSnippet: String,
        dataTableCommandLineSnippet: String,
    ) {
        if (origin.license == Licenses.Proprietary) {
            writeln("This recipe has no required configuration options. Users of Moderne can run it via the Moderne CLI:")
        } else {
            writeln(
                "This recipe has no required configuration options. " +
                        "It can be activated by adding a dependency on `${origin.groupId}:${origin.artifactId}` " +
                        "in your build file or by running a shell command (in which case no build changes are needed):"
            )
        }

        //language=markdown
        val gradleSnippet = if (suppressGradle) "" else """
            <TabItem value="gradle" label="Gradle">

            1. Add the following to your `build.gradle` file:

            ```groovy title="build.gradle"
            plugins {
                id("org.openrewrite.rewrite") version("latest.release")
            }
            
            rewrite {
                activeRecipe("${recipeDescriptor.name}")
                setExportDatatables(true)
            }
            
            repositories {
                mavenCentral()
            }
            
            dependencies {
                rewrite("${origin.groupId}:${origin.artifactId}:${"{{${origin.versionPlaceholderKey()}}}"}")
            }
            ```

            2. Run `gradle rewriteRun` to run the recipe.
            </TabItem>
            
            <TabItem value="gradle-init-script" label="Gradle init script">

            1. Create a file named `init.gradle` in the root of your project.

            ```groovy title="init.gradle"
            initscript {
                repositories {
                    maven { url "https://plugins.gradle.org/m2" }
                }
                dependencies { classpath("org.openrewrite:plugin:{{VERSION_REWRITE_GRADLE_PLUGIN}}") }
            }
            rootProject {
                plugins.apply(org.openrewrite.gradle.RewritePlugin)
                dependencies {
                    rewrite("${origin.groupId}:${origin.artifactId}:${"{{${origin.versionPlaceholderKey()}}}"}")
                }
                rewrite {
                    activeRecipe("${recipeDescriptor.name}")
                    setExportDatatables(true)
                }
                afterEvaluate {
                    if (repositories.isEmpty()) {
                        repositories {
                            mavenCentral()
                        }
                    }
                }
            }
            ```

            2. Run the recipe.

            ```shell title="shell"
            gradle --init-script init.gradle rewriteRun
            ```

            </TabItem>
            """.trimIndent()

        //language=markdown
        val mavenSnippet = if (suppressMaven) "" else """
            <TabItem value="maven" label="Maven POM">

            1. Add the following to your `pom.xml` file:

            ```xml title="pom.xml"
            <project>
              <build>
                <plugins>
                  <plugin>
                    <groupId>org.openrewrite.maven</groupId>
                    <artifactId>rewrite-maven-plugin</artifactId>
                    <version>{{VERSION_REWRITE_MAVEN_PLUGIN}}</version>
                    <configuration>
                      $dataTableSnippet
                      <activeRecipes>
                        <recipe>${recipeDescriptor.name}</recipe>
                      </activeRecipes>
                    </configuration>
                    <dependencies>
                      <dependency>
                        <groupId>${origin.groupId}</groupId>
                        <artifactId>${origin.artifactId}</artifactId>
                        <version>${"{{${origin.versionPlaceholderKey()}}}"}</version>
                      </dependency>
                    </dependencies>
                  </plugin>
                </plugins>
              </build>
            </project>
            ```

            2. Run `mvn rewrite:run` to run the recipe.
            </TabItem>
            
            <TabItem value="maven-command-line" label="Maven Command Line">
            You will need to have [Maven](https://maven.apache.org/download.cgi) installed on your machine before you can run the following command.

            ```shell title="shell"
            mvn -U org.openrewrite.maven:rewrite-maven-plugin:run -Drewrite.recipeArtifactCoordinates=${origin.groupId}:${origin.artifactId}:RELEASE -Drewrite.activeRecipes=${recipeDescriptor.name} $dataTableCommandLineSnippet
            ```
            </TabItem>
            """.trimIndent()

        writeln(
            """
<Tabs groupId="projectType">
$gradleSnippet
$mavenSnippet
$cliSnippet
</Tabs>
""".trimIndent()
        )
    }

    companion object {
        private fun printValue(value: Any): String =
            if (value is Array<*>) {
                value.contentDeepToString()
            } else {
                value.toString()
            }

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

        private fun getRecipePath(recipe: RecipeDescriptor): String =
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
