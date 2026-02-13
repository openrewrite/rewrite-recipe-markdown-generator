@file:Suppress("SENSELESS_COMPARISON")

package org.openrewrite

import org.openrewrite.config.RecipeDescriptor
import org.openrewrite.marketplace.RecipeBundle
import org.openrewrite.python.rpc.PythonRewriteRpc
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

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

        /**
         * Group IDs for Python recipe modules, used when creating synthetic origins.
         */
        private val PYTHON_GROUP_IDS = mapOf(
            "rewrite-python" to "org.openrewrite",
            "rewrite-migrate-python" to "org.openrewrite.recipe"
        )

        /**
         * Repository URLs for Python recipe modules, used when creating synthetic origins.
         */
        private val PYTHON_REPO_URLS = mapOf(
            "rewrite-python" to "https://github.com/openrewrite/rewrite/blob/main/",
            "rewrite-migrate-python" to "https://github.com/moderneinc/rewrite-migrate-python/blob/main/"
        )
    }

    data class PythonRecipeResult(
        val descriptors: List<RecipeDescriptor>,
        val recipeToSource: Map<String, URI>,
        val syntheticOrigins: Map<URI, RecipeOrigin> = emptyMap()
    )

    /**
     * Gets the pip package name for a given artifact ID.
     */
    private fun getPipPackageName(artifactId: String): String {
        return PYTHON_RECIPE_MODULES[artifactId]
            ?: throw IllegalArgumentException("No pip package configured for artifact: $artifactId")
    }

    private fun mapRecipeToSourceUri(recipeName: String, artifactId: String): URI {
        return URI.create("python-search://$artifactId/$recipeName")
    }

    /**
     * Loads Python recipes from pip packages via RPC.
     *
     * Discovers pip packages from PYTHON_RECIPE_MODULES, using version info from
     * recipeOrigins when available. Packages without a Maven artifact are still
     * loaded (with the latest pip version).
     *
     * @return A result containing the recipe descriptors and a mapping of recipe names to source URIs
     */
    fun loadPythonRecipes(): PythonRecipeResult {
        if (PYTHON_RECIPE_MODULES.isEmpty()) {
            println("No Python recipe modules configured.")
            return PythonRecipeResult(emptyList(), emptyMap())
        }

        // Build list of packages to load, using version from recipeOrigins when available
        data class PipPackageInfo(val artifactId: String, val pipPackageName: String, val version: String?)

        val packagesToLoad = PYTHON_RECIPE_MODULES.map { (artifactId, pipPackage) ->
            val origin = recipeOrigins.values.firstOrNull { it.artifactId == artifactId }
            PipPackageInfo(artifactId, pipPackage, origin?.version)
        }

        println("Found ${packagesToLoad.size} Python recipe module(s): ${packagesToLoad.joinToString { it.artifactId }}")

        var rpc: PythonRewriteRpc? = null
        try {
            // Build and start the RPC client
            val builder = PythonRewriteRpc.builder()
            val effectivePipPath = pipPackagesPath ?: Files.createTempDirectory("rewrite-python-packages")
            builder.pipPackagesPath(effectivePipPath)

            // Pre-install recipe pip packages to the target directory. bootstrapOpenrewrite() only
            // installs the core 'openrewrite' package; additional recipe packages must be installed
            // separately so the RPC server can discover their entry points.
            for (pkg in packagesToLoad) {
                if (pkg.pipPackageName == "openrewrite") continue
                val targetDir = effectivePipPath.toAbsolutePath().normalize().toString()
                val versionSpec = if (pkg.version != null) "${pkg.pipPackageName}==${pkg.version}" else pkg.pipPackageName
                println("Pre-installing pip package: $versionSpec")
                val proc = ProcessBuilder("python3", "-m", "pip", "install", "--target=$targetDir", versionSpec)
                    .redirectErrorStream(true)
                    .start()
                val output = proc.inputStream.bufferedReader().readText()
                if (!proc.waitFor(2, TimeUnit.MINUTES)) {
                    proc.destroyForcibly()
                    System.err.println("Warning: Timed out installing $versionSpec")
                } else if (proc.exitValue() != 0) {
                    System.err.println("Warning: Failed to pip install $versionSpec (exit code ${proc.exitValue()})")
                    System.err.println(output)
                }
            }

            rpc = builder.get()
            println("Started Python RPC process for Python recipe loading")

            val allDescriptors = mutableListOf<RecipeDescriptor>()
            val recipeToSource = mutableMapOf<String, URI>()

            // Install and collect recipes from each pip package, tracking which package each recipe came from
            for (pkg in packagesToLoad) {
                try {
                    val versionLabel = pkg.version ?: "latest"
                    println("Installing Python recipes from pip package: ${pkg.pipPackageName}@$versionLabel")

                    val response = rpc.installRecipes(pkg.pipPackageName, pkg.version ?: "")
                    println("  Installed ${response.recipesInstalled} recipe(s) from ${pkg.pipPackageName}")

                    val descriptors = rpc.getMarketplace(RecipeBundle("pip", pkg.pipPackageName, null, null, null))
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

                    // getMarketplace is accumulative, so only take recipes not already seen
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

            // Get all recipe descriptors from the RPC process
            println("Retrieved ${allDescriptors.size} Python recipe descriptor(s) via RPC")

            // Create synthetic origins for modules not already in recipeOrigins
            val syntheticOrigins = mutableMapOf<URI, RecipeOrigin>()
            for (pkg in packagesToLoad) {
                if (recipeOrigins.values.none { it.artifactId == pkg.artifactId }) {
                    val syntheticUri = URI.create("python-search://${pkg.artifactId}")
                    val groupId = PYTHON_GROUP_IDS[pkg.artifactId] ?: "org.openrewrite.recipe"
                    val origin = RecipeOrigin(groupId, pkg.artifactId, pkg.version ?: "latest", syntheticUri)
                    origin.repositoryUrl = PYTHON_REPO_URLS[pkg.artifactId] ?: ""
                    origin.license = Licenses.Proprietary
                    syntheticOrigins[syntheticUri] = origin
                    println("Created synthetic origin for ${pkg.artifactId} (not available as Maven artifact)")
                }
            }

            return PythonRecipeResult(allDescriptors, recipeToSource, syntheticOrigins)

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
