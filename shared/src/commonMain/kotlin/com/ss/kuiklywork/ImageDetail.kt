package com.ss.kuiklywork

import com.ss.kuiklywork.base.BasePager
import com.ss.kuiklywork.base.BridgeModule
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

private const val NETBIAN_HOST = "https://pic.netbian.com"
private const val DETAIL_TIMEOUT_TEXT = "\u8be6\u60c5\u52a0\u8f7d\u5931\u8d25\uff0c\u5df2\u663e\u793a\u5217\u8868\u56fe"

data class NetbianDetail(
    val imageUrl: String,
    val downloadUrl: String,
    val title: String,
    val categoryName: String,
    val categoryUrl: String,
    val size: String,
    val fileSize: String,
    val updatedAt: String
)

@Page("imageDetail")
internal class ImageDetailPage : BasePager() {

    var titleText by observable("")
    var detailUrl by observable("")
    var displayImageUrl by observable("")
    var downloadUrl by observable("")
    var categoryName by observable("")
    var categoryUrl by observable("")
    var imageSizeText by observable("")
    var fileSizeText by observable("")
    var updatedAtText by observable("")
    var statusText by observable("")
    var netbianLoggedIn by observable(false)
    var downloading by observable(false)
    private var listImageUrl = ""

    override fun created() {
        super.created()
        titleText = pagerData.params.optString("title", "")
        displayImageUrl = pagerData.params.optString("imageUrl", "")
        listImageUrl = pagerData.params.optString("listImageUrl", displayImageUrl)
        downloadUrl = displayImageUrl
        detailUrl = pagerData.params.optString("detailUrl", "")
        refreshLoginState()
        loadDetail()
    }

    override fun pageDidAppear() {
        super.pageDidAppear()
        refreshLoginState()
    }

