package org.openrewrite

import org.openrewrite.config.CategoryDescriptor
import org.openrewrite.config.RecipeDescriptor
import org.openrewrite.internal.StringUtils
import java.nio.file.Path

/**
 * Represents a hierarchical category of recipes
 */
data class Category(
    val simpleName: String,
    val path: String,
    val descriptor: CategoryDescriptor?,
    val recipes: List<RecipeDescriptor>,
    val subcategories: List<Category>
) {
    val displayName: String = if (descriptor == null) {
        StringUtils.capitalize(simpleName)
    } else {
        descriptor.displayName.replace("`", "")
    }

    fun categoryIndex(): String {
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
                appendLine("## Recipes")
                appendLine()
                for (recipe in recipes) {
                    val recipePath = recipe.name.lowercase().replace(".", "/")
                    appendLine("* [${recipe.displayNameEscaped()}](/recipes/$recipePath)")
                }
            }
        }.toString()
    }

    fun writeCategoryIndex(outputRoot: Path) {
        val categoryPath = outputRoot.resolve(path)
        categoryPath.toFile().mkdirs()
        val categoryIndexPath = categoryPath.resolve("README.md")
        categoryIndexPath.toFile().writeText(categoryIndex())

        for (subcategory in subcategories) {
            subcategory.writeCategoryIndex(outputRoot)
        }
    }

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
            recipeDescriptors: List<RecipeDescriptor>,
            categoryDescriptors: List<CategoryDescriptor>
        ): List<Category> {
            val categories = LinkedHashMap<String, CategoryBuilder>()

            for (recipe in recipeDescriptors) {
                val category = recipe.tags.firstOrNull()?.replace('.', '/') ?: continue
                categories.putRecipe(category, recipe, categoryDescriptors)
            }

            return categories.values
                .asSequence()
                .map { it.build(categoryDescriptors) }
                .sortedBy { it.simpleName }
                .toList()
        }

        private fun MutableMap<String, CategoryBuilder>.putRecipe(
            categoryPathSegments: String,
            recipe: RecipeDescriptor,
            categoryDescriptors: List<CategoryDescriptor>
        ): CategoryBuilder {
            var category = this
            val pathSegments = categoryPathSegments.split('/')
            var categoryPath = ""
            for (pathSegment in pathSegments) {
                categoryPath += if (categoryPath.isEmpty()) pathSegment else "/$pathSegment"
                category.putIfAbsent(
                    pathSegment,
                    CategoryBuilder(categoryPath)
                )
                if (pathSegment == pathSegments.last()) {
                    category[pathSegment]!!.recipes += recipe
                } else {
                    category = category[pathSegment]!!.subcategories
                }
            }
            return this[pathSegments.first()]!!
        }

        private fun findCategoryDescriptor(
            path: String,
            categoryDescriptors: List<CategoryDescriptor>
        ): CategoryDescriptor? {
            val tagName = path.replace('/', '.')
            return categoryDescriptors.find { it.packageName == tagName }
        }
    }
}