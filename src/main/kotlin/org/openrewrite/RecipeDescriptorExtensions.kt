package org.openrewrite

import org.openrewrite.config.OptionDescriptor
import org.openrewrite.config.RecipeDescriptor

enum class EscapeContext {
    MARKDOWN_LINK,       // For use in markdown links: [text](url)
    MARKDOWN_CODE,       // For use with backticks: `<script>`
    MARKDOWN_ESCAPED,    // For use with backslashes: \<script\>
    HTML_ENTITIES,       // For use with HTML entities: &lt;script&gt;
    SIDEBAR_LABEL,       // For sidebar formatting with spaces
    RECIPE_PAGE,         // For recipe pages with comment style
    PLAIN                // Just remove URLs, minimal escaping
}

/**
 * Extension function to escape special characters in recipe display names
 * consistently based on the context where they will be used.
 */
fun RecipeDescriptor.displayNameEscaped(): String {
    return displayName
        // Always remove URLs in markdown format [text](url)
        .replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1")
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}

fun RecipeDescriptor.asYaml(): String {
    val s = StringBuilder()
    s.appendLine("""
---
type: specs.openrewrite.org/v1beta/recipe
name: $name
displayName: ${displayNameEscaped()}
description: |
  ${description?.replace("\n", "\n  ")?.replace("```. [Source]", "```\n  [Source]") ?: ""}
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
