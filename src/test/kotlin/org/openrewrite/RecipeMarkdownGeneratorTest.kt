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
}
