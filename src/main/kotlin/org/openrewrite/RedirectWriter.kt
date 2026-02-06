package org.openrewrite

import org.openrewrite.RecipeMarkdownGenerator.Companion.getRecipePath
import org.openrewrite.config.RecipeDescriptor
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Generates HTML redirect pages for proprietary recipes and categories.
 *
 * Since Docusaurus's redirect plugin only supports internal redirects, we generate
 * actual HTML files that perform client-side redirects to Moderne docs.
 * These files should be placed in the `static/` directory of the docs site.
 */
object RedirectWriter {

    /**
     * Writes HTML redirect pages for categories that only exist in Moderne docs.
     *
     * When all recipes in a category are proprietary, the category page doesn't exist
     * in OpenRewrite docs. This method creates redirect pages for those categories.
     *
     * @param outputPath Base output directory where redirect HTML files will be written
     * @param allRecipes All recipe descriptors (including proprietary)
     * @param openSourceRecipes Open-source recipe descriptors only
     * @param moderneBaseUrl Base URL for Moderne docs
     * @param moderneLinkBasePath Base path for recipe links on Moderne
     */
    fun writeCategoryRedirects(
        outputPath: Path,
        allRecipes: List<RecipeDescriptor>,
        openSourceRecipes: List<RecipeDescriptor>,
        moderneBaseUrl: String,
        moderneLinkBasePath: String
    ) {
        // Extract category paths from all recipes (Moderne docs has all categories)
        val allCategoryPaths = allRecipes.mapNotNull { recipe ->
            val recipePath = getRecipePath(recipe)
            val lastSlash = recipePath.lastIndexOf('/')
            if (lastSlash > 0) recipePath.substring(0, lastSlash) else null
        }.toSet()

        // Extract category paths from open-source recipes (OpenRewrite docs)
        val openSourceCategoryPaths = openSourceRecipes.mapNotNull { recipe ->
            val recipePath = getRecipePath(recipe)
            val lastSlash = recipePath.lastIndexOf('/')
            if (lastSlash > 0) recipePath.substring(0, lastSlash) else null
        }.toSet()

        // Find categories that exist only in Moderne docs (proprietary-only categories)
        val proprietaryOnlyCategories = allCategoryPaths - openSourceCategoryPaths

        if (proprietaryOnlyCategories.isEmpty()) {
            return
        }

        // Create the static/recipes directory for redirect HTML files
        val staticRecipesPath = outputPath.resolve("static/recipes")
        Files.createDirectories(staticRecipesPath)

        var successCount = 0
        for (categoryPath in proprietaryOnlyCategories.sorted()) {
            try {
                val targetUrl = "$moderneBaseUrl$moderneLinkBasePath/$categoryPath"
                val categoryName = categoryPath.substringAfterLast('/')
                    .replaceFirstChar { it.uppercase() }

                // Create the redirect file at the category path
                val redirectFilePath = staticRecipesPath.resolve("$categoryPath/index.html")
                Files.createDirectories(redirectFilePath.parent)

                // Generate HTML redirect page
                val htmlContent = generateCategoryRedirectHtml(categoryName, targetUrl)
                Files.writeString(redirectFilePath, htmlContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
                successCount++
            } catch (e: Exception) {
                System.err.println("Warning: Could not generate redirect for category $categoryPath: ${e.message}")
            }
        }

        if (successCount > 0) {
            println("Wrote $successCount category redirect page(s) to $staticRecipesPath")
        }
    }

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

    /**
     * Generates an HTML page that redirects a category to the target URL.
     */
    private fun generateCategoryRedirectHtml(categoryName: String, targetUrl: String): String {
        return """
            |<!DOCTYPE html>
            |<html lang="en">
            |<head>
            |    <meta charset="UTF-8">
            |    <meta http-equiv="refresh" content="0; url=$targetUrl">
            |    <link rel="canonical" href="$targetUrl">
            |    <title>Redirecting to $categoryName recipes</title>
            |    <script>window.location.replace("$targetUrl");</script>
            |</head>
            |<body>
            |    <p>This recipe category has moved to Moderne docs.</p>
            |    <p>If you are not redirected automatically, <a href="$targetUrl">click here</a>.</p>
            |</body>
            |</html>
        """.trimMargin()
    }
}
