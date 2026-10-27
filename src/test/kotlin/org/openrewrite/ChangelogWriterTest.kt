package org.openrewrite

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.*

class ChangelogWriterTest {
    @Test
    fun diffsAgainstAndUpdatesTheGivenBaseline(@TempDir dir: Path) {
        val baseline = dir.resolve("recipeDescriptors.yml")
        baseline.toFile().writeText(
            """
            rewrite-core:
              artifactId: "rewrite-core"
              version: "8.94.0"
              markdownRecipeDescriptors: {}

            """.trimIndent()
        )
        val recipe = MarkdownRecipeDescriptor(
            "org.openrewrite.text.ChangeText",
            "Completely replaces the contents of the text file with other text.",
            "https://docs.openrewrite.org/recipes/text/changetext",
            TreeSet(),
            artifactId = "rewrite-core"
        )

        ChangelogWriter().createRecipeDescriptorsYaml(
            TreeMap(mapOf("rewrite-core" to MarkdownRecipeArtifact("rewrite-core", "8.95.0", TreeMap(mapOf(recipe.name to recipe))))),
            1,
            "8.95.0",
            dir,
            baseline
        )

        assertThat(dir.resolve("8-95-0-Release.md")).content().contains("org.openrewrite.text.ChangeText")
        assertThat(baseline).content().contains("version: \"8.95.0\"", "org.openrewrite.text.ChangeText")
    }

    @Test
    fun onlyListsOptionsThatDiffer() {
        val table = optionChangesTable(
            setOf(
                RecipeOption("groupIdPattern", "String", true),
                RecipeOption("version", "String", false),
                RecipeOption("scope", "String", false),
                RecipeOption("legacy", "Boolean", false),
            ),
            setOf(
                RecipeOption("groupIdPattern", "String", true),
                RecipeOption("version", "String", true),
                RecipeOption("scope", "List<String>", false),
                RecipeOption("onlyDirect", "Boolean", false),
            )
        )

        assertThat(table).isEqualTo(
            """
            | Option | Type | Required | Change |
            | --- | --- | --- | --- |
            | `legacy` | `Boolean` | No | Removed |
            | `onlyDirect` | `Boolean` | No | Added |
            | `scope` | `String` → `List<String>` | No | Changed |
            | `version` | `String` | No → Yes | Changed |

            """.trimIndent()
        )
    }
}
