@file:Suppress("SENSELESS_COMPARISON")

package org.openrewrite

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.openrewrite.RecipeMarkdownGenerator.Companion.findOrigin
import org.openrewrite.RecipeMarkdownGenerator.Companion.getRecipePath
import org.openrewrite.config.CategoryDescriptor
import org.openrewrite.config.RecipeDescriptor
import org.openrewrite.internal.StringUtils
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class RecipesCsvWriter(
    private val allRecipeDescriptors: List<RecipeDescriptor>,
    private val allCategoryDescriptors: List<CategoryDescriptor>,
    private val recipeOrigins: Map<URI, RecipeOrigin>,
    private val recipeToSource: Map<String, URI>
) {
    private val objectMapper = ObjectMapper().registerKotlinModule()

    private val dataTablesToIgnore = listOf(
        "org.openrewrite.table.SearchResults",
        "org.openrewrite.table.SourcesFileResults",
        "org.openrewrite.table.SourcesFileErrors",
        "org.openrewrite.table.RecipeRunStats"
    )

    companion object {
        private const val CATEGORY_COUNT = 6
    }

    fun writeCsv(outputPath: Path) {
        val csvPath = outputPath.resolve("recipes-v5.csv")
        Files.newBufferedWriter(csvPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING).use { writer ->
            // Header: match the Moderne CLI column order exactly
            val header = mutableListOf(
                "ecosystem", "packageName", "requestedVersion", "version",
                "name", "displayName", "description", "recipeCount"
            )
            for (i in 1..CATEGORY_COUNT) {
                header.add("category$i")
            }
            for (i in 1..CATEGORY_COUNT) {
                header.add("category${i}Description")
            }
            header.addAll(listOf("options", "dataTables"))
            writer.write(header.joinToString(","))
            writer.newLine()

            // Build category descriptor lookup: package name -> CategoryDescriptor
            val categoryDescriptorsByPackage = allCategoryDescriptors.associateBy { it.packageName }

            // Rows
            for (descriptor in allRecipeDescriptors.sortedBy { it.name }) {
                val source = recipeToSource[descriptor.name] ?: continue
                val origin = findOrigin(source, descriptor.name, recipeOrigins) ?: continue

                val ecosystem = determineEcosystem(descriptor)
                val packageName = determinePackageName(origin, ecosystem)
                val categories = getCategories(descriptor)
                val recipeCount = descriptor.recipeList?.size ?: 0

                val row = mutableListOf(
                    csvEscape(ecosystem),
                    csvEscape(packageName),
                    "LATEST",
                    csvEscape(origin.version),
                    csvEscape(descriptor.name),
                    csvEscape(descriptor.displayName ?: ""),
                    csvEscape((descriptor.description ?: "").replace("\n", " ").trim()),
                    recipeCount.toString()
                )

                // Category names (category1 = most specific)
                for (i in 0 until CATEGORY_COUNT) {
                    row.add(if (i < categories.size) csvEscape(categories[i].displayName) else "")
                }

                // Category descriptions
                for (i in 0 until CATEGORY_COUNT) {
                    if (i < categories.size) {
                        val catDesc = categoryDescriptorsByPackage[categories[i].packageName]
                        row.add(if (catDesc?.description != null) csvEscape(catDesc.description) else "")
                    } else {
                        row.add("")
                    }
                }

                row.addAll(
                    listOf(
                        csvEscape(formatOptions(descriptor)),
                        csvEscape(formatDataTables(descriptor))
                    )
                )

                writer.write(row.joinToString(","))
                writer.newLine()
            }
        }

        println("Wrote recipes-v5.csv with ${allRecipeDescriptors.size} recipe(s) to $csvPath")
    }

    private fun determineEcosystem(recipeDescriptor: RecipeDescriptor): String {
        val source = recipeToSource[recipeDescriptor.name] ?: return "maven"
        val sourceString = source.toString()
        return when {
            sourceString.startsWith("typescript-search://") -> "npm"
            sourceString.startsWith("python-search://") -> "pip"
            else -> "maven"
        }
    }

    private fun determinePackageName(origin: RecipeOrigin, ecosystem: String): String {
        return when (ecosystem) {
            "npm" -> TypeScriptRecipeLoader.TYPESCRIPT_RECIPE_MODULES[origin.artifactId]
                ?: "@openrewrite/${origin.artifactId}"

            "pip" -> PythonRecipeLoader.PYTHON_RECIPE_MODULES[origin.artifactId]
                ?: origin.artifactId

            else -> "${origin.groupId}:${origin.artifactId}"
        }
    }

    /**
     * Build the category hierarchy for a recipe, from most specific to least specific.
     * Each entry is a CategoryDescriptor-like object with a display name and package name.
     */
    private data class CategoryInfo(val displayName: String, val packageName: String)

    private fun getCategories(recipeDescriptor: RecipeDescriptor): List<CategoryInfo> {
        val recipePath = getRecipePath(recipeDescriptor)
        val segments = recipePath.split("/")
        // Last segment is the recipe itself, preceding segments are categories
        val categorySegments = if (segments.size > 1) segments.dropLast(1) else emptyList()

        // Build category infos from outermost to innermost, then reverse for most-specific-first
        val categoryInfos = mutableListOf<CategoryInfo>()
        for (i in categorySegments.indices) {
            val path = categorySegments.subList(0, i + 1).joinToString("/")
            val packageName = "org.openrewrite.${path.replace('/', '.')}"
            val descriptor = allCategoryDescriptors.find { it.packageName == packageName }
            val displayName = descriptor?.displayName
                ?: StringUtils.capitalize(categorySegments[i])
            categoryInfos.add(CategoryInfo(displayName, packageName))
        }
        return categoryInfos.reversed()
    }

    private fun formatOptions(recipeDescriptor: RecipeDescriptor): String {
        if (recipeDescriptor.options == null || recipeDescriptor.options.isEmpty()) return ""
        val optionsList = recipeDescriptor.options.mapNotNull { option ->
            if (option.name == null) return@mapNotNull null
            val map = mutableMapOf<String, Any?>(
                "name" to option.name.toString(),
                "type" to (option.type ?: "String"),
                "displayName" to (option.displayName ?: ""),
                "description" to (option.description ?: "")
            )
            if (option.example != null) {
                map["example"] = option.example
            }
            if (option.isRequired) {
                map["required"] = true
            }
            map
        }
        if (optionsList.isEmpty()) return ""
        return objectMapper.writeValueAsString(optionsList)
    }

    private fun formatDataTables(recipeDescriptor: RecipeDescriptor): String {
        if (recipeDescriptor.dataTables == null || recipeDescriptor.dataTables.isEmpty()) return ""
        val filtered = recipeDescriptor.dataTables.filter { it.name !in dataTablesToIgnore }
        if (filtered.isEmpty()) return ""
        val tablesList = filtered.map { dataTable ->
            val map = mutableMapOf<String, Any?>(
                "name" to dataTable.name,
                "displayName" to (dataTable.displayName ?: ""),
                "description" to (dataTable.description ?: "")
            )
            if (dataTable.columns != null && dataTable.columns.isNotEmpty()) {
                map["columns"] = dataTable.columns.map { column ->
                    mapOf(
                        "name" to column.name,
                        "type" to (column.type ?: "String"),
                        "displayName" to (column.displayName ?: ""),
                        "description" to (column.description ?: "")
                    )
                }
            }
            map
        }
        return objectMapper.writeValueAsString(tablesList)
    }

    private fun csvEscape(value: String): String {
        if (value.isEmpty()) return ""
        return if (value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }
}
