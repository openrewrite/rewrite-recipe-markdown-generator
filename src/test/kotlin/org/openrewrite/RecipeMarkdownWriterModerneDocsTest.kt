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
        // Includes a "Precondition bellwether" that must be filtered out of the rendered list.
        recipeList = listOf(
            singleRecipe(),
            descriptor("org.openrewrite.java.BellwetherFoo", "Precondition bellwether", "internal"),
        )
    ).withPreconditions(
        listOf(descriptor("org.openrewrite.java.search.FindFoo", "Find foo", "Finds foo."))
    )

    private fun generate(
        recipe: RecipeDescriptor,
        dir: Path,
        forModerneDocs: Boolean,
        license: License = Licenses.Apache2,
        proprietary: Set<String> = emptySet(),
    ): String {
        RecipeMarkdownWriter(mutableMapOf(), emptyMap(), proprietary, forModerneDocs)
            .writeRecipe(recipe, dir, origin(license))
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

        // Title + description are emitted as markdown children (not string props) so MDX renders the
        // inline code in the title and any links in the description natively.
        assertThat(out).contains("<RecipeHeader.Title>Replace `foo`</RecipeHeader.Title>")
        assertThat(out).contains("<RecipeHeader.Description>Replaces foo with bar.</RecipeHeader.Description>")

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
        // Preconditions are fed through to RecipeList's `preconditions` prop (Jayd's #798 change).
        assertThat(out).contains("preconditions={")
        assertThat(out).contains("\"name\":\"Find foo\"")
        // Internal "Precondition bellwether" recipes are filtered out (rewrite-docs#250).
        assertThat(out).doesNotContain("Precondition bellwether")
        // Open-source recipe keeps the canonical link to OpenRewrite docs.
        assertThat(out).contains("rel=\"canonical\"")
        assertThat(out).contains("docs.openrewrite.org")
    }

    @Test
    fun moderneDocsEmitsNpmUsageForJavaScriptRecipe(@TempDir dir: Path) {
        val recipe = descriptor("org.openrewrite.javascript.FormatFoo", "Format foo", "Formats foo.")
        // isJavaScriptRecipe keys off a `typescript-search://` source URI.
        val recipeToSource = mapOf(recipe.name to URI.create("typescript-search://rewrite-static-analysis/format"))
        RecipeMarkdownWriter(mutableMapOf(), recipeToSource, emptySet(), forModerneDocs = true)
            .writeRecipe(recipe, dir, origin())
        val out = Files.readString(Files.walk(dir).filter { it.toString().endsWith(".md") }.findFirst().orElseThrow())

        // JS recipes get a UsageList with an npm package, not Maven/Gradle coordinates.
        assertThat(out).contains("<UsageList usage={")
        assertThat(out).contains("\"npmPackage\":")
        assertThat(out).doesNotContain("\"groupId\":")
    }

    @Test
    fun moderneDocsEmitsAbsoluteHrefForSubRecipes(@TempDir dir: Path) {
        val sub = singleRecipe() // org.openrewrite.java.ReplaceFoo
        val composite = descriptor(
            "org.openrewrite.java.CompositeFoo", "Composite foo", "Runs foo.", recipeList = listOf(sub),
        )
        val recipeToSource = mapOf(sub.name to URI.create("file:///tmp/recipes.jar"))
        RecipeMarkdownWriter(mutableMapOf(), recipeToSource, emptySet(), forModerneDocs = true)
            .writeRecipe(composite, dir, origin())
        val out = Files.readString(Files.walk(dir).filter { it.toString().endsWith(".md") }.findFirst().orElseThrow())

        // Sub-recipe links are absolute from the catalog root with a trailing slash (a relative path would
        // double up against the current recipe URL in a raw <a href>; the slashless form 301-redirects).
        assertThat(out).contains("\"href\":\"/user-documentation/recipes/recipe-catalog/java/replacefoo/\"")
    }

    @Test
    fun moderneDocsRendersDescriptionMarkdownAndEscapesJsxOpeners(@TempDir dir: Path) {
        val recipe = descriptor(
            "org.openrewrite.java.LinkFoo", "Link foo",
            "Use `Map<String>` and see [docs](https://example.com/x). Compare a < b.",
        )
        RecipeMarkdownWriter(mutableMapOf(), emptyMap(), emptySet(), forModerneDocs = true)
            .writeRecipe(recipe, dir, origin())
        val out = Files.readString(Files.walk(dir).filter { it.toString().endsWith(".md") }.findFirst().orElseThrow())

        // The markdown link is preserved verbatim; the `<` inside the code span stays literal; the bare
        // `<` outside code is escaped so MDX doesn't read it as a JSX tag.
        assertThat(out).contains(
            "<RecipeHeader.Description>Use `Map<String>` and see [docs](https://example.com/x). Compare a &lt; b.</RecipeHeader.Description>",
        )
    }

    @Test
    fun moderneDocsTruncatesStaleContentWhenSamePathWrittenTwice(@TempDir dir: Path) {
        // A recipe's native page and a cross-category duplicate of a different recipe can resolve to the
        // same path. The second (shorter) write must fully replace the first, not leave a stale tail that
        // produces unparseable MDX.
        val writer = RecipeMarkdownWriter(mutableMapOf(), emptyMap(), emptySet(), forModerneDocs = true)

        // First: a content-heavy recipe (definition + examples + usage) at a shared relative path.
        writer.writeRecipeTo(compositeRecipe(), dir, origin(), "shared/page")
        val longText = Files.readString(dir.resolve("shared/page.md"))
        assertThat(longText).contains("## Examples", "Composite foo")

        // Then: a minimal recipe (no definition/options/examples — only header + usage) to the SAME path.
        val minimal = RecipeDescriptor(
            "org.openrewrite.java.Tiny", "Tiny", "Tiny", "A tiny recipe.", emptySet(), Duration.ofMinutes(1),
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), jar
        )
        writer.writeRecipeTo(minimal, dir, origin(), "shared/page")
        val shortText = Files.readString(dir.resolve("shared/page.md"))

        assertThat(shortText.length).isLessThan(longText.length)
        assertThat(shortText.trimEnd()).endsWith("</UsageList>")
        // No stale tail bleeding through from the first, longer write.
        assertThat(shortText).doesNotContain("## Examples", "## Definition", "Composite foo")
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
