package org.openrewrite

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

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
}
