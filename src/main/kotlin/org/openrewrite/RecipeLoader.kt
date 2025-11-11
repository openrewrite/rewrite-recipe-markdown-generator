package org.openrewrite

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.openrewrite.config.CategoryDescriptor
import org.openrewrite.config.Environment
import org.openrewrite.config.RecipeDescriptor
import java.net.URI
import java.net.URL
import java.net.URLClassLoader
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
        // Load recipes in parallel
        val environmentData = loadEnvironmentDataAsync()

        // Build mapping from recipe name to source URI
        val recipeToSource = mutableMapOf<String, URI>()
        environmentData.forEach { envData ->
            envData.recipeDescriptors.forEach { descriptor ->
                recipeToSource[descriptor.name] = envData.sourceUri
            }
        }

        // Combine all results
        return RecipeLoadResult(
            allRecipeDescriptors = environmentData.flatMap { it.recipeDescriptors },
            allCategoryDescriptors = environmentData.flatMap { it.categoryDescriptors },
            allRecipes = environmentData.flatMap { it.recipes },
            recipeToSource = recipeToSource
        )
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
