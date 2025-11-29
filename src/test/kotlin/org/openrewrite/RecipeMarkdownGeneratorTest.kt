package org.openrewrite

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import picocli.CommandLine
import java.io.PrintWriter
import java.io.StringWriter
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
        )
        assertThat(exitCode).isEqualTo(0)

        val latestVersionsMd = tempDir.resolve("latest-versions-of-every-openrewrite-module.md")
        assertTrue(latestVersionsMd.toFile().exists())
        val contents = Files.readString(latestVersionsMd)
        assertTrue(contents.contains("2.x"), contents)
    }

    @Test
    fun getRecipePathForSpringBootUpgrades() {
        // Test existing Spring Boot 3.4 versions (both Moderne and OpenRewrite)
        val moderneRecipe34 = getRecipePath("io.moderne.java.spring.boot3.UpgradeSpringBoot_3_4")
        val communityRecipe34 = getRecipePath("org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_4")

        assertThat(moderneRecipe34).isEqualTo("java/spring/boot3/upgradespringboot_3_4-moderne-edition")
        assertThat(communityRecipe34).isEqualTo("java/spring/boot3/upgradespringboot_3_4-community-edition")

        // Test future Spring Boot 4.x versions
        val springBoot4Moderne = getRecipePath("io.moderne.java.spring.boot4.UpgradeSpringBoot_4_0")
        val springBoot4Community = getRecipePath("org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_2")

        assertThat(springBoot4Moderne).isEqualTo("java/spring/boot4/upgradespringboot_4_0-moderne-edition")
        assertThat(springBoot4Community).isEqualTo("java/spring/boot4/upgradespringboot_4_2-community-edition")

        // Test future Spring Boot 3.x minor versions
        val springBoot35Moderne = getRecipePath("io.moderne.java.spring.boot3.UpgradeSpringBoot_3_5")
        assertThat(springBoot35Moderne).isEqualTo("java/spring/boot3/upgradespringboot_3_5-moderne-edition")

        // No change for recipes up to 3.3
        val springBoot33Community = getRecipePath("org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_3")
        assertThat(springBoot33Community).isEqualTo("java/spring/boot3/upgradespringboot_3_3")

        // Test future Spring Boot 5.x versions
        val springBoot5Community = getRecipePath("org.openrewrite.java.spring.boot5.UpgradeSpringBoot_5_1")
        assertThat(springBoot5Community).isEqualTo("java/spring/boot5/upgradespringboot_5_1-community-edition")
    }

    private fun getRecipePath(recipeName: String): String {
        return RecipeMarkdownGenerator.getRecipePath(
            org.openrewrite.config.RecipeDescriptor(
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
                null
            )
        )
    }
}