    private fun refreshLoginState() {
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).getNetbianLoginState { response ->
            netbianLoggedIn = response?.optBoolean("isLoggedIn", false) ?: false
        }
    }

    private fun loadDetail() {
        if (detailUrl.isEmpty()) {
            return
        }
        statusText = "\u6b63\u5728\u52a0\u8f7d\u8be6\u60c5..."
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).fetchHtml(detailUrl) { response ->
            val html = response?.optString("body", "") ?: ""
            if (response?.optInt("code", -1) != 0 || html.isEmpty()) {
                statusText = DETAIL_TIMEOUT_TEXT
                return@fetchHtml
            }
            val detail = parseNetbianDetail(html, displayImageUrl, detailUrl, titleText)
            displayImageUrl = detail.imageUrl
            downloadUrl = detail.downloadUrl.ifEmpty { detail.imageUrl }
            categoryName = detail.categoryName
            categoryUrl = detail.categoryUrl
            imageSizeText = detail.size
            fileSizeText = detail.fileSize
            updatedAtText = detail.updatedAt
            if (detail.title.isNotEmpty()) {
                titleText = detail.title
            }
            statusText = ""
        }
    }

    fun downloadImage() {
        if (downloading) return
        if (!netbianLoggedIn) {
            acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).openNetbianLogin { response ->
                netbianLoggedIn = response?.optBoolean("isLoggedIn", false) ?: false
                if (netbianLoggedIn) {
                    downloadImage()
                }
            }
            return
        }
        val targetUrl = downloadUrl.ifEmpty { displayImageUrl }
        if (targetUrl.isEmpty()) {
            acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).toast("\u6ca1\u6709\u53ef\u4e0b\u8f7d\u7684\u56fe\u7247")
            return
        }
        downloading = true
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).downloadNetbianImage(targetUrl, titleText, detailUrl) { response ->
            downloading = false
            val code = response?.optInt("code", -1) ?: -1
            val message = response?.optString("message", "") ?: ""
            val bridgeModule = acquireModule<BridgeModule>(BridgeModule.MODULE_NAME)
            if (code != 0) {
                bridgeModule.toast(message.ifEmpty { "\u4e0b\u8f7d\u5931\u8d25" })
                return@downloadNetbianImage
            }
            bridgeModule.markNetbianImageDownloaded(listImageUrl.ifEmpty { displayImageUrl }) {
                bridgeModule.toast(message.ifEmpty { "\u5df2\u4fdd\u5b58\u56fe\u7247" })
            }
        }
    }

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            attr {
                backgroundColor(Color(0xFFF2F2F7))
            }
            RouterNavBar {
                attr {
                    title = "\u56fe\u7247\u8be6\u60c5"
                    backDisable = false
                }
            }
            View {
                attr {
                    flex(1f)
                    backgroundColor(Color(0xFF0B0B0D))
                }
                View {
                    attr {
                        flex(1f)
                        backgroundColor(Color(0xFF111111))
                    }
                    Image {
                        attr {
                            src(ctx.displayImageUrl)
                            flex(1f)
                            resizeContain()
                        }
                    }
                    View {
                        attr {
                            absolutePosition(left = 14f, right = 14f, bottom = 14f)
                            padding(14f)
                            backgroundColor(Color(0x66000000))
                            borderRadius(8f)
                            boxShadow(BoxShadow(0f, 5f, 20f, Color(0x99000000)), true)
                        }
                        Text {
                            attr {
                                text(ctx.titleText.ifEmpty { "\u5f7c\u5cb8\u56fe\u7f51\u56fe\u7247" })
                                fontSize(17f)
                                color(Color.WHITE)
                                fontWeightBold()
                                lines(2)
                            }
                        }
                        Text {
                            attr {
                                text(ctx.categoryRowText())
                                height(if (ctx.categoryName.isEmpty()) 0f else 20f)
                                fontSize(13f)
                                color(Color(0xFFE8E8E8))
                                marginTop(if (ctx.categoryName.isEmpty()) 0f else 7f)
                                lines(1)
                            }
                        }
                        Text {
                            attr {
                                text(ctx.sizeRowText())
                                height(if (ctx.imageSizeText.isEmpty()) 0f else 20f)
                                fontSize(13f)
                                color(Color(0xFFE8E8E8))
                                marginTop(if (ctx.imageSizeText.isEmpty()) 0f else 7f)
                                lines(1)
                            }
                        }
                        Text {
                            attr {
                                text(ctx.fileSizeRowText())
                                height(if (ctx.fileSizeText.isEmpty()) 0f else 20f)
                                fontSize(13f)
                                color(Color(0xFFE8E8E8))
                                marginTop(if (ctx.fileSizeText.isEmpty()) 0f else 7f)
                                lines(1)
                            }
                        }
                        Text {
                            attr {
                                text(ctx.updatedAtRowText())
                                height(if (ctx.updatedAtText.isEmpty()) 0f else 20f)
                                fontSize(13f)
                                color(Color(0xFFE8E8E8))
                                marginTop(if (ctx.updatedAtText.isEmpty()) 0f else 7f)
                                lines(1)
                            }
                        }
                    }
                }
                Text {
                    attr {
                        text(ctx.statusText)
                        height(if (ctx.statusText.isEmpty()) 0f else 44f)
                        fontSize(13f)
                        color(Color(0xFF666666))
                        textAlignCenter()
                    }
                }
                View {
                    attr {
                        margin(12f, 16f, 16f, 16f)
                        height(48f)
                        borderRadius(8f)
                        backgroundColor(Color(0xFF1E6BFF))
                        allCenter()
                        boxShadow(BoxShadow(0f, 3f, 12f, Color(0x331E6BFF)), true)
                    }
                    Text {
                        attr {
                            text(if (ctx.downloading) "\u4e0b\u8f7d\u4e2d..." else if (ctx.netbianLoggedIn) "\u4e0b\u8f7d\u56fe\u7247" else "\u767b\u5f55\u540e\u4e0b\u8f7d")
                            fontSize(16f)
                            color(Color.WHITE)
                            fontWeightBold()
                        }
                    }
                    event {
                        click {
                            ctx.downloadImage()
                        }
                    }
                }
            }
        }
    }

    private fun categoryRowText(): String {
        return when {
            categoryName.isEmpty() -> ""
            categoryUrl.isNotEmpty() -> "\u5206\u7c7b [$categoryName]($categoryUrl)"
            else -> "\u5206\u7c7b $categoryName"
        }
    }

    private fun sizeRowText(): String {
        return if (imageSizeText.isEmpty()) "" else "\u5c3a\u5bf8$imageSizeText"
    }

    private fun fileSizeRowText(): String {
        return if (fileSizeText.isEmpty()) "" else "\u4f53\u79ef $fileSizeText"
    }

    private fun updatedAtRowText(): String {
        return if (updatedAtText.isEmpty()) "" else "\u6700\u8fd1\u66f4\u65b0$updatedAtText"
    }
}

