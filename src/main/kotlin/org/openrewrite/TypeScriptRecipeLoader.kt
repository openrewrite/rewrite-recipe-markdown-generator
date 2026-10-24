@file:Suppress("SENSELESS_COMPARISON")

package org.openrewrite

import org.openrewrite.config.RecipeDescriptor
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc
import org.openrewrite.marketplace.RecipeBundle
import java.net.URI
import java.nio.file.Files
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
            "rewrite-nodejs" to "@openrewrite/recipes-nodejs",
            "rewrite-angular" to "@openrewrite/recipes-angular",
            "rewrite-react" to "@openrewrite/recipes-react",
            "recipes-javascript-code-quality" to "@openrewrite/recipes-code-quality",
            "recipes-javascript-date-libraries" to "@openrewrite/recipes-date-libraries"
        )

        // moderneinc/recipes-javascript publishes these proprietary modules to npm alone, with no Maven
        // artifact; their coordinates only shape the synthesized origin, and are distinct from the C#
        // `recipes-code-quality`.
        private val NPM_ONLY_MODULES = setOf(
            "recipes-javascript-code-quality",
            "recipes-javascript-date-libraries"
        )
        private const val NPM_ONLY_GROUP_ID = "io.moderne.recipe"
        private const val NPM_ONLY_REPO_URL = "https://github.com/moderneinc/recipes-javascript/blob/main/"

        internal fun isNpmOnly(artifactId: String) = artifactId in NPM_ONLY_MODULES

        /**
         * Build [RecipeOrigin]s for the npm-only TypeScript recipe modules, with each package's version
         * resolved from [CodeGenomeNpm], so they're loaded and listed in the version table and
         * `npm install` command like the modules that have a Maven artifact to anchor them.
         *
         * @param artifactIds when non-empty, restricts to these artifactIds (mirrors `--only-artifacts`
         *   so local runs don't make needless registry requests).
         */
        @JvmStatic
        fun buildVersionAnchorOrigins(
            artifactIds: Set<String> = emptySet(),
            latestVersion: (String) -> String? = CodeGenomeNpm::latestVersion
        ): Map<URI, RecipeOrigin> {
            val origins = mutableMapOf<URI, RecipeOrigin>()
            for (artifactId in NPM_ONLY_MODULES) {
                if (artifactIds.isNotEmpty() && artifactId !in artifactIds) continue
                val npmPackage = TYPESCRIPT_RECIPE_MODULES.getValue(artifactId)
                val version = latestVersion(npmPackage)
                if (version == null) {
                    System.err.println("Skipping TypeScript version anchor for $artifactId: could not resolve the latest version of $npmPackage from the Code Genome Project")
                    continue
                }
                val uri = URI.create("typescript-search://$artifactId")
                val origin = RecipeOrigin(NPM_ONLY_GROUP_ID, artifactId, version, uri)
                origin.repositoryUrl = NPM_ONLY_REPO_URL
                origin.license = Licenses.Proprietary
                origins[uri] = origin
            }
            return origins
        }
    }

    data class TypeScriptRecipeResult(
        val descriptors: List<RecipeDescriptor>,
        val recipeToSource: Map<String, URI>
    )

    /**
     * The [TYPESCRIPT_RECIPE_MODULES] this run loads. Unlike the other RPC-backed languages, these
     * are discovered from [recipeOrigins], which `--only-artifacts` has already narrowed.
     */
    val configuredModules: List<RecipeOrigin> = recipeOrigins.values.filter(::hasTypeScriptRecipes)

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
        val typeScriptOrigins = configuredModules

        if (typeScriptOrigins.isEmpty()) {
            println("No TypeScript recipe modules detected.")
            return TypeScriptRecipeResult(emptyList(), emptyMap())
        }

        println("Found ${typeScriptOrigins.size} module(s) with TypeScript recipes: ${typeScriptOrigins.joinToString { it.artifactId }}")

        // An install dir of its own, so the Code Genome Project credentials in its .npmrc never land in
        // the working directory, where the RPC would install by default.
        val installDir = recipeInstallDir ?: Files.createTempDirectory("typescript-recipes")
        val npmrc = CodeGenomeNpm.writeNpmrc(installDir)
        var rpc: JavaScriptRewriteRpc? = null
        try {
            // Build and start the RPC client
            val builder = JavaScriptRewriteRpc.builder()
            builder.recipeInstallDir(installDir)

            rpc = builder.get()
            println("Started Node.js RPC process for TypeScript recipe loading")

            val allDescriptors = mutableListOf<RecipeDescriptor>()
            val recipeToSource = mutableMapOf<String, URI>()

            // Install recipes from each npm package, tracking which origin each recipe came from
            for (origin in typeScriptOrigins) {
                try {
                    val packageName = getNpmPackageName(origin)
                    println("Installing TypeScript recipes from npm package: $packageName@${origin.version}")

                    val versionSpec = if (isNpmOnly(origin.artifactId)) {
                        CodeGenomeNpm.tarballUrl(packageName, origin.version)
                    } else {
                        origin.version
                    }
                    val count = rpc.installRecipes(packageName, versionSpec)
                    println("  Installed $count recipe(s) from $packageName")

                    val descriptors = rpc.getMarketplace(RecipeBundle("npm", packageName, null, null, null))
                        .allRecipes
                        .mapNotNull { r ->
                            try {
                                val requiredOptions = r.options
                                    .filter { it.isRequired }
                                    .associate { it.name to "PlaceholderValueToFoolValidation" }
                                rpc.prepareRecipe(r.name, requiredOptions).descriptor
                            } catch (e: Exception) {
                                System.err.println("Warning: Failed to prepare recipe ${r.name}: ${e.message}")
                                null
                            }
                        }

                    // getMarketplace is accumulative, so only take recipes not already seen
                    val newDescriptors = descriptors.filter { it.name !in recipeToSource }
                    for (descriptor in newDescriptors) {
                        val sourceUri = mapRecipeToSourceUri(descriptor.name, origin)
                        recipeToSource[descriptor.name] = sourceUri
                    }

                    allDescriptors.addAll(newDescriptors)

                } catch (e: Exception) {
                    System.err.println("Warning: Failed to install recipes from ${origin.artifactId}: ${e.message}")
                    e.printStackTrace()
                }
            }

            // Get all recipe descriptors from the RPC process
            println("Retrieved ${allDescriptors.size} TypeScript recipe descriptor(s) via RPC")

            return TypeScriptRecipeResult(allDescriptors, recipeToSource)

        } catch (e: Exception) {
            throw RuntimeException(
                "Failed to load TypeScript recipes. Ensure Node.js is installed and available on PATH.", e
            )
        } finally {
            // Cleanup: shutdown the RPC process
            rpc?.shutdown()
            println("Shut down Node.js RPC process")
            npmrc?.let(Files::deleteIfExists)
        }
    }
}
