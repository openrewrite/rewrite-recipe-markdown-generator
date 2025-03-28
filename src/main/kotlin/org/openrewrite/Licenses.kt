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
