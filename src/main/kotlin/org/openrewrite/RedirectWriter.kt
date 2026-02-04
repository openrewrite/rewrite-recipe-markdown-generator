package org.openrewrite

import org.openrewrite.RecipeMarkdownGenerator.Companion.getRecipePath
import org.openrewrite.config.RecipeDescriptor
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Generates HTML redirect pages for proprietary recipes.
 *
 * Since Docusaurus's redirect plugin only supports internal redirects, we generate
 * actual HTML files that perform client-side redirects to Moderne docs.
 * These files should be placed in the `static/` directory of the docs site.
 */
object RedirectWriter {

    /**
     * Writes HTML redirect pages for proprietary recipes.
     *
     * Each redirect page uses both meta refresh and JavaScript redirect for maximum compatibility.
     * The generated files should be copied to `static/recipes/` in the docs site.
     *
     * @param outputPath Base output directory where redirect HTML files will be written
     * @param proprietaryRecipes List of proprietary recipe descriptors that need redirects
     * @param moderneBaseUrl Base URL for Moderne docs (e.g., "https://docs.moderne.io")
     * @param moderneLinkBasePath Base path for recipe links on Moderne (e.g., "/user-documentation/recipes/recipe-catalog")
     */
    fun writeRedirectConfig(
        outputPath: Path,
        proprietaryRecipes: List<RecipeDescriptor>,
        moderneBaseUrl: String,
        moderneLinkBasePath: String
    ) {
        if (proprietaryRecipes.isEmpty()) {
            return
        }

        // Create the static/recipes directory for redirect HTML files
        val staticRecipesPath = outputPath.resolve("static/recipes")
        Files.createDirectories(staticRecipesPath)

        var successCount = 0
        for (recipe in proprietaryRecipes) {
            try {
                val recipePath = getRecipePath(recipe)
                val targetUrl = "$moderneBaseUrl$moderneLinkBasePath/$recipePath"

                // Create the directory structure for this recipe
                // Use index.html in a directory for clean URLs (e.g., /recipes/ai/findagentsinuse/ -> index.html)
                val redirectFilePath = staticRecipesPath.resolve("$recipePath/index.html")
                Files.createDirectories(redirectFilePath.parent)

                // Generate HTML redirect page
                val htmlContent = generateRedirectHtml(recipe.displayName, targetUrl)
                Files.writeString(redirectFilePath, htmlContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
                successCount++
            } catch (e: Exception) {
                System.err.println("Warning: Could not generate redirect for recipe ${recipe.name}: ${e.message}")
            }
        }

        println("Wrote $successCount redirect page(s) to $staticRecipesPath")
    }

    /**
     * Generates an HTML page that redirects to the target URL.
     * Uses both meta refresh (for browsers without JS) and JavaScript redirect.
     */
    private fun generateRedirectHtml(recipeDisplayName: String, targetUrl: String): String {
        return """
            |<!DOCTYPE html>
            |<html lang="en">
            |<head>
            |    <meta charset="UTF-8">
            |    <meta http-equiv="refresh" content="0; url=$targetUrl">
            |    <link rel="canonical" href="$targetUrl">
            |    <title>Redirecting to $recipeDisplayName</title>
            |    <script>window.location.replace("$targetUrl");</script>
            |</head>
            |<body>
            |    <p>This recipe has moved to Moderne docs.</p>
            |    <p>If you are not redirected automatically, <a href="$targetUrl">click here</a>.</p>
            |</body>
            |</html>
        """.trimMargin()
    }
}
