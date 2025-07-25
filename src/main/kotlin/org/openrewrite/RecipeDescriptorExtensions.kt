package org.openrewrite

import org.openrewrite.config.OptionDescriptor
import org.openrewrite.config.RecipeDescriptor

fun RecipeDescriptor.displayNameEscaped(): String {
    return escape(displayName)
        // Always remove URLs in markdown format [text](url)
        .replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1")
}
fun RecipeDescriptor.descriptionEscaped(): String {
    if (description.isNullOrBlank()) {
        return ""
    }
    return escape(description)
        .replace("\n", " ")
        .trim()
}
private fun escape(string: String): String = string
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")

fun RecipeDescriptor.asYaml(): String {
    val s = StringBuilder()
    s.appendLine("""
---
type: specs.openrewrite.org/v1beta/recipe
name: $name
displayName: ${displayNameEscaped()}
description: |
  ${descriptionEscaped()}
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
    (0 until indentation).forEach { _  ->
        prefixBuilder.append("  ")
    }

    val prefix = prefixBuilder.toString()
    val formattedValue = if (value is Array<*>) {
        val asArray =  value as Array<*>
        "[${asArray.joinToString(", ")}]"
    } else if (value == "*") {
        "\"*\""
    } else {
        value
    }
    return "$prefix$name: $formattedValue\n"
}
