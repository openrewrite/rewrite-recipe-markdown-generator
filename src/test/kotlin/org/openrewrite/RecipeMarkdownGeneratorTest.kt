package org.openrewrite

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.openrewrite.config.Environment
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
    fun latestVersionsMultiEcosystemInstall(@TempDir tempDir: Path) {
        fun mk(g: String, a: String, v: String): RecipeOrigin {
            val o = RecipeOrigin(g, a, v, URI.create("file:///$a.jar"))
            o.repositoryUrl = "https://github.com/openrewrite/$a/blob/main/"
            o.license = Licenses.Apache2
            return o
        }
        val origins = listOf(
            mk("org.openrewrite.recipe", "rewrite-spring", "5.0.0"),
            mk("org.openrewrite", "rewrite-python", "1.2.3"),
            mk("org.openrewrite.recipe", "rewrite-migrate-python", "0.5.0"),
            mk("org.openrewrite", "rewrite-javascript", "0.9.0"),
            mk("io.moderne.recipe", "recipes-code-quality", "0.1.0"),
            mk("org.openrewrite.recipe", "recipes-go", "0.4.1"),
            mk("org.openrewrite", "rewrite-polyglot", "2.10.11"),
        )
        VersionWriter().createLatestVersionsMarkdown(tempDir, origins, "8.x", "2.x", "1.x", "6.x", "5.x", forModerneDocs = true)
        val out = Files.readString(tempDir.resolve("latest-versions-of-every-openrewrite-module.md"))
        assertThat(out).contains("mod config recipes jar install org.openrewrite.recipe:rewrite-spring:{{VERSION_ORG_OPENREWRITE_RECIPE_REWRITE_SPRING}}")
        assertThat(out).contains("mod config recipes jar install org.openrewrite.recipe:rewrite-spring:LATEST")
        assertThat(out).contains("mod config recipes pip install openrewrite=={{VERSION_ORG_OPENREWRITE_REWRITE_PYTHON}} openrewrite-migrate-python=={{VERSION_ORG_OPENREWRITE_RECIPE_REWRITE_MIGRATE_PYTHON}}")
        assertThat(out).contains("mod config recipes pip install openrewrite openrewrite-migrate-python")
        assertThat(out).contains("mod config recipes npm install @openrewrite/rewrite@{{VERSION_ORG_OPENREWRITE_REWRITE_JAVASCRIPT}}")
        assertThat(out).contains("mod config recipes npm install @openrewrite/rewrite")
        assertThat(out).contains("mod config recipes nuget install OpenRewrite.Recipes.CSharp.CodeQuality@{{VERSION_IO_MODERNE_RECIPE_RECIPES_CODE_QUALITY}}")
        assertThat(out).contains("mod config recipes nuget install OpenRewrite.Recipes.CSharp.CodeQuality")
        assertThat(out).contains("mod config recipes go install github.com/moderneinc/recipes-go@v{{VERSION_ORG_OPENREWRITE_RECIPE_RECIPES_GO}}")
        assertThat(out).contains("mod config recipes go install github.com/moderneinc/recipes-go")

        // The Moderne Installation GraphQL mutation must use installRecipesUniversal with a
        // RecipeBundleInput; loadRecipesAsync was removed from the Moderne API.
        assertThat(out).doesNotContain("loadRecipesAsync")
        assertThat(out).contains("mutation seedOpenRewriteArtifacts {")
        assertThat(out).contains("load_org_openrewrite_recipe_rewrite_spring: installRecipesUniversal(")
        assertThat(out).contains("bundle: { maven: { groupId: \"org.openrewrite.recipe\", artifactId: \"rewrite-spring\", version: \"LATEST\" } }")

        // Moderne docs include the proprietary moderne-recipe-bom row.
        assertThat(out).contains("io.moderne.recipe:moderne-recipe-bom")

        // C# modules appear in the version table by their NuGet identity, not a fabricated
        // Maven coordinate, and in the GraphQL mutation via the `nuget` bundle variant rather
        // than a fabricated Maven one.
        assertThat(out).contains("[OpenRewrite.Recipes.CSharp.CodeQuality]")
        assertThat(out).doesNotContain("[io.moderne.recipe:recipes-code-quality]")
        assertThat(out).doesNotContain("artifactId: \"recipes-code-quality\"")
        assertThat(out).contains("bundle: { nuget: { packageName: \"OpenRewrite.Recipes.CSharp.CodeQuality\", version: \"*-*\" } }")

        // Version-only modules keep their table row, but have no recipes to install.
        assertThat(out).contains("[org.openrewrite:rewrite-polyglot]")
        assertThat(out).doesNotContain("org.openrewrite:rewrite-polyglot:")
        assertThat(out).doesNotContain("artifactId: \"rewrite-polyglot\"")
    }

    @Test
    fun latestVersionsOpenRewriteDocsExcludeProprietaryButKeepMsal(@TempDir tempDir: Path) {
        fun mk(g: String, a: String, v: String, license: License): RecipeOrigin {
            val o = RecipeOrigin(g, a, v, URI.create("file:///$a.jar"))
            o.repositoryUrl = "https://github.com/openrewrite/$a/blob/main/"
            o.license = license
            return o
        }
        // A mix spanning all three license tiers and every install ecosystem.
        val origins = listOf(
            mk("org.openrewrite.recipe", "rewrite-spring", "5.0.0", Licenses.Apache2),        // Apache -> keep
            mk("org.openrewrite", "rewrite-python", "1.2.3", Licenses.MSAL),                  // MSAL pip -> keep
            mk("org.openrewrite.recipe", "rewrite-migrate-python", "0.5.0", Licenses.Proprietary), // pip -> drop
            mk("org.openrewrite", "rewrite-javascript", "0.9.0", Licenses.MSAL),              // MSAL npm -> keep
            mk("org.openrewrite.recipe", "rewrite-angular", "1.5.0", Licenses.Proprietary),   // npm -> drop
            mk("io.moderne.recipe", "rewrite-spring", "0.37.0", Licenses.Proprietary),        // jar -> drop
            mk("org.openrewrite.recipe", "recipes-go", "0.4.1", Licenses.Proprietary),        // go -> drop (whole command)
        )

        // OpenRewrite docs: proprietary dropped, Apache/MSAL kept.
        val orDir = tempDir.resolve("openrewrite")
        Files.createDirectories(orDir)
        VersionWriter().createLatestVersionsMarkdown(orDir, origins, "8.x", "2.x", "1.x", "6.x", "5.x", forModerneDocs = false)
        val orOut = Files.readString(orDir.resolve("latest-versions-of-every-openrewrite-module.md"))

        // Kept: Apache jar + both MSAL ecosystem packages.
        assertThat(orOut).contains("org.openrewrite.recipe:rewrite-spring:")
        assertThat(orOut).contains("mod config recipes pip install openrewrite==")
        assertThat(orOut).contains("mod config recipes npm install @openrewrite/rewrite@")
        // Dropped: every proprietary module, its table row, and the moderne-recipe-bom row.
        assertThat(orOut).doesNotContain("io.moderne.recipe:")
        assertThat(orOut).doesNotContain("openrewrite-migrate-python")
        assertThat(orOut).doesNotContain("angular")
        // recipes-go was the only Go module, so the whole `go install` command disappears.
        assertThat(orOut).doesNotContain("mod config recipes go install")
        assertThat(orOut).doesNotContain("load_io_moderne_recipe_rewrite_spring")

        // Moderne docs: everything present.
        val modDir = tempDir.resolve("moderne")
        Files.createDirectories(modDir)
        VersionWriter().createLatestVersionsMarkdown(modDir, origins, "8.x", "2.x", "1.x", "6.x", "5.x", forModerneDocs = true)
        val modOut = Files.readString(modDir.resolve("latest-versions-of-every-openrewrite-module.md"))

        assertThat(modOut).contains("io.moderne.recipe:rewrite-spring:")
        assertThat(modOut).contains("io.moderne.recipe:moderne-recipe-bom")
        assertThat(modOut).contains("openrewrite-migrate-python")
        assertThat(modOut).contains("recipes-angular")
        assertThat(modOut).contains("mod config recipes go install github.com/moderneinc/recipes-go")
    }

    @Test
    fun goModulesAreModerneDocsOnlyRegardlessOfManifestLicense() {
        // Go recipes are Moderne proprietary and must never reach docs.openrewrite.org. The exclusion keys
        // off the module list rather than the jar manifest, so a mis-stamped License-Url on the (metadata
        // only) Maven artifact cannot leak a whole ecosystem into the open-source docs.
        for (artifactId in GoRecipeLoader.GO_RECIPE_MODULES.keys) {
            val origin = RecipeOrigin("org.openrewrite.recipe", artifactId, "0.5.3", URI.create("file:///$artifactId.jar"))
                .apply { license = Licenses.Apache2 }
            assertThat(RecipeMarkdownGenerator.isModerneDocsOnly(origin))
                .describedAs("Go module %s must be excluded from the OpenRewrite docs", artifactId)
                .isTrue()
        }
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
            "org.apache.camel.SomeRecipe",
            "io.axoniq.framework.migration.UpgradeAxon4ToAxoniq5"
        )
        initializeConflictDetection(recipes)
        // Drive the hidden-category stripping off the real `root: true` metadata
        // (rewrite-core's core-categories.yml flags com, io, org, ... as root).
        RecipeMarkdownGenerator.initializeRootCategories(
            Environment.builder().scanRuntimeClasspath().build().listCategoryDescriptors()
        )

        // Leading root-category segments (com, io, org, ...) are stripped so these don't
        // surface as hidden top-level categories in the recipe catalog.
        assertThat(getRecipePath("com.google.errorprone.SomeRecipe"))
            .isEqualTo("google/errorprone/somerecipe")
        assertThat(getRecipePath("org.apache.camel.SomeRecipe"))
            .isEqualTo("apache/camel/somerecipe")
        assertThat(getRecipePath("io.axoniq.framework.migration.UpgradeAxon4ToAxoniq5"))
            .isEqualTo("axoniq/framework/migration/upgradeaxon4toaxoniq5")
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

    @Test
    fun findOriginHandlesGoSearchScheme() {
        val mavenUri = URI.create("file:///recipes-go.jar")
        val origin = RecipeOrigin("org.openrewrite.recipe", "recipes-go", "0.4.1", mavenUri)
        origin.license = Licenses.Proprietary
        val origins = mapOf(mavenUri to origin)

        val source = URI.create("go-search://recipes-go/org.openrewrite.golang.codequality.SimplifyBooleanExpression")
        val found = RecipeMarkdownGenerator.findOrigin(source, "org.openrewrite.golang.codequality.SimplifyBooleanExpression", origins)

        assertThat(found).isNotNull
        assertThat(found!!.artifactId).isEqualTo("recipes-go")
    }

    @Test
    fun golangRecipesMapToGolangDirectory() {
        // org.openrewrite.golang.* maps to golang/... via the existing org.openrewrite path logic.
        initializeConflictDetection(emptyList())
        assertThat(getRecipePath("org.openrewrite.golang.codequality.SimplifyBooleanExpression"))
            .isEqualTo("golang/codequality/simplifybooleanexpression")
    }

    @Test
    fun escapeMdxOutsideCodeSpansEscapesUnbalancedTagsInProse() {
        // The literal `<EntitySimpleName>` would be parsed by MDX as an unclosed JSX tag and
        // break the docs build (https://github.com/openrewrite/rewrite-docs changelog build).
        assertThat(escapeMdxOutsideCodeSpans("annotates the field with @EventTag(key = \"<EntitySimpleName>\")."))
            .isEqualTo("annotates the field with @EventTag(key = \"&lt;EntitySimpleName>\").")
    }

    @Test
    fun escapeMdxOutsideCodeSpansEscapesCurlyBracesInProse() {
        assertThat(escapeMdxOutsideCodeSpans("rewrite the configurations { } block"))
            .isEqualTo("rewrite the configurations \\{ \\} block")
    }

    @Test
    fun escapeMdxOutsideCodeSpansLeavesCodeSpansUntouched() {
        // Generics inside backticks are valid and must render verbatim, not as escaped entities.
        assertThat(escapeMdxOutsideCodeSpans("Replace `Predicate<T>` with `Predicate.not(x)`"))
            .isEqualTo("Replace `Predicate<T>` with `Predicate.not(x)`")
        assertThat(escapeMdxOutsideCodeSpans("the `java { }` extension block"))
            .isEqualTo("the `java { }` extension block")
    }

    @Test
    fun escapeMdxOutsideCodeSpansHandlesMixedContent() {
        assertThat(escapeMdxOutsideCodeSpans("set idType = <ResolvedType>.class via `autodetected(<IdType>.class)`"))
            .isEqualTo("set idType = &lt;ResolvedType>.class via `autodetected(<IdType>.class)`")
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
            mutableListOf(),
            URI.create("https://example.com/recipe")
        )
    }
}
