package org.openrewrite

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RecipeLoaderTest {

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
