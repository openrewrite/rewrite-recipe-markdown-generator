@file:Suppress("SENSELESS_COMPARISON")

package org.openrewrite

import com.github.difflib.DiffUtils
import com.github.difflib.patch.Patch
import org.openrewrite.RecipeMarkdownGenerator.Companion.getRecipePath
import org.openrewrite.RecipeMarkdownGenerator.Companion.useAndApply
import org.openrewrite.RecipeMarkdownGenerator.Companion.writeln
import org.openrewrite.config.RecipeDescriptor
import java.io.BufferedWriter
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.regex.Pattern

class RecipeMarkdownWriter(
    val recipeContainedBy: MutableMap<String, MutableSet<RecipeDescriptor>>,
    val recipeToSource: Map<String, URI>
) {

    /**
     * Determines if a recipe is a JavaScript/TypeScript recipe based on its source URI.
     */
    private fun isJavaScriptRecipe(recipeDescriptor: RecipeDescriptor): Boolean {
        val recipeSource = recipeToSource[recipeDescriptor.name] ?: return false
        return recipeSource.toString().startsWith("typescript-search://")
    }

    fun writeRecipe(
        recipeDescriptor: RecipeDescriptor,
        outputPath: Path,
        origin: RecipeOrigin
    ) {
        val formattedRecipeTitle = recipeDescriptor.displayNameEscaped()
        val formattedRecipeDescription = getFormattedRecipeDescription(recipeDescriptor.description)
        val formattedLongRecipeName = recipeDescriptor.name.replace("_".toRegex(), "\\\\_").trim()

        val recipeMarkdownPath = outputPath.resolve(getRecipePath(recipeDescriptor) + ".md")
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
            writeTags(recipeDescriptor)
            writeSourceLinks(recipeDescriptor, origin)
            writeOptions(recipeDescriptor)
            writeDefinition(recipeDescriptor, origin)
            writeUsedBy(recipeContainedBy[recipeDescriptor.name])
            writeExamples(recipeDescriptor)
            writeUsage(recipeDescriptor, origin)
            // Skip Moderne link for JavaScript recipes as these don't exist there yet.
            if (!isJavaScriptRecipe(recipeDescriptor)) {
                writeModerneLink(recipeDescriptor)
            }
            writeDataTables(recipeDescriptor)
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

        // Check for Markdown formatting indicators (headers, bullet lists, numbered lists)
        // These suggest the description is already formatted as Markdown and should preserve newlines
        val markdownIndicatorRegex = Pattern.compile("(^|\\n)#{1,6}\\s|\\n\\s*[-*+]\\s|\\n\\s*\\d+\\.\\s")
        if (markdownIndicatorRegex.matcher(formattedRecipeDescription).find()) {
            return formattedRecipeDescription
        }

        if (specialCharsOutsideBackticksRegex.matcher(formattedRecipeDescription).find()) {
            // If special characters exist and are not wrapped in backticks, wrap the entire string in triple backticks
            return "```\n${formattedRecipeDescription?.replace("```", "")?.trim()}\n```\n"
        }

        // Special characters may exist here - but they are already wrapped in backticks
        return "_" + formattedRecipeDescription?.replace("\n", " ")?.trim() + "_"
    }

    private fun BufferedWriter.writeTags(recipeDescriptor: RecipeDescriptor) {
        if (recipeDescriptor.tags != null && recipeDescriptor.tags.isNotEmpty()) {
            writeln("### Tags")
            newLine()
            for (tag in recipeDescriptor.tags) {
                if (tag.lowercase().startsWith("rspec-s")) {
                    writeln("* [$tag](https://sonarsource.github.io/rspec/#/rspec/${tag.substring(6)})")
                } else if (tag.lowercase().startsWith("rspec-")) {
                    writeln("* [$tag](https://sonarsource.github.io/rspec/#/rspec/S${tag.substring(6)})")
                } else {
                    val tagAnchor = tag
                        .lowercase()
                        .substringBefore('-')
                        .substringBefore('_')
                        .replace(' ', '-')
                    writeln("* [$tag](/reference/recipes-by-tag#${tagAnchor})")
                }
            }
            newLine()
        }
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
            val recipeSource = recipeToSource[recipeDescriptor.name]
            requireNotNull(recipeSource) { "Could not find source URI for recipe ${recipeDescriptor.name}" }
            val recipeSourceFileName = recipeSource.toString().substringAfterLast('/')
            //language=markdown
            writeln(
                """
            ## Recipe source

            [GitHub: $recipeSourceFileName](${origin.githubUrl(recipeDescriptor.name, recipeSource)}),
            [Issue Tracker](${origin.issueTrackerUrl()}),
            [Maven Central](https://central.sonatype.com/artifact/${origin.groupId}/${origin.artifactId}/)
            """.trimIndent()
            )

            if (recipeDescriptor.recipeList != null && recipeDescriptor.recipeList.size > 1) {
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
        if (recipeDescriptor.options != null && recipeDescriptor.options.isNotEmpty()) {
            writeln(
                """
                ## Options

                | Type | Name | Description | Example |
                | --- | --- | --- | --- |
                """.trimIndent()
            )
            for (option in recipeDescriptor.options) {
                var description = if (option.description == null) {
                    ""
                } else {
                    option.description
                        .replace("\n", "<br />")
                        .replace("|", "\\|")
                        // Ensure that anything that matches ${variable} is wrapped in ``
                        // Otherwise Docusaurus tries to parse it as a variable.
                        .replace(Regex("(?<!`)\\$\\{[^}]+}(?!`)")) { matchResult ->
                            "`${matchResult.value}`"
                        }
                }
                if (!option.isRequired) {
                    description = "*Optional*. $description"
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
        if (recipeDescriptor.dataTables != null && recipeDescriptor.dataTables.isNotEmpty()) {
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
        if (recipeDescriptor.examples != null && recipeDescriptor.examples.isNotEmpty()) {
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
                if (example.parameters != null && example.parameters.isNotEmpty() &&
                    recipeDescriptor.options != null && recipeDescriptor.options.isNotEmpty()
                ) {
                    writeln("###### Parameters")
                    writeln("| Parameter | Value |")
                    writeln("| --- | --- |")
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

    /**
     * Gets the npm package name for a JavaScript recipe module.
     * Uses the single source of truth from TypeScriptRecipeLoader.
     */
    private fun getNpmPackageName(origin: RecipeOrigin): String {
        return TypeScriptRecipeLoader.TYPESCRIPT_RECIPE_MODULES[origin.artifactId]
            ?: "@openrewrite/${origin.artifactId}"
    }

    private fun BufferedWriter.writeUsage(
        recipeDescriptor: RecipeDescriptor,
        origin: RecipeOrigin
    ) {
        // Usage
        newLine()
        writeln("## Usage")
        newLine()

        // Handle JavaScript recipes separately
        if (isJavaScriptRecipe(recipeDescriptor)) {
            writeJavaScriptUsage(recipeDescriptor, origin)
            return
        }

        val suppressJava = recipeDescriptor.name.contains(".csharp.") ||
                recipeDescriptor.name.contains(".dotnet.") ||
                recipeDescriptor.name.contains(".nodejs.") ||
                recipeDescriptor.name.contains(".python.") ||
                origin.license == Licenses.Proprietary
        val suppressMaven = suppressJava || recipeDescriptor.name.contains(".gradle.")
        val suppressGradle = suppressJava || recipeDescriptor.name.contains(".maven.")
        val requiresConfiguration = recipeDescriptor.options?.any { it.isRequired } ?: false
        val requiresDependency = !origin.isFromCoreLibrary()

        val dataTableSnippet =
            if (recipeDescriptor.dataTables == null || recipeDescriptor.dataTables.isEmpty()) "" else "<exportDatatables>true</exportDatatables>"

        val dataTableCommandLineSnippet =
            if (recipeDescriptor.dataTables == null || recipeDescriptor.dataTables.isEmpty()) "" else "-Drewrite.exportDatatables=true"

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

            for (recipe in recipeDescriptor.recipeList) {
                // https://github.com/openrewrite/rewrite-docs/issues/250
                if (recipe.displayName == "Precondition bellwether") {
                    continue
                }

                writeln("* [${recipe.displayNameEscaped()}]($pathToRecipes" + getRecipePath(recipe) + ")")

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
                .mapNotNull { recipe ->
                    try {
                        "* [${recipe.displayNameEscaped()}](/recipes/${getRecipePath(recipe)}.md)"
                    } catch (e: RuntimeException) {
                        System.err.println("Warning: Could not generate path for recipe ${recipe.name}: ${e.message}")
                        null
                    }
                }
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

    private fun BufferedWriter.writeJavaScriptUsage(
        recipeDescriptor: RecipeDescriptor,
        origin: RecipeOrigin
    ) {
        val npmPackageName = getNpmPackageName(origin)

        //language=markdown
        writeln(
            """
            In order to run JavaScript recipes, you will need to use the [Moderne CLI](https://docs.moderne.io/user-documentation/moderne-cli/getting-started/cli-intro).
            For JavaScript specific configuration instructions, please see our [configuring JavaScript guide](https://docs.moderne.io/user-documentation/moderne-cli/how-to-guides/javascript).

            Once the CLI is installed, you can install this JavaScript recipe package by running the following command:

            ```shell title="Install the recipe package"
            mod config recipes npm install $npmPackageName
            ```

            Then, you can run the recipe via:

            ```shell title="Run the recipe"
            mod run . --recipe ${recipeDescriptor.name}
            ```
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
    }
}
