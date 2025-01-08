package org.openrewrite

enum class License {
    Apache2,
    MSAL,
    Proprietary
}

fun getLicense(recipeOrigin: RecipeOrigin): License {
    val apache2 = setOf(
        "rewrite-all",
        "rewrite-analysis",
        "rewrite-generative-ai",
        "rewrite-java-dependencies",
        "rewrite-kotlin",
        "rewrite-liberty",
        "rewrite-micronaut",
        "rewrite-openapi",
        "rewrite-quarkus",
        "rewrite-recommendations",
        "rewrite-third-party",
    )

    val msal = setOf(
        "rewrite-apache",
        "rewrite-csharp",
        "rewrite-csharp-recipes",
        "rewrite-cucumber-jvm",
        "rewrite-docker",
        "rewrite-feature-flags",
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
    )

    // Non-exhaustive list of proprietary recipes; such that those under "org.openrewrite" are assigned correctly
    val proprietary = setOf(
        "rewrite-cobol",
        "rewrite-java-security",
        "rewrite-polyglot",
        "rewrite-remote",
    )

    return when {
        // Moderne internal recipes are proprietary, and checked first to pick up internal complementary modules
        recipeOrigin.groupId == "io.moderne.recipe" || proprietary.contains(recipeOrigin.artifactId) -> License.Proprietary
        // Then check for MSAL modules, which might publish under "org.openrewrite" or "org.openrewrite.recipe"
        msal.contains(recipeOrigin.artifactId) -> License.MSAL
        // Finally, check for Apache2 recipes
        recipeOrigin.groupId == "org.openrewrite" || apache2.contains(recipeOrigin.artifactId) -> License.Apache2
        // Anything not explicitly declared is proprietary
        else -> License.Proprietary
    }
}
