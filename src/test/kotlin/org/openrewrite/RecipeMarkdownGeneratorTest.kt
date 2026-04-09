package org.openrewrite

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.openrewrite.config.OptionDescriptor
import org.openrewrite.config.RecipeDescriptor
import picocli.CommandLine
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

class RecipeMarkdownGeneratorTest {
    @Test
    fun latestVersions(@TempDir tempDir: Path) {
        val generator = RecipeMarkdownGenerator()
        val stringWriter = StringWriter()
        val commandLine = CommandLine(generator)
        commandLine.setOut(PrintWriter(stringWriter))

        val exitCode = commandLine.execute(
            tempDir.toFile().toString(),
            "", // recipe sources
            "", // recipe classpath

            "8.x", // Rewrite
            "2.x", // Recipe BOM
            "1.x", // Moderne Recipe BOM
            "6.x", // Gradle plugin
            "5.x", // Maven plugin
            "--latest-versions-only",
        )
        assertThat(exitCode).isEqualTo(0)

        val latestVersionsMd = tempDir.resolve("latest-versions-of-every-openrewrite-module.md")
        assertTrue(latestVersionsMd.toFile().exists())
        val contents = Files.readString(latestVersionsMd)
        assertTrue(contents.contains("2.x"), contents)
    }

    @Test
    fun conflictingRecipesGetEditionSuffix() {
        // When both moderne and openrewrite have the same recipe, they should get edition suffixes
        val recipes = listOf(
            "io.moderne.java.spring.boot3.UpgradeSpringBoot_3_4",
            "org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_4",
            "io.moderne.java.spring.security6.UpgradeSpringSecurity_6_5",
            "org.openrewrite.java.spring.security6.UpgradeSpringSecurity_6_5",
            "io.moderne.hibernate.UpgradeHibernate_6_6",
            "org.openrewrite.hibernate.UpgradeHibernate_6_6"
        )
        initializeConflictDetection(recipes)

        // Spring Boot 3.4 - both exist, so both get suffixes
        assertThat(getRecipePath("io.moderne.java.spring.boot3.UpgradeSpringBoot_3_4"))
            .isEqualTo("java/spring/boot3/upgradespringboot_3_4-moderne-edition")
        assertThat(getRecipePath("org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_4"))
            .isEqualTo("java/spring/boot3/upgradespringboot_3_4-community-edition")

        // Spring Security 6.5 - both exist, so both get suffixes
        assertThat(getRecipePath("io.moderne.java.spring.security6.UpgradeSpringSecurity_6_5"))
            .isEqualTo("java/spring/security6/upgradespringsecurity_6_5-moderne-edition")
        assertThat(getRecipePath("org.openrewrite.java.spring.security6.UpgradeSpringSecurity_6_5"))
            .isEqualTo("java/spring/security6/upgradespringsecurity_6_5-community-edition")

        // Hibernate 6.6 - both exist, so both get suffixes
        assertThat(getRecipePath("io.moderne.hibernate.UpgradeHibernate_6_6"))
            .isEqualTo("hibernate/upgradehibernate_6_6-moderne-edition")
        assertThat(getRecipePath("org.openrewrite.hibernate.UpgradeHibernate_6_6"))
            .isEqualTo("hibernate/upgradehibernate_6_6-community-edition")
    }

    @Test
    fun nonConflictingRecipesHaveNoSuffix() {
        // When only one version exists, no suffix should be added
        val recipes = listOf(
            "org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_3",  // Only community exists
            "io.moderne.java.spring.boot3.UpgradeSpringBoot_3_5"       // Only moderne exists
        )
        initializeConflictDetection(recipes)

        // Spring Boot 3.3 - only community exists, no suffix
        assertThat(getRecipePath("org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_3"))
            .isEqualTo("java/spring/boot3/upgradespringboot_3_3")

        // Spring Boot 3.5 - only moderne exists, no suffix
        assertThat(getRecipePath("io.moderne.java.spring.boot3.UpgradeSpringBoot_3_5"))
            .isEqualTo("java/spring/boot3/upgradespringboot_3_5")
    }

