package org.openrewrite

import org.junit.jupiter.api.Assertions.assertEquals
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
        assertEquals(0, exitCode)

        val latestVersionsMd = tempDir.resolve("latest-versions-of-every-openrewrite-module.md")
        assertTrue(latestVersionsMd.toFile().exists())
        val contents = Files.readString(latestVersionsMd)
        assertTrue(contents.contains("2.x"), contents)
    }

    @Test
    fun testGetRecipePathForSpringBootUpgrades() {
        // Test existing Spring Boot 3.4 versions (both Moderne and OpenRewrite)
        val moderneRecipe34 = getRecipePath("io.moderne.java.spring.boot3.UpgradeSpringBoot_3_4")
        val communityRecipe34 = getRecipePath("org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_4")

        assertEquals("java/spring/boot3/upgradespringboot_3_4-moderne-edition", moderneRecipe34)
        assertEquals("java/spring/boot3/upgradespringboot_3_4-community-edition", communityRecipe34)

        // Test future Spring Boot 4.x versions
        val springBoot4Moderne = getRecipePath("io.moderne.java.spring.boot4.UpgradeSpringBoot_4_0")
        val springBoot4Community = getRecipePath("org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_2")

        assertEquals("java/spring/boot4/upgradespringboot_4_0-moderne-edition", springBoot4Moderne)
        assertEquals("java/spring/boot4/upgradespringboot_4_2-community-edition", springBoot4Community)

        // Test future Spring Boot 3.x minor versions
        val springBoot35Moderne = getRecipePath("io.moderne.java.spring.boot3.UpgradeSpringBoot_3_5")
        val springBoot36Community = getRecipePath("org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_6")

        assertEquals("java/spring/boot3/upgradespringboot_3_5-moderne-edition", springBoot35Moderne)
        assertEquals("java/spring/boot3/upgradespringboot_3_6-community-edition", springBoot36Community)

        // Test future Spring Boot 5.x versions
        val springBoot5Community = getRecipePath("org.openrewrite.java.spring.boot5.UpgradeSpringBoot_5_1")
        assertEquals("java/spring/boot5/upgradespringboot_5_1-community-edition", springBoot5Community)
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
                java.net.URI.create("test://test")
            )
        )
    }
}
