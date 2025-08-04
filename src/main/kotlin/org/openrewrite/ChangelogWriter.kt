package org.openrewrite

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class ChangelogWriter {

    fun createRecipeDescriptorsYaml(
        markdownArtifacts: TreeMap<String, MarkdownRecipeArtifact>,
        recipeCount: Int,
        rewriteBomVersion: String
    ) {
        val mapper = ObjectMapper(YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER))
        mapper.registerKotlinModule()

        // Read in the old saved recipes for comparison with the latest release
        val recipeDescriptorFile = "src/main/resources/recipeDescriptors.yml"
        val oldArtifacts: TreeMap<String, MarkdownRecipeArtifact> =
            mapper.readValue(Path.of(recipeDescriptorFile).toFile())

        // Build up all the information to make a changelog
        val newArtifacts = getNewArtifacts(markdownArtifacts, oldArtifacts)
        val removedArtifacts = getRemovedArtifacts(markdownArtifacts, oldArtifacts)
        val newRecipes = TreeSet<MarkdownRecipeDescriptor>()
        val removedRecipes = TreeSet<MarkdownRecipeDescriptor>()

        getNewAndRemovedRecipes(markdownArtifacts, oldArtifacts, newRecipes, removedRecipes)

        val changedRecipes = getChangedRecipes(markdownArtifacts, oldArtifacts, newRecipes, removedRecipes)

        if (newArtifacts.isNotEmpty() ||
            removedArtifacts.isNotEmpty() ||
            newRecipes.isNotEmpty() ||
            removedRecipes.isNotEmpty() ||
            changedRecipes.isNotEmpty()
        ) {
            buildChangelog(
                newArtifacts,
                removedArtifacts,
                newRecipes,
                removedRecipes,
                changedRecipes,
                recipeCount,
                rewriteBomVersion
            )
        }

        // Now that we've compared the versions and built the changelog,
        // write the latest recipe information to a file for next time
        mapper.writeValue(File(recipeDescriptorFile), markdownArtifacts)
    }

    private fun buildChangelog(
        newArtifacts: TreeSet<String>,
        removedArtifacts: TreeSet<String>,
        newRecipes: TreeSet<MarkdownRecipeDescriptor>,
        removedRecipes: TreeSet<MarkdownRecipeDescriptor>,
        changedRecipes: TreeSet<ChangedRecipe>,
        recipeCount: Int,
        rewriteBomVersion: String
    ) {
        // Get the date to label the changelog
        val formatted = getDateFormattedYYYYMMDD()
        val changelog: File = File("src/main/resources/${rewriteBomVersion.replace('.', '-')}-Release.md")

        // Clear the file in case this is being generated multiple times
        changelog.writeText("")

        changelog.appendText(
            """
        ---
        description: What's changed in OpenRewrite version ${rewriteBomVersion}.
        ---

        """.trimIndent()
        )
        changelog.appendText("\n# $rewriteBomVersion release ($formatted)")

        changelog.appendText("\n\n_Total recipe count: ${recipeCount}_")
        changelog.appendText("\n\n:::info")
        changelog.appendText("\nThis changelog only shows what recipes have been added, removed, or changed. OpenRewrite may do releases that do not include these types of changes. To see these changes, please go to the [releases page](https://github.com/openrewrite/rewrite/releases).")
        changelog.appendText("\n:::\n\n")

        changelog.appendText("## Corresponding CLI version\n\n")

        // Get the latest staging and stable versions of the CLI
        val stagingVersion = getLatestStagingVersion()
        val stableVersion = getLatestStableVersion()
        if (stableVersion != null) {
            changelog.appendText("* Stable CLI version `${stableVersion}`\n")
        }
        if (stagingVersion != null) {
            changelog.appendText("* Staging CLI version: `${stagingVersion}`\n\n")
        }

        // An example of what the changelog could look like after the below statements can be found here:
        // https://gist.github.com/mike-solomon/16727159ec86ee0f0406ba389cbaecb1
        if (newArtifacts.isNotEmpty()) {
            changelog.appendText("## New Artifacts")

            for (newArtifact in newArtifacts) {
                changelog.appendText("\n* $newArtifact")
            }

            changelog.appendText("\n\n")
        }

        if (removedArtifacts.isNotEmpty()) {
            changelog.appendText("## Removed Artifacts")

            for (removedArtifact in removedArtifacts) {
                changelog.appendText("\n* $removedArtifact")
            }

            changelog.appendText("\n\n")
        }

        if (newRecipes.isNotEmpty()) {
            changelog.appendText("## New Recipes\n")

            for (newRecipe in newRecipes) {
                changelog.appendText("\n* [${newRecipe.name}](${newRecipe.docLink}): ${newRecipe.description.trim()} ")
            }

            changelog.appendText("\n\n")
        }

        if (removedRecipes.isNotEmpty()) {
            changelog.appendText("## Removed Recipes\n")

            for (removedRecipe in removedRecipes) {
                changelog.appendText("\n* **${removedRecipe.name}**: ${removedRecipe.description.trim()} ")
            }

            changelog.appendText("\n\n")
        }

        if (changedRecipes.isNotEmpty()) {
            changelog.appendText("## Changed Recipes\n")

            for (changedRecipe in changedRecipes) {
                changelog.appendText("\n* [${changedRecipe.name}](${changedRecipe.docLink}) was changed:")
                changelog.appendText("\n  * Old Options:")

                if (changedRecipe.oldOptions?.isEmpty() == true) {
                    changelog.appendText("\n    * `None`")
                } else {
                    for (oldOption in changedRecipe.oldOptions!!) {
                        changelog.appendText("\n    * `${oldOption.name}: { type: ${oldOption.type}, required: ${oldOption.required} }`")
                    }
                }

                changelog.appendText("\n  * New Options:")

                if (changedRecipe.newOptions?.isEmpty() == true) {
                    changelog.appendText("\n    * `None`")
                } else {
                    for (newOption in changedRecipe.newOptions!!) {
                        changelog.appendText("\n    * `${newOption.name}: { type: ${newOption.type}, required: ${newOption.required} }`")
                    }
                }
            }
        }
    }

    private fun getNewArtifacts(
        markdownArtifacts: TreeMap<String, MarkdownRecipeArtifact>,
        oldArtifacts: TreeMap<String, MarkdownRecipeArtifact>,
    ): TreeSet<String> {
        val newArtifacts = TreeSet<String>()

        for (artifactId in markdownArtifacts.keys) {
            if (!oldArtifacts.containsKey(artifactId)) {
                newArtifacts.add(artifactId)
            }
        }

        return newArtifacts
    }

    private fun getRemovedArtifacts(
        markdownArtifacts: TreeMap<String, MarkdownRecipeArtifact>,
        oldArtifacts: TreeMap<String, MarkdownRecipeArtifact>,
    ): TreeSet<String> {
        val removedArtifacts = TreeSet<String>()

        for (artifactId in oldArtifacts.keys) {
            if (!markdownArtifacts.containsKey(artifactId)) {
                removedArtifacts.add(artifactId)
            }
        }

        return removedArtifacts
    }

    // This updates the newRecipes and removedRecipes variables to contain the list of new and removed recipes
    private fun getNewAndRemovedRecipes(
        markdownArtifacts: TreeMap<String, MarkdownRecipeArtifact>,
        oldArtifacts: TreeMap<String, MarkdownRecipeArtifact>,
        newRecipes: TreeSet<MarkdownRecipeDescriptor>,
        removedRecipes: TreeSet<MarkdownRecipeDescriptor>,
    ) {
        for (markdownArtifact in markdownArtifacts.values) {
            val oldArtifact = oldArtifacts[markdownArtifact.artifactId]

            if (oldArtifact != null) {
                // Check for new recipes
                for (markdownRecipeDescriptors in markdownArtifact.markdownRecipeDescriptors) {
                    if (!oldArtifact.markdownRecipeDescriptors.containsKey(markdownRecipeDescriptors.key)) {
                        newRecipes.add(markdownRecipeDescriptors.value)
                    }
                }

                // Check for deleted recipes
                for (oldMarkdownRecipeDescriptors in oldArtifact.markdownRecipeDescriptors) {
                    if (!markdownArtifact.markdownRecipeDescriptors.containsKey(oldMarkdownRecipeDescriptors.key)) {
                        removedRecipes.add(oldMarkdownRecipeDescriptors.value)
                    }
                }
            } else {
                // If there's no old artifact, just add all of the recipes to the new recipe list
                for (markdownRecipeDescriptors in markdownArtifact.markdownRecipeDescriptors) {
                    newRecipes.add(markdownRecipeDescriptors.value)
                }
            }
        }
    }

    private fun getChangedRecipes(
        markdownArtifacts: TreeMap<String, MarkdownRecipeArtifact>,
        oldArtifacts: TreeMap<String, MarkdownRecipeArtifact>,
        newRecipes: TreeSet<MarkdownRecipeDescriptor>,
        removedRecipes: TreeSet<MarkdownRecipeDescriptor>,
    ): TreeSet<ChangedRecipe> {
        val changedRecipes = TreeSet<ChangedRecipe>()

        for (markdownArtifact in markdownArtifacts.values) {
            val oldArtifact = oldArtifacts[markdownArtifact.artifactId]

            if (oldArtifact != null) {
                for ((recipeDescriptorName, markdownRecipeDescriptor) in markdownArtifact.markdownRecipeDescriptors) {
                    if (newRecipes.contains(markdownRecipeDescriptor) || removedRecipes.contains(oldArtifact.markdownRecipeDescriptors[recipeDescriptorName])) {
                        // Don't report changes to recipe options if a recipe has been added or removed
                    } else {
                        val newOptions = markdownRecipeDescriptor.options
                        val oldOptions = oldArtifact.markdownRecipeDescriptors[recipeDescriptorName]?.options

                        if (newOptions != oldOptions) {
                            val changedRecipe = ChangedRecipe(
                                markdownArtifact.artifactId,
                                recipeDescriptorName,
                                markdownRecipeDescriptor.description,
                                markdownRecipeDescriptor.docLink,
                                newOptions,
                                oldOptions
                            )
                            changedRecipes.add(changedRecipe)
                        }
                    }
                }
            }
        }

        return changedRecipes
    }

    private fun getDateFormattedYYYYMMDD(): String? {
        val current = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        return current.format(formatter)
    }

}

data class ChangedRecipe(
    val artifactId: String,
    val name: String,
    val description: String,
    val docLink: String,
    val newOptions: TreeSet<RecipeOption>?,
    val oldOptions: TreeSet<RecipeOption>?,
): Comparable<ChangedRecipe> {
    override fun compareTo(other: ChangedRecipe): Int {
        if (this.artifactId != other.artifactId) {
            return this.artifactId.compareTo(other.artifactId)
        }
        if (this.name != other.name) {
            return this.name.compareTo(other.name)
        }
        if (this.description != other.description) {
            return this.description.compareTo(other.description)
        }
        if (this.newOptions != other.newOptions) {
            return -1
        }
        if (this.oldOptions != other.oldOptions) {
            return -1
        }
        return 0
    }
}
