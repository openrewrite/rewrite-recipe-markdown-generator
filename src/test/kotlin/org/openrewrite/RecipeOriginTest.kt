package org.openrewrite

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.net.URI

class RecipeOriginTest {
    @Test
    fun githubUrl() {
        val githubUrl = RecipeOrigin(
            "org.openrewrite.recipe", "rewrite-migrate-java", "2.4.2", URI.create("file:///tmp/foo")
        ).githubUrl(
            "org.openrewrite.java.migrate.apache.commons.lang.ApacheCommonsStringUtilsRecipes\$AbbreviateRecipe",
            URI("https://github.com")
        )
        assertEquals(
            "https://github.com/openrewrite/rewrite-migrate-java/blob/main/src/main/java/org/openrewrite/java/migrate/apache/commons/lang/ApacheCommonsStringUtils.java",
            githubUrl
        )
    }
}