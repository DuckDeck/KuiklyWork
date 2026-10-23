package com.ss.kuiklywork.base

/** Null means the page did not provide a usable account label. */
internal fun parseNncosLoginState(html: String): Boolean? {
    val content = html.replace(
        Regex("<!--[\\s\\S]*?-->|<(script|style)\\b[^>]*>[\\s\\S]*?</\\1\\s*>", RegexOption.IGNORE_CASE),
        ""
    )
    val icon = Regex("<i\\b[^>]*>([\\s\\S]*?)</i\\s*>", RegexOption.IGNORE_CASE)
        .findAll(content)
        .firstOrNull { match ->
            val openingTag = match.value.substringBefore('>')
            val classes = Regex("(?:^|\\s)class\\s*=\\s*([\"'])(.*?)\\1", RegexOption.IGNORE_CASE)
                .find(openingTag)?.groupValues?.getOrNull(2)?.split(Regex("\\s+"))
            classes?.any { it.equals("icon-people", ignoreCase = true) } == true
        } ?: return null
    var label = icon.groupValues[1].nncosAccountText()
    if (label.isEmpty()) {
        // The live site uses <a><i class="icon-people"></i> 登录</a>.
        val accountLink = Regex("<(a|button)\\b[^>]*>([\\s\\S]*?)</\\1\\s*>", RegexOption.IGNORE_CASE)
            .findAll(content)
            .firstOrNull { it.range.first <= icon.range.first && it.range.last >= icon.range.last }
        label = accountLink?.groupValues?.getOrNull(2)?.nncosAccountText()
            ?: content.substring(icon.range.last + 1).substringBefore('<').nncosAccountText()
    }
    return label.takeIf { it.isNotEmpty() }?.let { it != "登录" }
}

private fun String.nncosAccountText(): String {
    return replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ", ignoreCase = true)
        .replace("&amp;", "&", ignoreCase = true)
        .replace(Regex("&#(x[0-9a-f]+|[0-9]+);", RegexOption.IGNORE_CASE)) { match ->
            val value = match.groupValues[1]
            val code = if (value.startsWith("x", ignoreCase = true)) {
                value.substring(1).toIntOrNull(16)
            } else value.toIntOrNull()
            code?.takeIf { it in 0..0xFFFF }?.toChar()?.toString() ?: match.value
        }
        .filterNot { it.isWhitespace() || it == '\u200B' || it == '\uFEFF' }
}
