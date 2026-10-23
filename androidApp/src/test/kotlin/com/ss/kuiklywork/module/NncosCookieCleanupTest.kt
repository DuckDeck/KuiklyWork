package com.ss.kuiklywork.module

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NncosCookieCleanupTest {
    private val authName = "wordpress_0123456789abcdef0123456789abcdef"
    private val secureAuthName = "wordpress_sec_0123456789abcdef0123456789abcdef"

    @Test
    fun discoversBackendCookiesThatAreNotVisibleAtTheRoot() {
        val expired = mutableListOf<String>()
        expireNncosCookies(
            readCookies = { url ->
                when (url) {
                    "https://www.nncos.com/wp-admin/" -> "$authName=old; $secureAuthName=old"
                    "https://www.nncos.com/wp-content/plugins/" -> "$authName=old; $secureAuthName=old"
                    else -> ""
                }
            },
            expireCookie = { _, cookie -> expired.add(cookie) }
        )
        for (name in listOf(authName, secureAuthName)) {
            for (path in listOf("/wp-admin", "/wp-content/plugins")) {
                assertTrue(expired.any { it.startsWith("$name=") && "Path=$path;" in it })
            }
        }
    }

    @Test
    fun orphanRepairLeavesAnonymousAndOtherSiteCookiesAlone() {
        val expired = mutableListOf<String>()
        expireNncosCookies(
            readCookies = { url ->
                assertTrue(url.startsWith("https://www.nncos.com/") || url.startsWith("https://nncos.com/"))
                if (url.contains("/wp-admin")) "$authName=old; RIMINI_SESSION=guest; userLanguage=zh"
                else "RIMINI_SESSION=guest; userLanguage=zh"
            },
            expireCookie = { url, cookie ->
                assertTrue(url == "https://www.nncos.com/" || url == "https://nncos.com/")
                expired.add(cookie)
            },
            orphanedAuthOnly = true
        )
        assertTrue(expired.any { it.startsWith("$authName=") })
        assertFalse(expired.any { it.startsWith("RIMINI_SESSION=") || it.startsWith("userLanguage=") })
    }

    @Test
    fun openingLoginDoesNotClearAnExistingFrontendLogin() {
        val expired = mutableListOf<String>()
        expireNncosCookies(
            readCookies = { "wordpress_logged_in_hash=valid; $authName=valid" },
            expireCookie = { _, cookie -> expired.add(cookie) },
            orphanedAuthOnly = true
        )
        assertTrue(expired.isEmpty())
    }

    @Test
    fun fullInvalidationAlsoExpiresRootAndParentDomainCookies() {
        val expired = mutableListOf<String>()
        expireNncosCookies(
            readCookies = { "wordpress_logged_in_hash=old; RIMINI_SESSION=old" },
            expireCookie = { _, cookie -> expired.add(cookie) }
        )
        assertTrue(expired.any {
            it.startsWith("wordpress_logged_in_hash=") && "Path=/;" in it && "Max-Age=0" in it
        })
        assertTrue(expired.any {
            it.startsWith("RIMINI_SESSION=") && "Domain=nncos.com;" in it && "Max-Age=0" in it
        })
    }
}
