package org.openrewrite

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class RecipeLoaderTest {
    
    @Test
    fun `loadRecipes with empty sources returns empty result`() {
        val loader = RecipeLoader()
        
        val result = loader.loadRecipes("", "")
        
        assertNotNull(result)
        assertTrue(result.allRecipeDescriptors.isEmpty())
        assertTrue(result.allCategoryDescriptors.isEmpty())
        assertTrue(result.allRecipes.isEmpty())
        assertTrue(result.recipeOrigins.isEmpty())
    }
    
    @Test
    fun `loadRecipes handles invalid classpath gracefully`() {
        val loader = RecipeLoader()
        
        // Should not throw exception with invalid classpath
        val result = loader.loadRecipes("", "invalid/path/to/jar")
        
        assertNotNull(result)
    }
    
    @Test
    fun `loadRecipes parses recipe sources correctly`(@TempDir tempDir: Path) {
        val loader = RecipeLoader()
        
        // Create dummy jar files for testing
        val jar1 = tempDir.resolve("test1.jar").toFile()
        val jar2 = tempDir.resolve("test2.jar").toFile()
        jar1.createNewFile()
        jar2.createNewFile()
        
        val recipeSources = "org.example:artifact1:1.0.0:${jar1.absolutePath};org.example:artifact2:2.0.0:${jar2.absolutePath}"
        
        val result = loader.loadRecipes(recipeSources, "")
        
        assertNotNull(result)
        assertEquals(2, result.recipeOrigins.size)
        
        val origins = result.recipeOrigins.values.toList()
        assertTrue(origins.any { it.groupId == "org.example" && it.artifactId == "artifact1" && it.version == "1.0.0" })
        assertTrue(origins.any { it.groupId == "org.example" && it.artifactId == "artifact2" && it.version == "2.0.0" })
    }
}