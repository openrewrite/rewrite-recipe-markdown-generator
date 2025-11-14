package org.openrewrite

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.openrewrite.config.CategoryDescriptor
import org.openrewrite.config.DeclarativeRecipe
import org.openrewrite.config.Environment
import org.openrewrite.config.RecipeDescriptor
import java.net.URI
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.jar.Manifest
import kotlin.io.path.toPath

/**
 * Data class to hold both descriptors and recipes loaded from an environment
 */
private data class EnvironmentData(
    val recipeDescriptors: Collection<RecipeDescriptor>,
    val categoryDescriptors: Collection<CategoryDescriptor>,
    val recipes: Collection<Recipe>,
    val sourceUri: URI
)

/**
 * Result of loading recipes from multiple sources
 */
data class RecipeLoadResult(
    val allRecipeDescriptors: List<RecipeDescriptor>,
    val allCategoryDescriptors: List<CategoryDescriptor>,
    val allRecipes: List<Recipe>,
    val recipeToSource: Map<String, URI>
)

/**
 * Responsible for loading recipes from JAR files and classpaths
 */
class RecipeLoader {

    val classloader: ClassLoader
    val dependencies: List<Path>
    val recipeOrigins: Map<URI, RecipeOrigin>

    constructor(recipeClasspath: String, recipeOrigins: Map<URI, RecipeOrigin>) {
        // Create classloader from classpath
        dependencies = recipeClasspath.split(";")
            .map(Paths::get)
        classloader = dependencies
            .map(Path::toUri)
            .map(URI::toURL)
            .toTypedArray<URL>()
            .let { URLClassLoader(it) }
        this.recipeOrigins = recipeOrigins
    }

    /**
     * Load recipes from the specified sources and classpath
     */
    fun loadRecipes(): RecipeLoadResult {
        // Load Java/YAML recipes in parallel
        val environmentData = loadEnvironmentDataAsync()

        // Build mapping from recipe name to source URI
        val recipeToSource = mutableMapOf<String, URI>()

        // Build a map of all recipes by name for quick lookup
        val allRecipesByName = environmentData.flatMap { it.recipes }.associateBy { it.name }

        environmentData.forEach { envData ->
            // Scan YAML files in this JAR to find declarative recipe sources
            val yamlRecipeToFile = scanYamlRecipesInJar(envData.sourceUri)

            envData.recipeDescriptors.forEach { descriptor ->
                val recipe = allRecipesByName[descriptor.name]

                // Check if this is a declarative (YAML) recipe
                if (recipe is DeclarativeRecipe) {
                    // Use the YAML file URI if we found it
                    val yamlFile = yamlRecipeToFile[descriptor.name]
                    if (yamlFile != null) {
                        recipeToSource[descriptor.name] = yamlFile
                    } else {
                        // Fallback to JAR URI if we couldn't find the YAML file
                        recipeToSource[descriptor.name] = envData.sourceUri
                    }
                } else {
                    // For Java recipes, use the JAR URI
                    recipeToSource[descriptor.name] = envData.sourceUri
                }
            }
        }

        // Load TypeScript recipes via RPC
        println("\nChecking for TypeScript/JavaScript recipes...")
        val typeScriptLoader = TypeScriptRecipeLoader(recipeOrigins)
        val typeScriptResult = typeScriptLoader.loadTypeScriptRecipes()

        // Merge TypeScript results with Java/YAML results
        val allDescriptors = environmentData.flatMap { it.recipeDescriptors }.toMutableList()
        allDescriptors.addAll(typeScriptResult.descriptors)
        recipeToSource.putAll(typeScriptResult.recipeToSource)

        // Combine all results
        return RecipeLoadResult(
            allRecipeDescriptors = allDescriptors,
            allCategoryDescriptors = environmentData.flatMap { it.categoryDescriptors },
            allRecipes = environmentData.flatMap { it.recipes },
            recipeToSource = recipeToSource
        )
    }

