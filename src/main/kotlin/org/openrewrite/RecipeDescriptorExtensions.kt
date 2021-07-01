package org.openrewrite

import org.openrewrite.config.OptionDescriptor
import org.openrewrite.config.RecipeDescriptor

fun RecipeDescriptor.asYaml(): String {
    val s = StringBuilder()
    s.appendLine("""
        ---
        type: specs.openrewrite.org/v1beta/recipe
        name: $name
        displayName: $displayName
        description: $description
    """.trimIndent())
    if(tags.isNotEmpty()) {
        s.appendLine("tags:")
        for(tag in tags) {
            s.appendLine("  - $tag")
        }
    }
    if(options.isNotEmpty()) {
        for(option in options) {
            s.appendLine(option.asYaml())
        }
    }
    if(recipeList.isNotEmpty()) {
        s.appendLine("recipeList:")
        for(subRecipe in recipeList) {
            s.append("  - ${subRecipe.name}")
            if(subRecipe.options.isEmpty()) {
                s.appendLine()
            } else {
                s.appendLine(":")
            }
            for(subOption in subRecipe.options) {
                s.append(subOption.asYaml(3))
            }
        }
    }
    return s.toString()
}

fun OptionDescriptor.asYaml(indentation: Int = 0): String {
    if(value == null) {
        return ""
    }
    val prefixBuilder = StringBuilder()
    (0 until indentation).forEach { _ ->
        prefixBuilder.append("  ")
    }

    val prefix = prefixBuilder.toString()
    val formattedValue = if(value is Array<*>) {
        val asArray = value as Array<*>
        "[${asArray.joinToString(", ")}]"
    } else {
        value
    }
    return "$prefix$name: $formattedValue\n"
}
