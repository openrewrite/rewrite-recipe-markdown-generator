package org.openrewrite

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.openrewrite.config.CategoryDescriptor
import org.openrewrite.config.Environment
import org.openrewrite.config.RecipeDescriptor
import org.openrewrite.internal.StringUtils
import org.openrewrite.internal.StringUtils.isNullOrEmpty
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import java.io.BufferedWriter
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.io.path.toPath
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
        description = ["The version of the Rewrite Gradle Plugin to display in relevant samples"]
    )
    lateinit var gradlePluginVersion: String

    @Parameters(
        index = "4",
        defaultValue = "",
        description = ["The version of the Rewrite Maven Plugin to display in relevant samples"]
    )
    lateinit var mavenPluginVersion: String

    @Parameters(
        index = "5",
        defaultValue = "release",
        description = ["The type of deploy being done (either release or snapshot)"]
    )
    lateinit var deployType: String

    @Parameters(
        index = "6",
        defaultValue = "renameMe",
        description = ["The name of the diff file to be generated when making a diff log"]
    )
    lateinit var diffFileName: String

    override fun run() {
        val outputPath = Paths.get(destinationDirectoryName)
        val recipesPath = outputPath.resolve("reference/recipes")
        try {
            Files.createDirectories(recipesPath)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

        val env: Environment
        val recipeOrigins: Map<URI, RecipeOrigin>

        // Load recipe details into memory
        if (recipeSources.isNotEmpty() && recipeClasspath.isNotEmpty()) {
            recipeOrigins = RecipeOrigin.parse(recipeSources)

            val classloader = recipeClasspath.split(";")
                .map(Paths::get)
                .map(Path::toUri)
                .map(URI::toURL)
                .toTypedArray()
                .let { URLClassLoader(it) }

            val dependencies: MutableCollection<Path> = mutableListOf()
            recipeClasspath.split(";")
                .map(Paths::get)
                .toCollection(dependencies)

            val envBuilder = Environment.builder()
            for (recipeOrigin in recipeOrigins) {
                // If you are running this with an old version of Rewrite (for diff log purposes), you'll need
                // to update the below line to look like this instead:
                // envBuilder.scanJar(recipeOrigin.key.toPath(), classloader)
                envBuilder.scanJar(recipeOrigin.key.toPath(), dependencies, classloader)
            }
            env = envBuilder.build()
        } else {
            recipeOrigins = emptyMap()
            env = Environment.builder()
                .scanRuntimeClasspath()
                .build()
        }

        // Recipes fully loaded into recipeDescriptors
        val recipeDescriptors: Collection<RecipeDescriptor> = env.listRecipeDescriptors()
        val categoryDescriptors = ArrayList(env.listCategoryDescriptors())
        val markdownArtifacts = TreeMap<String, MarkdownRecipeArtifact>()

        // Create the recipe docs
        for (recipeDescriptor in recipeDescriptors) {
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
            writeRecipe(recipeDescriptor, recipesPath, origin, gradlePluginVersion, mavenPluginVersion)

            val recipeOptions = TreeSet<RecipeOption>()
            for (recipeOption in recipeDescriptor.options) {
                val name = recipeOption.name as String
                val ro = RecipeOption(name, recipeOption.type, recipeOption.isRequired)
                recipeOptions.add(ro)
            }

            var recipeDescription = recipeDescriptor.description
            if (recipeDescriptor.description.isNullOrEmpty()) {
                recipeDescription = ""
            }

            val docBaseUrl = "https://docs.openrewrite.org/reference/recipes/"

            // Changes something like org.openrewrite.circleci.InstallOrb to https://docs.openrewrite.org/reference/recipes/circleci/installorb
            val docLink =
                docBaseUrl + recipeDescriptor.name.lowercase(Locale.getDefault()).removePrefix("org.openrewrite.")
                    .replace('.', '/')

            val recipeSource = recipeDescriptor.source.toString();
            var isImperative = true;

            // YAML recipes will have a source that ends with META-INF/rewrite/something.yml
            // Used to help with time spent calculations. Imperative = 12 hours, Declarative = 4 hours
            if (recipeSource.substring(recipeSource.length - 3) == "yml") {
                isImperative = false;
            }

            // Used to create changelogs
            val markdownRecipeDescriptor =
                MarkdownRecipeDescriptor(
                    recipeDescriptor.name,
                    recipeDescription,
                    docLink,
                    recipeOptions,
                    isImperative,
                    origin.artifactId
                )
            val markdownArtifact = markdownArtifacts.computeIfAbsent(origin.artifactId) {
                MarkdownRecipeArtifact(
                    origin.artifactId,
                    origin.version,
                    TreeMap<String, MarkdownRecipeDescriptor>()
                )
            }
            markdownArtifact.markdownRecipeDescriptors[recipeDescriptor.name] = markdownRecipeDescriptor
        }

        val mapper = ObjectMapper(YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER))
        mapper.registerKotlinModule()

        // Location of the recipe metadata from previous runs
        var recipeDescriptorFile = "src/main/resources/recipeDescriptors.yml"
        if (deployType == "snapshot") {
            recipeDescriptorFile = "src/main/resources/snapshotRecipeDescriptors.yml"
        } else if (deployType == "diff") {
            recipeDescriptorFile = "src/main/resources/diffRecipeDescriptors.yml"
        }

        // Read in the old saved recipes for comparison with the latest release
        val oldArtifacts: TreeMap<String, MarkdownRecipeArtifact> =
            mapper.readValue(Path.of(recipeDescriptorFile).toFile())

        // Build up all the information to make a changelog
        val newArtifacts = getNewArtifacts(markdownArtifacts, oldArtifacts)
        val removedArtifacts = getRemovedArtifacts(markdownArtifacts, oldArtifacts)
        val newRecipes = TreeSet<MarkdownRecipeDescriptor>()
        val removedRecipes = TreeSet<MarkdownRecipeDescriptor>()

        getNewAndRemovedRecipes(markdownArtifacts, oldArtifacts, newRecipes, removedRecipes)

        val changedRecipes = getChangedRecipes(markdownArtifacts, oldArtifacts, newRecipes, removedRecipes)

        if (deployType == "diff") {
            buildDiffLog(newRecipes)
        } else {
            // Create the changelog itself if there are any changes
            if (newArtifacts.isNotEmpty() ||
                removedArtifacts.isNotEmpty() ||
                newRecipes.isNotEmpty() ||
                removedRecipes.isNotEmpty() ||
                changedRecipes.isNotEmpty()
            ) {
                buildChangelog(newArtifacts, removedArtifacts, newRecipes, removedRecipes, changedRecipes, deployType)
            }
        }

        // Now that we've compared the versions and built the changelog,
        // write the latest recipe information to a file for next time
        mapper.writeValue(File(recipeDescriptorFile), markdownArtifacts)

        val categories = Category.fromDescriptors(recipeDescriptors, categoryDescriptors)

        // Write SUMMARY_snippet.md
        val summarySnippetPath = outputPath.resolve("SUMMARY_snippet.md")
        Files.newBufferedWriter(summarySnippetPath, StandardOpenOption.CREATE).useAndApply {
            for (category in categories) {
                write(category.summarySnippet(0))
            }
        }

        // Write the README.md for each category
        for (category in categories) {
            val categoryIndexPath = outputPath.resolve("reference/recipes/")
            category.writeCategoryIndex(categoryIndexPath)
        }
    }

    private fun getNewArtifacts(
        markdownArtifacts: TreeMap<String, MarkdownRecipeArtifact>,
        oldArtifacts: TreeMap<String, MarkdownRecipeArtifact>,
    ): TreeSet<String> {
        val newArtifacts = TreeSet<String>()

        for (artifactId in markdownArtifacts.keys) {
            if (!oldArtifacts.containsKey(artifactId)) {
                newArtifacts.add(artifactId)
            }
        }

        return newArtifacts
    }

    private fun getRemovedArtifacts(
        markdownArtifacts: TreeMap<String, MarkdownRecipeArtifact>,
        oldArtifacts: TreeMap<String, MarkdownRecipeArtifact>,
    ): TreeSet<String> {
        val removedArtifacts = TreeSet<String>()

        for (artifactId in oldArtifacts.keys) {
            if (!markdownArtifacts.containsKey(artifactId)) {
                removedArtifacts.add(artifactId)
            }
        }

        return removedArtifacts
    }

    // This updates the newRecipes and removedRecipes variables to contain the list of new and removed recipes
    private fun getNewAndRemovedRecipes(
        markdownArtifacts: TreeMap<String, MarkdownRecipeArtifact>,
        oldArtifacts: TreeMap<String, MarkdownRecipeArtifact>,
        newRecipes: TreeSet<MarkdownRecipeDescriptor>,
        removedRecipes: TreeSet<MarkdownRecipeDescriptor>,
    ) {
        for (markdownArtifact in markdownArtifacts.values) {
            val oldArtifact = oldArtifacts[markdownArtifact.artifactId]

            if (oldArtifact != null) {
                // Check for new recipes
                for (markdownRecipeDescriptors in markdownArtifact.markdownRecipeDescriptors) {
                    if (!oldArtifact.markdownRecipeDescriptors.containsKey(markdownRecipeDescriptors.key)) {
                        newRecipes.add(markdownRecipeDescriptors.value)
                    }
                }

                // Check for deleted recipes
                for (oldMarkdownRecipeDescriptors in oldArtifact.markdownRecipeDescriptors) {
                    if (!markdownArtifact.markdownRecipeDescriptors.containsKey(oldMarkdownRecipeDescriptors.key)) {
                        removedRecipes.add(oldMarkdownRecipeDescriptors.value)
                    }
                }
            } else {
                // If there's no old artifact, just add all of the recipes to the new recipe list
                for (markdownRecipeDescriptors in markdownArtifact.markdownRecipeDescriptors) {
                    newRecipes.add(markdownRecipeDescriptors.value)
                }
            }
        }
    }

    private fun getChangedRecipes(
        markdownArtifacts: TreeMap<String, MarkdownRecipeArtifact>,
        oldArtifacts: TreeMap<String, MarkdownRecipeArtifact>,
        newRecipes: TreeSet<MarkdownRecipeDescriptor>,
        removedRecipes: TreeSet<MarkdownRecipeDescriptor>,
    ): TreeSet<ChangedRecipe> {
        val changedRecipes = TreeSet<ChangedRecipe>()

        for (markdownArtifact in markdownArtifacts.values) {
            val oldArtifact = oldArtifacts[markdownArtifact.artifactId]

            if (oldArtifact != null) {
                for ((recipeDescriptorName, markdownRecipeDescriptor) in markdownArtifact.markdownRecipeDescriptors) {
                    if (newRecipes.contains(markdownRecipeDescriptor) || removedRecipes.contains(oldArtifact.markdownRecipeDescriptors[recipeDescriptorName])) {
                        // Don't report changes to recipe options if a recipe has been added or removed
                    } else {
                        val newOptions = markdownRecipeDescriptor.options
                        val oldOptions = oldArtifact.markdownRecipeDescriptors[recipeDescriptorName]?.options

                        if (newOptions != oldOptions) {
                            val changedRecipe = ChangedRecipe(
                                markdownArtifact.artifactId,
                                recipeDescriptorName,
                                markdownRecipeDescriptor.description,
                                markdownRecipeDescriptor.docLink,
                                newOptions,
                                oldOptions
                            )
                            changedRecipes.add(changedRecipe)
                        }
                    }
                }
            }
        }

        return changedRecipes
    }

    private fun buildChangelog(
        newArtifacts: TreeSet<String>,
        removedArtifacts: TreeSet<String>,
        newRecipes: TreeSet<MarkdownRecipeDescriptor>,
        removedRecipes: TreeSet<MarkdownRecipeDescriptor>,
        changedRecipes: TreeSet<ChangedRecipe>,
        deployType: String
    ) {
        // Get the date to label the changelog
        val current = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val formatted = current.format(formatter)

        val changelog: File = if (deployType == "release") {
            File("src/main/resources/CHANGELOG-$formatted.md")
        } else {
            File("src/main/resources/snapshot-CHANGELOG-$formatted.md")
        }

        // Clear the file in case this is being generated multiple times
        changelog.writeText("")

        if (deployType == "snapshot") {
            changelog.appendText("# Snapshot ($formatted)")

            changelog.appendText("\n\n{% hint style=\"info\" %}")
            changelog.appendText("\nWant to learn how to use snapshot versions in your project? Check out our [snapshot version guide](/reference/snapshot-instructions.md).")
            changelog.appendText("\n{% endhint %}\n\n")
        } else {
            changelog.appendText("# X.XX.X release ($formatted)\n\n")
        }

        // An example of what the changelog could look like after the below statements can be found here:
        // https://gist.github.com/mike-solomon/16727159ec86ee0f0406ba389cbaecb1
        if (newArtifacts.isNotEmpty()) {
            changelog.appendText("## New Artifacts")

            for (newArtifact in newArtifacts) {
                changelog.appendText("\n* $newArtifact")
            }

            changelog.appendText("\n\n")
        }

        if (removedArtifacts.isNotEmpty()) {
            changelog.appendText("## Removed Artifacts")

            for (removedArtifact in removedArtifacts) {
                changelog.appendText("\n* $removedArtifact")
            }

            changelog.appendText("\n\n")
        }

        if (newRecipes.isNotEmpty()) {
            changelog.appendText("## New Recipes")

            for (newRecipe in newRecipes) {
                changelog.appendText("\n* [${newRecipe.name}](${newRecipe.docLink}): ${newRecipe.description.trim()} ")
            }

            changelog.appendText("\n\n")
        }

        if (removedRecipes.isNotEmpty()) {
            changelog.appendText("## Removed Recipes")

            for (removedRecipe in removedRecipes) {
                changelog.appendText("\n* **${removedRecipe.name}**: ${removedRecipe.description.trim()} ")
            }

            changelog.appendText("\n\n")
        }

        if (changedRecipes.isNotEmpty()) {
            changelog.appendText("## Changed Recipes")

            for (changedRecipe in changedRecipes) {
                changelog.appendText("\n* [${changedRecipe.name}](${changedRecipe.docLink}) was changed:")
                changelog.appendText("\n  * Old Options:")

                if (changedRecipe.oldOptions?.isEmpty() == true) {
                    changelog.appendText("\n    * `None`")
                } else {
                    for (oldOption in changedRecipe.oldOptions!!) {
                        changelog.appendText("\n    * `${oldOption.name}: { type: ${oldOption.type}, required: ${oldOption.required} }`")
                    }
                }

                changelog.appendText("\n  * New Options:")

                if (changedRecipe.newOptions?.isEmpty() == true) {
                    changelog.appendText("\n    * `None`")
                } else {
                    for (newOption in changedRecipe.newOptions!!) {
                        changelog.appendText("\n    * `${newOption.name}: { type: ${newOption.type}, required: ${newOption.required} }`")
                    }
                }
            }
        }
    }

    private fun buildDiffLog(
        newRecipes: TreeSet<MarkdownRecipeDescriptor>,
    ) {
        val artifactToRecipes = TreeMap<String, TreeSet<MarkdownRecipeDescriptor>>()
        for (newRecipe in newRecipes) {
            if (artifactToRecipes.containsKey(newRecipe.artifactId)) {
                artifactToRecipes[newRecipe.artifactId]?.add(newRecipe)
            } else {
                val recipes = TreeSet<MarkdownRecipeDescriptor>();
                recipes.add(newRecipe)
                artifactToRecipes[newRecipe.artifactId] = recipes
            }
        }

        val diffFile = File("src/main/resources/$diffFileName.md")

        // Clear the file in case this is being generated multiple times
        diffFile.writeText("")

        if (artifactToRecipes.isNotEmpty()) {
            diffFile.appendText("# New Recipes")

            var totalTimeSaved = 0;

            for (artifact in artifactToRecipes.keys) {
                diffFile.appendText("\n\n## $artifact\n")

                val recipes = artifactToRecipes[artifact]

                if (recipes != null) {
                    var timeSavedPerArtifact = 0;

                    for (recipe in recipes) {
                        val isImperative = recipe.isImperative
                        var timeSaved = 4;

                        if (isImperative) {
                            timeSaved = 12;
                        }

                        totalTimeSaved += timeSaved
                        timeSavedPerArtifact += timeSaved

                        diffFile.appendText("\n* [${recipe.name}](${recipe.docLink}) — ${timeSaved}h")
                    }

                    diffFile.appendText("\n\nInitial recipe development time: ${timeSavedPerArtifact}h")
                }
            }

            diffFile.appendText("\n\nTotal initial recipe development time: ${totalTimeSaved}h")
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

        val displayName: String =
            if (descriptor == null) {
                StringUtils.capitalize(simpleName)
            } else {
                descriptor.displayName.replace("`", "")
            }

        /**
         * Produce the snippet for this category to be fitted into Gitbook's SUMMARY.md, which provides the index
         * that makes markdown documents accessible through gitbook's interface
         */
        fun summarySnippet(indentationDepth: Int): String {
            val indentBuilder = StringBuilder("  ")
            for (i in 0 until indentationDepth) {
                indentBuilder.append("  ")
            }
            val indent = indentBuilder.toString()
            val result = StringBuilder()

            if (path == "") {
                // Recipes that don't have a path are part of the "core" set of recipes
                result.appendLine("$indent* [Core](reference/recipes/core-README.md)")
            } else {
                result.appendLine("$indent* [$displayName](reference/recipes/$path/README.md)")
            }

            for (recipe in recipes) {
                // Section headings will display backticks, rather than rendering as code. Omit them so it doesn't look terrible
                result.appendLine(
                    "$indent  * [${
                        recipe.displayName.replace(
                            "`",
                            ""
                        )
                    }](${getRecipeRelativePath(recipe)}.md)"
                )
            }
            for (category in subcategories) {
                result.append(category.summarySnippet(indentationDepth + 1))
            }
            return result.toString()
        }

        /**
         * Produce the contents of the README.md file for this category.
         */
        private fun categoryIndex(): String {
            return StringBuilder().apply {
                appendLine("# $displayName")
                // While the description is not _supposed_ to be nullable it has happened before
                @Suppress("SENSELESS_COMPARISON")
                if (descriptor != null && descriptor.description != null) {
                    appendLine()
                    appendLine("_${descriptor.description}_")
                }
                appendLine()

                if (subcategories.isNotEmpty()) {
                    appendLine("## Categories")
                    appendLine()
                    for (subcategory in subcategories) {
                        appendLine("* [${subcategory.displayName}](/reference/recipes/${subcategory.path})")
                    }
                    appendLine()
                }

                if (recipes.isNotEmpty()) {
                    appendLine("## Recipes")
                    appendLine()
                    for (recipe in recipes) {
                        val recipeSimpleName = recipe.name.substring(recipe.name.lastIndexOf('.') + 1).lowercase()
                        // Anything except a relative link ending in .md will be mangled.
                        // If you touch this line double check that it works when imported into gitbook
                        appendLine("* [${recipe.displayName}](./${recipeSimpleName}.md)")
                    }
                    appendLine()
                }

            }.toString()
        }

        fun writeCategoryIndex(outputRoot: Path) {
            if (path.isBlank()) {
                // "Core" recipes need to be handled differently as they do not have a path like other recipes.
                val corePath = outputRoot.resolve("core-README.md")

                Files.newBufferedWriter(corePath, StandardOpenOption.CREATE).useAndApply {
                    writeln("# Core Recipes")
                    newLine()
                    writeln("_Recipes broadly applicable to all types of source files._")
                    newLine()
                    writeln("## Recipes")
                    newLine()

                    for (recipe in recipes) {
                        val recipeSimpleName = recipe.name.substring(recipe.name.lastIndexOf('.') + 1).lowercase()

                        writeln("* [${recipe.displayName}](${recipeSimpleName}.md)")
                    }
                }

                // Also need to make an empty README for GitBook linking
                val emptyReadmePath = outputRoot.resolve("README.md")

                Files.newBufferedWriter(emptyReadmePath, StandardOpenOption.CREATE).useAndApply {
                    writeln("# Recipes")
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
        gradlePluginVersion: String,
        mavenPluginVersion: String
    ) {
        val recipeMarkdownPath = getRecipePath(outputPath, recipeDescriptor)
        Files.createDirectories(recipeMarkdownPath.parent)
        Files.newBufferedWriter(recipeMarkdownPath, StandardOpenOption.CREATE).useAndApply {
            write(
                """
                # ${recipeDescriptor.displayName}
                
                **${recipeDescriptor.name.replace("_".toRegex(), "\\\\_")}**
                
            """.trimIndent()
            )

            newLine()

            if (!isNullOrEmpty(recipeDescriptor.description)) {
                writeln("_" + recipeDescriptor.description.trim() + "_")
            }
            newLine()
            if (recipeDescriptor.tags.isNotEmpty()) {
                writeln("### Tags")
                newLine()
                for (tag in recipeDescriptor.tags) {
                    writeln("* $tag")
                }
                newLine()
            }
            writeln(
                """
                ## Source
                
                [GitHub](${
                    origin.githubUrl(
                        recipeDescriptor.name,
                        recipeDescriptor.source
                    )
                }), [Issue Tracker](${origin.issueTrackerUrl()}), [Maven Central](https://central.sonatype.com/artifact/${origin.groupId}/${origin.artifactId}/${origin.version}/jar)
                
                * groupId: ${origin.groupId}
                * artifactId: ${origin.artifactId}
                * version: ${origin.version}
                
            """.trimIndent()
            )

            // Options
            if (recipeDescriptor.options.isNotEmpty()) {
                writeln(
                        """
                    ## Options
                    
                    | Type | Name | Description |
                    | -- | -- | -- |
                """.trimIndent()
                )
                for (option in recipeDescriptor.options) {
                    var description = if (option.description == null) {
                        ""
                    } else {
                        option.description
                    }
                    description = if (option.isRequired) {
                        description
                    } else {
                        "*Optional*. $description"
                    }
                    // This should preserve casing and plurality
                    description = description.replace("method patterns?".toRegex(RegexOption.IGNORE_CASE)) { match ->
                        "[${match.value}](/reference/method-patterns.md)"
                    }
                    writeln(
                            """
                        | `${option.type}` | ${option.name} | $description |
                    """.trimIndent()
                    )
                }
                newLine()
            }

            // examples
            if (!recipeDescriptor.examples.isEmpty()) {
                val subject = if (recipeDescriptor.examples.size > 1) "Examples" else "Example"
                writeln("## ${subject}")

                for (i in 0 until recipeDescriptor.examples.size) {
                    if (i > 0) {
                        writeln("---")
                    }

                    val example = recipeDescriptor.examples.get(i)
                    val description =
                        if (example.description != null && example.description.isNotEmpty()) example.description else ""

                    if (recipeDescriptor.examples.size > 1) {
                        writeln("##### Example ${i + 1}: " + description)
                    }

//                    if (example.parameters != null && !example.parameters.isEmpty()) {
//                        write("|")
//                        for (param in example.parameters) {
//                            write(" ")
//                            write(param)
//                            write(" |")
//                        }
//                    }

                    for (source in example.sources) {
                        val hasChange = source.after != null && source.after.isNotEmpty()
                        val beforeTitle = if (hasChange) "Before" else "No change"
                        val isNewFile = source.before == null && source.after != null
                        val afterTile = if (isNewFile) "New file" else "After"

                        newLine()

                        writeln("###### ${beforeTitle}")
                        writeln("{% code title=\"${source.path}\" %}")
                        writeln(
                                """
                        |```${source.language}
                        |${source.before}
                        |```
                        """.trimMargin()
                        )
                        writeln("{% endcode %}")

                        if (hasChange) {
                            newLine()
                            writeln("###### ${afterTile}")
                            writeln("{% code title=\"${source.path}\" %}")
                            writeln(
                                    """
                                |```${source.language}
                                |${source.after}
                                |```
                                """.trimMargin()
                            )
                        }

                        writeln("{% endcode %}")
                    }
                }
                newLine()
            }

            // Contributors
            if (recipeDescriptor.contributors.isNotEmpty()) {
                writeln("## Contributors")
                for (contributors in recipeDescriptor.contributors) {
                    writeln("* [${contributors.name}](${contributors.email})")
                }
                newLine()
            }

            // Usage
            newLine()
            writeln("## Usage")
            newLine()

            val suppressMaven = recipeDescriptor.name.contains(".gradle.")
            val suppressGradle = recipeDescriptor.name.contains(".maven.")
            val requiresConfiguration = recipeDescriptor.options.any { it.isRequired }
            val requiresDependency = !origin.isFromCoreLibrary()
            if (requiresConfiguration) {
                val exampleRecipeName =
                    "com.yourorg." + recipeDescriptor.name.substring(recipeDescriptor.name.lastIndexOf('.') + 1) + "Example"
                write("This recipe has required configuration parameters. ")
                write("Recipes with required configuration parameters cannot be activated directly. ")
                write("To activate this recipe you must create a new recipe which fills in the required parameters. ")
                write("In your `rewrite.yml` create a new recipe with a unique name. ")
                write("For example: `$exampleRecipeName`.")
                newLine()
                writeln("Here's how you can define and customize such a recipe within your rewrite.yml:")
                write(
                    """
                    
                    {% code title="rewrite.yml" %}
                    ```yaml
                    ---
                    type: specs.openrewrite.org/v1beta/recipe
                    name: $exampleRecipeName
                    displayName: ${recipeDescriptor.displayName} example
                    recipeList:
                      - ${recipeDescriptor.name}:
                    
                """.trimIndent()
                )
                for (option in recipeDescriptor.options) {
                    val ex = if (option.example != null && "String" == option.type
                        && (option.example.matches("^[{}\\[\\],`|=%@*!?-].*".toRegex())
                                || option.example.matches(".*:\\s.*".toRegex()))
                    ) {
                        "'" + option.example + "'"
                    } else {
                        option.example
                    }
                    writeln("      ${option.name}: $ex")
                }
                writeln("```")
                writeln("{% endcode %}")
                newLine()
                if (requiresDependency) {
                    writeSnippetsWithConfigurationWithDependency(
                        exampleRecipeName,
                        origin,
                        gradlePluginVersion,
                        mavenPluginVersion,
                        suppressMaven,
                        suppressGradle
                    )
                } else {
                    writeSnippetsWithConfigurationWithoutDependency(
                        exampleRecipeName,
                        gradlePluginVersion,
                        mavenPluginVersion,
                        suppressMaven,
                        suppressGradle
                    )
                }
            } else {
                if (origin.isFromCoreLibrary()) {
                    writeSnippetsFromCoreLibrary(
                        recipeDescriptor, gradlePluginVersion, mavenPluginVersion, suppressMaven, suppressGradle
                    )
                } else {
                    writeSnippetForOtherLibrary(
                        origin, recipeDescriptor, gradlePluginVersion, mavenPluginVersion, suppressMaven, suppressGradle
                    )
                }
            }

            if (recipeDescriptor.recipeList.isNotEmpty()) {
                writeln(
                    """
                    
                    ## Definition
                    
                    {% tabs %}
                    {% tab title="Recipe List" %}
                """.trimIndent()
                )
                val recipeDepth = getRecipePath(recipeDescriptor).chars().filter { ch: Int -> ch == '/'.code }.count()
                val pathToRecipesBuilder = StringBuilder()
                for (i in 0 until recipeDepth) {
                    pathToRecipesBuilder.append("../")
                }
                val pathToRecipes = pathToRecipesBuilder.toString()
                for (recipe in recipeDescriptor.recipeList) {
                    writeln("* [" + recipe.displayName + "](" + pathToRecipes + getRecipePath(recipe) + ".md)")
                    if (recipe.options.isNotEmpty()) {
                        for (option in recipe.options) {
                            if (option.value != null) {
                                writeln("  * " + option.name + ": `" + printValue(option.value!!) + "`")
                            }
                        }
                    }
                }
                newLine()
                writeln(
                    """
                    {% endtab %}

                    {% tab title="Yaml Recipe List" %}
                    ```yaml
                """.trimIndent()
                )
                writeln(recipeDescriptor.asYaml())
                writeln(
                    """
                    ```
                    {% endtab %}
                    {% endtabs %}
                """.trimIndent()
                )
            }

            newLine()
            writeln(
                """
                ## See how this recipe works across multiple open-source repositories

                [![Moderne Link Image](/.gitbook/assets/ModerneRecipeButton.png)](https://public.moderne.io/recipes/${recipeDescriptor.name})

                The community edition of the Moderne platform enables you to easily run recipes across thousands of open-source repositories.

                Please [contact Moderne](https://moderne.io/product) for more information about safely running the recipes on your own codebase in a private SaaS.
            """.trimIndent()
            )
        }
    }

    private fun BufferedWriter.writeSnippetsWithConfigurationWithoutDependency(
        exampleRecipeName: String,
        gradlePluginVersion: String,
        mavenPluginVersion: String,
        suppressMaven: Boolean,
        suppressGradle: Boolean
    ) {
        val gradleSnippet = if (suppressGradle) "" else """
                            {% tab title="Gradle" %}
                            {% code title="build.gradle" %}
                            ```groovy
                            plugins {
                                id("org.openrewrite.rewrite") version("$gradlePluginVersion")
                            }
                            
                            rewrite {
                                activeRecipe("$exampleRecipeName")
                            }
                            
                            repositories {
                                mavenCentral()
                            }
                            ```
                            {% endcode %}
                            {% endtab %}
                            """.trimIndent()
        val mavenSnippet = if (suppressMaven) "" else """
                            {% tab title="Maven" %}
                            {% code title="pom.xml" %}
                            ```markup
                            <project>
                              <build>
                                <plugins>
                                  <plugin>
                                    <groupId>org.openrewrite.maven</groupId>
                                    <artifactId>rewrite-maven-plugin</artifactId>
                                    <version>$mavenPluginVersion</version>
                                    <configuration>
                                      <activeRecipes>
                                        <recipe>$exampleRecipeName</recipe>
                                      </activeRecipes>
                                    </configuration>
                                  </plugin>
                                </plugins>
                              </build>
                            </project>
                            ```
                            {% endcode %}
                            {% endtab %}
                            """.trimIndent()
        writeln(
            """
Now that `$exampleRecipeName` has been defined activate it in your build file:
{% tabs %}
$gradleSnippet
$mavenSnippet
{% endtabs %}
""".trimIndent()
        )
    }

    private fun BufferedWriter.writeSnippetsWithConfigurationWithDependency(
        exampleRecipeName: String,
        origin: RecipeOrigin,
        gradlePluginVersion: String,
        mavenPluginVersion: String,
        suppressMaven: Boolean,
        suppressGradle: Boolean
    ) {
        val gradleSnippet = if (suppressGradle) "" else """
                            {% tab title="Gradle" %}
                            {% code title="build.gradle" %}
                            ```groovy
                            plugins {
                                id("org.openrewrite.rewrite") version("$gradlePluginVersion")
                            }
                            
                            rewrite {
                                activeRecipe("$exampleRecipeName")
                            }
                            
                            repositories {
                                mavenCentral()
                            }
                            
                            dependencies {
                                rewrite("${origin.groupId}:${origin.artifactId}:${origin.version}")
                            }
                            ```
                            {% endcode %}
                            {% endtab %}
                            """.trimIndent()
        val mavenSnippet = if (suppressMaven) "" else """
                            {% tab title="Maven" %}
                            {% code title="pom.xml" %}
                            ```markup
                            <project>
                              <build>
                                <plugins>
                                  <plugin>
                                    <groupId>org.openrewrite.maven</groupId>
                                    <artifactId>rewrite-maven-plugin</artifactId>
                                    <version>$mavenPluginVersion</version>
                                    <configuration>
                                      <activeRecipes>
                                        <recipe>$exampleRecipeName</recipe>
                                      </activeRecipes>
                                    </configuration>
                                    <dependencies>
                                      <dependency>
                                        <groupId>${origin.groupId}</groupId>
                                        <artifactId>${origin.artifactId}</artifactId>
                                        <version>${origin.version}</version>
                                      </dependency>
                                    </dependencies>
                                  </plugin>
                                </plugins>
                              </build>
                            </project>
                            ```
                            {% endcode %}
                            {% endtab %}
                            """.trimIndent()
        writeln(
            """
Now that `$exampleRecipeName` has been defined activate it and take a dependency on ${origin.groupId}:${origin.artifactId}:${origin.version} in your build file:
{% tabs %}
$gradleSnippet
$mavenSnippet
{% endtabs %}
""".trimIndent()
        )
    }

    private fun BufferedWriter.writeSnippetsFromCoreLibrary(
        recipeDescriptor: RecipeDescriptor,
        gradlePluginVersion: String,
        mavenPluginVersion: String,
        suppressMaven: Boolean,
        suppressGradle: Boolean
    ) {
        writeln(
            "This recipe has no required configuration parameters and comes from a rewrite core library. " +
                    "It can be activated directly without adding any dependencies."
        )
        val gradleSnippet = if (suppressGradle) "" else """
                            {% tab title="Gradle" %}
                            {% code title="build.gradle" %}
                            ```groovy
                            plugins {
                                id("org.openrewrite.rewrite") version("$gradlePluginVersion")
                            }
                            
                            rewrite {
                                activeRecipe("${recipeDescriptor.name}")
                            }
                            
                            repositories {
                                mavenCentral()
                            }
                            
                            ```
                            {% endcode %}
                            {% endtab %}
                            """.trimIndent()
        val mavenSnippet = if (suppressMaven) "" else """
                            {% tab title="Maven POM" %}
                            {% code title="pom.xml" %}
                            ```markup
                            <project>
                              <build>
                                <plugins>
                                  <plugin>
                                    <groupId>org.openrewrite.maven</groupId>
                                    <artifactId>rewrite-maven-plugin</artifactId>
                                    <version>$mavenPluginVersion</version>
                                    <configuration>
                                      <activeRecipes>
                                        <recipe>${recipeDescriptor.name}</recipe>
                                      </activeRecipes>
                                    </configuration>
                                  </plugin>
                                </plugins>
                              </build>
                            </project>
                            ```
                            {% endcode %}
                            {% endtab %}
                            
                            {% tab title="Maven Command Line" %}
                            You will need to have [Maven](https://maven.apache.org/download.cgi) installed on your machine before you can run the following command.
                            {% code title="shell" %}
                            ```shell
                            mvn -U org.openrewrite.maven:rewrite-maven-plugin:run \
                              -Drewrite.activeRecipes=${recipeDescriptor.name}
                            ```
                            {% endcode %}
                            {% endtab %}
                            """.trimIndent()
        writeln(
            """
{% tabs %}
$gradleSnippet
$mavenSnippet
{% endtabs %}
""".trimIndent()
        )
    }

    private fun BufferedWriter.writeSnippetForOtherLibrary(
        origin: RecipeOrigin,
        recipeDescriptor: RecipeDescriptor,
        gradlePluginVersion: String,
        mavenPluginVersion: String,
        suppressMaven: Boolean,
        suppressGradle: Boolean
    ) {
        writeln(
            "This recipe has no required configuration options. It can be activated by adding a dependency on " +
                    "`${origin.groupId}:${origin.artifactId}:${origin.version}` in your build file or by running a shell " +
                    "command (in which case no build changes are needed): "
        )
        val gradleSnippet = if (suppressGradle) "" else """
                            {% tab title="Gradle" %}
                            {% code title="build.gradle" %}
                            ```groovy
                            plugins {
                                id("org.openrewrite.rewrite") version("$gradlePluginVersion")
                            }
                            
                            rewrite {
                                activeRecipe("${recipeDescriptor.name}")
                            }
                            
                            repositories {
                                mavenCentral()
                            }
                            
                            dependencies {
                                rewrite("${origin.groupId}:${origin.artifactId}:${origin.version}")
                            }
                            ```
                            {% endcode %}
                            {% endtab %}
                            """.trimIndent()
        val mavenSnippet = if (suppressMaven) "" else """
                            {% tab title="Maven POM" %}
                            {% code title="pom.xml" %}
                            ```markup
                            <project>
                              <build>
                                <plugins>
                                  <plugin>
                                    <groupId>org.openrewrite.maven</groupId>
                                    <artifactId>rewrite-maven-plugin</artifactId>
                                    <version>$mavenPluginVersion</version>
                                    <configuration>
                                      <activeRecipes>
                                        <recipe>${recipeDescriptor.name}</recipe>
                                      </activeRecipes>
                                    </configuration>
                                    <dependencies>
                                      <dependency>
                                        <groupId>${origin.groupId}</groupId>
                                        <artifactId>${origin.artifactId}</artifactId>
                                        <version>${origin.version}</version>
                                      </dependency>
                                    </dependencies>
                                  </plugin>
                                </plugins>
                              </build>
                            </project>
                            ```
                            {% endcode %}
                            {% endtab %}
                            
                            {% tab title="Maven Command Line" %}
                            {% code title="shell" %}
                            You will need to have [Maven](https://maven.apache.org/download.cgi) installed on your machine before you can run the following command.
                            
                            ```shell
                            mvn -U org.openrewrite.maven:rewrite-maven-plugin:run \
                              -Drewrite.recipeArtifactCoordinates=${origin.groupId}:${origin.artifactId}:RELEASE \
                              -Drewrite.activeRecipes=${recipeDescriptor.name}
                            ```
                            {% endcode %}
                            {% endtab %}
                            """.trimIndent()
        writeln(
            """
{% tabs %}
$gradleSnippet
$mavenSnippet
{% endtabs %}
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
            if (recipe.name.startsWith("org.openrewrite")) {
                recipe.name.substring(16).replace("\\.".toRegex(), "/").lowercase(Locale.getDefault())
            } else {
                throw RuntimeException("Recipe package unrecognized: ${recipe.name}")
            }

        private fun getRecipePath(recipesPath: Path, recipeDescriptor: RecipeDescriptor) =
            recipesPath.resolve(getRecipePath(recipeDescriptor) + ".md")

        private fun getRecipeRelativePath(recipe: RecipeDescriptor): String =
            "reference/recipes/" + getRecipePath(recipe)

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
}