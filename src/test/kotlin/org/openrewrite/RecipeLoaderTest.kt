package org.openrewrite

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalStateException
import org.assertj.core.api.Assertions.assertThatNoException
import org.junit.jupiter.api.Test

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
