package org.openrewrite

import java.net.URI

data class License (val uri: URI, val name: String) {
    fun markdown() = "[${name}](${uri})"
}

data object Licenses {
    val Apache2 = License(URI("https://www.apache.org/licenses/LICENSE-2.0"), "Apache License Version 2.0")
    val MSAL = License(URI("https://docs.moderne.io/licensing/moderne-source-available-license"), "Moderne Source Available")
    val Proprietary = License(URI("https://docs.moderne.io/licensing/overview"), "Moderne Proprietary")
    val Unknown = License(URI(""), "License Unknown")

    fun get(url: String?, name: String?): License = when {
        url == null -> Unknown
        url == "https://www.apache.org/licenses/LICENSE-2.0" -> Apache2
        url == "https://docs.moderne.io/licensing/moderne-source-available-license" -> MSAL
        url == "https://docs.moderne.io/licensing/overview" -> Proprietary
        name != null -> License(URI(url), name)
        else -> Unknown
    }
}

//todo remove logic after all recipe artifacts have been released with MANIFEST.MF containing license information
fun getLicenseFallback(recipeOrigin: RecipeOrigin): License {
    val apache2 = setOf(
        "rewrite-all",
        "rewrite-analysis",
        "rewrite-generative-ai",
        "rewrite-java-dependencies",
        "rewrite-kotlin",
        "rewrite-liberty",
        "rewrite-micronaut",
        "rewrite-openapi",
        "rewrite-polyglot",
        "rewrite-quarkus",
        "rewrite-recommendations",
        "rewrite-third-party",
    )

    val msal = setOf(
        "rewrite-apache",
        "rewrite-codemods",
        "rewrite-csharp",
        "rewrite-csharp-recipes",
        "rewrite-cucumber-jvm",
        "rewrite-docker",
        "rewrite-feature-flags",
        "rewrite-github-actions",
        "rewrite-gitlab",
        "rewrite-hibernate",
        "rewrite-javascript",
        "rewrite-jenkins",
        "rewrite-logging-frameworks",
        "rewrite-micrometer",
        "rewrite-migrate-java",
        "rewrite-python",
        "rewrite-okhttp",
        "rewrite-spring",
        "rewrite-static-analysis",
        "rewrite-struts",
        "rewrite-testing-frameworks",
        "rewrite-rewrite",
    )

    // Non-exhaustive list of proprietary recipes; such that those under "org.openrewrite" are assigned correctly
    val proprietary = setOf(
        "rewrite-cobol",
        "rewrite-java-security",
        "rewrite-remote",
    )

    return when {
        // Moderne internal recipes are proprietary, and checked first to pick up internal complementary modules
        recipeOrigin.groupId == "io.moderne.recipe" || proprietary.contains(recipeOrigin.artifactId) -> Licenses.Proprietary
        // Then check for MSAL modules, which might publish under "org.openrewrite" or "org.openrewrite.recipe"
        msal.contains(recipeOrigin.artifactId) -> Licenses.MSAL
        // Finally, check for Apache2 recipes
        recipeOrigin.groupId == "org.openrewrite" || apache2.contains(recipeOrigin.artifactId) -> Licenses.Apache2
        // Anything not explicitly declared is proprietary
        else -> Licenses.Proprietary
    }
}