    @Test
    fun conflictingPathsAreDistinct() {
        // Verify that conflicting recipes end up with different paths
        val recipes = listOf(
            "io.moderne.java.spring.security6.UpgradeSpringSecurity_6_5",
            "org.openrewrite.java.spring.security6.UpgradeSpringSecurity_6_5"
        )
        initializeConflictDetection(recipes)

        val modernePath = getRecipePath("io.moderne.java.spring.security6.UpgradeSpringSecurity_6_5")
        val communityPath = getRecipePath("org.openrewrite.java.spring.security6.UpgradeSpringSecurity_6_5")

        assertThat(modernePath)
                .isNotEqualTo(communityPath)
                .endsWith("-moderne-edition")
        assertThat(communityPath).endsWith("-community-edition")
    }

    @Test
    fun hasConflictReturnsTrueForConflictingRecipes() {
        val recipes = listOf(
            "io.moderne.java.spring.boot3.UpgradeSpringBoot_3_4",
            "org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_4",
            "org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_3"
        )
        initializeConflictDetection(recipes)

        assertThat(RecipeMarkdownGenerator.hasConflict("io.moderne.java.spring.boot3.UpgradeSpringBoot_3_4")).isTrue()
        assertThat(RecipeMarkdownGenerator.hasConflict("org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_4")).isTrue()
        assertThat(RecipeMarkdownGenerator.hasConflict("org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_3")).isFalse()
    }

    @Test
    fun thirdPartyRecipesUnaffected() {
        // Third-party recipes should never get edition suffixes
        val recipes = listOf(
            "com.google.errorprone.SomeRecipe",
            "org.apache.camel.SomeRecipe"
        )
        initializeConflictDetection(recipes)

        assertThat(getRecipePath("com.google.errorprone.SomeRecipe"))
            .isEqualTo("com/google/errorprone/somerecipe")
        assertThat(getRecipePath("org.apache.camel.SomeRecipe"))
            .isEqualTo("org/apache/camel/somerecipe")
    }

    @Test
    fun leafMatchingParentDirGetsRecipeSuffix() {
        // Docusaurus treats codequality/codequality.md as the directory index,
        // colliding with codequality/README.md. The recipe should get a "-recipe" suffix.
        val recipes = listOf(
            "OpenRewrite.Recipes.CodeQuality.CodeQuality",
            "OpenRewrite.Recipes.CodeQuality.SomeRecipe",
            "OpenRewrite.Recipes.Search.FindSomething"  // Leaf != parent, no suffix
        )
        initializeConflictDetection(recipes)

        // CodeQuality.CodeQuality -> codequality/codequality collides, gets suffix
        assertThat(getRecipePath("OpenRewrite.Recipes.CodeQuality.CodeQuality"))
            .isEqualTo("csharp/recipes/codequality/codequality-recipe")

        // Child recipes where leaf != parent are unaffected
        assertThat(getRecipePath("OpenRewrite.Recipes.CodeQuality.SomeRecipe"))
            .isEqualTo("csharp/recipes/codequality/somerecipe")
        assertThat(getRecipePath("OpenRewrite.Recipes.Search.FindSomething"))
            .isEqualTo("csharp/recipes/search/findsomething")
    }

    @Test
    fun leafMatchingParentAlsoAppliesToJavaRecipes() {
        // The existing assertj.Assertj case is handled by manual override,
        // but the generic detection should also catch it
        val recipes = listOf(
            "org.openrewrite.java.testing.cleanup.Cleanup"
        )
        initializeConflictDetection(recipes)

        assertThat(getRecipePath("org.openrewrite.java.testing.cleanup.Cleanup"))
            .isEqualTo("java/testing/cleanup/cleanup-recipe")
    }

