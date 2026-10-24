package org.openrewrite

import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64

/**
 * The Code Genome Project's npm registry, the source of truth for moderneinc/recipes-javascript's
 * packages: registry.npmjs.org stopped receiving their releases at 0.1.2. It takes the same credentials
 * as the Code Genome Project's Maven repository, which the `run` task passes on from Gradle properties.
 */
object CodeGenomeNpm {
    private const val REGISTRY = "https://artifacts.codegenomeproject.org/npm/"

    private val basicAuth: String? = run {
        val username = System.getenv("CODEGENOME_USERNAME")
        val password = System.getenv("CODEGENOME_PASSWORD")
        if (username.isNullOrEmpty() || password.isNullOrEmpty()) null
        else Base64.getEncoder().encodeToString("$username:$password".toByteArray())
    }

    /** The version the registry tags `latest`, or `null` without credentials or when it can't be reached. */
    fun latestVersion(packageName: String): String? {
        val auth = basicAuth ?: run {
            System.err.println("No Code Genome Project credentials (CODEGENOME_USERNAME/CODEGENOME_PASSWORD) to resolve $packageName")
            return null
        }
        return httpGet("$REGISTRY$packageName", mapOf("Authorization" to "Basic $auth"), ::latestDistTag)
    }

    internal fun latestDistTag(packument: String): String? =
        LENIENT_JSON_MAPPER.readTree(packument).path("dist-tags").path("latest").textValue()

    /** Installing from a tarball URL, rather than by version, leaves every other package on its own registry. */
    fun tarballUrl(packageName: String, version: String): String =
        "$REGISTRY$packageName/-/${packageName.substringAfterLast('/')}-$version.tgz"

    /** Lets `npm install` in [installDir] authenticate to the registry; returns the file to delete afterwards. */
    fun writeNpmrc(installDir: Path): Path? {
        val auth = basicAuth ?: return null
        return Files.writeString(installDir.resolve(".npmrc"), "//artifacts.codegenomeproject.org/npm/:_auth=$auth\n")
    }
}
