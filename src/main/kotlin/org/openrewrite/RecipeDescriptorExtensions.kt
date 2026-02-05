package org.openrewrite

import org.openrewrite.config.OptionDescriptor
import org.openrewrite.config.RecipeDescriptor

fun RecipeDescriptor.displayNameEscaped(): String =
    escapeHtml(displayName)
        // Always remove URLs in markdown format [text](url)
        .replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1")
        .trim() + edition()

fun RecipeDescriptor.displayNameEscapedMdx(): String =
    escapeMdx(displayName)
        // Always remove URLs in markdown format [text](url)
        .replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1")
        .trim() + edition()

// For MDX content (escapes curly braces)
fun RecipeDescriptor.descriptionEscaped(): String {
    if (description.isNullOrBlank()) {
        return ""
    }
    return escapeMdx(description)
        .replace("\n", " ")
        .trim()
}

// For YAML/code blocks (no curly brace escaping)
private fun RecipeDescriptor.descriptionEscapedHtml(): String {
    if (description.isNullOrBlank()) {
        return ""
    }
    return escapeHtml(description)
        .replace("\n", " ")
        .trim()
}

private fun RecipeDescriptor.edition(): String =
    when (name) {
        "io.moderne.java.spring.boot3.UpgradeSpringBoot_3_4",
        "io.moderne.java.spring.boot3.UpgradeSpringBoot_3_5",
            -> " (Moderne Edition)"

        "org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_4",
        "org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_5"
            -> " (Community Edition)"

        else ->
            if (name.startsWith("io.moderne.hibernate") ||
                name.startsWith("io.moderne.java.spring.boot4.UpgradeSpringBoot_")
            ) {
                " (Moderne Edition)"
            } else if (name.startsWith("org.openrewrite.hibernate") ||
                name.startsWith("org.openrewrite.java.spring.boot4.UpgradeSpringBoot_")
            ) {
                " (Community Edition)"
            } else {
                ""
            }
    }

// Escapes for HTML/basic markdown (no curly brace escaping - safe for YAML frontmatter)
fun escapeHtml(string: String): String = string
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")

// Escapes for MDX content (includes curly brace escaping - NOT safe for YAML)
// Escapes { and } with backslashes so MDX treats them as literal characters
fun escapeMdx(string: String): String = escapeHtml(string)
    .replace("{", "\\{")
    .replace("}", "\\}")

fun RecipeDescriptor.asYaml(): String {
    val s = StringBuilder()
    s.appendLine("""
---
type: specs.openrewrite.org/v1beta/recipe
name: $name
displayName: ${displayNameEscaped()}
description: |
  ${descriptionEscapedHtml()}
    """.trimIndent())
    if (tags.isNotEmpty()) {
        s.appendLine("tags:")
        for (tag in tags) {
            s.appendLine("  - $tag")
        }
    }
    if (options.isNotEmpty()) {
        for (option in options) {
            s.appendLine(option.asYaml())
        }
    }
    if (recipeList.isNotEmpty()) {
        s.appendLine("recipeList:")
        for (subRecipe in recipeList) {
            // https://github.com/openrewrite/rewrite-docs/issues/250
            if (subRecipe.name.contains("Bellwether")) {
                continue;
            }

            s.append("  - ${subRecipe.name}")
            if (subRecipe.options.isEmpty() || subRecipe.options.all { it.value == null }) {
                s.appendLine()
            } else {
                s.appendLine(":")
            }
            for (subOption in subRecipe.options) {
                s.append(subOption.asYaml(3))
            }
        }
    }
    return s.toString()
}

fun OptionDescriptor.asYaml(indentation: Int = 0): String {
    if (value == null) {
        return ""
    }
    val prefixBuilder = StringBuilder()
    (0 until indentation).forEach { _ ->
        prefixBuilder.append("  ")
    }

    val prefix = prefixBuilder.toString()
    val formattedValue = if (value is Array<*>) {
        val asArray = value as Array<*>
        "[${asArray.joinToString(", ")}]"
    } else if (value == "*") {
        "\"*\""
    } else {
        value
    }
    return "$prefix$name: $formattedValue\n"
}
