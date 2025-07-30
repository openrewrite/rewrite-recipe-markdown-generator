package org.openrewrite.writers

import org.openrewrite.ChangedRecipe
import org.openrewrite.MarkdownRecipeArtifact
import org.openrewrite.MarkdownRecipeDescriptor
import org.openrewrite.RecipeOption
import java.io.BufferedWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * Writes changelog files for recipe updates
 */
class ChangelogWriter(
    private val rewriteBomVersion: String,
    private val newArtifacts: TreeSet<MarkdownRecipeArtifact>,
    private val removedArtifacts: TreeSet<MarkdownRecipeArtifact>,
    private val newRecipes: TreeSet<MarkdownRecipeDescriptor>,
    private val removedRecipes: TreeSet<MarkdownRecipeDescriptor>,
    private val changedRecipes: TreeSet<ChangedRecipe>,
    private val deployType: String,
    private val recipeCount: Int
) : MarkdownWriter {

    override fun write(outputPath: Path) {
        val changelogLocation = determineChangelogPath(outputPath)
        
        Files.newBufferedWriter(changelogLocation, StandardOpenOption.CREATE).useAndApply {
            writeHeader()
            writeSummary()
            writeNewArtifacts()
            writeRemovedArtifacts()
            writeNewRecipes()
            writeRemovedRecipes()
            writeChangedRecipes()
        }
    }
    
    private fun determineChangelogPath(outputPath: Path): Path {
        return if (deployType == "snapshot") {
            outputPath.resolve("SNAPSHOT-changelog-${getDateFormattedYYYYMMDD()}.md")
        } else {
            outputPath.resolve("changelog/rewrite-${rewriteBomVersion}.md")
        }
    }
    
    private fun BufferedWriter.writeHeader() {
        // language=markdown
        writeln(
            """
            # Rewrite $rewriteBomVersion release (${getDateFormattedYYYYMMDD()})
            
            {% hint style="info" %}
            This changelog only shows what recipes have been added, removed, or changed. To see the full change log, see the release notes for the particular module's release:
            
            * [${rewriteBomVersion} release notes](https://github.com/openrewrite/rewrite/releases/tag/v${rewriteBomVersion})
            * [Gradle plugin ${rewriteBomVersion} release notes](https://github.com/openrewrite/rewrite-gradle-plugin/releases/tag/v${rewriteBomVersion})
            * [Maven plugin ${rewriteBomVersion} release notes](https://github.com/openrewrite/rewrite-maven-plugin/releases/tag/v${rewriteBomVersion})
            {% endhint %}
            """.trimIndent()
        )
        writeln()
    }
    
    private fun BufferedWriter.writeSummary() {
        val timeSaved = calculateTimeSaved()
        
        //language=markdown
        writeln(
            """
            ## Summary
            Number of recipes available: **$recipeCount**
            
            """.trimIndent()
        )
        
        if (timeSaved > 0) {
            writeln(
                """
                ## Estimated time saved
                Together, the recipes released in this new version of Rewrite saves enterprises an estimated **${timeSaved / 8} days** of effort. 
                Read more about [how we calculate time saved](https://docs.openrewrite.org/impact-analysis#time-saved).
                """.trimIndent()
            )
            writeln()
        }
    }
    
    private fun BufferedWriter.writeNewArtifacts() {
        if (newArtifacts.isNotEmpty()) {
            writeln("## New artifacts")
            writeln()
            newArtifacts.forEach { artifact ->
                writeln("* ${artifact.artifactId}:${artifact.version}")
            }
            writeln()
        }
    }
    
    private fun BufferedWriter.writeRemovedArtifacts() {
        if (removedArtifacts.isNotEmpty()) {
            writeln("## Removed artifacts")
            writeln()
            removedArtifacts.forEach { artifact ->
                writeln("* **${artifact.artifactId}:${artifact.version}**")
            }
            writeln()
        }
    }
    
    private fun BufferedWriter.writeNewRecipes() {
        if (newRecipes.isNotEmpty()) {
            writeln("## New recipes")
            writeln()
            newRecipes.forEach { recipe ->
                writeln("* [${recipe.name}](${recipe.docLink}) - ${recipe.description}")
            }
            writeln()
        }
    }
    
    private fun BufferedWriter.writeRemovedRecipes() {
        if (removedRecipes.isNotEmpty()) {
            writeln("## Removed recipes")
            writeln()
            removedRecipes.forEach { recipe ->
                writeln("* **${recipe.name}** - ${recipe.description}")
            }
            writeln()
        }
    }
    
    private fun BufferedWriter.writeChangedRecipes() {
        if (changedRecipes.isNotEmpty()) {
            writeln("## Changed recipes")
            writeln()
            changedRecipes.forEach { recipe ->
                writeln("* [${recipe.name}](${recipe.docLink}) was changed")
                
                // Show added options
                recipe.newOptions?.let { newOpts ->
                    recipe.oldOptions?.let { oldOpts ->
                        val addedOptions = newOpts - oldOpts
                        if (addedOptions.isNotEmpty()) {
                            writeln("  * New options:")
                            addedOptions.forEach { option ->
                                writeln("    * `${option.name}`: ${getOptionDescription(option)}")
                            }
                        }
                    }
                }
                
                // Show removed options
                recipe.oldOptions?.let { oldOpts ->
                    recipe.newOptions?.let { newOpts ->
                        val removedOptions = oldOpts - newOpts
                        if (removedOptions.isNotEmpty()) {
                            writeln("  * Removed options:")
                            removedOptions.forEach { option ->
                                writeln("    * `${option.name}`: ${getOptionDescription(option)}")
                            }
                        }
                    }
                }
            }
            writeln()
        }
    }
    
    private fun calculateTimeSaved(): Int {
        return newRecipes.map { recipe ->
            if (recipe.isImperative) 12 else 4
        }.sum()
    }
    
    private fun getOptionDescription(option: RecipeOption): String {
        return "${option.type}${if (option.required) " (required)" else " (optional)"}"
    }
    
    private fun getDateFormattedYYYYMMDD(): String {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    }
}