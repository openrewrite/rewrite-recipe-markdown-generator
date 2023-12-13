package org.openrewrite

import com.fasterxml.jackson.annotation.JsonIgnore

data class RecipeOption(
    val name: String,
    val type: String,
    @JsonIgnore
    val example: String?,
    val required: Boolean
): Comparable<RecipeOption> {
    override fun compareTo(other: RecipeOption): Int {
        return compareBy(RecipeOption::name)
            .then(compareBy(RecipeOption::type))
            .then(compareBy(RecipeOption::example))
            .then(compareBy(RecipeOption::required))
            .compare(this, other)
    }
}