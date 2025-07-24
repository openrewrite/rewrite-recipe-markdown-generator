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
fun RecipeDescriptor.escapeDisplayName(context: EscapeContext = EscapeContext.MARKDOWN_CODE): String {
    var result = displayName
    
    // Always remove URLs in markdown format [text](url)
    result = result.replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1")
    
    when (context) {
        EscapeContext.MARKDOWN_LINK -> {
            // For use in markdown links - use backticks
            result = result.replace("<script>", "`<script>`")
        }
        
        EscapeContext.MARKDOWN_CODE -> {
            // Default behavior - wrap problematic tags in backticks
            result = result.replace("<script>", "`<script>`")
            result = result.replace("<p>", "`<p>`")
        }
        
        EscapeContext.MARKDOWN_ESCAPED -> {
            // Escape with backslashes for markdown
            result = result.replace("<", "\\<")
            result = result.replace(">", "\\>")
        }
        
        EscapeContext.HTML_ENTITIES -> {
            // Use HTML entities
            result = result.replace("&", "&amp;")
            result = result.replace("<", "&lt;")
            result = result.replace(">", "&gt;")
            result = result.replace("\"", "&quot;")
            // Special case: preserve backtick-wrapped code
            result = result.replace("`&lt;", "`<")
            result = result.replace("&gt;`", ">`")
        }
        
        EscapeContext.SIDEBAR_LABEL -> {
            // For sidebar - remove backticks, escape quotes, add spaces
            result = result.replace("`", "")
            result = result.replace("\"", "\\\"")
            result = result.replace("<script>", "<script >")
            result = result.replace("<p>", "< p >")
        }
        
        EscapeContext.RECIPE_PAGE -> {
            // For recipe pages - use comment style
            result = result.replace("<p>", "< p >")
            result = result.replace("<script>", "//<script//>")
        }
        
        EscapeContext.PLAIN -> {
            // Just basic escaping - remove backticks only
            result = result.replace("`", "")
        }
    }
    
    return result
}

fun RecipeDescriptor.asYaml(): String {
    val s = StringBuilder()
    s.appendLine("""
---
type: specs.openrewrite.org/v1beta/recipe
name: $name
displayName: $displayName
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
