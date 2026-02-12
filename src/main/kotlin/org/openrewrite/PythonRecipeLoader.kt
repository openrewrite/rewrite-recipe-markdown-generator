@file:Suppress("SENSELESS_COMPARISON")

package org.openrewrite

import org.openrewrite.config.RecipeDescriptor
import org.openrewrite.python.rpc.PythonRewriteRpc
import org.openrewrite.marketplace.RecipeBundle
import java.net.URI
import java.nio.file.Path

/**
 * Loads Python recipes via RPC by communicating with a Python process.
 * This allows the markdown generator to discover recipes written in Python without
 * needing to parse Python source files directly.
 */
class PythonRecipeLoader(
    private val recipeOrigins: Map<URI, RecipeOrigin>,
    private val pipPackagesPath: Path? = null
) {

    companion object {
        /**
         * Registry mapping artifact IDs to their corresponding pip package names.
         * Add new Python recipe modules here with a single line:
         * "artifact-id" to "pip-package-name"
         *
         * This is the single source of truth for Python recipe module mappings.
         * Both recipe loading and documentation generation use this registry.
         */
        val PYTHON_RECIPE_MODULES = mapOf(
            "rewrite-python" to "openrewrite",
            "rewrite-migrate-python" to "openrewrite-migrate-python"
        )
    }

    data class PythonRecipeResult(
        val descriptors: List<RecipeDescriptor>,
        val recipeToSource: Map<String, URI>
    )

    /**
     * Detects which recipe modules contain Python recipes by checking if they're
     * registered in the PYTHON_RECIPE_MODULES map.
     */
    private fun hasPythonRecipes(origin: RecipeOrigin): Boolean {
        return origin.artifactId in PYTHON_RECIPE_MODULES
    }

    /**
     * Gets the pip package name for a given recipe origin.
     */
    private fun getPipPackageName(origin: RecipeOrigin): String {
        return PYTHON_RECIPE_MODULES[origin.artifactId]
            ?: throw IllegalArgumentException("No pip package configured for artifact: ${origin.artifactId}")
    }

    /**
     * Attempts to map a recipe name to its Python source file location.
     * This is used to generate GitHub links to the recipe source.
     *
     * Note: The exact file path mapping is complex because recipe names don't directly
     * correspond to file names. For now, we link to the GitHub search for the recipe name.
     */
    private fun mapRecipeToSourceUri(recipeName: String, origin: RecipeOrigin): URI {
        // For now, use a search-based URI that will be converted to a GitHub code search link
        // This ensures users can find the recipe even if our file path mapping is imperfect
        return URI.create("python-search://${origin.artifactId}/$recipeName")
    }

    /**
     * Loads Python recipes from pip packages via RPC.
     *
     * @return A result containing the recipe descriptors and a mapping of recipe names to source URIs
     */
    fun loadPythonRecipes(): PythonRecipeResult {
        val pythonOrigins = recipeOrigins.values.filter { hasPythonRecipes(it) }

        if (pythonOrigins.isEmpty()) {
            println("No Python recipe modules detected.")
            return PythonRecipeResult(emptyList(), emptyMap())
        }

        println("Found ${pythonOrigins.size} module(s) with Python recipes: ${pythonOrigins.joinToString { it.artifactId }}")

        var rpc: PythonRewriteRpc? = null
        try {
            // Build and start the RPC client
            val builder = PythonRewriteRpc.builder()
            if (pipPackagesPath != null) {
                builder.pipPackagesPath(pipPackagesPath)
            }

            rpc = builder.get()
            println("Started Python RPC process for Python recipe loading")

            val allDescriptors = mutableListOf<RecipeDescriptor>()
            val recipeToSource = mutableMapOf<String, URI>()

            // Install recipes from each pip package
            for (origin in pythonOrigins) {
                try {
                    val packageName = getPipPackageName(origin)
                    println("Installing Python recipes from pip package: $packageName@${origin.version}")

                    val response = rpc.installRecipes(packageName, origin.version)
                    println("  Installed ${response.recipesInstalled} recipe(s) from $packageName")

                    allDescriptors.addAll(
                        rpc.getMarketplace(RecipeBundle("pip", packageName, null, null, null))
                            .allRecipes
                            .mapNotNull { r ->
                                try {
                                    val requiredOptions = r.options
                                        ?.filter { it.isRequired }
                                        ?.associate { it.name to "PlaceholderValueToFoolValidation" }
                                        ?: emptyMap()
                                    rpc.prepareRecipe(r.name, requiredOptions).descriptor
                                } catch (e: Exception) {
                                    System.err.println("Warning: Failed to prepare recipe ${r.name}: ${e.message}")
                                    null
                                }
                            }
                    )

                } catch (e: Exception) {
                    System.err.println("Warning: Failed to install recipes from ${origin.artifactId}: ${e.message}")
                    e.printStackTrace()
                }
            }

            // Get all recipe descriptors from the RPC process
            println("Retrieved ${allDescriptors.size} Python recipe descriptor(s) via RPC")

            // Map each recipe to its source file location
            for (descriptor in allDescriptors) {
                if (descriptor.options != null && descriptor.options.isNotEmpty()) {
                    println("  Recipe: ${descriptor.name}")
                    for (option in descriptor.options) {
                        println("    Option name: ${option.name}")
                        println("      type: ${option.type}")
                        println("      description: ${option.description}")
                        println("      example: ${option.example}")
                        println("      required: ${option.isRequired}")
                    }
                }

                // Find the origin this recipe belongs to
                val origin = pythonOrigins.firstOrNull { origin ->
                    descriptor.name.startsWith("org.openrewrite.${origin.artifactId.removePrefix("rewrite-")}")
                } ?: pythonOrigins.first()

                val sourceUri = mapRecipeToSourceUri(descriptor.name, origin)
                recipeToSource[descriptor.name] = sourceUri
            }

            return PythonRecipeResult(allDescriptors, recipeToSource)

        } catch (e: Exception) {
            System.err.println("Error loading Python recipes: ${e.message}")
            e.printStackTrace()
            return PythonRecipeResult(emptyList(), emptyMap())
        } finally {
            // Cleanup: shutdown the RPC process
            rpc?.shutdown()
            println("Shut down Python RPC process")
        }
    }
}
