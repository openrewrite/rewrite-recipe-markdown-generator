package org.openrewrite

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.openrewrite.config.OptionDescriptor
import org.openrewrite.config.RecipeDescriptor
import java.net.URI

class GoRecipeLoaderTest {

    @Test
    fun registryMapsRecipesGoToGoModulePath() {
        assertThat(GoRecipeLoader.GO_RECIPE_MODULES)
            .containsEntry("recipes-go", "github.com/moderneinc/recipes-go")
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
        // Go recipes delegate to Java (e.g. golang.ChangeMethodName -> java.ChangeMethodName); seed those so prepareRecipe resolves.
        val descriptors = listOf(
            descriptor("org.openrewrite.java.ChangeMethodName", "Change method name", "Rename a method"),
            descriptor("org.openrewrite.java.ChangeType", "Change type", "Change a type reference")
        )

        val marketplace = GoRecipeLoader.buildMarketplace(descriptors)

        assertThat(marketplace.findRecipe("org.openrewrite.java.ChangeMethodName")).isNotNull
        assertThat(marketplace.findRecipe("org.openrewrite.java.ChangeType")).isNotNull
        assertThat(marketplace.findRecipe("org.openrewrite.java.NonExistent")).isNull()
    }

    @Test
    fun buildMarketplaceHandlesEmptyDescriptors() {
        val marketplace = GoRecipeLoader.buildMarketplace(emptyList())

        assertThat(marketplace.allRecipes).isEmpty()
    }
}
