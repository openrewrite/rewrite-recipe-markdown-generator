package org.openrewrite

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalStateException
import org.assertj.core.api.Assertions.assertThatNoException
import org.junit.jupiter.api.Test
import java.net.URI

class RecipeLoaderTest {

    @Test
    fun emptyRecipeSourceFailsAFullRun() {
        assertThatIllegalStateException().isThrownBy {
            RecipeLoader.reportEmptyRecipeSources(
                listOf("Go: 0 recipes from 1 configured module(s). Check that Go 1.25+ and the rewrite-go-rpc server are installed."),
                requireAllRecipeSources = true
            )
        }.withMessageContaining("Go: 0 recipes")
            .withMessageContaining("--allow-empty-recipe-sources")
    }

    @Test
    fun everyEmptyRecipeSourceIsReportedAtOnce() {
        assertThatIllegalStateException().isThrownBy {
            RecipeLoader.reportEmptyRecipeSources(listOf("Go: none", "C#: none"), requireAllRecipeSources = true)
        }.withMessageContaining("Go: none").withMessageContaining("C#: none")
    }

    @Test
    fun emptyRecipeSourceOnlyWarnsWhenNotRequired() {
        assertThatNoException().isThrownBy {
            RecipeLoader.reportEmptyRecipeSources(listOf("Go: none"), requireAllRecipeSources = false)
        }
    }

    @Test
    fun noEmptyRecipeSourcesIsAlwaysFine() {
        assertThatNoException().isThrownBy {
            RecipeLoader.reportEmptyRecipeSources(emptyList(), requireAllRecipeSources = true)
        }
    }

    @Test
    fun onlyArtifactsNarrowsTheRpcBackedModulesLoaded() {
        val onlyArtifacts = setOf("rewrite-python", "recipes-csharp-core", "recipes-go")

        assertThat(PythonRecipeLoader(emptyMap(), onlyArtifacts = onlyArtifacts).configuredModules)
            .containsOnlyKeys("rewrite-python")
        assertThat(CSharpRecipeLoader(emptyMap(), onlyArtifacts = onlyArtifacts).configuredModules)
            .containsOnlyKeys("recipes-csharp-core")
        assertThat(GoRecipeLoader(emptyMap(), onlyArtifacts = onlyArtifacts).configuredModules)
            .containsOnlyKeys("recipes-go")
    }

    @Test
    fun aLanguageLeftOutOfOnlyArtifactsIsNotRequiredToLoad() {
        // Nothing configured for a language means nothing to check; only narrowing to a module of
        // that language makes an empty result a failure.
        val onlyArtifacts = setOf("rewrite-java")

        assertThat(PythonRecipeLoader(emptyMap(), onlyArtifacts = onlyArtifacts).configuredModules).isEmpty()
        assertThat(CSharpRecipeLoader(emptyMap(), onlyArtifacts = onlyArtifacts).configuredModules).isEmpty()
        assertThat(GoRecipeLoader(emptyMap(), onlyArtifacts = onlyArtifacts).configuredModules).isEmpty()
    }

    @Test
    fun noOnlyArtifactsLeavesEveryModuleConfigured() {
        assertThat(PythonRecipeLoader(emptyMap()).configuredModules)
            .isEqualTo(PythonRecipeLoader.PYTHON_RECIPE_MODULES)
        assertThat(CSharpRecipeLoader(emptyMap()).configuredModules)
            .isEqualTo(CSharpRecipeLoader.CSHARP_RECIPE_MODULES)
        assertThat(GoRecipeLoader(emptyMap()).configuredModules)
            .isEqualTo(GoRecipeLoader.GO_RECIPE_MODULES)
    }

    @Test
    fun typeScriptModulesAreConfiguredByTheOriginsThatSurviveOnlyArtifacts() {
        val javascript = URI.create("file:///rewrite-javascript.jar")
        val angular = URI.create("file:///rewrite-angular.jar")
        val origins = mapOf(
            javascript to RecipeOrigin("org.openrewrite", "rewrite-javascript", "1.0.0", javascript),
            angular to RecipeOrigin("io.moderne.recipe", "rewrite-angular", "1.0.0", angular)
        )

        assertThat(TypeScriptRecipeLoader(origins).configuredModules)
            .extracting<String> { it.artifactId }
            .containsExactlyInAnyOrder("rewrite-javascript", "rewrite-angular")
        assertThat(TypeScriptRecipeLoader(origins.filterValues { it.artifactId == "rewrite-javascript" }).configuredModules)
            .extracting<String> { it.artifactId }
            .containsExactly("rewrite-javascript")
    }

    @Test
    fun sanitizePathSegmentRemovesHash() {
        assertThat(RecipeLoader.sanitizePathSegment("C#")).isEqualTo("csharp")
    }

    @Test
    fun sanitizePathSegmentHandlesFSharp() {
        assertThat(RecipeLoader.sanitizePathSegment("F#")).isEqualTo("fsharp")
    }

    @Test
    fun sanitizePathSegmentLowercases() {
        assertThat(RecipeLoader.sanitizePathSegment("Python")).isEqualTo("python")
    }

    @Test
    fun sanitizePathSegmentLeavesPlainSegmentsUnchanged() {
        assertThat(RecipeLoader.sanitizePathSegment("java")).isEqualTo("java")
    }
}
