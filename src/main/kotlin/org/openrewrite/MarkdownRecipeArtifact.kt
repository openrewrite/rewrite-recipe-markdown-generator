package org.openrewrite

import java.util.TreeSet

data class MarkdownRecipeArtifact (
    val artifactId: String,
    val version: String,
    val markdownRecipeDescriptors: TreeSet<MarkdownRecipeDescriptor>,
) : Comparable<MarkdownRecipeArtifact> {
    override fun compareTo(other: MarkdownRecipeArtifact): Int {
        if (this.artifactId != other.artifactId) return this.artifactId.compareTo(other.artifactId)
        if (this.version != other.version) return this.version.compareTo(other.version)
        if (this.markdownRecipeDescriptors != other.markdownRecipeDescriptors) return -1
        return 0
    }
}