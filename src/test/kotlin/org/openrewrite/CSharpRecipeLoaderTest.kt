package org.openrewrite

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.openrewrite.config.OptionDescriptor
import org.openrewrite.config.RecipeDescriptor
import java.net.URI

class CSharpRecipeLoaderTest {

    @Test
    fun registryContainsAllThreeNuGetPackages() {
        assertThat(CSharpRecipeLoader.CSHARP_RECIPE_MODULES).containsKeys(
            "recipes-code-quality",
            "recipes-migrate-dotnet",
            "recipes-tunit"
        )
        assertThat(CSharpRecipeLoader.CSHARP_RECIPE_MODULES["recipes-code-quality"])
            .isEqualTo("OpenRewrite.CodeQuality")
        assertThat(CSharpRecipeLoader.CSHARP_RECIPE_MODULES["recipes-migrate-dotnet"])
            .isEqualTo("OpenRewrite.MigrateDotNet")
        assertThat(CSharpRecipeLoader.CSHARP_RECIPE_MODULES["recipes-tunit"])
            .isEqualTo("OpenRewrite.TUnit")
    }

    private fun descriptor(name: String, displayName: String, description: String,
                           options: List<OptionDescriptor> = emptyList()): RecipeDescriptor {
        return RecipeDescriptor(
            name, displayName, displayName, description,
            emptySet(), null, options, emptyList(), emptyList(),
            emptyList(), emptyList(), emptyList(), emptyList(),
            URI.create("file:///test")
        )
    }

    @Test
    fun buildMarketplacePopulatesFromJavaDescriptors() {
        val descriptors = listOf(
            descriptor("org.openrewrite.java.ChangeMethodName", "Change method name", "Rename a method"),
            descriptor("org.openrewrite.java.ChangeType", "Change type", "Change a type reference")
        )

        val marketplace = CSharpRecipeLoader.buildMarketplace(descriptors)

        assertThat(marketplace.findRecipe("org.openrewrite.java.ChangeMethodName")).isNotNull
        assertThat(marketplace.findRecipe("org.openrewrite.java.ChangeType")).isNotNull
        assertThat(marketplace.findRecipe("org.openrewrite.java.NonExistent")).isNull()
    }

    @Test
    fun buildMarketplaceHandlesEmptyDescriptors() {
        val marketplace = CSharpRecipeLoader.buildMarketplace(emptyList())

        assertThat(marketplace.allRecipes).isEmpty()
    }

    @Test
    fun buildMarketplacePreservesRecipeOptions() {
        val options = listOf(
            OptionDescriptor("methodPattern", "String", "Method pattern", "A method pattern", null, null, true, null),
            OptionDescriptor("newName", "String", "New name", "The new name", null, null, true, null)
        )
        val descriptors = listOf(
            descriptor("org.openrewrite.java.ChangeMethodName", "Change method name", "Rename a method", options)
        )

        val marketplace = CSharpRecipeLoader.buildMarketplace(descriptors)
        val listing = marketplace.findRecipe("org.openrewrite.java.ChangeMethodName")

        assertThat(listing).isNotNull
        assertThat(listing!!.options).hasSize(2)
        assertThat(listing.options.map { it.name }).containsExactly("methodPattern", "newName")
    }
}
