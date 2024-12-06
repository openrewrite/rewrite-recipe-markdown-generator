package org.openrewrite

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.difflib.DiffUtils
import com.github.difflib.patch.Patch
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
import java.util.stream.Collectors
import kotlin.io.path.toPath
import kotlin.system.exitProcess

// These recipes contain invalid markdown and would cause issues
// with doc generation if left in.
private val recipesToIgnore = listOf(
    "org.apache.camel.upgrade.camel45.UseExtendedCamelContextGetters"
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
        description = ["The version of the Rewrite Gradle Plugin to display in relevant samples"]
    )
    lateinit var gradlePluginVersion: String

    @Parameters(
        index = "6",
        defaultValue = "",
        description = ["The version of the Rewrite Maven Plugin to display in relevant samples"]
    )
    lateinit var mavenPluginVersion: String

    @Parameters(
        index = "7",
        defaultValue = "release",
        description = ["The type of deploy being done (either release or snapshot)"]
    )
    lateinit var deployType: String

    @Parameters(
        index = "8",
        defaultValue = "renameMe",
        description = ["The name of the diff file to be generated when making a diff log"]
    )
    lateinit var diffFileName: String

    // These are common in every recipe - so let's not use them when generating the list of recipes with data tables.
    private val dataTablesToIgnore = listOf(
        "org.openrewrite.table.SourcesFileResults",
        "org.openrewrite.table.SourcesFileErrors",
        "org.openrewrite.table.RecipeRunStats"
    )

    override fun run() {
        val outputPath = Paths.get(destinationDirectoryName)

        val env: Environment
        val recipeOrigins: Map<URI, RecipeOrigin>

        if (recipeSources.isNotEmpty()) {
            recipeOrigins = RecipeOrigin.parse(recipeSources)

            // Write latest-versions-of-every-openrewrite-module.md
            createLatestVersionsJs(outputPath, recipeOrigins)
            createLatestVersionsMarkdown(outputPath, recipeOrigins)
            if (recipeClasspath.isEmpty()) {
                return
            }

            // Load recipe details into memory
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

        val recipesPath = outputPath.resolve("recipes")
        try {
            Files.createDirectories(recipesPath)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

        // Recipes fully loaded into recipeDescriptors
        val recipeDescriptors: Collection<RecipeDescriptor> = env.listRecipeDescriptors()
        val categoryDescriptors = ArrayList(env.listCategoryDescriptors())
        val markdownArtifacts = TreeMap<String, MarkdownRecipeArtifact>()

        val recipesWithDataTables = ArrayList<RecipeDescriptor>();

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

            val filteredDataTables = recipeDescriptor.dataTables.filter { dataTable ->
                dataTable.name !in dataTablesToIgnore
            }

            if (filteredDataTables.isNotEmpty()) {
                recipesWithDataTables.add(recipeDescriptor);
            }

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

            val docBaseUrl = "https://docs.openrewrite.org/recipes/"

            // Changes something like org.openrewrite.circleci.InstallOrb to https://docs.openrewrite.org/recipes/circleci/installorb
            var docLink = docBaseUrl + recipeDescriptor.name.lowercase(Locale.getDefault())
                .removePrefix("org.openrewrite.")
                .removePrefix("io.moderne.")
                .replace('.', '/')
                .replace(
                    "$",
                    "usd"
                ) // needed for refaster templates + gitbook as we have started using $ in our recipe descriptors :(

            // Some of our recipes fall in a "core" category. These do not have a package like other recipes.
            // For example: org.openrewrite.recipeName
            // In this case, we want the generated link to include the /core/ directory.
            if (recipeDescriptor.name.count { it == '.' } == 2) {
                docLink = docBaseUrl + "core/" + recipeDescriptor.name.lowercase(Locale.getDefault())
                    .removePrefix("org.openrewrite.")
                    .removePrefix("io.moderne.")
                    .replace('.', '/')
                    .replace(
                        "$",
                        "usd"
                    ) // needed for refaster templates + gitbook as we have started using $ in our recipe descriptors :(
            }

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

        val categories = Category.fromDescriptors(recipeDescriptors, categoryDescriptors).sortedBy { it.simpleName }

        // Write recipes-with-data-tables.md
        val recipesWithDataTablesPath = outputPath.resolve("recipes-with-data-tables.md")
        Files.newBufferedWriter(recipesWithDataTablesPath, StandardOpenOption.CREATE).useAndApply {
            writeln("# Recipes with Data Tables\n")

            //language=markdown
            writeln("_This doc contains all of the recipes with **unique** data tables that have been explicitly " +
                    "added by the recipe author. If a recipe contains only the default data tables, " +
                    "it won't be included in this list._\n")

            for (recipe in recipesWithDataTables) {
                var recipePath = "";

                if (recipe.name.count { it == '.' } == 2 &&
                    recipe.name.contains("org.openrewrite.")) {
                    recipePath = "recipes/core/" + recipe.name.removePrefix("org.openrewrite.").lowercase();
                } else if (recipe.name.contains("io.moderne.ai")) {
                    recipePath = "recipes/ai/" + recipe.name.removePrefix("io.moderne.ai.").replace(".", "/").lowercase();
                } else {
                    recipePath = "recipes/" + recipe.name.removePrefix("org.openrewrite.").replace(".", "/").lowercase();
                }

                writeln("### [${recipe.displayName}](../${recipePath})\n ")
                writeln("_${recipe.name}_\n")
                writeln("${recipe.description}\n")
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

        // Write the README.md for each category
        for (category in categories) {
            val categoryIndexPath = outputPath.resolve("recipes/")
            category.writeCategoryIndex(categoryIndexPath)
        }
    }

    private fun createLatestVersionsMarkdown(
        outputPath: Path,
        recipeOrigins: Map<URI, RecipeOrigin>
    ) {
        val versionsSnippetPath = outputPath.resolve("latest-versions-of-every-openrewrite-module.md")
        Files.newBufferedWriter(versionsSnippetPath, StandardOpenOption.CREATE).useAndApply {
            val bomLink =
                "[${rewriteRecipeBomVersion}](https://github.com/openrewrite/rewrite-recipe-bom/releases/tag/v${rewriteRecipeBomVersion})"
            val mavenLink =
                "[${mavenPluginVersion}](https://github.com/openrewrite/rewrite-maven-plugin/releases/tag/v${mavenPluginVersion})"
            val gradleLink =
                "[${gradlePluginVersion}](https://github.com/openrewrite/rewrite-gradle-plugin/releases/tag/v${gradlePluginVersion})"

            //language=markdown
            writeln(
                """
                # Latest versions of every OpenRewrite module

                OpenRewrite's modules are published to [Maven Central](https://search.maven.org/search?q=org.openrewrite).
                Each time a release is made, a bill of materials artifact is also published to correctly align and manage the versions of all published artifacts.
                The Gradle plugin is published to the [Gradle Plugin Portal](https://plugins.gradle.org/plugin/org.openrewrite.rewrite).

                It is highly recommended that developers use the [rewrite-recipe-bom](https://github.com/openrewrite/rewrite-recipe-bom)
                to align the versions of Rewrite's modules to ensure compatibility.
                The use of the "bill of materials" means that a developer will only need to specify explicit versions of the BOM and the build plugins:

                | Module                                                                                                                | Version    |
                |-----------------------------------------------------------------------------------------------------------------------| ---------- |
                | [**org.openrewrite.recipe:rewrite-recipe-bom**](https://github.com/openrewrite/rewrite-recipe-bom)                    | **${bomLink}** |
                | [**org.openrewrite:rewrite-maven-plugin**](https://github.com/openrewrite/rewrite-maven-plugin)                       | **${mavenLink}** |
                | [**org.openrewrite:rewrite-gradle-plugin**](https://github.com/openrewrite/rewrite-gradle-plugin)                     | **${gradleLink}** |
                """.trimIndent()
            )
            var cliInstallGavs = ""
            for (origin in recipeOrigins.values) {
                val versionPlaceholder = "{{VERSION_${origin.artifactId.uppercase().replace('-', '_')}}}"
                cliInstallGavs += "${origin.groupId}:${origin.artifactId}:${versionPlaceholder} "
                val repoLink = "[${origin.groupId}:${origin.artifactId}](${origin.githubUrl()})"
                val releaseLink = "[${origin.version}](${origin.githubUrl()}/releases/tag/v${origin.version})"
                writeln("| ${repoLink.padEnd(117)} | ${releaseLink.padEnd(90)} |")
            }
            //language=markdown
            writeln(
                """
                
                ## CLI Installation
                
                Install all of the latest versions of the OpenRewrite recipe modules into the Moderne CLI:
                
                ```bash
                mod config recipes jar install ${cliInstallGavs}
                ```
                """.trimIndent()
            )
        }
    }

    private fun createLatestVersionsJs(
        outputPath: Path,
        recipeOrigins: Map<URI, RecipeOrigin>
    ) {
        val versionsSnippetPath = outputPath.resolve("latest-versions.js")
        Files.newBufferedWriter(versionsSnippetPath, StandardOpenOption.CREATE).useAndApply {
            var recipeModuleVersions = ""
            for (origin in recipeOrigins.values) {
                val key = origin.artifactId.uppercase().replace('-', '_')
                recipeModuleVersions += "                  \"{{VERSION_$key}}\": \"${origin.version}\",\n"
            }
            writeln(
                //language=ts
                """
                const latestVersions = {
                  "{{VERSION_REWRITE_RECIPE_BOM}}": "${rewriteRecipeBomVersion}",
                  "{{VERSION_REWRITE_GRADLE_PLUGIN}}": "${gradlePluginVersion}",
                  "{{VERSION_REWRITE_MAVEN_PLUGIN}}": "${mavenPluginVersion}",
                  ${recipeModuleVersions.trim()}
                };
                export default latestVersions;
                """.trimIndent()
            )
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
        val formatted = getDateFormattedYYYYMMDD()

        val changelog: File = if (deployType == "release") {
            File("src/main/resources/${rewriteBomVersion.replace('.', '-')}-Release.md")
        } else {
            File("src/main/resources/snapshot-CHANGELOG-$formatted.md")
        }

        // Clear the file in case this is being generated multiple times
        changelog.writeText("")

        if (deployType == "snapshot") {
            changelog.appendText("# Snapshot ($formatted)")

            changelog.appendText("\n\n:::info")
            changelog.appendText("\nWant to learn how to use snapshot versions in your project? Check out our [snapshot version guide](/reference/snapshot-instructions.md).")
            changelog.appendText("\n:::\n\n")
        } else {
            changelog.appendText("# $rewriteBomVersion release ($formatted)")

            changelog.appendText("\n\n:::info")
            changelog.appendText("\nThis changelog only shows what recipes have been added, removed, or changed. OpenRewrite may do releases that do not include these types of changes. To see these changes, please go to the [releases page](https://github.com/openrewrite/rewrite/releases).")
            changelog.appendText("\n:::\n\n")
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
            changelog.appendText("## New Recipes\n")

            for (newRecipe in newRecipes) {
                changelog.appendText(
                    "\n* [${newRecipe.name}](${newRecipe.docLink}): ${
                        newRecipe.description.trim().replace("\n", "<br />")
                    } "
                )
            }

            changelog.appendText("\n\n")
        }

        if (removedRecipes.isNotEmpty()) {
            changelog.appendText("## Removed Recipes\n")

            for (removedRecipe in removedRecipes) {
                changelog.appendText("\n* **${removedRecipe.name}**: ${removedRecipe.description.trim()} ")
            }

            changelog.appendText("\n\n")
        }

        if (changedRecipes.isNotEmpty()) {
            changelog.appendText("## Changed Recipes\n")

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

    private fun getDateFormattedYYYYMMDD(): String? {
        val current = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        return current.format(formatter)
    }

    private fun buildDiffLog(
        newRecipes: TreeSet<MarkdownRecipeDescriptor>,
    ) {
        val artifactToRecipes = TreeMap<String, TreeSet<MarkdownRecipeDescriptor>>()
        for (newRecipe in newRecipes) {
            if (artifactToRecipes.containsKey(newRecipe.artifactId)) {
                artifactToRecipes[newRecipe.artifactId]?.add(newRecipe)
            } else {
                val recipes = TreeSet<MarkdownRecipeDescriptor>()
                recipes.add(newRecipe)
                artifactToRecipes[newRecipe.artifactId] = recipes
            }
        }

        val diffFile = File("src/main/resources/$diffFileName.md")

        // Clear the file in case this is being generated multiple times
        diffFile.writeText("")

        if (artifactToRecipes.isNotEmpty()) {
            diffFile.appendText("# New Recipes")

            var totalTimeSaved = 0

            for (artifact in artifactToRecipes.keys) {
                diffFile.appendText("\n\n## $artifact\n")

                val recipes = artifactToRecipes[artifact]

                if (recipes != null) {
                    var timeSavedPerArtifact = 0

                    for (recipe in recipes) {
                        val isImperative = recipe.isImperative
                        var timeSaved = 4

                        if (isImperative) {
                            timeSaved = 12
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

                            var recipeSimpleName = recipe.name.substring(recipe.name.lastIndexOf('.') + 1).lowercase()
                            val formattedDisplayName = recipe.displayName
                                .replace("<script>", "\\<script\\>")
                                .replace("<p>", "< p >")

                            val recipePathToDocusaurusRenamedPath: Map<String, String> = mapOf(
                                "org.openrewrite.java.testing.assertj.Assertj" to "assertj-best-practices",
                                "org.openrewrite.java.migrate.javaee7" to "javaee7-recipe",
                                "org.openrewrite.java.migrate.javaee8" to "javaee8-recipe"
                            )

                            // Docusaurus expects that if a file is called "assertj" inside of the folder "assertj" that it's the
                            // README for said folder. Due to how generic we've made this recipe name, we need to change it for the
                            // docs so that they parse correctly.
                            if (recipePathToDocusaurusRenamedPath.containsKey(recipe.name)) {
                                recipeSimpleName = recipePathToDocusaurusRenamedPath[recipe.name]!!
                            }

                            // Anything except a relative link ending in .md will be mangled.
                            // If you touch this line double check that it works when imported into gitbook
                            appendLine("* [${formattedDisplayName}](./${recipeSimpleName}.md)")
                        }

                        appendLine()
                    }

                    if (normalRecipes.isNotEmpty()) {
                        appendLine("## Recipes")
                        appendLine()

                        for (recipe in normalRecipes) {
                            val recipeSimpleName = recipe.name.substring(recipe.name.lastIndexOf('.') + 1).lowercase()
                            val formattedDisplayName = recipe.displayName
                                .replace("<script>", "\\<script\\>")
                                .replace("<p>", "< p >")

                            // Anything except a relative link ending in .md will be mangled.
                            // If you touch this line double check that it works when imported into gitbook
                            appendLine("* [${formattedDisplayName}](./${recipeSimpleName}.md)")
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
                        val recipeSimpleName = recipe.name.substring(recipe.name.lastIndexOf('.') + 1).lowercase()
                        val formattedDisplayName = recipe.displayName
                            .replace("<", "\\<")
                            .replace(">", "\\>")

                        writeln("* [${formattedDisplayName}](./${recipeSimpleName}.md)")
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
        gradlePluginVersion: String,
        mavenPluginVersion: String
    ) {
        if (recipesToIgnore.contains(recipeDescriptor.name)) {
            return;
        }

        val sidebarFormattedName = recipeDescriptor.displayName
            .replace("`", "")
            .replace("\"", "\\\"")
            .replace("<script>", "<script >")
            .replace("<p>", "< p >")
            .trim()

        val formattedRecipeTitle = recipeDescriptor?.displayName
            ?.replace("`<", "&lt;")
            ?.replace(">`", "&gt;")
            ?.replace("<", "&lt;")
            ?.replace(">", "&gt;")
            ?.trim()

        val formattedRecipeDescription = getFormattedRecipeDescription(recipeDescriptor)

        val formattedLongRecipeName = recipeDescriptor.name.replace("_".toRegex(), "\\\\_").trim()

        val recipeMarkdownPath = getRecipePath(outputPath, recipeDescriptor)
        Files.createDirectories(recipeMarkdownPath.parent)
        Files.newBufferedWriter(recipeMarkdownPath, StandardOpenOption.CREATE).useAndApply {
            write(
                """
---
sidebar_label: "$sidebarFormattedName"
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# $formattedRecipeTitle

**$formattedLongRecipeName**

""".trimIndent()
            )

            newLine()

            if (!isNullOrEmpty(recipeDescriptor.description)) {
                writeln(formattedRecipeDescription)
            }
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
                        writeln("* $tag")
                    }
                }
                newLine()
            }

            writeSourceLinks(recipeDescriptor, origin)
            writeOptions(recipeDescriptor)
            writeDefinition(recipeDescriptor)
            writeUsage(recipeDescriptor, origin)
            writeModerneLink(recipeDescriptor)
            writeDataTables(recipeDescriptor)
            writeExamples(recipeDescriptor)
            writeContributors(recipeDescriptor)
        }
    }

    private fun getFormattedRecipeDescription(recipeDescriptor: RecipeDescriptor): String {
        val specialCharacters = listOf('<', '>', '{', '}')
        val formattedRecipeDescription = recipeDescriptor?.description

        if (specialCharacters.any { recipeDescriptor?.description?.contains(it) == true }) {
            return "```\n${formattedRecipeDescription?.replace("```", "")?.trim()}\n```\n"
        } else {
            return "_" + formattedRecipeDescription?.replace("\n", " ")?.trim() + "_"
        }
    }

    private fun BufferedWriter.writeSourceLinks(recipeDescriptor: RecipeDescriptor, origin: RecipeOrigin) {
        val versionPlaceholderKey = "{{VERSION_${origin.artifactId.uppercase().replace('-', '_')}}}"
        //language=markdown
        writeln(
            """
            ## Recipe source
            
            [GitHub](${
                origin.githubUrl(
                    recipeDescriptor.name,
                    recipeDescriptor.source
                )
            }), [Issue Tracker](${origin.issueTrackerUrl()}), [Maven Central](https://central.sonatype.com/artifact/${origin.groupId}/${origin.artifactId}/)

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
                // This should preserve casing and plurality
                description = description.replace("method patterns?".toRegex(RegexOption.IGNORE_CASE)) { match ->
                    "[${match.value}](/reference/method-patterns)"
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

                """.trimIndent()
            )

            for (dataTable in recipeDescriptor.dataTables) {
                //language=markdown
                writeln(
                    """
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

                newLine()
            }
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
                recipeDescriptor.name.contains(".python.")
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
            write("This recipe has required configuration parameters. ")
            write("Recipes with required configuration parameters cannot be activated directly. ")
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
                displayName: ${recipeDescriptor.displayName} example
                recipeList:
                  - ${recipeDescriptor.name}:
                
                """.trimIndent()
            )
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

    private fun BufferedWriter.writeDefinition(recipeDescriptor: RecipeDescriptor) {
        if (recipeDescriptor.recipeList.isNotEmpty()) {
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

                val formattedRecipeDisplayName = recipe.displayName
                    .replace("<p>", "< p >")
                    .replace("<script>", "//<script//>")

                if (recipesToIgnore.contains(recipe.name)) {
                    continue;
                }

                if (recipesThatShouldHaveLinksRemoved.contains(recipeDescriptor.name)) {
                    writeln("* $formattedRecipeDisplayName");
                } else {
                    writeln("* [" + formattedRecipeDisplayName + "](" + pathToRecipes + getRecipePath(recipe) + ")")
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
                    }.collect(Collectors.joining(", "))
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
        val versionPlaceholderKey = "{{VERSION_${origin.artifactId.uppercase().replace('-', '_')}}}"
        //language=markdown
        return """
            <TabItem value="moderne-cli" label="Moderne CLI">

            You will need to have configured the [Moderne CLI](https://docs.moderne.io/user-documentation/moderne-cli/getting-started/cli-intro) on your machine before you can run the following command.

            ```shell title="shell"
            mod run . --recipe ${trimmedRecipeName}${cliOptions}
            ```

            If the recipe is not available locally, then you can install it using:
            ```shell
            mod config recipes jar install ${origin.groupId}:${origin.artifactId}:${versionPlaceholderKey}
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
    ) {
        //language=markdown
        val gradleSnippet = if (suppressGradle) "" else """
            <TabItem value="gradle" label="Gradle">

            1. Add the following to your `build.gradle` file:
            ```groovy title="build.gradle"
            plugins {
                id("org.openrewrite.rewrite") version("{{VERSION_REWRITE_GRADLE_PLUGIN}}")
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

    private fun BufferedWriter.writeSnippetsWithConfigurationWithDependency(
        exampleRecipeName: String,
        origin: RecipeOrigin,
        suppressMaven: Boolean,
        suppressGradle: Boolean,
        cliSnippet: String,
        dataTableSnippet: String,
    ) {
        val versionPlaceholderKey = "{{VERSION_${origin.artifactId.uppercase().replace('-', '_')}}}"
        //language=markdown
        val gradleSnippet = if (suppressGradle) "" else """
            <TabItem value="gradle" label="Gradle">

            1. Add the following to your `build.gradle` file:

            ```groovy title="build.gradle"
            plugins {
                id("org.openrewrite.rewrite") version("{{VERSION_REWRITE_GRADLE_PLUGIN}}")
            }
            
            rewrite {
                activeRecipe("$exampleRecipeName")
                setExportDatatables(true)
            }
            
            repositories {
                mavenCentral()
            }
            
            dependencies {
                rewrite("${origin.groupId}:${origin.artifactId}:${versionPlaceholderKey}")
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
                        <version>${versionPlaceholderKey}</version>
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

        writeln(
            """
Now that `$exampleRecipeName` has been defined, activate it and take a dependency on `${origin.groupId}:${origin.artifactId}:${versionPlaceholderKey}` in your build file:
<Tabs groupId="projectType">
$gradleSnippet
$mavenSnippet
$cliSnippet
</Tabs>
""".trimIndent()
        )
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
                id("org.openrewrite.rewrite") version("{{VERSION_REWRITE_GRADLE_PLUGIN}}")
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
        writeln(
            """This recipe has no required configuration options. It can be activated by adding a dependency on `${origin.groupId}:${origin.artifactId}` in your build file or by running a shell command (in which case no build changes are needed): """
        )

        val versionPlaceholderKey = "{{VERSION_${origin.artifactId.uppercase().replace('-', '_')}}}"
        //language=markdown
        val gradleSnippet = if (suppressGradle) "" else """
            <TabItem value="gradle" label="Gradle">

            1. Add the following to your `build.gradle` file:

            ```groovy title="build.gradle"
            plugins {
                id("org.openrewrite.rewrite") version("{{VERSION_REWRITE_GRADLE_PLUGIN}}")
            }
            
            rewrite {
                activeRecipe("${recipeDescriptor.name}")
                setExportDatatables(true)
            }
            
            repositories {
                mavenCentral()
            }
            
            dependencies {
                rewrite("${origin.groupId}:${origin.artifactId}:${versionPlaceholderKey}")
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
                    rewrite("${origin.groupId}:${origin.artifactId}:${versionPlaceholderKey}")
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
                        <version>${versionPlaceholderKey}</version>
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

        private val recipePathToDocusaurusRenamedPath: Map<String, String> = mapOf(
            "org.openrewrite.java.testing.assertj.Assertj" to "java/testing/assertj/assertj-best-practices",
            "org.openrewrite.java.migrate.javaee7" to "java/migrate/javaee7-recipe",
            "org.openrewrite.java.migrate.javaee8" to "java/migrate/javaee8-recipe"
        )

        private fun getRecipePath(recipe: RecipeDescriptor): String =
        // Docusaurus expects that if a file is called "assertj" inside of the folder "assertj" that it's the
        // README for said folder. Due to how generic we've made this recipe name, we need to change it for the
            // docs so that they parse correctly.
            if (recipePathToDocusaurusRenamedPath.containsKey(recipe.name)) {
                recipePathToDocusaurusRenamedPath[recipe.name]!!
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
                recipe.name.startsWith("io.quarkus") ||
                recipe.name.startsWith("org.apache") ||
                recipe.name.startsWith("org.axonframework") ||
                recipe.name.startsWith("software.amazon.awssdk") ||
                recipe.name.startsWith("tech.picnic")
            ) {
                recipe.name.replace("\\.".toRegex(), "/").lowercase(Locale.getDefault())
            } else {
                throw RuntimeException("Recipe package unrecognized: ${recipe.name}")
            }

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
}
