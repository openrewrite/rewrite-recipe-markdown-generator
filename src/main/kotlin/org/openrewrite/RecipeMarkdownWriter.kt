@file:Suppress("SENSELESS_COMPARISON")

package org.openrewrite

import com.github.difflib.DiffUtils
import com.github.difflib.patch.Patch
import org.openrewrite.RecipeMarkdownGenerator.Companion.getRecipePath
import org.openrewrite.RecipeMarkdownGenerator.Companion.hasConflict
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

    private fun writeRecipeToPath(
        recipeDescriptor: RecipeDescriptor,
        recipeMarkdownPath: Path,
        origin: RecipeOrigin,
        crossCategoryNote: String?
    ) {
        val formattedRecipeTitle = recipeDescriptor.displayNameEscaped()  // For YAML frontmatter (no curly brace escaping)
        val formattedRecipeTitleMdx = recipeDescriptor.displayNameEscapedMdx()  // For MDX content (with curly brace escaping)
        val formattedRecipeDescription = getFormattedRecipeDescription(recipeDescriptor.description)
        val formattedLongRecipeName = recipeDescriptor.name.replace("_".toRegex(), "\\\\_").trim()
        Files.createDirectories(recipeMarkdownPath.parent)
        Files.newBufferedWriter(recipeMarkdownPath, StandardOpenOption.CREATE).useAndApply {
            // For Moderne docs, add canonical link to OpenRewrite docs for open source recipes
            val canonicalHead = if (forModerneDocs && !isProprietaryRecipe(recipeDescriptor.name)) {
                """
<head>
  <link rel="canonical" href="https://docs.openrewrite.org/recipes/${getRecipePath(recipeDescriptor)}" />
</head>

"""
            } else {
                ""
            }
            write(
                """
---
sidebar_label: "${formattedRecipeTitle.replace("&#39;", "'")}"
---

${canonicalHead}import Tabs from '@theme/Tabs';
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
                        "<pre>${optionExample.replace("<", "\\<")}</pre>".replace("\n", "<br />")
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
                val optionExample = option.example
                val isList = option.type == "List" || option.type.startsWith("List<")
                val ex = if (optionExample != null && option.type == "String" &&
                    (optionExample.matches("^[{}\\[\\],`|=%@*!?-].*".toRegex()) ||
                            optionExample.matches(".*:\\s.*".toRegex()))
                ) {
                    "'" + optionExample + "'"
                } else if (optionExample != null && option.type == "String" && optionExample.contains('\n')) {
                    ">\n        " + optionExample.replace("\n", "\n        ")
                } else if (option.type == "boolean") {
                    "false"
                } else {
                    option.example
                }
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

    companion object {
        private fun printValue(value: Any): String =
            if (value is Array<*>) {
                value.contentDeepToString()
            } else {
                value.toString()
            }
    }
}
