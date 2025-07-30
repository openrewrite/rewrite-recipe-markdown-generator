package org.openrewrite.writers

import com.github.difflib.DiffUtils
import org.openrewrite.*
import org.openrewrite.config.DataTableDescriptor
import org.openrewrite.config.OptionDescriptor
import org.openrewrite.config.RecipeDescriptor
import java.io.BufferedWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.stream.Collectors

/**
 * Writes markdown documentation for individual recipes
 */
class RecipeMarkdownWriter(
    private val recipeDescriptor: RecipeDescriptor,
    private val origin: RecipeOrigin,
    private val recipeContainedBy: Set<RecipeDescriptor>?,
    private val gradlePluginVersion: String,
    private val mavenPluginVersion: String
) : MarkdownWriter {

    companion object {
        // These are common in every recipe - so let's not use them when generating the list of recipes with data tables.
        private val dataTablesToIgnore = listOf(
            "org.openrewrite.table.SourcesFileResults",
            "org.openrewrite.table.SourcesFileErrors",
            "org.openrewrite.table.RecipeRunStats"
        )
    }

    override fun write(outputPath: Path) {
        val recipePath = getRecipePath(outputPath, recipeDescriptor)
        Files.createDirectories(recipePath.parent)
        
        Files.newBufferedWriter(recipePath, StandardOpenOption.CREATE).useAndApply {
            writeHeader()
            writeln()
            writeDescription()
            writeln()
            writeSourceLinks()
            writeLicense()
            writeOptions()
            writeDataTables()
            writeExamples()
            writeUsage()
            writeDefinition()
            writeUsedBy()
            writeModerneLink()
            writeContributors()
        }
    }

    private fun BufferedWriter.writeHeader() {
        writeln("# ${recipeDescriptor.displayNameEscaped()}")
        writeln()
        writeln("**${recipeDescriptor.name}**")
    }

    private fun BufferedWriter.writeDescription() {
        val description = getFormattedRecipeDescription(recipeDescriptor.description)
        if (description != null) {
            writeln("_$description._")
        }
    }

    private fun BufferedWriter.writeSourceLinks() {
        if (origin.isFromCoreLibrary()) {
            val sourceUri = origin.githubUrl(recipeDescriptor.name, recipeDescriptor.source)
            writeln("## Recipe source")
            writeln()
            writeln("[GitHub]($sourceUri), [Issue Tracker](https://github.com/openrewrite/rewrite/issues), [Maven Central](https://central.sonatype.com/artifact/${origin.groupId}/${origin.artifactId}/${origin.version}/jar)")
            writeln()
        } else {
            writeln("## Recipe source")
            writeln()
            writeln("[GitHub](${recipeDescriptor.source}), Issue Tracker, Maven Central")
            writeln()
        }
    }

    private fun BufferedWriter.writeLicense() {
        writeln("* groupId: `${origin.groupId}`")
        writeln("* artifactId: `${origin.artifactId}`")
        writeln("* version: `${origin.version}`")
        writeln()
        origin.license?.let {
            if (it.name != "Unknown") {
                writeln("{% hint style=\"info\" %}")
                writeln("This recipe is available in the [${it.name}](${it.uri}) licensed module `${origin.groupId}:${origin.artifactId}` which you can optionally include in your build:")
                writeln()
                writeln("{% code %}")
                writeln("```groovy")
                writeln("dependencies {")
                writeln("    rewrite(\"${origin.groupId}:${origin.artifactId}:${origin.version}\")")
                writeln("}")
                writeln("```")
                writeln("{% endcode %}")
                writeln("{% endhint %}")
                writeln()
            }
        }
    }

    private fun BufferedWriter.writeOptions() {
        if (recipeDescriptor.options.isNotEmpty()) {
            writeln("## Options")
            writeln()
            writeln("| Type | Name | Description | Example |")
            writeln("| -- | -- | -- | -- |")
            for (option in recipeDescriptor.options) {
                val type = option.type.replace("|", "\\|")
                val exampleValue = option.example?.let { " `${printValue(it)}` " } ?: ""
                writeln("| `$type` | ${option.name} | ${option.description} |$exampleValue|")
            }
            writeln()
        }
    }

    private fun BufferedWriter.writeDataTables() {
        val filteredDataTables = recipeDescriptor.dataTables.filter { dataTable ->
            dataTable.name !in dataTablesToIgnore
        }
        
        if (filteredDataTables.isNotEmpty()) {
            writeln("## Data Tables")
            writeln()
            writeln("### Source files that had results")
            writeln("**org.openrewrite.table.SourcesFileResults**")
            writeln()
            writeln("_Source files that were modified by the recipe run._")
            writeln()
            writeln("| Column Name | Description |")
            writeln("| ----------- | ----------- |")
            writeln("| Source path before the run | The source path of the file before the run. `null` when a source file was created during the run. |")
            writeln("| Source path after the run | A recipe may modify the source path. This is the path after the run. `null` when a source file was deleted during the run. |")
            writeln("| Parent of the recipe that made changes | In a hierarchical recipe, the parent of the recipe that made a change. Empty if this is the root of a hierarchy or if the recipe is not hierarchical at all. |")
            writeln("| Recipe that made changes | The specific recipe that made a change. |")
            writeln("| Estimated time saving | An estimated effort that a developer to fix manually instead of using this recipe, in unit of seconds. |")
            writeln("| Cycle | The recipe cycle in which the change was made. |")
            writeln()
            writeln("### Source files that errored on a recipe")
            writeln("**org.openrewrite.table.SourcesFileErrors**")
            writeln()
            writeln("_The details of all errors produced by a recipe run._")
            writeln()
            writeln("| Column Name | Description |")
            writeln("| ----------- | ----------- |")
            writeln("| Source path | The file that failed to parse. |")
            writeln("| Recipe that made changes | The specific recipe that made a change. |")
            writeln("| Stack trace | The stack trace of the failure. |")
            writeln()
            
            for (dataTable in filteredDataTables) {
                writeln("### ${dataTable.displayName}")
                writeln("**${dataTable.name}**")
                writeln()
                if (dataTable.description != null) {
                    writeln("_${dataTable.description}_")
                    writeln()
                }
                writeln("| Column Name | Description |")
                writeln("| ----------- | ----------- |")
                for (column in dataTable.columns) {
                    writeln("| ${column.displayName} | ${column.description} |")
                }
                writeln()
            }
        }
    }

    private fun BufferedWriter.writeExamples() {
        if (recipeDescriptor.examples.isNotEmpty()) {
            writeln("## Examples")
            for (example in recipeDescriptor.examples) {
                writeln("##### Example ${recipeDescriptor.examples.indexOf(example) + 1}")
                writeln()
                
                if (example.parameters.isNotEmpty() && recipeDescriptor.options.isNotEmpty()) {
                    writeln("###### Parameters")
                    writeln("| Parameter | Value |")
                    writeln("| -- | -- |")
                    for ((index, option) in recipeDescriptor.options.withIndex()) {
                        if (index < example.parameters.size) {
                            writeln("|${option.name}|`${example.parameters[index]}`|")
                        }
                    }
                }
                
                writeln()
                for (source in example.sources) {
                    val path = source.path ?: ""
                    writeln("###### Before")
                    if (path.isNotEmpty()) {
                        writeln("{% code title=\"$path\" %}")
                    }
                    source.before?.let {
                        writeln("```${source.language}")
                        writeln(it)
                        writeln("```")
                    }
                    if (path.isNotEmpty()) {
                        writeln("{% endcode %}")
                    }
                    writeln()
                    
                    if (source.after != null) {
                        writeln("###### After")
                        if (path.isNotEmpty()) {
                            writeln("{% code title=\"$path\" %}")
                        }
                        writeln("```${source.language}")
                        writeln(source.after)
                        writeln("```")
                        if (path.isNotEmpty()) {
                            writeln("{% endcode %}")
                        }
                        writeln()
                        
                        source.before?.let {
                            writeln("{% hint style=\"info\" %}")
                            writeln("This example recipe modifies the source file. Automatically generated changes are indicated in **diff** below.")
                            writeln("{% endhint %}")
                            writeln()
                            writeln("###### Diff")
                            if (path.isNotEmpty()) {
                                writeln("{% code title=\"$path\" %}")
                            }
                            writeln("```diff")
                            writeln(generateDiff(path, it, source.after))
                            writeln("```")
                            if (path.isNotEmpty()) {
                                writeln("{% endcode %}")
                            }
                            writeln()
                        }
                    }
                }
            }
        }
    }

    private fun BufferedWriter.writeUsage() {
        writeln("## Usage")
        writeln()
        writeln("This recipe has no required configuration options. It can be activated by adding a dependency on `${origin.groupId}:${origin.artifactId}:${origin.version}` in your build file or by running a shell command (in which case no build changes are needed):")
        
        val cliOptions = buildCliOptions(recipeDescriptor)
        if (origin.isFromCoreLibrary()) {
            writeSnippetsFromCoreLibrary(origin, recipeDescriptor.name, cliOptions)
        } else {
            writeSnippetForOtherLibrary(origin, recipeDescriptor.name, cliOptions)
        }
    }

    private fun BufferedWriter.writeDefinition() {
        if (recipeDescriptor.recipeList.isNotEmpty()) {
            writeln("## Definition")
            writeln()
            writeln("{% tabs %}")
            writeln("{% tab title=\"Recipe List\" %}")
            writeln("* [${recipeDescriptor.displayNameEscaped()}](${recipeDescriptor.name.lowercase().replace(".", "/")})")
            for (r in recipeDescriptor.recipeList) {
                val prefix = "  "
                writeRecipeDefinition(prefix, r)
            }
            writeln()
            writeln("{% endtab %}")
            writeln()
            writeln("{% tab title=\"Yaml Recipe List\" %}")
            writeln("```yaml")
            writeln(recipeDescriptor.asYaml())
            writeln("```")
            writeln("{% endtab %}")
            writeln("{% endtabs %}")
            writeln()
        }
    }

    private fun BufferedWriter.writeUsedBy() {
        recipeContainedBy?.let {
            if (it.isNotEmpty()) {
                writeln("## Usage")
                writeln()
                writeln("This recipe has no required configuration options. It can be activated directly after taking a dependency on `${origin.groupId}:${origin.artifactId}:${origin.version}` in your build file:")
                writeln()
                writeln("{% tabs %}")
                writeln("{% tab title=\"Gradle\" %}")
                writeln("1. Add the following to your `build.gradle` file:")
                writeln("{% code title=\"build.gradle\" %}")
                writeln("```groovy")
                writeln("plugins {")
                writeln("    id(\"org.openrewrite.rewrite\") version(\"${gradlePluginVersion}\")")
                writeln("}")
                writeln()
                writeln("rewrite {")
                writeln("    activeRecipe(\"${recipeDescriptor.name}\")")
                writeln("    setExportDatatables(true)")
                writeln("}")
                writeln()
                writeln("repositories {")
                writeln("    mavenCentral()")
                writeln("}")
                writeln()
                writeln("dependencies {")
                writeln("    rewrite(\"${origin.groupId}:${origin.artifactId}:${origin.version}\")")
                writeln("}")
                writeln("```")
                writeln("{% endcode %}")
                writeln("2. Run `gradle rewriteRun` to run the recipe.")
                writeln("{% endtab %}")
                writeln()
                writeln("{% tab title=\"Maven POM\" %}")
                writeln("1. Add the following to your `pom.xml` file:")
                writeln("{% code title=\"pom.xml\" %}")
                writeln("```xml")
                writeln("<project>")
                writeln("  <build>")
                writeln("    <plugins>")
                writeln("      <plugin>")
                writeln("        <groupId>org.openrewrite.maven</groupId>")
                writeln("        <artifactId>rewrite-maven-plugin</artifactId>")
                writeln("        <version>${mavenPluginVersion}</version>")
                writeln("        <configuration>")
                writeln("          <exportDatatables>true</exportDatatables>")
                writeln("          <activeRecipes>")
                writeln("            <recipe>${recipeDescriptor.name}</recipe>")
                writeln("          </activeRecipes>")
                writeln("        </configuration>")
                writeln("        <dependencies>")
                writeln("          <dependency>")
                writeln("            <groupId>${origin.groupId}</groupId>")
                writeln("            <artifactId>${origin.artifactId}</artifactId>")
                writeln("            <version>${origin.version}</version>")
                writeln("          </dependency>")
                writeln("        </dependencies>")
                writeln("      </plugin>")
                writeln("    </plugins>")
                writeln("  </build>")
                writeln("</project>")
                writeln("```")
                writeln("{% endcode %}")
                writeln("2. Run `mvn rewrite:run` to run the recipe.")
                writeln("{% endtab %}")
                writeln()
                writeln("{% endtabs %}")
                writeln()
                writeln("## Used by")
                writeln()
                writeln("* [${it.first().displayNameEscaped()}](/recipes/${getRecipePath(it.first())})")
                for (r in it.drop(1)) {
                    writeln("* [${r.displayNameEscaped()}](/recipes/${getRecipePath(r)})")
                }
                writeln()
            }
        }
    }

    private fun BufferedWriter.writeModerneLink() {
        writeln("## See how this recipe works across multiple open-source repositories")
        writeln()
        writeln("[![Moderne Link Image](/.gitbook/assets/ModerneRecipeButton.png)](https://app.moderne.io/recipes/${recipeDescriptor.name})")
        writeln()
        writeln("The community edition of the Moderne platform enables you to easily run recipes across thousands of open-source repositories.")
        writeln()
        writeln("Please [contact Moderne](https://moderne.io/product) for more information about safely running the recipes on your own codebase in a private SaaS.")
        writeln("## License")
        writeln()
        writeln("This recipe is available under the [${origin.license?.name ?: "Unknown"}](${origin.license?.uri ?: ""}) license.")
        writeln()
    }

    private fun BufferedWriter.writeContributors() {
        if (recipeDescriptor.contributors.isNotEmpty()) {
            writeln("## Contributors")
            writeln(recipeDescriptor.contributors.stream()
                .map { contributor ->
                    if (contributor.email.contains("noreply")) {
                        contributor.name
                    } else {
                        "[${contributor.name}](mailto:${contributor.email})"
                    }
                }.collect(Collectors.joining(", ")))
        }
    }

    // Helper methods
    private fun getFormattedRecipeDescription(description: String?): String? {
        return description?.let {
            if (it.endsWith(".")) it.substring(0, it.length - 1) else it
        }
    }

    private fun printValue(value: Any): String = when (value) {
        is List<*> -> value.joinToString(",", "[", "]") { printValue(it ?: "") }
        else -> value.toString()
    }

    private fun buildCliOptions(recipeDescriptor: RecipeDescriptor): String {
        return recipeDescriptor.options
            .filter { it.isRequired }
            .joinToString(" ") { "--${it.name}=${getExampleValue(it)}" }
    }

    private fun getExampleValue(option: OptionDescriptor): String {
        return option.example?.toString() ?: when (option.type) {
            "String" -> "value"
            "boolean", "Boolean" -> "true"
            "int", "Integer" -> "1"
            else -> "value"
        }
    }

    private fun generateDiff(path: String?, original: String, revised: String): String {
        val patch = DiffUtils.diff(original.lines(), revised.lines())
        val diffContent = StringBuilder()
        val contextLinesBefore = 2
        val contextLinesAfter = 2
        
        for (delta in patch.deltas) {
            val originalPosition = delta.source.position
            val revisedPosition = delta.target.position
            
            // Add context before
            val startContext = (originalPosition - contextLinesBefore).coerceAtLeast(0)
            for (i in startContext until originalPosition) {
                if (i < original.lines().size) {
                    diffContent.append("  ${original.lines()[i]}\n")
                }
            }
            
            // Add removed lines
            delta.source.lines.forEach { line ->
                diffContent.append("- $line\n")
            }
            
            // Add added lines
            delta.target.lines.forEach { line ->
                diffContent.append("+ $line\n")
            }
            
            // Add context after
            val endContext = (originalPosition + delta.source.size() + contextLinesAfter).coerceAtMost(original.lines().size)
            for (i in (originalPosition + delta.source.size()) until endContext) {
                if (i < original.lines().size) {
                    diffContent.append("  ${original.lines()[i]}\n")
                }
            }
        }
        
        return diffContent.toString()
    }

    private fun BufferedWriter.writeRecipeDefinition(prefix: String, recipe: RecipeDescriptor) {
        writeln("$prefix* [${recipe.displayNameEscaped()}](${getRecipePath(recipe)})")
        for (r in recipe.recipeList) {
            writeRecipeDefinition("$prefix  ", r)
        }
    }

    private fun BufferedWriter.writeSnippetsFromCoreLibrary(
        origin: RecipeOrigin,
        recipeName: String,
        cliOptions: String
    ) {
        writeln("{% tabs %}")
        writeln("{% tab title=\"Gradle\" %}")
        writeln("{% code title=\"build.gradle\" %}")
        writeln("```groovy")
        writeln("plugins {")
        writeln("    id(\"org.openrewrite.rewrite\") version(\"${gradlePluginVersion}\")")
        writeln("}")
        writeln()
        writeln("rewrite {")
        writeln("    activeRecipe(\"$recipeName\")")
        writeln("    setExportDatatables(true)")
        writeln("}")
        writeln()
        writeln("repositories {")
        writeln("    mavenCentral()")
        writeln("}")
        writeln("```")
        writeln("{% endcode %}")
        writeln("{% endtab %}")
        writeln("{% tab title=\"Gradle init script\" %}")
        writeln("{% code title=\"init.gradle\" %}")
        writeln("```groovy")
        writeln("initscript {")
        writeln("    repositories {")
        writeln("        maven { url \"https://plugins.gradle.org/m2\" }")
        writeln("    }")
        writeln("    dependencies { classpath(\"org.openrewrite:plugin:${gradlePluginVersion}\") }")
        writeln("}")
        writeln("rootProject {")
        writeln("    plugins.apply(org.openrewrite.gradle.RewritePlugin)")
        writeln("    dependencies {")
        writeln("        rewrite(\"${origin.groupId}:${origin.artifactId}:${origin.version}\")")
        writeln("    }")
        writeln("    rewrite {")
        writeln("        activeRecipe(\"$recipeName\")")
        writeln("        setExportDatatables(true)")
        writeln("    }")
        writeln("    afterEvaluate {")
        writeln("        if (repositories.isEmpty()) {")
        writeln("            repositories {")
        writeln("                mavenCentral()")
        writeln("            }")
        writeln("        }")
        writeln("    }")
        writeln("}")
        writeln("```")
        writeln("{% endcode %}")
        writeln("{% endtab %}")
        writeln("{% tab title=\"Maven POM\" %}")
        writeln("{% code title=\"pom.xml\" %}")
        writeln("```xml")
        writeln("<project>")
        writeln("  <build>")
        writeln("    <plugins>")
        writeln("      <plugin>")
        writeln("        <groupId>org.openrewrite.maven</groupId>")
        writeln("        <artifactId>rewrite-maven-plugin</artifactId>")
        writeln("        <version>${mavenPluginVersion}</version>")
        writeln("        <configuration>")
        writeln("          <exportDatatables>true</exportDatatables>")
        writeln("          <activeRecipes>")
        writeln("            <recipe>$recipeName</recipe>")
        writeln("          </activeRecipes>")
        writeln("        </configuration>")
        writeln("      </plugin>")
        writeln("    </plugins>")
        writeln("  </build>")
        writeln("</project>")
        writeln("```")
        writeln("{% endcode %}")
        writeln("{% endtab %}")
        writeln()
        writeln("{% tab title=\"Moderne CLI\" %}")
        writeln("You will need to have configured the [Moderne CLI](https://docs.moderne.io/user-documentation/moderne-cli/getting-started/cli-intro) on your machine before you can run the following command.")
        writeln()
        writeln("{% code title=\"shell\" %}")
        writeln("```shell")
        writeln("mod run . --recipe $recipeName")
        writeln("```")
        writeln("{% endcode %}")
        writeln("{% endtab %}")
        writeln("{% endtabs %}")
        writeln()
    }

    private fun BufferedWriter.writeSnippetForOtherLibrary(
        origin: RecipeOrigin,
        recipeName: String,
        cliOptions: String
    ) {
        writeSnippetsWithConfigurationWithDependency(origin, recipeName, cliOptions)
    }

    private fun BufferedWriter.writeSnippetsWithConfigurationWithDependency(
        origin: RecipeOrigin,
        recipeName: String,
        cliOptions: String
    ) {
        writeln("{% tabs %}")
        writeln("{% tab title=\"Gradle\" %}")
        writeln("1. Add the following to your `build.gradle` file:")
        writeln("{% code title=\"build.gradle\" %}")
        writeln("```groovy")
        writeln("plugins {")
        writeln("    id(\"org.openrewrite.rewrite\") version(\"${gradlePluginVersion}\")")
        writeln("}")
        writeln()
        writeln("rewrite {")
        writeln("    activeRecipe(\"$recipeName\")")
        writeln("    setExportDatatables(true)")
        writeln("}")
        writeln()
        writeln("repositories {")
        writeln("    mavenCentral()")
        writeln("}")
        writeln()
        writeln("dependencies {")
        writeln("    rewrite(\"${origin.groupId}:${origin.artifactId}:${origin.version}\")")
        writeln("}")
        writeln("```")
        writeln("{% endcode %}")
        writeln("2. Run `gradle rewriteRun` to run the recipe.")
        writeln("{% endtab %}")
        writeln()
        writeln("{% tab title=\"Gradle init script\" %}")
        writeln("1. Create a file named `init.gradle` in the root of your project.")
        writeln("{% code title=\"init.gradle\" %}")
        writeln("```groovy")
        writeln("initscript {")
        writeln("    repositories {")
        writeln("        maven { url \"https://plugins.gradle.org/m2\" }")
        writeln("    }")
        writeln("    dependencies { classpath(\"org.openrewrite:plugin:${gradlePluginVersion}\") }")
        writeln("}")
        writeln("rootProject {")
        writeln("    plugins.apply(org.openrewrite.gradle.RewritePlugin)")
        writeln("    dependencies {")
        writeln("        rewrite(\"${origin.groupId}:${origin.artifactId}:${origin.version}\")")
        writeln("    }")
        writeln("    rewrite {")
        writeln("        activeRecipe(\"$recipeName\")")
        writeln("        setExportDatatables(true)")
        writeln("    }")
        writeln("    afterEvaluate {")
        writeln("        if (repositories.isEmpty()) {")
        writeln("            repositories {")
        writeln("                mavenCentral()")
        writeln("            }")
        writeln("        }")
        writeln("    }")
        writeln("}")
        writeln("```")
        writeln("{% endcode %}")
        writeln("2. Run the recipe.")
        writeln("{% code title=\"shell\" %}")
        writeln("```shell")
        writeln("gradle --init-script init.gradle rewriteRun")
        writeln("```")
        writeln("{% endcode %}")
        writeln("{% endtab %}")
        writeln("{% tab title=\"Maven POM\" %}")
        writeln("1. Add the following to your `pom.xml` file:")
        writeln("{% code title=\"pom.xml\" %}")
        writeln("```xml")
        writeln("<project>")
        writeln("  <build>")
        writeln("    <plugins>")
        writeln("      <plugin>")
        writeln("        <groupId>org.openrewrite.maven</groupId>")
        writeln("        <artifactId>rewrite-maven-plugin</artifactId>")
        writeln("        <version>${mavenPluginVersion}</version>")
        writeln("        <configuration>")
        writeln("          <exportDatatables>true</exportDatatables>")
        writeln("          <activeRecipes>")
        writeln("            <recipe>$recipeName</recipe>")
        writeln("          </activeRecipes>")
        writeln("        </configuration>")
        writeln("        <dependencies>")
        writeln("          <dependency>")
        writeln("            <groupId>${origin.groupId}</groupId>")
        writeln("            <artifactId>${origin.artifactId}</artifactId>")
        writeln("            <version>${origin.version}</version>")
        writeln("          </dependency>")
        writeln("        </dependencies>")
        writeln("      </plugin>")
        writeln("    </plugins>")
        writeln("  </build>")
        writeln("</project>")
        writeln("```")
        writeln("{% endcode %}")
        writeln("2. Run `mvn rewrite:run` to run the recipe.")
        writeln("{% endtab %}")
        writeln()
        writeln("{% tab title=\"Moderne CLI\" %}")
        writeln("You will need to have configured the [Moderne CLI](https://docs.moderne.io/user-documentation/moderne-cli/getting-started/cli-intro) on your machine before you can run the following command.")
        writeln()
        writeln("{% code title=\"shell\" %}")
        writeln("```shell")
        writeln("mod run . --recipe $recipeName")
        writeln("```")
        writeln("{% endcode %}")
        writeln("{% endtab %}")
        writeln("{% endtabs %}")
        writeln()
    }

    private fun getRecipePath(recipe: RecipeDescriptor): String =
        recipe.name.lowercase().replace(".", "/")

    private fun getRecipePath(recipesPath: Path, recipeDescriptor: RecipeDescriptor) =
        recipesPath.resolve(getRecipePath(recipeDescriptor) + ".md")
}