private fun parseNetbianDetail(
    html: String,
    fallbackImageUrl: String,
    detailUrl: String,
    fallbackTitle: String
): NetbianDetail {
    val title = parseTitle(html).ifEmpty { fallbackTitle.decodeBasicHtmlEntities().trim() }
    val imageUrl = parseLargeImageUrl(html).ifEmpty { fallbackImageUrl }
    val downloadUrl = parseDownloadUrl(html, detailUrl).ifEmpty { imageUrl }
    val category = parseCategory(html)
    return NetbianDetail(
        imageUrl = imageUrl,
        downloadUrl = downloadUrl,
        title = title,
        categoryName = category.first,
        categoryUrl = category.second,
        size = parseInfoField(html, "\u5c3a\u5bf8"),
        fileSize = parseInfoField(html, "\u4f53\u79ef"),
        updatedAt = parseInfoField(html, "\u6700\u8fd1\u66f4\u65b0")
    )
}

private fun parseTitle(html: String): String {
    val h1 = Regex("<h1[^>]*>([\\s\\S]*?)</h1>", RegexOption.IGNORE_CASE)
        .find(html)?.groupValues?.getOrNull(1)
        ?.stripHtmlTags()
        ?.decodeBasicHtmlEntities()
        ?.trim()
    if (!h1.isNullOrEmpty()) return h1
    return Regex("<title[^>]*>([\\s\\S]*?)</title>", RegexOption.IGNORE_CASE)
        .find(html)?.groupValues?.getOrNull(1)
        ?.substringBefore("_")
        ?.stripHtmlTags()
        ?.decodeBasicHtmlEntities()
        ?.trim()
        ?: ""
}

private fun parseCategory(html: String): Pair<String, String> {
    val categoryBlock = Regex(
        "<p>\\s*\u5206\u7c7b\\s*<span>([\\s\\S]*?)</span>\\s*</p>",
        RegexOption.IGNORE_CASE
    ).find(html)?.groupValues?.getOrNull(1) ?: return "" to ""
    val link = Regex("<a\\b[^>]*href=([\"'])(.*?)\\1[^>]*>([\\s\\S]*?)</a>", RegexOption.IGNORE_CASE)
        .find(categoryBlock)
    val name = link?.groupValues?.getOrNull(3)
        ?.stripHtmlTags()
        ?.decodeBasicHtmlEntities()
        ?.trim()
        ?: categoryBlock.stripHtmlTags().decodeBasicHtmlEntities().trim()
    val url = link?.groupValues?.getOrNull(2)
        ?.takeIf { it.isNotBlank() }
        ?.toAbsoluteNetbianUrl()
        ?: ""
    return name to url
}

private fun parseInfoField(html: String, label: String): String {
    val pattern = "<p>\\s*${Regex.escape(label)}\\s*<span>([\\s\\S]*?)</span>\\s*</p>"
    return Regex(pattern, RegexOption.IGNORE_CASE)
        .find(html)
        ?.groupValues
        ?.getOrNull(1)
        ?.stripHtmlTags()
        ?.decodeBasicHtmlEntities()
        ?.trim()
        ?: ""
}

private fun parseLargeImageUrl(html: String): String {
    val imageTags = Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(html).map { it.value }.toList()
    imageTags.mapNotNull { tag ->
        tag.extractHtmlAttr("data-pic")
            ?.takeIf { it.contains("/uploads/allimg/") }
            ?.toAbsoluteNetbianUrl()
    }.firstOrNull()?.also {
        return it
    }
    val candidates = imageTags.mapNotNull { tag ->
        val src = tag.extractHtmlAttr("src") ?: return@mapNotNull null
        if (!src.contains("/uploads/allimg/")) return@mapNotNull null
        src.toAbsoluteNetbianUrl()
    }
    return candidates.maxByOrNull { it.length } ?: ""
}

