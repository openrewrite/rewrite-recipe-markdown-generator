package org.openrewrite.writers

import java.io.BufferedWriter
import java.nio.file.Path

/**
 * Base interface for all markdown writers
 */
interface MarkdownWriter {
    /**
     * Write content to the specified path
     */
    fun write(outputPath: Path)
}

/**
 * Extension functions for BufferedWriter to simplify markdown generation
 */
fun BufferedWriter.writeln(text: String = "") {
    write(text)
    newLine()
}

fun BufferedWriter.useAndApply(withFun: BufferedWriter.() -> Unit): Unit = use { it.apply(withFun) }