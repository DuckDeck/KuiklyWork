package com.ss.kuiklywork.module

/**
 * CookieManager.getCookie only returns cookies applicable to the supplied URL.
 * WordPress backend authentication cookies also live outside the site root.
 */
internal fun expireNncosCookies(
    readCookies: (String) -> String?,
    expireCookie: (String, String) -> Unit,
    orphanedAuthOnly: Boolean = false
) {
    val origins = listOf("https://www.nncos.com", "https://nncos.com")
    val paths = listOf("/", "/wp-admin", "/wp-admin/", "/wp-content/plugins", "/wp-content/plugins/")
    val rootHeaders = origins.map { readCookies("$it/").orEmpty() }
    if (orphanedAuthOnly && rootHeaders.any { header ->
            header.split(';').any { pair ->
                pair.substringBefore('=').trim().startsWith("wordpress_logged_in_", ignoreCase = true) &&
                    pair.substringAfter('=', "").trim().isNotEmpty()
            }
        }
    ) return

    val names = (rootHeaders + origins.flatMap { origin ->
        paths.drop(1).map { path -> readCookies("$origin$path").orEmpty() }
    }).flatMap { it.split(';') }
        .map { it.substringBefore('=').trim() }
        .filter { it.isNotEmpty() }
        .filter { name ->
            !orphanedAuthOnly || Regex("^wordpress_(?:sec_)?[a-f0-9]{32}$", RegexOption.IGNORE_CASE).matches(name)
        }
        .distinct()

    origins.forEach { origin ->
        val host = origin.substringAfter("://")
        val domains = listOf("", "; Domain=$host", "; Domain=nncos.com").distinct()
        names.forEach { name ->
            paths.forEach { path ->
                domains.forEach { domain ->
                    expireCookie(
                        "$origin/",
                        "$name=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=$path$domain; Secure; HttpOnly"
                    )
                }
            }
        }
    }
}
