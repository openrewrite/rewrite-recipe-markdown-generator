package org.openrewrite

import java.net.URI

data class License (val uri: URI, val name: String) {
    fun markdown(): String {
        return if (uri.toString().isEmpty()) {
            name
        } else {
            "[${name}](${uri})"
        }
    }
}

data object Licenses {
    private val Apache_URI = "https://www.apache.org/licenses/LICENSE-2.0"
    private val MSAL_URI = "https://docs.moderne.io/licensing/moderne-source-available-license"
    private val Proprietary_URI = "https://docs.moderne.io/licensing/overview"

    val Apache2 = License(URI(Apache_URI), "Apache License Version 2.0")
    val Proprietary = License(URI(Proprietary_URI), "Moderne Proprietary License")
    val MSAL = License(URI(MSAL_URI), "Moderne Source Available License")
    val Unknown = License(URI(""), "License Unknown")

    fun get(url: String?, name: String?): License = when {
        url == null -> Unknown
        url == Apache_URI -> Apache2
        url == MSAL_URI -> MSAL
        url == Proprietary_URI -> Proprietary
        name != null -> License(URI(url), name)
        else -> Unknown
    }
}