    @Test
    fun findOriginHandlesCSharpSearchScheme() {
        val syntheticUri = URI.create("csharp-search://recipes-code-quality")
        val origin = RecipeOrigin("io.moderne.recipe", "recipes-code-quality", "0.1.0", syntheticUri)
        origin.license = Licenses.Proprietary
        val origins = mapOf(syntheticUri to origin)

        val source = URI.create("csharp-search://recipes-code-quality/org.openrewrite.csharp.cleanup.SomeRecipe")
        val found = RecipeMarkdownGenerator.findOrigin(source, "org.openrewrite.csharp.cleanup.SomeRecipe", origins)

        assertThat(found).isNotNull
        assertThat(found!!.artifactId).isEqualTo("recipes-code-quality")
    }

    private fun initializeConflictDetection(recipeNames: List<String>) {
        val descriptors = recipeNames.map { createDescriptor(it) }
        RecipeMarkdownGenerator.initializeConflictDetection(descriptors)
    }

    private fun getRecipePath(recipeName: String): String {
        return RecipeMarkdownGenerator.getRecipePath(createDescriptor(recipeName))
    }

    @Test
    fun asYamlIncludesPreconditions() {
        initializeConflictDetection(emptyList())

        val precondition = RecipeDescriptor(
            "org.openrewrite.java.search.HasJavaVersion",
            "Has Java version",
            "",
            "Check Java version",
            mutableSetOf(),
            java.time.Duration.ZERO,
            mutableListOf(OptionDescriptor("version", "String", "Version", "The version", null, null, false, "21.X")),
            mutableListOf(),  // preconditions
            mutableListOf(),  // recipeList
            mutableListOf(),
            mutableListOf(),
            mutableListOf(),
            mutableListOf(),
            URI.create("https://example.com/recipe")
        )

        val subRecipe = RecipeDescriptor(
            "org.openrewrite.java.spring.AddSpringProperty",
            "Add Spring property",
            "",
            "Add a property",
            mutableSetOf(),
            java.time.Duration.ZERO,
            mutableListOf(
                OptionDescriptor("property", "String", "Property", "The property", null, null, true, "spring.threads.virtual.enabled"),
                OptionDescriptor("value", "String", "Value", "The value", null, null, true, "true")
            ),
            mutableListOf(),
            mutableListOf(),
            mutableListOf(),
            mutableListOf(),
            mutableListOf(),
            mutableListOf(),
            URI.create("https://example.com/recipe")
        )

        val descriptor = RecipeDescriptor(
            "org.openrewrite.java.spring.boot3.EnableVirtualThreads",
            "Enable Virtual Threads on Java 21",
            "",
            "Set spring.threads.virtual.enabled to true.",
            mutableSetOf(),
            java.time.Duration.ZERO,
            mutableListOf(),
            mutableListOf(precondition),  // preconditions
            mutableListOf(subRecipe),     // recipeList
            mutableListOf(),
            mutableListOf(),
            mutableListOf(),
            mutableListOf(),
            URI.create("https://example.com/recipe")
        )

        val yaml = descriptor.asYaml()
        assertThat(yaml).contains("preconditions:")
        assertThat(yaml).contains("  - org.openrewrite.java.search.HasJavaVersion:")
        assertThat(yaml).contains("      version: 21.X")
        assertThat(yaml).contains("recipeList:")
        assertThat(yaml).contains("  - org.openrewrite.java.spring.AddSpringProperty:")
        assertThat(yaml).contains("      property: spring.threads.virtual.enabled")

        // preconditions should appear before recipeList
        assertThat(yaml.indexOf("preconditions:")).isLessThan(yaml.indexOf("recipeList:"))
    }

    @Test
    fun asYamlOmitsPreconditionsWhenEmpty() {
        initializeConflictDetection(emptyList())

        val descriptor = createDescriptor("org.openrewrite.test.SomeRecipe")
        val yaml = descriptor.asYaml()
        assertThat(yaml).doesNotContain("preconditions:")
    }

    private fun createDescriptor(recipeName: String): RecipeDescriptor {
        return RecipeDescriptor(
            recipeName,
            recipeName,
            "",
            "Test recipe",
            mutableSetOf(),
            java.time.Duration.ZERO,
            mutableListOf(),
            mutableListOf(),
            mutableListOf(),
            mutableListOf(),
            mutableListOf(),
            mutableListOf(),
            URI.create("https://example.com/recipe")
        )
    }
}