private fun parseDownloadUrl(html: String, detailUrl: String): String {
    Regex("<a\\b[^>]*data-id=([\"'])(\\d+)\\1[^>]*>", RegexOption.IGNORE_CASE)
        .find(html)
        ?.groupValues
        ?.getOrNull(2)
        ?.also {
            return "$NETBIAN_HOST/e/extend/netbiandownload.php?id=$it&t=0"
        }

    val hrefCandidates = Regex("<a\\b[^>]*href=([\"'])(.*?)\\1[^>]*>", RegexOption.IGNORE_CASE)
        .findAll(html)
        .mapNotNull { match ->
            val whole = match.value
            val href = match.groupValues.getOrNull(2) ?: return@mapNotNull null
            val lower = (whole + href).lowercase()
            if (lower.contains("down") || lower.contains("download") || lower.contains("xiazai") || lower.contains("\u4e0b\u8f7d")) {
                href
            } else {
                null
            }
        }
        .filter { it.isHttpLikePath() }
        .map { it.toAbsoluteNetbianUrl() }
        .toList()
    if (hrefCandidates.isNotEmpty()) {
        return hrefCandidates.first()
    }

    val urlRegexes = listOf(
        Regex("https?://pic\\.netbian\\.com/(?:uploads/allimg|e/[^\"'\\s<>]*(?:Down|down|download|xiazai))[^\"'\\s<>]+", RegexOption.IGNORE_CASE),
        Regex("(/e/[^\"'\\s<>]*(?:Down|down|download|xiazai)[^\"'\\s<>]*)", RegexOption.IGNORE_CASE),
        Regex("(/uploads/allimg/[^\"'\\s<>]+)", RegexOption.IGNORE_CASE),
        Regex("(/[^\"'\\s<>]*(?:Down|down|download|xiazai)[^\"'\\s<>]*)", RegexOption.IGNORE_CASE)
    )
    urlRegexes.forEach { regex ->
        regex.findAll(html).map { match ->
            match.groupValues.getOrElse(1) { match.value }
        }.firstOrNull { it.isHttpLikePath() }?.also {
            return it.toAbsoluteNetbianUrl()
        }
    }

    val id = Regex("/tupian/(\\d+)\\.html", RegexOption.IGNORE_CASE).find(detailUrl)?.groupValues?.getOrNull(1)
    if (!id.isNullOrEmpty()) {
        return "$NETBIAN_HOST/e/extend/netbiandownload.php?id=$id&t=0"
    }

    val classId = Regex("classid\\s*[=:]\\s*([0-9]+)", RegexOption.IGNORE_CASE).find(html)?.groupValues?.getOrNull(1)
    if (!id.isNullOrEmpty() && !classId.isNullOrEmpty()) {
        return "$NETBIAN_HOST/e/DownSys/DownSoft/?classid=$classId&id=$id&pathid=0"
    }
    return ""
}

private fun String.isHttpLikePath(): Boolean {
    return startsWith("http://") || startsWith("https://") || startsWith("/")
}

private fun String.extractHtmlAttr(name: String): String? {
    val regex = Regex("$name\\s*=\\s*([\"'])(.*?)\\1", RegexOption.IGNORE_CASE)
    return regex.find(this)?.groupValues?.getOrNull(2)
}

private fun String.toAbsoluteNetbianUrl(): String {
    return when {
        startsWith("//") -> "https:$this"
        startsWith("http://") || startsWith("https://") -> this
        startsWith("/") -> "$NETBIAN_HOST$this"
        else -> "$NETBIAN_HOST/$this"
    }
}

private fun String.decodeBasicHtmlEntities(): String {
    return replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")
}

private fun String.stripHtmlTags(): String {
    return replace(Regex("<[^>]+>"), "")
}
