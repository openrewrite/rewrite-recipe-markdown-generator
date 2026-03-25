@file:Suppress("SENSELESS_COMPARISON")

package org.openrewrite

import org.openrewrite.config.RecipeDescriptor
import org.openrewrite.csharp.rpc.CSharpRewriteRpc
import org.openrewrite.marketplace.RecipeBundle
import java.net.URI

/**
 * Loads C# recipes via RPC by communicating with a .NET process.
 * This allows the markdown generator to discover recipes written in C# without
 * needing to parse C# source files directly.
 */
class CSharpRecipeLoader(
    private val recipeOrigins: Map<URI, RecipeOrigin>
) {

    companion object {
        /**
         * Registry mapping artifact IDs to their corresponding NuGet package names.
         */
        val CSHARP_RECIPE_MODULES = mapOf(
            "recipes-code-quality" to "OpenRewrite.CodeQuality",
            "recipes-migrate-dotnet" to "OpenRewrite.MigrateDotNet",
            "recipes-tunit" to "OpenRewrite.TUnit"
        )

        /**
         * Group IDs for C# recipe modules, used when creating synthetic origins.
         */
        private val CSHARP_GROUP_IDS = mapOf(
            "recipes-code-quality" to "io.moderne.recipe",
            "recipes-migrate-dotnet" to "io.moderne.recipe",
            "recipes-tunit" to "io.moderne.recipe"
        )

        /**
         * Repository URLs for C# recipe modules, used when creating synthetic origins.
         */
        private val CSHARP_REPO_URLS = mapOf(
            "recipes-code-quality" to "https://github.com/moderneinc/recipes-csharp/blob/main/",
            "recipes-migrate-dotnet" to "https://github.com/moderneinc/recipes-csharp/blob/main/",
            "recipes-tunit" to "https://github.com/moderneinc/recipes-csharp/blob/main/"
        )
    }

    data class CSharpRecipeResult(
        val descriptors: List<RecipeDescriptor>,
        val recipeToSource: Map<String, URI>,
        val syntheticOrigins: Map<URI, RecipeOrigin> = emptyMap()
    )

    private fun mapRecipeToSourceUri(recipeName: String, artifactId: String): URI {
        return URI.create("csharp-search://$artifactId/$recipeName")
    }

    /**
     * Returns a type-appropriate placeholder value for a required recipe option.
     * The C# RPC server performs strict type conversion, so string placeholders
     * fail for numeric/boolean option types.
     */
    private fun placeholderForType(type: String?): Any {
        return when (type?.lowercase()) {
            "int", "int32", "int64", "long", "short", "byte" -> 0
            "float", "double", "decimal" -> 0.0
            "bool", "boolean" -> false
            else -> "PlaceholderValueToFoolValidation"
        }
    }

    /**
     * Loads C# recipes from NuGet packages via RPC.
     */
    fun loadCSharpRecipes(): CSharpRecipeResult {
        if (CSHARP_RECIPE_MODULES.isEmpty()) {
            println("No C# recipe modules configured.")
            return CSharpRecipeResult(emptyList(), emptyMap())
        }

        data class NuGetPackageInfo(val artifactId: String, val nugetPackageName: String, val version: String?)

        val packagesToLoad = CSHARP_RECIPE_MODULES.map { (artifactId, nugetPackage) ->
            val origin = recipeOrigins.values.firstOrNull { it.artifactId == artifactId }
            NuGetPackageInfo(artifactId, nugetPackage, origin?.version)
        }

        println("Found ${packagesToLoad.size} C# recipe module(s): ${packagesToLoad.joinToString { it.artifactId }}")

        var rpc: CSharpRewriteRpc? = null
        try {
            rpc = CSharpRewriteRpc.builder().get()
            println("Started .NET RPC process for C# recipe loading")

            val allDescriptors = mutableListOf<RecipeDescriptor>()
            val recipeToSource = mutableMapOf<String, URI>()

            for (pkg in packagesToLoad) {
                try {
                    val versionLabel = pkg.version ?: "latest"
                    println("Installing C# recipes from NuGet package: ${pkg.nugetPackageName}@$versionLabel")

                    val response = if (pkg.version != null) {
                        rpc.installRecipes(pkg.nugetPackageName, pkg.version)
                    } else {
                        rpc.installRecipes(pkg.nugetPackageName)
                    }
                    println("  Installed ${response.recipesInstalled} recipe(s) from ${pkg.nugetPackageName}")

                    val descriptors = rpc.getMarketplace(RecipeBundle("nuget", pkg.nugetPackageName, null, null, null))
                        .allRecipes
                        .mapNotNull { r ->
                            try {
                                val requiredOptions = r.options
                                    .filter { it.isRequired }
                                    .associate { it.name to placeholderForType(it.type) }
                                rpc.prepareRecipe(r.name, requiredOptions).descriptor
                            } catch (e: Exception) {
                                System.err.println("Warning: Failed to prepare recipe ${r.name}: ${e.message}")
                                null
                            }
                        }

                    val newDescriptors = descriptors.filter { it.name !in recipeToSource }
                    for (descriptor in newDescriptors) {
                        val sourceUri = mapRecipeToSourceUri(descriptor.name, pkg.artifactId)
                        recipeToSource[descriptor.name] = sourceUri
                    }
                    allDescriptors.addAll(newDescriptors)

                } catch (e: Exception) {
                    System.err.println("Warning: Failed to install recipes from ${pkg.artifactId}: ${e.message}")
                    e.printStackTrace()
                }
            }

            println("Retrieved ${allDescriptors.size} C# recipe descriptor(s) via RPC")

            // Create synthetic origins for modules not already in recipeOrigins
            val syntheticOrigins = mutableMapOf<URI, RecipeOrigin>()
            for (pkg in packagesToLoad) {
                if (recipeOrigins.values.any { it.artifactId == pkg.artifactId }) continue
                val syntheticUri = URI.create("csharp-search://${pkg.artifactId}")
                val groupId = CSHARP_GROUP_IDS[pkg.artifactId] ?: "io.moderne.recipe"
                val origin = RecipeOrigin(groupId, pkg.artifactId, pkg.version ?: "latest", syntheticUri)
                origin.repositoryUrl = CSHARP_REPO_URLS[pkg.artifactId] ?: ""
                origin.license = Licenses.Proprietary
                syntheticOrigins[syntheticUri] = origin
                println("Created synthetic origin for ${pkg.artifactId} (NuGet-only package)")
            }

            return CSharpRecipeResult(allDescriptors, recipeToSource, syntheticOrigins)

        } catch (e: Exception) {
            System.err.println("WARNING: Failed to load C# recipes. Ensure .NET SDK is installed and available on PATH.")
            System.err.println("  ${e.message}")
            return CSharpRecipeResult(emptyList(), emptyMap())
        } finally {
            rpc?.shutdown()
            println("Shut down .NET RPC process")
        }
    }
}
