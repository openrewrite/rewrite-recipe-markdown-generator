@file:Suppress("SENSELESS_COMPARISON")

package org.openrewrite

import org.openrewrite.config.RecipeDescriptor
import org.openrewrite.golang.rpc.GoRewriteRpc
import org.openrewrite.marketplace.RecipeBundle
import org.openrewrite.marketplace.RecipeBundleReader
import org.openrewrite.marketplace.RecipeBundleResolver
import org.openrewrite.marketplace.RecipeListing
import org.openrewrite.marketplace.RecipeMarketplace
import java.net.URI

/**
 * Loads Go recipes via RPC from a `rewrite-go-rpc` (Go) process: start the server, install the Go
 * recipe module, then enumerate descriptors. Mirrors [CSharpRecipeLoader] — because Go recipes can
 * delegate to Java recipes, the RPC is seeded with a marketplace of the Java descriptors (and a
 * classpath resolver) so `prepareRecipe` can resolve them.
 *
 * Requires a Go toolchain and the `rewrite-go-rpc` server on `PATH`
 * (built from github.com/openrewrite/rewrite/rewrite-go/cmd/rpc).
 */
class GoRecipeLoader(
    private val recipeOrigins: Map<URI, RecipeOrigin>,
    private val javaDescriptors: Collection<RecipeDescriptor> = emptyList(),
    private val classloader: ClassLoader = GoRecipeLoader::class.java.classLoader
) {

    companion object {
        /** Artifact ID -> Go module path. Add new Go recipe modules here. */
        val GO_RECIPE_MODULES = mapOf(
            "recipes-go" to "github.com/moderneinc/recipes-go"
        )

        // Used only to synthesize an origin if a Go module ever lacks a Maven artifact (recipes-go has one).
        private val GO_GROUP_IDS = mapOf(
            "recipes-go" to "org.openrewrite.recipe"
        )
        private val GO_REPO_URLS = mapOf(
            "recipes-go" to "https://github.com/moderneinc/recipes-go/blob/main/"
        )

        private val CLASSPATH_BUNDLE = RecipeBundle("classpath", "java-recipes", null, null, null)

        /** Marketplace of Java descriptors so Go recipes that delegate to Java resolve in [GoRewriteRpc.prepareRecipe]. */
        @JvmStatic
        fun buildMarketplace(descriptors: Collection<RecipeDescriptor>): RecipeMarketplace {
            val marketplace = RecipeMarketplace()
            for (descriptor in descriptors) {
                marketplace.install(RecipeListing.fromDescriptor(descriptor, CLASSPATH_BUNDLE), emptyList())
            }
            return marketplace
        }
    }

    data class GoRecipeResult(
        val descriptors: List<RecipeDescriptor>,
        val recipeToSource: Map<String, URI>,
        val syntheticOrigins: Map<URI, RecipeOrigin> = emptyMap()
    )

    private fun mapRecipeToSourceUri(recipeName: String, artifactId: String): URI {
        return URI.create("go-search://$artifactId/$recipeName")
    }

    /** Type-appropriate placeholder for a required option (the RPC may do strict type conversion). */
    private fun placeholderForType(type: String?): Any {
        return when (type?.lowercase()) {
            "int", "int32", "int64", "long", "short", "byte" -> 0
            "float", "double", "decimal" -> 0.0
            "bool", "boolean" -> false
            else -> "PlaceholderValueToFoolValidation"
        }
    }

    /** Resolves delegated-to Java recipes from the classpath. */
    private fun classpathResolver(): RecipeBundleResolver {
        return object : RecipeBundleResolver {
            override fun getEcosystem() = CLASSPATH_BUNDLE.packageEcosystem

            override fun resolve(bundle: RecipeBundle): RecipeBundleReader {
                val loader = org.openrewrite.internal.RecipeLoader(classloader)
                return object : RecipeBundleReader {
                    override fun getBundle() = bundle
                    override fun read() = RecipeMarketplace()
                    override fun describe(listing: RecipeListing) = loader.load(listing.name, emptyMap()).descriptor
                    override fun prepare(listing: RecipeListing, options: MutableMap<String, Any>) =
                        loader.load(listing.name, options)
                }
            }
        }
    }

    /** Loads Go recipes from their Go modules via RPC. */
    fun loadGoRecipes(): GoRecipeResult {
        if (GO_RECIPE_MODULES.isEmpty()) {
            println("No Go recipe modules configured.")
            return GoRecipeResult(emptyList(), emptyMap())
        }

        data class GoPackageInfo(val artifactId: String, val goModule: String, val version: String?)

        val packagesToLoad = GO_RECIPE_MODULES.map { (artifactId, goModule) ->
            val origin = recipeOrigins.values.firstOrNull { it.artifactId == artifactId }
            GoPackageInfo(artifactId, goModule, origin?.version)
        }

        println("Found ${packagesToLoad.size} Go recipe module(s): ${packagesToLoad.joinToString { it.artifactId }}")

        val marketplace = buildMarketplace(javaDescriptors)
        if (javaDescriptors.isNotEmpty()) {
            println("Registered ${javaDescriptors.size} Java recipe(s) in marketplace for delegatesTo resolution")
        }
        // A delegating Go recipe's prepareRecipe returns the Java descriptor; track Java names to re-attribute below.
        val javaRecipeNames = javaDescriptors.map { it.name }.toSet()

        var rpc: GoRewriteRpc? = null
        try {
            rpc = GoRewriteRpc.builder()
                .marketplace(marketplace)
                .resolvers(listOf(classpathResolver()))
                .get()
            println("Started Go RPC process for Go recipe loading")

            val allDescriptors = mutableListOf<RecipeDescriptor>()
            val recipeToSource = mutableMapOf<String, URI>()

            for (pkg in packagesToLoad) {
                try {
                    // Go module versions are tagged vX.Y.Z; the Maven origin version is X.Y.Z.
                    val goVersion = pkg.version?.let { if (it.startsWith("v")) it else "v$it" }
                    val versionLabel = goVersion ?: "latest"
                    println("Installing Go recipes from module: ${pkg.goModule}@$versionLabel")

                    val response = if (goVersion != null) {
                        rpc.installRecipes(pkg.goModule, goVersion)
                    } else {
                        rpc.installRecipes(pkg.goModule)
                    }
                    println("  Installed ${response.recipesInstalled} recipe(s) from ${pkg.goModule}")

                    val allListings = rpc.getMarketplace(RecipeBundle("go", pkg.goModule, null, null, null))
                        .allRecipes

                    val descriptors = allListings.mapNotNull { listing ->
                        // getMarketplace is accumulative; skip recipes already attributed.
                        if (listing.name in recipeToSource) return@mapNotNull null
                        try {
                            val requiredOptions = listing.options
                                .filter { it.isRequired }
                                .associate { it.name to placeholderForType(it.type) }
                            val descriptor = rpc.prepareRecipe(listing.name, requiredOptions).descriptor

                            // A delegatesTo-Java recipe returns the Java-named descriptor; rebuild it under
                            // the Go name so the Go recipe gets its own doc page.
                            if (descriptor.name != listing.name && descriptor.name in javaRecipeNames) {
                                RecipeDescriptor(
                                    listing.name,
                                    listing.displayName,
                                    listing.displayName,
                                    listing.description,
                                    emptySet(),
                                    null,
                                    listing.options,
                                    listOf(descriptor),
                                    emptyList(),
                                    emptyList(),
                                    emptyList(),
                                    emptyList(),
                                    emptyList(),
                                    mapRecipeToSourceUri(listing.name, pkg.artifactId)
                                )
                            } else {
                                descriptor
                            }
                        } catch (e: Exception) {
                            System.err.println("Warning: Failed to prepare recipe ${listing.name}: ${e.message}")
                            null
                        }
                    }

                    for (descriptor in descriptors) {
                        if (descriptor.name in javaRecipeNames) continue
                        recipeToSource[descriptor.name] = mapRecipeToSourceUri(descriptor.name, pkg.artifactId)
                    }
                    allDescriptors.addAll(descriptors.filter { it.name !in javaRecipeNames })

                } catch (e: Exception) {
                    System.err.println("Warning: Failed to load recipes from ${pkg.artifactId}: ${e.message}")
                    e.printStackTrace()
                }
            }

            println("Retrieved ${allDescriptors.size} Go recipe descriptor(s) via RPC")

            // Defensive: synthesize an origin only if a module lacks a Maven artifact (recipes-go has one).
            val syntheticOrigins = mutableMapOf<URI, RecipeOrigin>()
            for (pkg in packagesToLoad) {
                if (recipeOrigins.values.any { it.artifactId == pkg.artifactId }) continue
                val syntheticUri = URI.create("go-search://${pkg.artifactId}")
                val groupId = GO_GROUP_IDS[pkg.artifactId] ?: "org.openrewrite.recipe"
                val origin = RecipeOrigin(groupId, pkg.artifactId, pkg.version ?: "latest", syntheticUri)
                origin.repositoryUrl = GO_REPO_URLS[pkg.artifactId] ?: ""
                origin.license = Licenses.Proprietary
                syntheticOrigins[syntheticUri] = origin
                println("Created synthetic origin for ${pkg.artifactId} (Go-module-only package)")
            }

            return GoRecipeResult(allDescriptors, recipeToSource, syntheticOrigins)

        } catch (e: Exception) {
            System.err.println("WARNING: Failed to load Go recipes. Ensure Go 1.23+ and the rewrite-go-rpc server are installed and available on PATH.")
            System.err.println("  ${e.message}")
            return GoRecipeResult(emptyList(), emptyMap())
        } finally {
            rpc?.shutdown()
            println("Shut down Go RPC process")
        }
    }
}
