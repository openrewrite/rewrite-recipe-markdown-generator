package org.openrewrite

import org.openrewrite.RecipeMarkdownGenerator.Companion.getRecipePath
import org.openrewrite.RecipeMarkdownGenerator.Companion.useAndApply
import org.openrewrite.RecipeMarkdownGenerator.Companion.writeln
import org.openrewrite.config.CategoryDescriptor
import org.openrewrite.config.RecipeDescriptor
import org.openrewrite.internal.StringUtils
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class CategoryWriter(
    val allRecipeDescriptors: List<RecipeDescriptor>,
    val allCategoryDescriptors: List<CategoryDescriptor>,
    val recipeLinkBasePath: String = "/recipes",
    val crossCategoryPaths: Map<String, List<String>> = emptyMap()
) {
    fun writeCategories(
        outputPath: Path,
        recipesSubdir: String = "recipes"
    ) {
        val categories =
            Category.fromDescriptors(allRecipeDescriptors, allCategoryDescriptors, recipeLinkBasePath, crossCategoryPaths)
                .sortedBy { it.simpleName }
        for (category in categories) {
            val categoryIndexPath = outputPath.resolve("$recipesSubdir/")
            category.writeCategoryIndex(categoryIndexPath)
        }
    }
}

data class Category(
    val simpleName: String,
    val path: String,
    val descriptor: CategoryDescriptor?,
    val recipes: List<RecipeDescriptor>,
    val subcategories: List<Category>,
    val recipeLinkBasePath: String = "/recipes",
    /** Maps recipe name → local filename for cross-category recipes in this category */
    val crossCategoryLocalPaths: Map<String, String> = emptyMap()
) {
    companion object {
        private data class CategoryBuilder(
            val path: String? = null,
            val recipes: MutableList<RecipeDescriptor> = mutableListOf(),
            val subcategories: LinkedHashMap<String, CategoryBuilder> = LinkedHashMap(),
            /** Maps recipe name → local filename for cross-category recipes */
            val crossCategoryLocalPaths: MutableMap<String, String> = mutableMapOf()
        ) {
            fun build(categoryDescriptors: List<CategoryDescriptor>, recipeLinkBasePath: String): Category {
                val simpleName = path!!.substring(path.lastIndexOf('/') + 1)
                val descriptor = findCategoryDescriptor(path, categoryDescriptors)
                // Do not consider backticks while sorting, they're formatting.
                val finalizedSubcategories = subcategories.values.asSequence()
                    .map { it.build(categoryDescriptors, recipeLinkBasePath) }
                    .sortedBy { it.displayName.replace("`", "") }
                    .toList()
                return Category(
                    simpleName,
                    path,
                    descriptor,
                    recipes.sortedBy { it.displayName.replace("`", "") },
                    finalizedSubcategories,
                    recipeLinkBasePath,
                    crossCategoryLocalPaths.toMap()
                )
            }
        }

        fun fromDescriptors(
            recipes: Iterable<RecipeDescriptor>,
            descriptors: List<CategoryDescriptor>,
            recipeLinkBasePath: String = "/recipes",
            crossCategoryPaths: Map<String, List<String>> = emptyMap()
        ): List<Category> {
            val result = LinkedHashMap<String, CategoryBuilder>()
            for (recipe in recipes) {
                result.putRecipe(getRecipeCategory(recipe), recipe)
            }

            // Add cross-category placements
            val recipesByName = recipes.associateBy { it.name }
            for ((recipeName, extraPaths) in crossCategoryPaths) {
                val recipe = recipesByName[recipeName] ?: continue
                for (extraPath in extraPaths) {
                    val slashIndex = extraPath.lastIndexOf("/")
                    if (slashIndex == -1) continue
                    val categoryPath = extraPath.substring(0, slashIndex)
                    val localFileName = extraPath.substringAfterLast('/')
                    result.putCrossCategoryRecipe(categoryPath, recipe, localFileName)
                }
            }

            return result.mapValues { it.value.build(descriptors, recipeLinkBasePath) }
                .values
                .toList()
        }

        private fun MutableMap<String, CategoryBuilder>.putRecipe(
            recipeCategory: String?,
            recipe: RecipeDescriptor
        ) {
            if (recipeCategory == null) {
                return
            }
            val pathSegments = recipeCategory.split("/")
            var category = this
            for (i in pathSegments.indices) {
                val pathSegment = pathSegments[i]
                val pathToCurrent = pathSegments.subList(0, i + 1).joinToString("/")
                if (!category.containsKey(pathSegment)) {
                    category[pathSegment] = CategoryBuilder(path = pathToCurrent)
                }
                if (i == pathSegments.size - 1) {
                    category[pathSegment]!!.recipes.add(recipe)
                }
                category = category[pathSegment]!!.subcategories
            }
        }

        /**
         * Add a recipe to a cross-category location, tracking its local filename
         * so the category index can link to the correct file.
         */
        private fun MutableMap<String, CategoryBuilder>.putCrossCategoryRecipe(
            categoryPath: String,
            recipe: RecipeDescriptor,
            localFileName: String
        ) {
            val pathSegments = categoryPath.split("/")
            var category = this
            for (i in pathSegments.indices) {
                val pathSegment = pathSegments[i]
                val pathToCurrent = pathSegments.subList(0, i + 1).joinToString("/")
                if (!category.containsKey(pathSegment)) {
                    category[pathSegment] = CategoryBuilder(path = pathToCurrent)
                }
                if (i == pathSegments.size - 1) {
                    val builder = category[pathSegment]!!
                    // Avoid adding the same recipe twice to the same category
                    if (builder.recipes.none { it.name == recipe.name }) {
                        builder.recipes.add(recipe)
                        builder.crossCategoryLocalPaths[recipe.name] = localFileName
                    }
                }
                category = category[pathSegment]!!.subcategories
            }
        }

        private fun getRecipeCategory(recipe: RecipeDescriptor): String {
            val recipePath = getRecipePath(recipe)
            val slashIndex = recipePath.lastIndexOf("/")
            return if (slashIndex == -1) {
                ""
            } else {
                recipePath.substring(0, slashIndex)
            }
        }

        private fun findCategoryDescriptor(
            categoryPathFragment: String,
            categoryDescriptors: Iterable<CategoryDescriptor>
        ): CategoryDescriptor? {
            val categoryPackage = "org.openrewrite.${categoryPathFragment.replace('/', '.')}"
            return categoryDescriptors.find { descriptor -> descriptor.packageName == categoryPackage }
        }
    }

    var displayName: String =
        if (descriptor == null) {
            StringUtils.capitalize(simpleName)
        } else {
            descriptor.displayName.replace("`", "")
        }

    /**
     * Produce the contents of the README.md file for this category.
     */
    private fun categoryIndex(): String {
        return StringBuilder().apply {
            // Needed to prevent LLM file-length warnings when parsing all the files in the build step.
            // This is because the docusaurus-plugin-llms plugin processes rendered HTML pages which combine
            // all recipes into one giant page.
            appendLine("---")
            appendLine("description: $displayName OpenRewrite recipes.")
            appendLine("---")
            appendLine()

            // Docusaurus gets confused when parsing C# as the sidebar title. We need to surround it in backticks
            // so it displays correctly.
            if (displayName == "C#") {
                appendLine("# `C#`")
            } else if (displayName == "Ai") {
                // Ai is not capitalized by default - so let's switch it to be AI
                appendLine("# AI")
            } else {
                appendLine("# $displayName")
            }

            // While the description is not _supposed_ to be nullable it has happened before
            @Suppress("SENSELESS_COMPARISON")
            if (descriptor != null && descriptor.description != null) {
                appendLine()
                if (descriptor.description.contains("\n") || descriptor.description.contains("_")) {
                    appendLine(descriptor.description)
                } else {
                    appendLine("_${descriptor.description}_")
                }
            }
            appendLine()

            if (subcategories.isNotEmpty()) {
                appendLine("## Categories")
                appendLine()
                for (subcategory in subcategories) {
                    appendLine("* [${subcategory.displayName}](${recipeLinkBasePath}/${subcategory.path})")
                }
                appendLine()
            }

            if (recipes.isNotEmpty()) {
                val compositeRecipes: MutableList<RecipeDescriptor> = mutableListOf()
                val normalRecipes: MutableList<RecipeDescriptor> = mutableListOf()

                for (recipe in recipes) {
                    if (1 < recipe.recipeList.size) {
                        compositeRecipes.add(recipe)
                    } else {
                        normalRecipes.add(recipe)
                    }
                }

                if (compositeRecipes.isNotEmpty()) {
                    appendLine("## Composite Recipes")
                    appendLine()
                    appendLine("_Recipes that include further recipes, often including the individual recipes below._")
                    appendLine()

                    for (recipe in compositeRecipes) {
                        // Use cross-category local filename if available, otherwise derive from recipe path
                        val localPath = crossCategoryLocalPaths[recipe.name]
                            ?: getRecipePath(recipe).substringAfterLast('/')
                        appendLine("* [${recipe.displayNameEscapedMdx()}](./$localPath.md)")
                    }

                    appendLine()
                }

                if (normalRecipes.isNotEmpty()) {
                    appendLine("## Recipes")
                    appendLine()

                    for (recipe in normalRecipes) {
                        // Use cross-category local filename if available, otherwise derive from recipe path
                        val localPath = crossCategoryLocalPaths[recipe.name]
                            ?: getRecipePath(recipe).substringAfterLast('/')
                        appendLine("* [${recipe.displayNameEscapedMdx()}](./${localPath}.md)")
                    }

                    appendLine()
                }
            }

        }.toString()
    }

    fun writeCategoryIndex(outputRoot: Path) {
        if (path.isBlank()) {
            // Create a core directory
            val recipesPath = outputRoot.resolve("core")
            try {
                Files.createDirectories(recipesPath)
            } catch (e: IOException) {
                throw RuntimeException(e)
            }

            // "Core" recipes need to be handled differently as they do not have a path like other recipes.
            val corePath = outputRoot.resolve("core/README.md")

            Files.newBufferedWriter(corePath, StandardOpenOption.CREATE).useAndApply {
                writeln("---")
                writeln("description: Core OpenRewrite recipes.")
                writeln("---")
                newLine()
                writeln("# Core Recipes")
                newLine()
                writeln("_Recipes broadly applicable to all types of source files._")
                newLine()
                writeln("## Recipes")
                newLine()

                for (recipe in recipes) {
                    val relativePath = crossCategoryLocalPaths[recipe.name]
                        ?: getRecipePath(recipe).substringAfterLast('/')
                    writeln("* [${recipe.displayNameEscapedMdx()}](./$relativePath.md)")
                }
            }

            return
        }

        val outputPath = outputRoot.resolve("$path/README.md")
        Files.createDirectories(outputPath.parent)
        Files.newBufferedWriter(outputPath, StandardOpenOption.CREATE).useAndApply {
            writeln(categoryIndex())
        }

        for (subcategory in subcategories) {
            subcategory.writeCategoryIndex(outputRoot)
        }
    }
}
