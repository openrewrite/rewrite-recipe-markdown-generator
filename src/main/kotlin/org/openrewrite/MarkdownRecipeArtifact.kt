package org.openrewrite

import java.util.TreeMap

data class MarkdownRecipeArtifact (
    val artifactId: String,
    val version: String,
    val markdownRecipeDescriptors: TreeMap<String, MarkdownRecipeDescriptor>,
) : Comparable<MarkdownRecipeArtifact> {
    override fun compareTo(other: MarkdownRecipeArtifact): Int {
        if (this.artifactId != other.artifactId) {
            return this.artifactId.compareTo(other.artifactId)
        }
        if (this.markdownRecipeDescriptors != other.markdownRecipeDescriptors) {
            return -1
        }
        return 0
    }
}
