package org.openrewrite

data class RecipeOption(
    val name: String,
    val type: String,
    val required: Boolean
): Comparable<RecipeOption> {
    override fun compareTo(other: RecipeOption): Int {
        if (this.name != other.name) return this.name.compareTo(other.name)
        if (this.type != other.type) return this.type.compareTo(other.type)
        if (this.required && !other.required) return 1
        if (!this.required && other.required) return -1

        return 0
    }
}