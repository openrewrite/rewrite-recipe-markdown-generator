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

    @Test
    fun versionAnchorUsesProxyVersionUnderTheFormerMavenCoordinates() {
        val origins = GoRecipeLoader.buildVersionAnchorOrigins { module ->
            assertThat(module).isEqualTo("github.com/moderneinc/recipes-go")
            "0.12.1"
        }

        val origin = origins.getValue(URI.create("go-search://recipes-go"))
        assertThat(origin.groupId).isEqualTo("org.openrewrite.recipe")
        assertThat(origin.artifactId).isEqualTo("recipes-go")
        assertThat(origin.version).isEqualTo("0.12.1")
        assertThat(origin.versionPlaceholderKey()).isEqualTo("VERSION_ORG_OPENREWRITE_RECIPE_RECIPES_GO")
        assertThat(origin.repositoryUrl).isEqualTo("https://github.com/moderneinc/recipes-go/blob/main/")
        assertThat(origin.license).isEqualTo(Licenses.Proprietary)
    }

    @Test
    fun versionAnchorSkipsUnresolvedAndUnselectedModules() {
        assertThat(GoRecipeLoader.buildVersionAnchorOrigins { null }).isEmpty()
        assertThat(GoRecipeLoader.buildVersionAnchorOrigins(setOf("rewrite-java")) { "0.12.1" }).isEmpty()
    }

    @Test
    fun stableVersionStripsLeadingV() {
        val latest = """{"Version":"v0.12.1","Time":"2026-10-08T07:24:02Z","Origin":{"VCS":"git","Ref":"refs/tags/v0.12.1"}}"""
        assertThat(stableGoModuleVersion(latest)).isEqualTo("0.12.1")
    }

    @Test
    fun stableVersionRejectsPrereleasesAndPseudoVersions() {
        assertThat(stableGoModuleVersion("""{"Version":"v0.13.0-rc.1"}""")).isNull()
        assertThat(stableGoModuleVersion("""{"Version":"v0.0.0-20261008072402-503e938229f0"}""")).isNull()
    }

    @Test
    fun modulePathEscapesCapitalLetters() {
        assertThat(escapeGoModulePath("github.com/moderneinc/recipes-go")).isEqualTo("github.com/moderneinc/recipes-go")
        assertThat(escapeGoModulePath("github.com/BurntSushi/toml")).isEqualTo("github.com/!burnt!sushi/toml")
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
