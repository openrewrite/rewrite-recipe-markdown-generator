package org.openrewrite

import java.util.*

data class ChangedRecipe(
    val artifactId: String,
    val name: String,
    val description: String,
    val docLink: String,
    val newOptions: TreeSet<RecipeOption>?,
    val oldOptions: TreeSet<RecipeOption>?,
): Comparable<ChangedRecipe> {
    override fun compareTo(other: ChangedRecipe): Int {
        if (this.artifactId != other.artifactId) {
            return this.artifactId.compareTo(other.artifactId)
        }
        if (this.name != other.name) {
            return this.name.compareTo(other.name)
        }
        if (this.description != other.description) {
            return this.description.compareTo(other.description)
        }
        if (this.newOptions != other.newOptions) {
            return -1
        }
        if (this.oldOptions != other.oldOptions) {
            return -1
        }
        return 0
    }
}
