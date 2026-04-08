package org.openrewrite

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.URI

class RecipeOriginTest {
    @Test
    fun githubUrl() {
        val origin = RecipeOrigin("org.openrewrite.recipe", "rewrite-migrate-java", "2.4.2", URI.create("file:///tmp/foo"))
        origin.repositoryUrl = "https://github.com/openrewrite/rewrite-migrate-java/blob/main"

        val githubUrl = origin.githubUrl(
            "org.openrewrite.java.migrate.apache.commons.lang.ApacheCommonsStringUtilsRecipes\$AbbreviateRecipe",
            URI("https://github.com")
        )
        assertThat(githubUrl).isEqualTo("https://github.com/openrewrite/rewrite-migrate-java/blob/main/src/main/java/org/openrewrite/java/migrate/apache/commons/lang/ApacheCommonsStringUtils.java")
    }

    @Test
    fun coreLibGithubUrl() {
        val origin = RecipeOrigin("org.openrewrite", "rewrite-docker", "8.0.0", URI.create("file:///tmp/foo"))
        origin.repositoryUrl = "https://github.com/openrewrite/rewrite/blob/main"

        assertThat(origin.isFromCoreLibrary()).isTrue()

        val githubUrl = origin.githubUrl(
            "org.openrewrite.docker.search.FindEndOfLifeImages",
            URI("https://github.com")
        )
        assertThat(githubUrl).isEqualTo("https://github.com/openrewrite/rewrite/blob/main/rewrite-docker/src/main/java/org/openrewrite/docker/search/FindEndOfLifeImages.java")
    }

    @Test
    fun standaloneRepoIsNotCoreLibrary() {
        val origin = RecipeOrigin("org.openrewrite.recipe", "rewrite-migrate-java", "2.4.2", URI.create("file:///tmp/foo"))
        origin.repositoryUrl = "https://github.com/openrewrite/rewrite-migrate-java/blob/main"

        assertThat(origin.isFromCoreLibrary()).isFalse()
    }

    @Test
    fun csharpRecipeGithubUrl() {
        val origin = RecipeOrigin("io.moderne.recipe", "recipes-code-quality", "0.1.0", URI.create("csharp-search://recipes-code-quality"))
        origin.repositoryUrl = "https://github.com/moderneinc/recipes-csharp/blob/main/"

        val githubUrl = origin.githubUrl(
            "org.openrewrite.csharp.cleanup.SomeRecipe",
            URI.create("csharp-search://recipes-code-quality/org.openrewrite.csharp.cleanup.SomeRecipe")
        )
        assertThat(githubUrl).isEqualTo("https://github.com/search?type=code&q=repo:moderneinc/recipes-csharp+org.openrewrite.csharp.cleanup.SomeRecipe")
    }

    @Test
    fun blankRepositoryUrlIsNotCoreLibrary() {
        val origin = RecipeOrigin("org.openrewrite", "rewrite-java", "8.0.0", URI.create("file:///tmp/foo"))

        assertThat(origin.isFromCoreLibrary()).isFalse()
    }
}
