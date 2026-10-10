package org.openrewrite

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ChangelogWriterTest {
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
