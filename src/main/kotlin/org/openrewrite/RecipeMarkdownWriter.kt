@file:Suppress("SENSELESS_COMPARISON")

package org.openrewrite

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.difflib.DiffUtils
import com.github.difflib.patch.Patch
import org.openrewrite.RecipeMarkdownGenerator.Companion.getRecipePath
import org.openrewrite.RecipeMarkdownGenerator.Companion.hasConflict
import org.openrewrite.RecipeMarkdownGenerator.Companion.useAndApply
import org.openrewrite.RecipeMarkdownGenerator.Companion.writeln
import org.openrewrite.config.OptionDescriptor
import org.openrewrite.config.RecipeDescriptor
import java.io.BufferedWriter
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.regex.Pattern

class RecipeMarkdownWriter(
    val recipeContainedBy: MutableMap<String, MutableSet<RecipeDescriptor>>,
    val recipeToSource: Map<String, URI>,
    val proprietaryRecipeNames: Set<String>,
    val forModerneDocs: Boolean = false
) {

    /**
     * Check if a recipe is proprietary based on its name.
     */
    private fun isProprietaryRecipe(recipeName: String): Boolean {
        return proprietaryRecipeNames.contains(recipeName)
    }

    /**
     * Get the appropriate link for a recipe.
     * For Moderne docs: all links are internal (relative paths to recipe-catalog)
     * For OpenRewrite docs: proprietary recipes link to Moderne docs, others are local
     */
    private fun getRecipeLink(recipe: RecipeDescriptor, pathToRecipes: String = ""): String {
        return if (forModerneDocs) {
            // Moderne docs: all recipes are local
            "$pathToRecipes${getRecipePath(recipe)}"
        } else if (isProprietaryRecipe(recipe.name)) {
            // OpenRewrite docs: proprietary recipes link to Moderne
            "https://docs.moderne.io/user-documentation/recipes/recipe-catalog/${getRecipePath(recipe)}"
        } else {
            "$pathToRecipes${getRecipePath(recipe)}"
        }
    }

    /**
     * Determines if a recipe is a JavaScript/TypeScript recipe based on its source URI.
     */
    private fun isJavaScriptRecipe(recipeDescriptor: RecipeDescriptor): Boolean {
        val recipeSource = recipeToSource[recipeDescriptor.name] ?: return false
        return recipeSource.toString().startsWith("typescript-search://")
    }

    /**
     * Determines if a recipe is a Python recipe based on its source URI.
     */
    private fun isPythonRecipe(recipeDescriptor: RecipeDescriptor): Boolean {
        val recipeSource = recipeToSource[recipeDescriptor.name] ?: return false
        return recipeSource.toString().startsWith("python-search://")
    }

    /**
     * Determines if a recipe is a C# recipe based on its source URI.
     */
    private fun isCSharpRecipe(recipeDescriptor: RecipeDescriptor): Boolean {
        val recipeSource = recipeToSource[recipeDescriptor.name] ?: return false
        return recipeSource.toString().startsWith("csharp-search://")
    }

    /**
     * Write a recipe to a custom path (for cross-category duplicates).
     * The target language is derived from the first segment of the custom path (e.g., "python" from "python/changemethodname").
     */
    fun writeRecipeTo(
        recipeDescriptor: RecipeDescriptor,
        outputPath: Path,
        origin: RecipeOrigin,
        customRelativePath: String
    ) {
        val targetLanguage = customRelativePath.substringBefore('/').replaceFirstChar { it.uppercase() }
        val sourceLanguage = getSourceLanguage(recipeDescriptor.name)
        val crossCategoryNote = ":::info\nThis $sourceLanguage recipe works on $targetLanguage code.\n:::"
        val recipeMarkdownPath = outputPath.resolve("$customRelativePath.md")
        writeRecipeToPath(recipeDescriptor, recipeMarkdownPath, origin, crossCategoryNote)
    }

    /**
     * Derive the source language from a recipe's fully qualified name.
     */
    private fun getSourceLanguage(recipeName: String): String {
        return when {
            recipeName.startsWith("org.openrewrite.java.") -> "Java"
            recipeName.startsWith("org.openrewrite.kotlin.") -> "Kotlin"
            recipeName.startsWith("org.openrewrite.python.") -> "Python"
            recipeName.startsWith("org.openrewrite.javascript.") -> "JavaScript"
            recipeName.startsWith("org.openrewrite.typescript.") -> "TypeScript"
            recipeName.startsWith("org.openrewrite.xml.") -> "XML"
            recipeName.startsWith("org.openrewrite.json.") -> "JSON"
            recipeName.startsWith("org.openrewrite.yaml.") -> "YAML"
            recipeName.startsWith("org.openrewrite.groovy.") -> "Groovy"
            recipeName.startsWith("org.openrewrite.csharp.") -> "C#"
            else -> "OpenRewrite"
        }
    }

    fun writeRecipe(
        recipeDescriptor: RecipeDescriptor,
        outputPath: Path,
        origin: RecipeOrigin
    ) {
        val recipeMarkdownPath = outputPath.resolve(getRecipePath(recipeDescriptor) + ".md")
        writeRecipeToPath(recipeDescriptor, recipeMarkdownPath, origin, null)
    }

    /**
     * Open a recipe markdown file for (over)writing. TRUNCATE_EXISTING is essential: the same path can be
     * written more than once in a run (a recipe's native page and a cross-category duplicate of a different
     * recipe can resolve to it). Without truncation a shorter second write leaves a stale tail from the
     * first, which silently corrupts plain markdown and produces unparseable MDX in the component output.
     */
    private fun newRecipeWriter(recipeMarkdownPath: Path): BufferedWriter {
        Files.createDirectories(recipeMarkdownPath.parent)
        return Files.newBufferedWriter(recipeMarkdownPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
    }

    private fun writeRecipeToPath(
        recipeDescriptor: RecipeDescriptor,
        recipeMarkdownPath: Path,
        origin: RecipeOrigin,
        crossCategoryNote: String?
    ) {
        if (forModerneDocs) { writeModerneComponentRecipe(recipeDescriptor, recipeMarkdownPath, origin, crossCategoryNote); return }

        val formattedRecipeTitle = recipeDescriptor.displayNameEscaped()  // For YAML frontmatter (no curly brace escaping)
        val formattedRecipeTitleMdx = recipeDescriptor.displayNameEscapedMdx()  // For MDX content (with curly brace escaping)
        val formattedRecipeDescription = getFormattedRecipeDescription(recipeDescriptor.description)
        val formattedLongRecipeName = recipeDescriptor.name.replace("_".toRegex(), "\\\\_").trim()
        newRecipeWriter(recipeMarkdownPath).useAndApply {
            // Note: the Moderne-docs canonical-link <head> is emitted by writeModerneComponentRecipe;
            // this path only runs for OpenRewrite docs (forModerneDocs is always false here).
            write(
                """
---
title: "${formattedRecipeTitle.replace("&#39;", "'")}"
sidebar_label: "${formattedRecipeTitle.replace("&#39;", "'")}"
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';
import RunRecipe from '@site/src/components/RunRecipe';

# $formattedRecipeTitleMdx

**$formattedLongRecipeName**

""".trimIndent()
            )

            newLine()
            writeln(formattedRecipeDescription)
            newLine()
            if (crossCategoryNote != null) {
                writeln(crossCategoryNote)
                newLine()
            }
            writeTags(recipeDescriptor)
            writeSourceLinks(recipeDescriptor, origin)
            writeOptions(recipeDescriptor)
            writeDefinition(recipeDescriptor, origin)
            writeUsedBy(recipeContainedBy[recipeDescriptor.name])
            writeExamples(recipeDescriptor)
            writeUsage(recipeDescriptor, origin)
            // Skip Moderne link for JavaScript and Python recipes as these don't exist there yet.
            if (!isJavaScriptRecipe(recipeDescriptor) && !isPythonRecipe(recipeDescriptor) && !isCSharpRecipe(recipeDescriptor)) {
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
                    writeln("* [$tag](https://next.sonarqube.com/sonarqube/coding_rules?languages=java&q=${tag.substring(6)}&open=java%3A${tag.substring(6)})")
                } else if (tag.lowercase().startsWith("rspec-")) {
                    writeln("* [$tag](https://next.sonarqube.com/sonarqube/coding_rules?languages=java&q=S${tag.substring(6)}&open=java%3AS${tag.substring(6)})")
                } else {
                    val tagAnchor = tag
                        .lowercase()
                        .substringBefore('-')
                        .substringBefore('_')
                        .replace(' ', '-')
                        .replace(".", "")
                    val tagBasePath = if (forModerneDocs) "/user-documentation/recipes/lists/recipes-by-tag" else "/reference/recipes-by-tag"
                    writeln("* [$tag](${tagBasePath}#${tagAnchor})")
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
            val githubUrl = origin.githubUrl(recipeDescriptor.name, recipeSource)
            //language=markdown
            writeln(
                """
            ## Recipe source

            [GitHub: ${githubUrl.substringAfterLast('/')}]($githubUrl),
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
                val optionExample = option.example
                val example = if (optionExample != null) {
                    if (optionExample.contains("\n")) {
                        "<pre>${optionExample.replace("<", "\\<").replace("{", "\\{").replace("}", "\\}")}</pre>".replace("\n", "<br />")
                    } else {
                        "`${optionExample}`"
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

                    _${escapeMdx(dataTable.description)}_

                    | Column Name | Description |
                    | ----------- | ----------- |
                    """.trimIndent()
                )

                for (column in dataTable.columns) {
                    //language=markdown
                    writeln(
                        """
                        | ${column.displayName} | ${escapeMdx(column.description ?: "")} |
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
                    val after = source.after
                    val hasChange = after != null && after.isNotEmpty()
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

                        write(after)
                        if (after != null && !after.endsWith("\n")) {
                            newLine()
                        }
                        writeln("```")

                        newLine()

                        // diff
                        if (source.before != null) {
                            writeln("</TabItem>")
                            writeln("<TabItem value=\"diff\" label=\"Diff\" >\n")

                            val diff = generateDiff(source.path, source.before, after)

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

    /**
     * Gets the pip package name for a Python recipe module.
     * Uses the single source of truth from PythonRecipeLoader.
     */
    private fun getPipPackageName(origin: RecipeOrigin): String {
        return PythonRecipeLoader.PYTHON_RECIPE_MODULES[origin.artifactId]
            ?: origin.artifactId
    }

    /**
     * Gets the NuGet package name for a C# recipe module.
     * Uses the single source of truth from CSharpRecipeLoader.
     */
    private fun getNuGetPackageName(origin: RecipeOrigin): String {
        return CSharpRecipeLoader.CSHARP_RECIPE_MODULES[origin.artifactId]
            ?: origin.artifactId
    }

    private fun BufferedWriter.writeUsage(
        recipeDescriptor: RecipeDescriptor,
        origin: RecipeOrigin
    ) {
        // Usage
        newLine()
        writeln("## Usage")
        newLine()

        // Handle JavaScript recipes
        if (isJavaScriptRecipe(recipeDescriptor)) {
            val npmPackageName = getNpmPackageName(origin)
            writeln(
                """
                <RunRecipe
                  recipeName="${recipeDescriptor.name}"
                  displayName="${recipeDescriptor.displayNameEscapedMdx()}"
                  npmPackage="$npmPackageName"
                />
                """.trimIndent()
            )
            return
        }

        // Handle Python recipes
        if (isPythonRecipe(recipeDescriptor)) {
            val pipPackageName = getPipPackageName(origin)
            writeln(
                """
                <RunRecipe
                  recipeName="${recipeDescriptor.name}"
                  displayName="${recipeDescriptor.displayNameEscapedMdx()}"
                  pipPackage="$pipPackageName"
                />
                """.trimIndent()
            )
            return
        }

        // Handle C# recipes
        if (isCSharpRecipe(recipeDescriptor)) {
            val nugetPackageName = getNuGetPackageName(origin)
            writeln(
                """
                <RunRecipe
                  recipeName="${recipeDescriptor.name}"
                  displayName="${recipeDescriptor.displayNameEscapedMdx()}"
                  nugetPackage="$nugetPackageName"
                />
                """.trimIndent()
            )
            return
        }

        val suppressJava = recipeDescriptor.name.contains(".csharp.") ||
                recipeDescriptor.name.contains(".dotnet.") ||
                recipeDescriptor.name.contains(".nodejs.") ||
                recipeDescriptor.name.contains(".python.") ||
                origin.license == Licenses.Proprietary ||
                forModerneDocs
        val suppressMaven = suppressJava || recipeDescriptor.name.contains(".gradle.")
        val suppressGradle = suppressJava || recipeDescriptor.name.contains(".maven.")
        val requiresConfiguration = recipeDescriptor.options.any { it.isRequired }
        val requiresDependency = !origin.isFromCoreLibrary()
        val hasDataTables = recipeDescriptor.dataTables != null && recipeDescriptor.dataTables.isNotEmpty()

        var cliOptions = ""

        if (requiresConfiguration) {
            val exampleRecipeName =
                "com.yourorg." + recipeDescriptor.name.substring(recipeDescriptor.name.lastIndexOf('.') + 1) + "Example"

            if (origin.license == Licenses.Proprietary || forModerneDocs) {
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

            for (option in recipeDescriptor.options) {
                if (!option.isRequired && option.example == null) {
                    continue
                }
                val isList = option.type == "List" || option.type.startsWith("List<")
                val ex = cliOptionExample(option)
                cliOptions += " --recipe-option \"${option.name}=$ex\""
                if (isList) {
                    writeln("      ${option.name}:")
                    writeln("        - ${ex ?: "TODO"}")
                } else {
                    writeln("      ${option.name}: $ex")
                }
            }
            writeln("```")
            newLine()
        }

        // Build the <RunRecipe> props
        val props = StringBuilder()
        props.appendLine("  recipeName=\"${recipeDescriptor.name}\"")
        props.appendLine("  displayName=\"${recipeDescriptor.displayNameEscapedMdx()}\"")

        props.appendLine("  groupId=\"${origin.groupId}\"")
        props.appendLine("  artifactId=\"${origin.artifactId}\"")
        props.appendLine("  versionKey=\"${origin.versionPlaceholderKey()}\"")
        if (!requiresDependency) {
            props.appendLine("  isCoreLibrary")
        }

        if (requiresConfiguration) {
            props.appendLine("  requiresConfiguration")
        }

        if (cliOptions.isNotEmpty()) {
            // Use single quotes for JSX to avoid issues with double quotes in option values.
            // Escape backslashes, single quotes, and newlines so the JS string literal is valid.
            val escaped = cliOptions.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
            props.appendLine("  cliOptions={'$escaped'}")
        }

        if (suppressGradle) {
            props.appendLine("  showGradle={false}")
        }

        if (suppressMaven) {
            props.appendLine("  showMaven={false}")
        }

        if (hasDataTables) {
            props.appendLine("  hasDataTables")
        }

        if (hasConflict(recipeDescriptor.name)) {
            props.appendLine("  useFullyQualifiedCliName")
        }

        writeln(
            """
<RunRecipe
${props.toString().trimEnd()}
/>""".trimIndent()
        )
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

            if (recipeDescriptor.preconditions.isNotEmpty()) {
                writeln("**Preconditions**")
                newLine()
                for (precondition in recipeDescriptor.preconditions) {
                    if (recipeToSource.containsKey(precondition.name)) {
                        val recipeLink = getRecipeLink(precondition, pathToRecipes)
                        writeln("* [${precondition.displayNameEscapedMdx()}]($recipeLink)")
                    } else {
                        writeln("* ${precondition.displayNameEscapedMdx()}")
                    }

                    if (precondition.options.isNotEmpty()) {
                        for (option in precondition.options) {
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
                writeln("**Recipes**")
                newLine()
            }

            for (recipe in recipeDescriptor.recipeList) {
                // https://github.com/openrewrite/rewrite-docs/issues/250
                if (recipe.displayName == "Precondition bellwether") {
                    continue
                }

                if (recipeToSource.containsKey(recipe.name)) {
                    val recipeLink = getRecipeLink(recipe, pathToRecipes)
                    writeln("* [${recipe.displayNameEscapedMdx()}]($recipeLink)")
                } else {
                    writeln("* ${recipe.displayNameEscapedMdx()}")
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
                .mapNotNull { recipe ->
                    try {
                        val link = if (isProprietaryRecipe(recipe.name)) {
                            "https://docs.moderne.io/user-documentation/recipes/recipe-catalog/${getRecipePath(recipe)}"
                        } else if (forModerneDocs) {
                            "/user-documentation/recipes/recipe-catalog/${getRecipePath(recipe)}.md"
                        } else {
                            "/recipes/${getRecipePath(recipe)}.md"
                        }
                        "* [${recipe.displayNameEscapedMdx()}]($link)"
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

    // -------------------------------------------------------------------------
    // Moderne docs MDX component output
    // -------------------------------------------------------------------------

    /**
     * Emits an MDX file that delegates all rendering to React components.
     * Only called when forModerneDocs == true.
     */
    private fun writeModerneComponentRecipe(
        recipeDescriptor: RecipeDescriptor,
        recipeMarkdownPath: Path,
        origin: RecipeOrigin,
        @Suppress("UNUSED_PARAMETER") crossCategoryNote: String?
    ) {
        val name = recipeDescriptor.name
        val title = recipeDescriptor.displayNameEscaped().replace("&#39;", "'")
        val isProprietary = isProprietaryRecipe(name)
        val recipePath = getRecipePath(recipeDescriptor)

        // Canonical <head> block for open-source recipes
        val canonicalHead = if (!isProprietary) {
            """
<head>
  <link rel="canonical" href="https://docs.openrewrite.org/recipes/$recipePath" />
</head>

"""
        } else {
            ""
        }

        val recipeType = if (recipeDescriptor.recipeList.isNullOrEmpty()) "Single recipe" else "Composite recipe"
        val languages = listOf(getSourceLanguage(name))
        // License as plain text (not the markdown-link form the OpenRewrite docs path uses).
        val licenseText = origin.license.name
        val artifact = "${origin.groupId}:${origin.artifactId}"
        val appLink = "https://app.moderne.io/recipes/$name"
        val markdownUrl = MODERNE_DOCS_MARKDOWN_BASE_URL + recipePath + ".md"

        // Source URL — omit for proprietary or when no source URI available
        val sourceUrl: String? = if (!isProprietary) {
            val recipeSource = recipeToSource[name]
            if (recipeSource != null) origin.githubUrl(name, recipeSource) else null
        } else null

        val description = recipeDescriptor.description ?: ""
        val tags = recipeDescriptor.tags?.toList() ?: emptyList<String>()

        newRecipeWriter(recipeMarkdownPath).useAndApply {
            //language=markdown
            writeln("---")
            writeln("title: \"$title\"")
            writeln("sidebar_label: \"$title\"")
            writeln("hide_title: true")
            writeln("---")
            newLine()

            write(canonicalHead)   // "" for proprietary recipes — a no-op

            writeln("import { RecipeHeader, RecipeMeta, RecipeList, OptionsTable, ExampleList, UsageList, DataTableList } from '@site/src/components/recipe';")
            newLine()

            // RecipeMeta — always emitted
            writeln("<RecipeMeta")
            writeln("  displayName={${mapper.writeValueAsString(recipeDescriptor.displayName)}}")
            writeln("  description={${mapper.writeValueAsString(description)}}")
            writeln("  fqName={${mapper.writeValueAsString(name)}}")
            writeln("  languages={${mapper.writeValueAsString(languages)}}")
            writeln("  license={${mapper.writeValueAsString(licenseText)}}")
            if (sourceUrl != null) {
                writeln("  sourceUrl={${mapper.writeValueAsString(sourceUrl)}}")
            }
            writeln("/>")
            newLine()

            // RecipeHeader — always emitted
            writeln("<RecipeHeader")
            writeln("  displayName={${mapper.writeValueAsString(recipeDescriptor.displayName)}}")
            writeln("  description={${mapper.writeValueAsString(description)}}")
            writeln("  type={${mapper.writeValueAsString(recipeType)}}")
            writeln("  languages={${mapper.writeValueAsString(languages)}}")
            writeln("  tags={${mapper.writeValueAsString(tags)}}")
            writeln("  license={${mapper.writeValueAsString(licenseText)}}")
            writeln("  fqName={${mapper.writeValueAsString(name)}}")
            writeln("  artifact={${mapper.writeValueAsString(artifact)}}")
            writeln("  appLink={${mapper.writeValueAsString(appLink)}}")
            writeln("  markdownUrl={${mapper.writeValueAsString(markdownUrl)}}")
            if (isProprietary) {
                writeln("  moderneOnly")
            }
            writeln("/>")
            newLine()

            // Sections: the `## ` heading is the component's children, blank-line-wrapped (below) so MDX
            // parses it as a real heading node and the native Docusaurus TOC picks it up. JSON props are
            // built lazily, only for sections that are actually emitted.
            // Definition = preconditions + sub-recipe list. Emit when either is present (a single recipe
            // can have preconditions without a recipe list). Unlike the OpenRewrite markdown path this is
            // not suppressed for proprietary recipes — Moderne docs shows their definitions too.
            val hasPreconditions = !recipeDescriptor.preconditions.isNullOrEmpty()
            if (!recipeDescriptor.recipeList.isNullOrEmpty() || hasPreconditions) {
                val recipesJson = subRecipeJson(recipeDescriptor.recipeList)
                val preconditionsAttr = recipeDescriptor.preconditions
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { " preconditions={${subRecipeJson(it)}}" }
                    ?: ""
                emitSection("<RecipeList recipes={$recipesJson}$preconditionsAttr>", "## Definition", "</RecipeList>")
            }
            if (!recipeDescriptor.options.isNullOrEmpty()) {
                emitSection("<OptionsTable options={${buildOptionsJson(recipeDescriptor)}}>", "## Options", "</OptionsTable>")
            }
            if (!recipeDescriptor.examples.isNullOrEmpty()) {
                emitSection("<ExampleList examples={${buildExamplesJson(recipeDescriptor)}}>", "## Examples", "</ExampleList>")
            }
            // Usage: every recipe gets one. buildUsageJson sets the right install coordinates —
            // npm/pip/nuget for JS/Python/C#, Maven/Gradle coordinates otherwise.
            emitSection("<UsageList usage={${buildUsageJson(recipeDescriptor, origin)}}>", "## Usage", "</UsageList>")
            if (!recipeDescriptor.dataTables.isNullOrEmpty()) {
                emitSection("<DataTableList tables={${buildDataTablesJson(recipeDescriptor)}}>", "## Data tables", "</DataTableList>")
            }
        }
    }

    /** Emit a recipe-section component with its `## ` heading as a blank-line-wrapped children slot. */
    private fun BufferedWriter.emitSection(openTag: String, heading: String, closeTag: String) {
        writeln(openTag)
        newLine()
        writeln(heading)
        newLine()
        writeln(closeTag)
        newLine()
    }

    /**
     * Build the JSON array of `{ name, href }` for RecipeList's `recipes` and `preconditions` props.
     * Unlinkable recipes (not in recipeToSource) get an empty href.
     */
    private fun subRecipeJson(recipes: List<RecipeDescriptor>?): String {
        val items = (recipes ?: emptyList<RecipeDescriptor>())
            // Skip internal "Precondition bellwether" recipes (rewrite-docs#250), like the markdown path.
            .filter { it.displayName != "Precondition bellwether" }
            .map { sub ->
                val href = recipeToSource[sub.name]?.let { getRecipeLink(sub) } ?: ""
                mapOf("name" to sub.displayName, "href" to href)
            }
        return mapper.writeValueAsString(items)
    }

    /**
     * Build the JSON array for OptionsTable.
     * Shape: { type: string; name: string; required: boolean; description: string; example?: string }[]
     */
    private fun buildOptionsJson(recipeDescriptor: RecipeDescriptor): String {
        val items = (recipeDescriptor.options ?: emptyList()).map { option ->
            val entry = mutableMapOf<String, Any?>(
                "type" to (option.type ?: "String"),
                "name" to (option.name ?: "unknown"),
                "required" to option.isRequired,
                "description" to (option.description ?: "")
            )
            if (option.example != null) {
                entry["example"] = option.example
            }
            entry
        }
        return mapper.writeValueAsString(items)
    }

    /**
     * Build the JSON array for ExampleList: one object per example with `parameters`, an optional
     * `unchanged` source, and `variants` (before/after/diff per source). The example's prose
     * `description` is intentionally not emitted — the redesigned ExampleList renders examples by
     * source language and has no per-example description field.
     */
    private fun buildExamplesJson(recipeDescriptor: RecipeDescriptor): String {
        val examples = recipeDescriptor.examples ?: emptyList()
        val options = recipeDescriptor.options ?: emptyList()

        val items = examples.map { example ->
            val exMap = mutableMapOf<String, Any?>()

            // parameters — zip option names with example parameter values
            if (example.parameters != null && example.parameters.isNotEmpty() && options.isNotEmpty()) {
                val params = options.zip(example.parameters).map { (opt, value) ->
                    mapOf("parameter" to (opt.name ?: "unknown"), "value" to value)
                }
                exMap["parameters"] = params
            }

            val variants = mutableListOf<Map<String, Any?>>()
            var unchangedSet = false

            for (source in example.sources) {
                val after = source.after
                val hasChange = after != null && after.isNotEmpty()
                val lang = source.language ?: "text"

                when {
                    !hasChange -> {
                        // no change — set unchanged (first such source only)
                        if (!unchangedSet) {
                            exMap["unchanged"] = mapOf(
                                "language" to lang,
                                "code" to (source.before ?: "")
                            )
                            unchangedSet = true
                        }
                    }
                    source.before == null -> {
                        // new file
                        variants.add(
                            mapOf(
                                "language" to lang,
                                "before" to "",
                                "after" to after!!,
                                "newFile" to true
                            )
                        )
                    }
                    else -> {
                        // before → after with diff
                        val diff = generateDiff(source.path, source.before, after!!)
                        variants.add(
                            mapOf(
                                "language" to lang,
                                "before" to source.before,
                                "after" to after,
                                "diff" to diff,
                                "newFile" to false
                            )
                        )
                    }
                }
            }

            exMap["variants"] = variants
            exMap
        }

        return mapper.writeValueAsString(items)
    }

    /**
     * Build the JSON object for UsageList.
     * Mirrors writeUsage's logic for determining which props to include.
     */
    private fun buildUsageJson(recipeDescriptor: RecipeDescriptor, origin: RecipeOrigin): String {
        val name = recipeDescriptor.name
        val usageMap = mutableMapOf<String, Any?>(
            "recipeName" to name,
            "displayName" to recipeDescriptor.displayName,
        )

        // JS/Python/C# recipes install from their own package managers (matching the markdown path's
        // per-ecosystem <RunRecipe>); everything else uses the Maven/Gradle coordinates + CLI options.
        when {
            isJavaScriptRecipe(recipeDescriptor) -> usageMap["npmPackage"] = getNpmPackageName(origin)
            isPythonRecipe(recipeDescriptor) -> usageMap["pipPackage"] = getPipPackageName(origin)
            isCSharpRecipe(recipeDescriptor) -> usageMap["nugetPackage"] = getNuGetPackageName(origin)
            else -> {
                val options = recipeDescriptor.options ?: emptyList()
                val requiresConfiguration = options.any { it.isRequired }
                usageMap["groupId"] = origin.groupId
                usageMap["artifactId"] = origin.artifactId
                usageMap["versionKey"] = origin.versionPlaceholderKey()
                usageMap["requiresConfiguration"] = requiresConfiguration

                // cliOptions shares the per-option example formatting with writeUsage.
                val cliOptions = if (requiresConfiguration) {
                    options.filter { it.isRequired || it.example != null }
                        .joinToString("") { " --recipe-option \"${it.name}=${cliOptionExample(it)}\"" }
                } else {
                    ""
                }
                if (cliOptions.isNotEmpty()) {
                    usageMap["cliOptions"] = cliOptions
                }
                if (hasConflict(name)) {
                    usageMap["useFullyQualifiedCliName"] = true
                }
            }
        }

        return mapper.writeValueAsString(usageMap)
    }

    /**
     * Build the JSON array for DataTableList.
     * Shape: { name: string; displayName: string; description: string; columns: { name: string; description: string }[] }[]
     */
    private fun buildDataTablesJson(recipeDescriptor: RecipeDescriptor): String {
        val dataTables = recipeDescriptor.dataTables ?: emptyList()
        val items = dataTables.map { dt ->
            mapOf(
                "name" to dt.name,
                "displayName" to dt.displayName,
                "description" to (dt.description ?: ""),
                "columns" to (dt.columns ?: emptyList()).map { col ->
                    mapOf(
                        "name" to col.displayName,
                        "description" to (col.description ?: "")
                    )
                }
            )
        }
        return mapper.writeValueAsString(items)
    }

    /**
     * The example value for an option as it appears in the Moderne CLI run command / rewrite.yml.
     * Shared by [writeUsage] (markdown) and [buildUsageJson] (component prop) so the two stay in sync.
     */
    private fun cliOptionExample(option: OptionDescriptor): String? {
        val optionExample = option.example
        return if (optionExample != null && option.type == "String" &&
            (optionExample.matches(YAML_SPECIAL_START_REGEX) || optionExample.matches(YAML_COLON_SPACE_REGEX))
        ) {
            "'" + optionExample + "'"
        } else if (optionExample != null && option.type == "String" && optionExample.contains('\n')) {
            ">\n        " + optionExample.replace("\n", "\n        ")
        } else if (option.type == "boolean") {
            "false"
        } else {
            option.example
        }
    }

    companion object {
        // Stateless + thread-safe; one instance for the whole run rather than per writer.
        private val mapper = jacksonObjectMapper()

        private const val MODERNE_DOCS_MARKDOWN_BASE_URL =
            "https://raw.githubusercontent.com/moderneinc/moderne-docs/refs/heads/main/docs/user-documentation/recipes/recipe-catalog/"

        // CLI/YAML option-example formatting (compiled once instead of per option per recipe).
        private val YAML_SPECIAL_START_REGEX = Regex("^[{}\\[\\],`|=%@*!?-].*")
        private val YAML_COLON_SPACE_REGEX = Regex(".*:\\s.*")

        private fun printValue(value: Any): String =
            if (value is Array<*>) {
                value.contentDeepToString()
            } else {
                value.toString()
            }
    }
}
