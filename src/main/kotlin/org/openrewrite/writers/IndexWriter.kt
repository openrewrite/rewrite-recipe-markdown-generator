package org.openrewrite.writers

import org.openrewrite.Category
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Writes index and category pages
 */
class IndexWriter(
    private val categories: List<Category>
) : MarkdownWriter {

    override fun write(outputPath: Path) {
        val recipesPath = outputPath.resolve("recipes/")
        Files.createDirectories(recipesPath)
        
        // Write index for each category
        for (category in categories) {
            writeCategoryIndex(category, recipesPath)
        }
    }
    
    private fun writeCategoryIndex(category: Category, recipesPath: Path) {
        val categoryPath = recipesPath.resolve(category.path)
        Files.createDirectories(categoryPath)
        val readmePath = categoryPath.resolve("README.md")
        
        Files.newBufferedWriter(readmePath, StandardOpenOption.CREATE).useAndApply {
            writeln(category.categoryIndex())
        }
        
        // Recursively write subcategories
        for (subcategory in category.subcategories) {
            writeCategoryIndex(subcategory, recipesPath)
        }
    }
}