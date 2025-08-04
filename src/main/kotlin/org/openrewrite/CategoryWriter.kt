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
    val allCategoryDescriptors: List<CategoryDescriptor>
) {
    fun writeCategories(
        outputPath: Path
    ) {
        val categories =
            Category.fromDescriptors(allRecipeDescriptors, allCategoryDescriptors)
                .sortedBy { it.simpleName }
        for (category in categories) {
            val categoryIndexPath = outputPath.resolve("recipes/")
            category.writeCategoryIndex(categoryIndexPath)
        }
    }
}

data class Category(
    val simpleName: String,
    val path: String,
    val descriptor: CategoryDescriptor?,
    val recipes: List<RecipeDescriptor>,
    val subcategories: List<Category>
) {
    companion object {
        private data class CategoryBuilder(
            val path: String? = null,
            val recipes: MutableList<RecipeDescriptor> = mutableListOf(),
            val subcategories: LinkedHashMap<String, CategoryBuilder> = LinkedHashMap()
        ) {
            fun build(categoryDescriptors: List<CategoryDescriptor>): Category {
                val simpleName = path!!.substring(path.lastIndexOf('/') + 1)
                val descriptor = findCategoryDescriptor(path, categoryDescriptors)
                // Do not consider backticks while sorting, they're formatting.
                val finalizedSubcategories = subcategories.values.asSequence()
                    .map { it.build(categoryDescriptors) }
                    .sortedBy { it.displayName.replace("`", "") }
                    .toList()
                return Category(
                    simpleName,
                    path,
                    descriptor,
                    recipes.sortedBy { it.displayName.replace("`", "") },
                    finalizedSubcategories
                )
            }
        }

        fun fromDescriptors(
            recipes: Iterable<RecipeDescriptor>,
            descriptors: List<CategoryDescriptor>
        ): List<Category> {
            val result = LinkedHashMap<String, CategoryBuilder>()
            for (recipe in recipes) {
                result.putRecipe(getRecipeCategory(recipe), recipe)
            }

            return result.mapValues { it.value.build(descriptors) }
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
                    appendLine("* [${subcategory.displayName}](/recipes/${subcategory.path})")
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
                        // Anything except a relative link ending in .md will be mangled.
                        val localPath = getRecipePath(recipe).substringAfterLast('/')
                        appendLine("* [${recipe.displayNameEscaped()}](./$localPath.md)")
                    }

                    appendLine()
                }

                if (normalRecipes.isNotEmpty()) {
                    appendLine("## Recipes")
                    appendLine()

                    for (recipe in normalRecipes) {
                        // Anything except a relative link ending in .md will be mangled.
                        val localPath = getRecipePath(recipe).substringAfterLast('/')
                        appendLine("* [${recipe.displayNameEscaped()}](./${localPath}.md)")
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
                writeln("# Core Recipes")
                newLine()
                writeln("_Recipes broadly applicable to all types of source files._")
                newLine()
                writeln("## Recipes")
                newLine()

                for (recipe in recipes) {
                    val relativePath = getRecipePath(recipe).substringAfterLast('/')
                    writeln("* [${recipe.displayNameEscaped()}](./$relativePath.md)")
                }
            }

            return
        }

        val outputPath = outputRoot.resolve("$path/README.md")
        Files.newBufferedWriter(outputPath, StandardOpenOption.CREATE).useAndApply {
            writeln(categoryIndex())
        }

        for (subcategory in subcategories) {
            subcategory.writeCategoryIndex(outputRoot)
        }
    }
}
