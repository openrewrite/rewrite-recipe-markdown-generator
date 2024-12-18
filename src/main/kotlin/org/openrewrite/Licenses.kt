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

    if (recipeOrigin.groupId == "org.openrewrite") {
        return License.Apache2
    }
    return when {
        apache2.contains(recipeOrigin.artifactId) -> License.Apache2
        msal.contains(recipeOrigin.artifactId) -> License.MSAL
        else -> License.Proprietary
    }
}
