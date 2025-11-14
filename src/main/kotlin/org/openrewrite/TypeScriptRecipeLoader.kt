@file:Suppress("SENSELESS_COMPARISON")

package org.openrewrite

import org.openrewrite.config.RecipeDescriptor
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc
import java.net.URI
import java.nio.file.Path

/**
 * Loads TypeScript/JavaScript recipes via RPC by communicating with a Node.js process.
 * This allows the markdown generator to discover recipes written in TypeScript without
 * needing to parse TypeScript source files directly.
 */
class TypeScriptRecipeLoader(
    private val recipeOrigins: Map<URI, RecipeOrigin>,
    private val recipeInstallDir: Path? = null
) {

    companion object {
        /**
         * Registry mapping artifact IDs to their corresponding npm package names.
         * Add new TypeScript recipe modules here with a single line:
         * "artifact-id" to "npm-package-name"
         *
         * This is the single source of truth for TypeScript recipe module mappings.
         * Both recipe loading and documentation generation use this registry.
         */
        val TYPESCRIPT_RECIPE_MODULES = mapOf(
            "rewrite-javascript" to "@openrewrite/rewrite",
            "rewrite-nodejs" to "@openrewrite/recipes-nodejs"
            // "rewrite-react" to "@openrewrite/recipes-react"  // Not yet published - uncomment when available
        )
    }

    data class TypeScriptRecipeResult(
        val descriptors: List<RecipeDescriptor>,
        val recipeToSource: Map<String, URI>
    )

    /**
     * Detects which recipe modules contain TypeScript recipes by checking if they're
     * registered in the TYPESCRIPT_RECIPE_MODULES map.
     */
    private fun hasTypeScriptRecipes(origin: RecipeOrigin): Boolean {
        return origin.artifactId in TYPESCRIPT_RECIPE_MODULES
    }

    /**
     * Gets the npm package name for a given recipe origin.
     */
    private fun getNpmPackageName(origin: RecipeOrigin): String {
        return TYPESCRIPT_RECIPE_MODULES[origin.artifactId]
            ?: throw IllegalArgumentException("No npm package configured for artifact: ${origin.artifactId}")
    }

    /**
     * Attempts to map a recipe name to its TypeScript source file location.
     * This is used to generate GitHub links to the recipe source.
     *
     * Note: The exact file path mapping is complex because recipe names don't directly
     * correspond to file names. For now, we link to the GitHub search for the recipe name.
     */
    private fun mapRecipeToSourceUri(recipeName: String, origin: RecipeOrigin): URI {
        // For now, use a search-based URI that will be converted to a GitHub code search link
        // This ensures users can find the recipe even if our file path mapping is imperfect
        return URI.create("typescript-search://${origin.artifactId}/$recipeName")
    }

    /**
     * Loads TypeScript recipes from npm packages via RPC.
     *
     * @return A result containing the recipe descriptors and a mapping of recipe names to source URIs
     */
    fun loadTypeScriptRecipes(): TypeScriptRecipeResult {
        val typeScriptOrigins = recipeOrigins.values.filter { hasTypeScriptRecipes(it) }

        if (typeScriptOrigins.isEmpty()) {
            println("No TypeScript recipe modules detected.")
            return TypeScriptRecipeResult(emptyList(), emptyMap())
        }

        println("Found ${typeScriptOrigins.size} module(s) with TypeScript recipes: ${typeScriptOrigins.joinToString { it.artifactId }}")

        var rpc: JavaScriptRewriteRpc? = null
        try {
            // Build and start the RPC client
            val builder = JavaScriptRewriteRpc.builder()
            if (recipeInstallDir != null) {
                builder.recipeInstallDir(recipeInstallDir)
            }

            rpc = builder.get()
            println("Started Node.js RPC process for TypeScript recipe loading")

            val allDescriptors = mutableListOf<RecipeDescriptor>()
            val recipeToSource = mutableMapOf<String, URI>()

            // Install recipes from each npm package
            for (origin in typeScriptOrigins) {
                try {
                    val packageName = getNpmPackageName(origin)
                    println("Installing TypeScript recipes from npm package: $packageName@${origin.version}")

                    val count = rpc.installRecipes(packageName, origin.version)
                    println("  Installed $count recipe(s) from $packageName")

                } catch (e: Exception) {
                    System.err.println("Warning: Failed to install recipes from ${origin.artifactId}: ${e.message}")
                    e.printStackTrace()
                }
            }

            // Get all recipe descriptors from the RPC process
            val descriptors = rpc.recipes
            println("Retrieved ${descriptors.size} TypeScript recipe descriptor(s) via RPC")

            // Map each recipe to its source file location
            for (descriptor in descriptors) {
                allDescriptors.add(descriptor)

                // Debug: Print option types for recipes with options
                // Remove this once the type checking is fixed: https://github.com/openrewrite/rewrite/issues/6293
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
                val origin = typeScriptOrigins.firstOrNull { origin ->
                    descriptor.name.startsWith("org.openrewrite.${origin.artifactId.removePrefix("rewrite-")}")
                } ?: typeScriptOrigins.first()

                val sourceUri = mapRecipeToSourceUri(descriptor.name, origin)
                recipeToSource[descriptor.name] = sourceUri
            }

            return TypeScriptRecipeResult(allDescriptors, recipeToSource)

        } catch (e: Exception) {
            System.err.println("Error loading TypeScript recipes: ${e.message}")
            e.printStackTrace()
            return TypeScriptRecipeResult(emptyList(), emptyMap())
        } finally {
            // Cleanup: shutdown the RPC process
            rpc?.shutdown()
            println("Shut down Node.js RPC process")
        }
    }
}
