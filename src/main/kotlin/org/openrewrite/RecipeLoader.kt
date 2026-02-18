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
    val recipeToSource: Map<String, URI>,
    val additionalOrigins: Map<URI, RecipeOrigin> = emptyMap(),
    /** Maps recipe name → list of additional doc paths for cross-category placement (e.g., "python/changemethodname") */
    val crossCategoryPaths: Map<String, List<String>> = emptyMap()
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

        val allCrossCategoryPaths = mutableMapOf<String, MutableList<String>>()

        environmentData.forEach { envData ->
            // Scan YAML files and CSV files in this JAR
            val jarMetadata = scanJarMetadata(envData.sourceUri)

            envData.recipeDescriptors.forEach { descriptor ->
                val recipe = allRecipesByName[descriptor.name]

                // Check if this is a declarative (YAML) recipe
                if (recipe is DeclarativeRecipe) {
                    // Use the YAML file URI if we found it
                    val yamlFile = jarMetadata.yamlRecipeToFile[descriptor.name]
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

            // Accumulate cross-category paths from CSV
            jarMetadata.crossCategoryPaths.forEach { (recipeName, paths) ->
                allCrossCategoryPaths.computeIfAbsent(recipeName) { mutableListOf() }.addAll(paths)
            }
        }

        // Load TypeScript recipes via RPC
        println("\nChecking for TypeScript/JavaScript recipes...")
        val typeScriptLoader = TypeScriptRecipeLoader(recipeOrigins)
        val typeScriptResult = typeScriptLoader.loadTypeScriptRecipes()

        // Load Python recipes via RPC
        println("\nChecking for Python recipes...")
        val pythonLoader = PythonRecipeLoader(recipeOrigins)
        val pythonResult = pythonLoader.loadPythonRecipes()

        // Merge TypeScript and Python results with Java/YAML results
        val allDescriptors = environmentData.flatMap { it.recipeDescriptors }.toMutableList()
        allDescriptors.addAll(typeScriptResult.descriptors)
        recipeToSource.putAll(typeScriptResult.recipeToSource)
        allDescriptors.addAll(pythonResult.descriptors)
        recipeToSource.putAll(pythonResult.recipeToSource)

        // Deduplicate recipes by name (same recipe may be discovered from multiple JARs
        // when scanJar is called with the full classpath as dependencies)
        val deduplicatedDescriptors = allDescriptors.distinctBy { it.name }
        val duplicateCount = allDescriptors.size - deduplicatedDescriptors.size
        if (duplicateCount > 0) {
            println("Removed $duplicateCount duplicate recipe descriptor(s).")
        }

        if (allCrossCategoryPaths.isNotEmpty()) {
            println("Found cross-category placements for ${allCrossCategoryPaths.size} recipe(s).")
        }

        // Combine all results
        return RecipeLoadResult(
            allRecipeDescriptors = deduplicatedDescriptors,
            allCategoryDescriptors = environmentData.flatMap { it.categoryDescriptors }.distinctBy { it.packageName },
            allRecipes = environmentData.flatMap { it.recipes }.distinctBy { it.name },
            recipeToSource = recipeToSource,
            additionalOrigins = pythonResult.syntheticOrigins,
            crossCategoryPaths = allCrossCategoryPaths
        )
    }

    /**
     * Result of scanning a JAR's META-INF/rewrite directory for YAML recipes and CSV cross-category data
     */
    private data class JarMetadata(
        val yamlRecipeToFile: Map<String, URI>,
        val crossCategoryPaths: Map<String, List<String>>
    )

    /**
     * Scan a JAR's META-INF/rewrite directory for YAML recipe definitions and CSV cross-category data.
     * Opens the JAR filesystem once and processes both .yml and recipes.csv files.
     */
    private fun scanJarMetadata(jarUri: URI): JarMetadata {
        val recipeToYamlFile = mutableMapOf<String, URI>()
        val crossCategoryPaths = mutableMapOf<String, MutableList<String>>()

        try {
            val jarPath = Paths.get(jarUri)
            if (!Files.exists(jarPath)) {
                return JarMetadata(recipeToYamlFile, crossCategoryPaths)
            }

            FileSystems.newFileSystem(jarPath, null as ClassLoader?).use { fs ->
                val rewriteDir = fs.getPath("/META-INF/rewrite")
                if (Files.exists(rewriteDir)) {
                    Files.walk(rewriteDir)
                        .filter { Files.isRegularFile(it) }
                        .forEach { filePath ->
                            val fileName = filePath.toString()
                            when {
                                fileName.endsWith(".yml") -> {
                                    try {
                                        val yamlContent = Files.readString(filePath)
                                        val namePattern = Regex("""^\s*name:\s*(.+)$""", RegexOption.MULTILINE)
                                        namePattern.findAll(yamlContent).forEach { match ->
                                            val recipeName = match.groupValues[1].trim()
                                            val yamlFileUri = URI("jar:file:${jarPath.toAbsolutePath()}!${filePath}")
                                            recipeToYamlFile[recipeName] = yamlFileUri
                                        }
                                    } catch (e: Exception) {
                                        println("Warning: Could not parse YAML file ${filePath}: ${e.message}")
                                    }
                                }
                                fileName.endsWith("recipes.csv") -> {
                                    try {
                                        parseCsvForCrossCategories(filePath, crossCategoryPaths)
                                    } catch (e: Exception) {
                                        println("Warning: Could not parse CSV file ${filePath}: ${e.message}")
                                    }
                                }
                            }
                        }
                }
            }
        } catch (e: Exception) {
            println("Warning: Could not scan JAR $jarUri: ${e.message}")
        }

        return JarMetadata(recipeToYamlFile, crossCategoryPaths)
    }

    /**
     * Parse a recipes.csv file to find recipes with cross-category placements.
     * Recipes that appear multiple times with different category2 values get additional doc paths.
     */
    private fun parseCsvForCrossCategories(csvPath: Path, result: MutableMap<String, MutableList<String>>) {
        val lines = Files.readAllLines(csvPath)
        if (lines.size < 2) return

        val header = parseCsvLine(lines[0])
        val nameIdx = header.indexOf("name")
        val cat1Idx = header.indexOf("category1")
        val cat2Idx = header.indexOf("category2")

        if (nameIdx == -1 || cat2Idx == -1) return

        // Collect all (recipeName → set of category paths) from CSV rows
        val recipeCategoryPaths = mutableMapOf<String, MutableSet<String>>()

        for (i in 1 until lines.size) {
            val columns = parseCsvLine(lines[i])
            if (columns.size <= maxOf(nameIdx, cat2Idx)) continue

            val recipeName = columns[nameIdx].trim()
            val cat1 = if (cat1Idx != -1) columns[cat1Idx].trim() else ""
            val cat2 = columns[cat2Idx].trim()

            if (recipeName.isBlank() || cat2.isBlank()) continue

            // Build the category doc path from category2 (top-level) and category1 (sub-category)
            val recipeFileName = recipeName.substringAfterLast('.').lowercase()
            val categoryPath = buildCategoryPath(cat1, cat2, recipeFileName)

            recipeCategoryPaths.computeIfAbsent(recipeName) { mutableSetOf() }.add(categoryPath)
        }

        // For each recipe, determine the primary path (matches package-derived path) and collect additional ones
        for ((recipeName, paths) in recipeCategoryPaths) {
            if (paths.size <= 1) continue // Only one category = no cross-category placement

            val primaryPath = getPrimaryPathFromPackage(recipeName)
            val additionalPaths = paths.filter { it != primaryPath }

            if (additionalPaths.isNotEmpty()) {
                result.computeIfAbsent(recipeName) { mutableListOf() }.addAll(additionalPaths)
            }
        }
    }

    /**
     * Build a doc path from CSV category columns and recipe filename.
     * E.g., cat2="Python", cat1="Search", filename="findmethods" → "python/search/findmethods"
     */
    private fun buildCategoryPath(cat1: String, cat2: String, recipeFileName: String): String {
        val parts = mutableListOf<String>()
        if (cat2.isNotBlank()) parts.add(cat2.lowercase())
        if (cat1.isNotBlank()) parts.add(cat1.lowercase())
        parts.add(recipeFileName)
        return parts.joinToString("/")
    }

    /**
     * Derive the primary doc path from a recipe's package name
     * (mirrors the logic in RecipeMarkdownGenerator.getBasePath)
     */
    private fun getPrimaryPathFromPackage(recipeName: String): String {
        return when {
            recipeName.startsWith("org.openrewrite") -> {
                if (recipeName.count { it == '.' } == 2) {
                    "core/" + recipeName.substring(16).lowercase()
                } else {
                    recipeName.substring(16).replace(".", "/").lowercase()
                }
            }
            recipeName.startsWith("io.moderne") -> {
                recipeName.substring(11).replace(".", "/").lowercase()
            }
            else -> {
                recipeName.replace(".", "/").lowercase()
            }
        }
    }

    /**
     * Parse a single CSV line, handling double-quoted fields that may contain commas.
     */
    private fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    // Escaped double quote inside quoted field
                    current.append('"')
                    i += 2
                    continue
                }
                c == '"' -> {
                    inQuotes = !inQuotes
                }
                c == ',' && !inQuotes -> {
                    fields.add(current.toString())
                    current.clear()
                }
                else -> {
                    current.append(c)
                }
            }
            i++
        }
        fields.add(current.toString())
        return fields
    }

    /**
     * Process recipe jars in parallel and collect both descriptors and recipes
     */
    private fun loadEnvironmentDataAsync(): List<EnvironmentData> = runBlocking {
        println("Starting parallel recipe loading...")
        recipeOrigins.entries
            .chunked(2)
            .flatMap { batch -> batch.map { recipeOrigin ->
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
            }.awaitAll() }
            .also {
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
                            attr.getValue("License-Url"),
                            attr.getValue("License-Name")
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
            if (license != null && license != Licenses.Unknown) {
                origin.license = license
            } else {
                println("Unable to determine License for ${origin}")
            }
            origin.repositoryUrl = mfInfos[uri]?.second ?: ""
        }
    }
}