    /**
     * Scan YAML files in a JAR to find which recipes are defined in which YAML files
     */
    private fun scanYamlRecipesInJar(jarUri: URI): Map<String, URI> {
        val recipeToYamlFile = mutableMapOf<String, URI>()

        try {
            val jarPath = Paths.get(jarUri)
            if (!Files.exists(jarPath)) {
                return recipeToYamlFile
            }

            FileSystems.newFileSystem(jarPath, null as ClassLoader?).use { fs ->
                val rewriteDir = fs.getPath("/META-INF/rewrite")
                if (Files.exists(rewriteDir)) {
                    Files.walk(rewriteDir)
                        .filter { Files.isRegularFile(it) && it.toString().endsWith(".yml") }
                        .forEach { yamlPath ->
                            try {
                                // Parse YAML file to find recipe names
                                val yamlContent = Files.readString(yamlPath)
                                // Look for lines like "name: org.openrewrite.java.jackson.JacksonBestPractices"
                                // or "  - org.openrewrite.SomeRecipe" (for recipeList entries)
                                val namePattern = Regex("""^\s*name:\s*(.+)$""", RegexOption.MULTILINE)
                                val matches = namePattern.findAll(yamlContent)

                                matches.forEach { match ->
                                    val recipeName = match.groupValues[1].trim()
                                    // Construct a URI for this YAML file in the format: jar:file:/path/to/jar.jar!/META-INF/rewrite/file.yml
                                    val yamlFileUri = URI("jar:file:${jarPath.toAbsolutePath()}!${yamlPath}")
                                    recipeToYamlFile[recipeName] = yamlFileUri
                                }
                            } catch (e: Exception) {
                                println("Warning: Could not parse YAML file ${yamlPath}: ${e.message}")
                            }
                        }
                }
            }
        } catch (e: Exception) {
            println("Warning: Could not scan JAR $jarUri for YAML recipes: ${e.message}")
        }

        return recipeToYamlFile
    }

    /**
     * Process recipe jars in parallel and collect both descriptors and recipes
     */
    private fun loadEnvironmentDataAsync(): List<EnvironmentData> = runBlocking {
        println("Starting parallel recipe loading...")
        recipeOrigins.entries
            .chunked(4) // Process in batches of jars
            .flatMap { batch ->
                batch.map { recipeOrigin ->
                    async(Dispatchers.IO) {
                        println("Processing ${recipeOrigin.key.toPath().fileName}")
                        // Create a separate environment for each jar
                        val batchEnvBuilder = Environment.builder()
                        // If you are running this with an old version of Rewrite (for diff log purposes), you'll need
                        // to update the below line to look like this instead:
                        // batchEnvBuilder.scanJar(recipeOrigin.key.toPath(), classloader)
                        batchEnvBuilder.scanJar(recipeOrigin.key.toPath(), dependencies, classloader)
                        val batchEnv = batchEnvBuilder.build()
                        EnvironmentData(
                            batchEnv.listRecipeDescriptors(),
                            batchEnv.listCategoryDescriptors(),
                            batchEnv.listRecipes(),
                            recipeOrigin.key
                        ).also {
                            println("Loaded ${it.recipeDescriptors.size} recipe descriptors from ${recipeOrigin.key.toPath().fileName}")
                        }
                    }
                }.awaitAll()
            }.also {
                println("Finished loading all recipes.")
            }
    }

    /**
     * Add license and source information from JAR manifests
     */
    fun addInfosFromManifests() {
        val mfInfos: Map<URI, Pair<License, String>> = classloader.getResources("META-INF/MANIFEST.MF").asSequence()
            .filter { it.path != null }
            .map {
                Pair(
                    URI(it.path.substringBeforeLast("!")),
                    Manifest(it.openStream()).mainAttributes
                )
            }
            .map { (p, attr) ->
                Pair(
                    p,
                    Triple(
                        Licenses.get(
                            attr.getValue("License-Url") ?: Licenses.Unknown.uri.toString(),
                            attr.getValue("License-Name") ?: Licenses.Unknown.name
                        ),
                        attr.getValue("Module-Origin")?.substringBefore(".git") ?: "",
                        attr.getValue("Module-Source")?.removePrefix("/")
                    )
                )
            }
            .filter { it.second.second.startsWith("http") }
            .associate { (path, mfValues) ->
                Pair(
                    path,
                    Pair(
                        mfValues.first,
                        if (mfValues.second.isNotEmpty()) "${mfValues.second}/blob/main/${mfValues.third ?: ""}" else ""
                    )
                )
            }

        // Apply manifest information to recipe origins
        recipeOrigins.forEach { (uri, origin) ->
            val license: License? = mfInfos[uri]?.first
            if (license != null) {
                origin.license = license
            } else {
                println("Unable to determine License for ${origin}")
            }
            origin.repositoryUrl = mfInfos[uri]?.second ?: ""
        }
    }
}
