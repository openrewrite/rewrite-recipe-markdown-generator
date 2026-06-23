package org.openrewrite

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.openrewrite.config.OptionDescriptor
import org.openrewrite.config.RecipeDescriptor
import org.openrewrite.config.RecipeExample
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/**
 * Verifies the `forModerneDocs = true` component-MDX output of [RecipeMarkdownWriter].
 * Constructs descriptors directly so it does not depend on recipe loading (which needs Python/pip).
 */
class RecipeMarkdownWriterModerneDocsTest {

    private val jar = URI.create("file:///tmp/recipes.jar")

    private fun origin(license: License = Licenses.Apache2) =
        RecipeOrigin("org.openrewrite.recipe", "rewrite-static-analysis", "1.0.0", jar)
            .apply { this.license = license }

    private fun example() = RecipeExample().apply {
        description = ""
        parameters = listOf("true")
        sources = listOf(RecipeExample.Source("class A {}\n", "class B {}\n", null, "java"))
    }

    private fun descriptor(
        name: String,
        displayName: String,
        description: String,
        options: List<OptionDescriptor> = emptyList(),
        recipeList: List<RecipeDescriptor> = emptyList(),
    ) = RecipeDescriptor(
        name, displayName, displayName, description, setOf("RSPEC-S1192"), Duration.ofMinutes(5),
        options, recipeList, emptyList(), emptyList(), emptyList(), listOf(example()), jar
    )

    private fun singleRecipe() = descriptor(
        "org.openrewrite.java.ReplaceFoo", "Replace `foo`", "Replaces foo with bar.",
        options = listOf(
            OptionDescriptor(
                "includeTestSources", "Boolean", "Include test sources",
                "Changes apply to test sources.", "true", null, false, null
            )
        )
    )

    private fun compositeRecipe() = descriptor(
        "org.openrewrite.java.CompositeFoo", "Composite foo", "Runs several foo recipes.",
        recipeList = listOf(singleRecipe())
    )

    private fun writer(forModerneDocs: Boolean, proprietary: Set<String> = emptySet()) =
        RecipeMarkdownWriter(mutableMapOf(), emptyMap(), proprietary, forModerneDocs)

    private fun generate(
        recipe: RecipeDescriptor,
        dir: Path,
        forModerneDocs: Boolean,
        license: License = Licenses.Apache2,
        proprietary: Set<String> = emptySet(),
    ): String {
        writer(forModerneDocs, proprietary).writeRecipe(recipe, dir, origin(license))
        val md = Files.walk(dir).filter { it.toString().endsWith(".md") }.findFirst().orElseThrow()
        return Files.readString(md)
    }

    @Test
    fun moderneDocsEmitsComponentsForSingleRecipe(@TempDir dir: Path) {
        val out = generate(
            singleRecipe(), dir, forModerneDocs = true,
            license = Licenses.MSAL, proprietary = setOf("org.openrewrite.java.ReplaceFoo")
        )

        // Frontmatter: hide_title, and no markdown H1 (RecipeHeader renders the title).
        assertThat(out).contains("hide_title: true")
        assertThat(out.lineSequence().none { it.startsWith("# ") }).isTrue()

        // Component import + header components.
        assertThat(out).contains("from '@site/src/components/recipe'")
        assertThat(out).contains("<RecipeMeta")
        assertThat(out).contains("<RecipeHeader")
        assertThat(out).contains("type={\"Single recipe\"}")
        assertThat(out).contains("moderneOnly")          // proprietary

        // Options + Examples + Usage, each with the heading passed as a blank-line-wrapped child.
        assertThat(out).contains("<OptionsTable options={")
        assertThat(out).contains("\n\n## Options\n\n</OptionsTable>")
        assertThat(out).contains("<ExampleList examples={")
        assertThat(out).contains("\n\n## Examples\n\n</ExampleList>")
        assertThat(out).contains("<UsageList usage={")
        assertThat(out).contains("\n\n## Usage\n\n</UsageList>")

        // Options JSON uses the component's field names.
        assertThat(out).contains("\"name\":\"includeTestSources\"")
        assertThat(out).contains("\"required\":false")
        // Example variant carries before/after.
        assertThat(out).contains("\"before\":\"class A {}")
        assertThat(out).contains("\"after\":\"class B {}")
    }

    @Test
    fun moderneDocsEmitsDefinitionForCompositeRecipe(@TempDir dir: Path) {
        val out = generate(compositeRecipe(), dir, forModerneDocs = true)

        assertThat(out).contains("type={\"Composite recipe\"}")
        assertThat(out).doesNotContain("moderneOnly")     // open source
        assertThat(out).contains("<RecipeList recipes={")
        assertThat(out).contains("\n\n## Definition\n\n</RecipeList>")
        // Open-source recipe keeps the canonical link to OpenRewrite docs.
        assertThat(out).contains("rel=\"canonical\"")
        assertThat(out).contains("docs.openrewrite.org")
    }

    @Test
    fun openRewriteDocsOutputUnchangedForSingleRecipe(@TempDir dir: Path) {
        // Proprietary license so writeSourceLinks takes its Moderne-only branch and doesn't require a
        // source URI lookup (this test only guards that the OpenRewrite path is untouched).
        val out = generate(
            singleRecipe(), dir, forModerneDocs = false,
            license = Licenses.Proprietary, proprietary = setOf("org.openrewrite.java.ReplaceFoo")
        )

        // The OpenRewrite path still renders a markdown H1 and no recipe components.
        assertThat(out).contains("# Replace `foo`")
        assertThat(out).doesNotContain("<RecipeHeader")
        assertThat(out).doesNotContain("hide_title: true")
    }
}
