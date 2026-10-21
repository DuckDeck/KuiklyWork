package com.ss.kuiklywork

internal const val NNCOS_HOST = "https://www.nncos.com"

internal data class CosImageItem(
    val imageUrl: String,
    val title: String,
    val detailUrl: String,
    val categoryName: String,
    val publishedAt: String,
    val isVip: Boolean = false,
    val downloaded: Boolean = false
)

internal data class CosPageResult(
    val items: List<CosImageItem>,
    val nextPageUrl: String?
)

internal fun nncosSearchUrl(keyword: String): String {
    val hex = "0123456789ABCDEF"
    val encoded = buildString {
        keyword.trim().encodeToByteArray().forEach { byte ->
            val value = byte.toInt() and 0xFF
            if (value in 65..90 || value in 97..122 || value in 48..57 ||
                value == 45 || value == 46 || value == 95 || value == 126
            ) {
                append(value.toChar())
            } else {
                append('%')
                append(hex[value shr 4])
                append(hex[value and 0xF])
            }
        }
    }
    return "$NNCOS_HOST/?cat=&s=$encoded"
}

internal fun parseNncosImages(html: String): CosPageResult {
    val articleRegex = Regex("<article\\b[^>]*class=[\"'][^\"']*post-item[^\"']*post-grid[^\"']*[\"'][^>]*>[\\s\\S]*?</article>", RegexOption.IGNORE_CASE)
    val anchorRegex = Regex("<a\\b(?=[^>]*\\bdata-bg\\s*=)[^>]*>", RegexOption.IGNORE_CASE)
    val items = mutableListOf<CosImageItem>()
    val seenUrls = mutableSetOf<String>()
    articleRegex.findAll(html).forEach { article ->
        val anchor = anchorRegex.find(article.value)?.value ?: return@forEach
        val imageUrl = anchor.extractCosHtmlAttr("data-bg")?.toAbsoluteCosUrl() ?: return@forEach
        val detailUrl = anchor.extractCosHtmlAttr("href")?.toAbsoluteCosUrl() ?: return@forEach
        if (!seenUrls.add(imageUrl)) return@forEach
        val title = anchor.extractCosHtmlAttr("title")
            ?.decodeCosHtmlEntities()?.trim().orEmpty().ifEmpty { "半次元图" }
        items.add(CosImageItem(
            imageUrl, title, detailUrl,
            article.value.extractCosCategoryName(),
            article.value.extractCosPublishedAt(),
            article.value.hasCosVipIcon()
        ))
    }
    val nextPageUrl = Regex("<a\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(html)
        .map { it.value }
        .firstOrNull { tag ->
            tag.extractCosHtmlAttr("class")?.split(Regex("\\s+"))
                ?.any { it.equals("page-next", ignoreCase = true) } == true
        }
        ?.extractCosHtmlAttr("href")
        ?.takeIf { it.isNotBlank() }
        ?.toAbsoluteCosUrl()
    return CosPageResult(items, nextPageUrl)
}

private fun String.extractCosHtmlAttr(name: String): String? {
    return Regex("\\b$name\\s*=\\s*([\"'])(.*?)\\1", RegexOption.IGNORE_CASE)
        .find(this)?.groupValues?.getOrNull(2)
}

private fun String.toAbsoluteCosUrl(): String {
    val url = decodeCosHtmlEntities()
    return when {
        url.startsWith("//") -> "https:$url"
        url.startsWith("http://") || url.startsWith("https://") -> url
        url.startsWith("/") -> "$NNCOS_HOST$url"
        else -> "$NNCOS_HOST/$url"
    }
}

private fun String.decodeCosHtmlEntities(): String {
    return replace("&amp;", "&")
        .replace("&#038;", "&")
        .replace("&#38;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")
}

private fun String.extractCosCategoryName(): String {
    val categoryBlock = Regex(
        "<div\\b[^>]*class=[\"'][^\"']*entry-category-dot[^\"']*[\"'][^>]*>[\\s\\S]*?</div>",
        RegexOption.IGNORE_CASE
    ).find(this)?.value ?: return ""
    return Regex("<a\\b[^>]*>([\\s\\S]*?)</a>", RegexOption.IGNORE_CASE)
        .find(categoryBlock)?.groupValues?.getOrNull(1)
        ?.replace(Regex("<[^>]+>"), "")
        ?.decodeCosHtmlEntities()?.trim().orEmpty()
}

private fun String.extractCosPublishedAt(): String {
    return Regex("<time\\b[^>]*class=[\"'][^\"']*pub-date[^\"']*[\"'][^>]*>([\\s\\S]*?)</time>", RegexOption.IGNORE_CASE)
        .find(this)?.groupValues?.getOrNull(1)
        ?.replace(Regex("<[^>]+>"), "")
        ?.decodeCosHtmlEntities()?.trim().orEmpty()
}

private fun String.hasCosVipIcon(): Boolean {
    return Regex("<[^>]+class=[\"'][^\"']*entry-vip-icon[^\"']*[\"']", RegexOption.IGNORE_CASE)
        .containsMatchIn(this)
}
