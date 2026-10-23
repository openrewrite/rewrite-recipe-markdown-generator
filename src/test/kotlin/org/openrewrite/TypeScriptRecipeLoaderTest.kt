package org.openrewrite

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.URI

class TypeScriptRecipeLoaderTest {

    @Test
    fun versionAnchorUsesRegistryVersionForNpmOnlyModules() {
        val requested = mutableListOf<String>()
        val origins = TypeScriptRecipeLoader.buildVersionAnchorOrigins { npmPackage ->
            requested += npmPackage
            "0.3.0"
        }

        assertThat(requested).containsExactlyInAnyOrder(
            "@openrewrite/recipes-code-quality",
            "@openrewrite/recipes-date-libraries"
        )
        val origin = origins.getValue(URI.create("typescript-search://recipes-javascript-code-quality"))
        assertThat(origin.groupId).isEqualTo("io.moderne.recipe")
        assertThat(origin.version).isEqualTo("0.3.0")
        assertThat(origin.repositoryUrl).isEqualTo("https://github.com/moderneinc/recipes-javascript/blob/main/")
        assertThat(origin.license).isEqualTo(Licenses.Proprietary)
        assertThat(TypeScriptRecipeLoader.TYPESCRIPT_RECIPE_MODULES[origin.artifactId])
            .isEqualTo("@openrewrite/recipes-code-quality")
    }

    @Test
    fun versionAnchorSkipsUnresolvedAndUnselectedModules() {
        assertThat(TypeScriptRecipeLoader.buildVersionAnchorOrigins { null }).isEmpty()
        assertThat(TypeScriptRecipeLoader.buildVersionAnchorOrigins(setOf("rewrite-java")) { "0.3.0" }).isEmpty()
    }

    @Test
    fun codeGenomeNpmReadsTheLatestDistTagAndTarballUrl() {
        val packument = """{"name":"@openrewrite/recipes-code-quality","dist-tags":{"latest":"0.3.0","next":"0.4.0-20261023-171845"}}"""
        assertThat(CodeGenomeNpm.latestDistTag(packument)).isEqualTo("0.3.0")
        assertThat(CodeGenomeNpm.latestDistTag("""{"dist-tags":{}}""")).isNull()
        assertThat(CodeGenomeNpm.tarballUrl("@openrewrite/recipes-code-quality", "0.3.0"))
            .isEqualTo("https://artifacts.codegenomeproject.org/npm/@openrewrite/recipes-code-quality/-/recipes-code-quality-0.3.0.tgz")
    }

    @Test
    fun npmOnlyModulesDoNotCollideWithCSharpArtifactIds() {
        assertThat(TypeScriptRecipeLoader.TYPESCRIPT_RECIPE_MODULES.keys)
            .doesNotContainAnyElementsOf(CSharpRecipeLoader.CSHARP_RECIPE_MODULES.keys)
    }
}
