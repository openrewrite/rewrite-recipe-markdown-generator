package org.openrewrite

import org.openrewrite.config.CategoryDescriptor
import java.lang.Runnable
import org.openrewrite.config.RecipeDescriptor
import java.lang.RuntimeException
import java.nio.file.Files
import java.io.IOException
import java.util.stream.Collectors
import java.io.BufferedWriter
import java.nio.file.StandardOpenOption
import java.lang.StringBuilder
import org.openrewrite.config.Environment
import org.openrewrite.internal.StringUtils
import org.openrewrite.internal.StringUtils.isNullOrEmpty
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.jvm.JvmStatic
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

    @Parameters(index = "1", defaultValue = "", description = ["A ';' delineated list of coordinates to search for recipes. " +
            "Each entry in the list must be of format groupId:artifactId:version:path where 'path' is a file path to the jar"])
    lateinit var recipeSources: String

    @Parameters(index = "2", defaultValue = "", description = ["A ';' delineated list of jars that provide the full " +
            "transitive dependency list for the recipeSources"])
    lateinit var recipeClasspath: String

    @Parameters(index = "3", defaultValue = "latest.release", description = ["The version of the Rewrite Gradle Plugin to display in relevant samples"])
    lateinit var gradlePluginVerison: String

    @Parameters(index = "4", defaultValue = "", description = ["The version of the Rewrite Maven Plugin to display in relevant samples"])
    lateinit var mavenPluginVerison: String

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
        if (recipeSources.isNotEmpty() && recipeClasspath.isNotEmpty()) {
            recipeOrigins = RecipeOrigin.parse(recipeSources)
            val classpath = recipeClasspath.splitToSequence(";")
                    .map(Paths::get)
                    .toList()
            env = Environment.builder()
                    .scanClasspath(classpath)
                    .build()
        } else {
            recipeOrigins = emptyMap()
            env = Environment.builder()
                    .scanRuntimeClasspath()
                    .build()
        }
        val recipeDescriptors: List<RecipeDescriptor> = ArrayList(env.listRecipeDescriptors())
        val categoryDescriptors = ArrayList(env.listCategoryDescriptors())
        val groupedRecipes: SortedMap<String, List<RecipeDescriptor>> = TreeMap()
        for (recipe in recipeDescriptors) {
            if (recipe.name.startsWith("org.openrewrite.text")) {
                continue
            }
            val recipeCategory = getRecipeCategory(recipe)
            val categoryRecipes = groupedRecipes.computeIfAbsent(recipeCategory) { mutableListOf() } as MutableList<RecipeDescriptor>
            categoryRecipes.add(recipe)
        }
        // insert missing parent categories
        val missingCategories: MutableMap<String, List<RecipeDescriptor>> = HashMap()
        for (category in groupedRecipes.keys) {
            var p = category
            while (p.isNotEmpty()) {
                if (!groupedRecipes.containsKey(p)) {
                    missingCategories[p] = emptyList()
                }
                p = if (p.contains("/")) {
                    p.substring(0, p.lastIndexOf("/"))
                } else {
                    ""
                }
            }
        }
        groupedRecipes.putAll(missingCategories)
        for (recipeDescriptor in recipeDescriptors) {
            if (recipeDescriptor.name.startsWith("org.openrewrite.text")) {
                continue
            }
            var origin: RecipeOrigin?
            var rawUri = recipeDescriptor.source.toString()
            val exclamationIndex = rawUri.indexOf('!')
            if (exclamationIndex == -1) {
                origin = recipeOrigins[recipeDescriptor.source]
            } else {
                // The recipe origin includes the path to the recipe within a jar
                // Strip the "jar:" prefix and the part of the URI pointing inside the jar
                rawUri = rawUri.substring(0, exclamationIndex)
                rawUri = rawUri.substring(4)
                val jarOnlyUri = URI.create(rawUri)
                origin = recipeOrigins[jarOnlyUri]
            }
            requireNotNull(origin) { "Could not find GAV coordinates of recipe " + recipeDescriptor.name + " from " + recipeDescriptor.source }
            writeRecipe(recipeDescriptor, recipesPath, origin, gradlePluginVerison, mavenPluginVerison)
        }
        val summarySnippetPath = outputPath.resolve("SUMMARY_snippet.md")
        Files.newBufferedWriter(summarySnippetPath, StandardOpenOption.CREATE).useAndApply {
            writeln("* Recipes")
            for (category in groupedRecipes.entries) {
                writeCategorySnippet(category)
                // get direct subcategory descendants
                val subcategories = groupedRecipes.keys.stream().filter { k: String -> k != category.key && k.startsWith(category.key) }
                        .map { k: String -> k.substring(category.key.length + 1) }
                        .filter { k: String -> !k.contains("/") }
                        .collect(Collectors.toSet())
                writeCategoryIndex(outputPath, categoryDescriptors, category, subcategories)
            }
        }
    }

    private fun writeCategoryIndex(outputPath: Path, categoryDescriptors: List<CategoryDescriptor>,  categoryEntry: Map.Entry<String, List<RecipeDescriptor>>, subcategories: Set<String>) {
        val category = categoryEntry.key
        val categoryIndexPath = outputPath.resolve("reference/recipes/$category/README.md")
        Files.newBufferedWriter(categoryIndexPath, StandardOpenOption.CREATE).useAndApply {
            val categoryName: String = if (category.contains("/")) {
                StringUtils.capitalize(category.substring(category.lastIndexOf("/") + 1))
            } else {
                StringUtils.capitalize(category)
            }
            writeln("# $categoryName")
            val categoryPackage = "org.openrewrite.${categoryEntry.key.replace('/', '.')}"
            val categoryDescriptor: CategoryDescriptor? = categoryDescriptors.find { it.packageName == categoryPackage }
            if(categoryDescriptor != null) {
                newLine()
                writeln("_${categoryDescriptor.description}_")
            }
            if (categoryEntry.value.isNotEmpty()) {
                newLine()
                writeln("### Recipes")
                for (recipe in categoryEntry.value) {
                    val recipePath = getRecipePath(recipe)
                    writeln("* [" + recipe.displayName + "](" + recipePath.substring(recipePath.lastIndexOf("/") + 1) + ".md)")
                }
            }
            if (subcategories.isNotEmpty()) {
                newLine()
                writeln("### Subcategories")
                for (subcategory in subcategories) {
                    writeln("* [" + StringUtils.capitalize(subcategory) + "](" + subcategory + "/README.md)")
                }
            }
        }
    }

    private fun writeRecipe(recipeDescriptor: RecipeDescriptor, outputPath: Path, origin: RecipeOrigin, gradlePluginVersion: String, mavenPluginVersion: String) {
        val recipeMarkdownPath = getRecipePath(outputPath, recipeDescriptor)
        Files.createDirectories(recipeMarkdownPath.parent)
        Files.newBufferedWriter(recipeMarkdownPath, StandardOpenOption.CREATE).useAndApply {
            write("""
                # ${recipeDescriptor.displayName}
                
                ** ${recipeDescriptor.name.replace("_".toRegex(), "\\\\_")}**
                
            """.trimIndent())
            if (!isNullOrEmpty(recipeDescriptor.description)) {
                writeln("_" + recipeDescriptor.description + "_")
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
            writeln("""
                ### Source
                
                Maven Central [entry](https://search.maven.org/artifact/${origin.groupId}/${origin.artifactId}/${origin.version}/jar)
                
                * groupId: ${origin.groupId}
                * artifactId: ${origin.artifactId}
                * version: ${origin.version}
                
            """.trimIndent())

            if (recipeDescriptor.options.isNotEmpty()) {
                writeln("""
                    ### Options
                    
                    | Type | Name | Description |
                    | -- | -- | -- |
                """.trimIndent())
                for (option in recipeDescriptor.options) {
                    writeln("""
                        | `${option.type}` | ${option.name} | ${option.description} |
                    """.trimIndent())
                }
            }
            if (recipeDescriptor.recipeList.isNotEmpty()) {
                writeln("## Recipe list")
                newLine()
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
            }
            writeln("## Usage")
            val requiresConfiguration = recipeDescriptor.options.any { it.isRequired }
            val requiresDependency = !origin.isFromCoreLibrary()
            if (requiresConfiguration) {
                val exampleRecipeName = "com.yourorg." + recipeDescriptor.name.substring(recipeDescriptor.name.lastIndexOf('.') + 1) + "Example"
                write("This recipe has required configuration parameters. ")
                write("Recipes with required configuration parameters cannot be activated directly. ")
                write("To activate this recipe you must create a new recipe which fills in the required parameters. ")
                write("In your rewrite.yml create a new recipe with a unique name. ")
                write("For example: `$exampleRecipeName`. ")
                newLine()
                writeln("Here's how you can define and customize such a recipe within your rewrite.yml:")
                write("""
                    
                    {% code title="rewrite.yml" %}
                    ```yaml
                    ---
                    type: specs.openrewrite.org/v1beta/recipe
                    name: $exampleRecipeName
                    displayName: ${recipeDescriptor.displayName} example
                    recipeList:
                      - ${recipeDescriptor.name}:
                    
                """.trimIndent())
                for(option in recipeDescriptor.options) {
                    writeln("      ${option.name}: ${option.example}")
                }
                writeln("```")
                writeln("{% endcode %}")
                newLine()
                if(requiresDependency) {
                    writeln("""
                        Now that `$exampleRecipeName` has been defined activate it and take a dependency on ${origin.groupId}:${origin.artifactId}:${origin.version} in your build file:
                        
                        {% tabs %}
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
                            rewrite("${origin.groupId}":"${origin.artifactId}":"${origin.version}")
                        }
                        ```
                        {% endcode %}
                        {% endtab %}
    
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
                        {% endtabs %}
                        
                """.trimIndent())
                } else {
                    writeln("""
                        
                        Now that `$exampleRecipeName` has been defined activate it in your build file:
                        
                        {% tabs %}
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
                        {% endtabs %}
                        
                    """.trimIndent())
                }
                writeln("Recipes can also be activated directly from the commandline by adding the argument `-DactiveRecipe=${exampleRecipeName}`")
            } else {
                if(origin.isFromCoreLibrary()) {
                    writeln("This recipe has no required configuration parameters and comes from a rewrite core library. " +
                            " It can be activated directly without adding any dependencies.")
                    writeln("""
                        
                        {% tabs %}
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
                                    <recipe>${recipeDescriptor.name}</recipe>
                                  </activeRecipes>
                                </configuration>
                              </plugin>
                            </plugins>
                          </build>
                        </project>
                        ```
                        
                    """.trimIndent())
                } else {
                    writeln("This recipe has no required configuration options and can be activated directly after " +
                            "taking a dependency on ${origin.groupId}:${origin.artifactId}:${origin.version} in your build file:")
                    writeln("""
                        
                        {% tabs %}
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
                            rewrite("${origin.groupId}":"${origin.artifactId}":"${origin.version}")
                        }
                        ```
                        {% endcode %}
                        {% endtab %}
    
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
                        {% endtabs %}
                        
                    """.trimIndent())
                }
                writeln("Recipes can also be activated directly from the commandline by adding the argument `-DactiveRecipe=${recipeDescriptor.name}`")
            }
        }
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
        fun BufferedWriter.useAndApply(withFun: BufferedWriter.()->Unit): Unit = use { it.apply(withFun) }

        fun BufferedWriter.writeCategorySnippet(categoryEntry: Map.Entry<String, List<RecipeDescriptor>>) {
            val indentBuilder = StringBuilder("  ")
            val category = categoryEntry.key
            val levels = category.chars().filter { ch: Int -> ch == '/'.code }.count()
            for (i in 0 until levels) {
                indentBuilder.append("  ")
            }
            val indent = indentBuilder.toString()
            val categoryBuilder = StringBuilder(indent).append("* [")
            if (category.contains("/")) {
                categoryBuilder.append(StringUtils.capitalize(category.substring(category.lastIndexOf("/") + 1)))
            } else {
                categoryBuilder.append(StringUtils.capitalize(category))
            }
            categoryBuilder.append("](reference/recipes/").append(category).append("/README.md)")
            writeln(categoryBuilder.toString())
            for (recipe in categoryEntry.value) {
                writeln(indent + "  * [" + recipe.displayName + "](" + getRecipeRelativePath(recipe) + ")")
            }
        }

        fun BufferedWriter.writeln(text: String) {
            write(text)
            newLine()
        }

        private fun getRecipeCategory(recipe: RecipeDescriptor): String {
            val recipePath = getRecipePath(recipe)
            return recipePath.substring(0, recipePath.lastIndexOf("/"))
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
                "reference/recipes/" + getRecipePath(recipe) + ".md"

        @JvmStatic
        fun main(args: Array<String>) {
            val exitCode = CommandLine(RecipeMarkdownGenerator()).execute(*args)
            exitProcess(exitCode)
        }
    }
}
