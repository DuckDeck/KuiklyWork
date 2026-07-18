package com.ss.kuiklywork

import com.ss.kuiklywork.base.BasePager
import com.ss.kuiklywork.base.BridgeModule
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.directives.vforLazy
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuikly.core.views.ActivityIndicator
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.List
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

private const val COS_DETAIL_HOST = "https://www.nncos.com"
private const val COS_DETAIL_FALLBACK_IMAGE_HEIGHT_RATIO = 1.5f

@Page("cosImageDetail")
internal class CosImageDetailPage : BasePager() {

    var titleText by observable("")
    var detailUrl by observable("")
    var imageUrls by observableList<String>()
    var imageAspectRatios by observable(emptyMap<String, Float>())
    var statusText by observable("")
    var downloading by observable(false)
    var downloadingIndex by observable(0)
    var downloaded by observable(false)

    override fun created() {
        super.created()
        titleText = pagerData.params.optString("title", "\u534a\u6b21\u5143\u56fe")
        detailUrl = pagerData.params.optString("detailUrl", "")
        loadDownloadState()
        loadDetail()
    }

    private fun loadDownloadState() {
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).getImageDownloadRecords { response ->
            if (response?.optInt("code", -1) != 0) return@getImageDownloadRecords
            downloaded = response.optString("urls", "")
                .lineSequence()
                .map { it.trim() }
                .any { it == detailUrl }
        }
    }

    private fun loadDetail() {
        if (detailUrl.isEmpty()) {
            statusText = "\u6ca1\u6709\u56fe\u7247\u8be6\u60c5\u5730\u5740"
            return
        }
        statusText = "\u6b63\u5728\u52a0\u8f7d..."
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).fetchHtml(detailUrl) { response ->
            val html = response?.optString("body", "") ?: ""
            if (response?.optInt("code", -1) != 0 || html.isEmpty()) {
                statusText = response?.optString("message", "\u8be6\u60c5\u52a0\u8f7d\u5931\u8d25") ?: "\u8be6\u60c5\u52a0\u8f7d\u5931\u8d25"
                return@fetchHtml
            }
            val result = parseNncosDetail(html)
            if (result.title.isNotEmpty()) titleText = result.title
            imageUrls.clear()
            imageAspectRatios = emptyMap()
            imageUrls.addAll(result.imageUrls)
            statusText = if (result.imageUrls.isEmpty()) "\u6ca1\u6709\u89e3\u6790\u5230\u56fe\u7247" else ""
        }
    }

    fun downloadAllImages() {
        if (downloading || downloaded) return
        if (imageUrls.isEmpty()) {
            acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).toast("\u6ca1\u6709\u53ef\u4e0b\u8f7d\u7684\u56fe\u7247")
            return
        }
        downloading = true
        downloadingIndex = 1
        downloadImageAt(0)
    }

    private fun downloadImageAt(index: Int) {
        if (index >= imageUrls.size) {
            acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).markImageDownloaded(detailUrl) { response ->
                downloading = false
                downloadingIndex = 0
                statusText = ""
                if (response?.optInt("code", -1) == 0) {
                    downloaded = true
                    acquireModule<BridgeModule>(BridgeModule.MODULE_NAME)
                        .toast("\u5df2\u4fdd\u5b58 ${imageUrls.size} \u5f20\u56fe\u7247")
                } else {
                    acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).toast("\u4e0b\u8f7d\u8bb0\u5f55\u4fdd\u5b58\u5931\u8d25")
                }
            }
            return
        }
        downloadingIndex = index + 1
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).downloadImage(
            url = imageUrls[index],
            title = "${titleText}_${index + 1}",
            referer = detailUrl
        ) { response ->
            if (response?.optInt("code", -1) != 0) {
                downloading = false
                downloadingIndex = 0
                statusText = ""
                val message = response?.optString("message", "\u4e0b\u8f7d\u5931\u8d25") ?: "\u4e0b\u8f7d\u5931\u8d25"
                acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).toast(message)
                return@downloadImage
            }
            downloadImageAt(index + 1)
        }
    }

    private fun imageHeight(imageUrl: String): Float {
        val ratio = imageAspectRatios[imageUrl] ?: COS_DETAIL_FALLBACK_IMAGE_HEIGHT_RATIO
        return pageData.pageViewWidth * ratio
    }

    private fun navigationTitle(): String {
        return if (titleText.length <= 10) titleText else "${titleText.take(10)}..."
    }

    private fun updateImageAspectRatio(imageUrl: String, imageWidth: Int, imageHeight: Int) {
        if (imageWidth <= 0 || imageHeight <= 0) return
        val ratio = (imageHeight.toFloat() / imageWidth.toFloat()).coerceIn(0.2f, 5f)
        if (imageAspectRatios[imageUrl] == ratio) return
        imageAspectRatios = imageAspectRatios + (imageUrl to ratio)
    }

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            attr {
                backgroundColor(Color(0xFFF2F2F7))
            }
            RouterNavBar {
                attr {
                    title = ctx.navigationTitle()
                    backDisable = false
                }
            }
            vif({ ctx.downloading }) {
                View {
                    attr {
                        height(36f)
                        flexDirectionRow()
                        allCenter()
                    }
                    ActivityIndicator {
                        attr {
                            isGrayStyle(true)
                            marginRight(8f)
                        }
                    }
                    Text {
                        attr {
                            text("\u6b63\u5728\u4e0b\u8f7d\u7b2c ${ctx.downloadingIndex}/${ctx.imageUrls.size} \u5f20")
                            fontSize(13f)
                            color(Color(0xFF666666))
                        }
                    }
                }
            }
            View {
                attr {
                    width(64f)
                    height(44f)
                    absolutePosition(top = ctx.pageData.statusBarHeight, right = 0f)
                    allCenter()
                }
                event {
                    click { ctx.downloadAllImages() }
                }
                Text {
                    attr {
                        text(
                            when {
                                ctx.downloading -> "\u4e0b\u8f7d\u4e2d"
                                ctx.downloaded -> "\u5df2\u4e0b\u8f7d"
                                else -> "\u4e0b\u8f7d"
                            }
                        )
                        fontSize(14f)
                        color(if (ctx.downloaded) Color(0xFF198754) else Color(0xFF1E6BFF))
                        fontWeightBold()
                    }
                }
            }
            List {
                attr {
                    flex(1f)
                    backgroundColor(Color(0xFF111111))
                    firstContentLoadMaxIndex(4)
                    preloadViewDistance(ctx.pageData.pageViewHeight)
                }
                View {
                    attr {
                        height(if (ctx.statusText.isNotEmpty() && ctx.imageUrls.isEmpty()) 48f else 0f)
                        allCenter()
                    }
                    event {
                        click {
                            if (ctx.imageUrls.isEmpty() && !ctx.downloading) ctx.loadDetail()
                        }
                    }
                    Text {
                        attr {
                            text(ctx.statusText)
                            fontSize(14f)
                            color(Color(0xFFB5B5B5))
                        }
                    }
                }
                vforLazy({ ctx.imageUrls }, maxLoadItem = 6) { imageUrl, _, _ ->
                    Image {
                        attr {
                            src(imageUrl)
                            width(ctx.pageData.pageViewWidth)
                            height(ctx.imageHeight(imageUrl))
                            resizeContain()
                        }
                        event {
                            loadResolution { resolution ->
                                ctx.updateImageAspectRatio(imageUrl, resolution.width, resolution.height)
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class CosDetailResult(val title: String, val imageUrls: List<String>)

private fun parseNncosDetail(html: String): CosDetailResult {
    val title = Regex("<h1\\b[^>]*>([\\s\\S]*?)</h1>", RegexOption.IGNORE_CASE)
        .find(html)?.groupValues?.getOrNull(1)
        ?.replace(Regex("<[^>]+>"), "")
        ?.decodeCosDetailHtmlEntities()
        ?.trim()
        .orEmpty()
    val postContent = Regex(
        "<article\\b[^>]*class=[\"'][^\"']*post-content[^\"']*[\"'][^>]*>[\\s\\S]*?</article>",
        RegexOption.IGNORE_CASE
    ).find(html)?.value
    val contentStart = Regex("<(?:div|article)\\b[^>]*class=[\"'][^\"']*entry-content[^\"']*[\"'][^>]*>", RegexOption.IGNORE_CASE)
        .find(html)?.range?.first ?: -1
    val downloadWidgetStart = html.indexOf("ri_post_down_widget", startIndex = maxOf(contentStart, 0), ignoreCase = true)
    val entryContent = when {
        contentStart < 0 -> ""
        downloadWidgetStart > contentStart -> html.substring(contentStart, downloadWidgetStart)
        else -> html.substring(contentStart)
    }
    val content = postContent ?: entryContent
    val seenUrls = mutableSetOf<String>()
    val imageUrls = Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(content)
        .mapNotNull { tag ->
            tag.value.extractCosDetailAttribute("data-src")
                ?: tag.value.extractCosDetailAttribute("data-original")
                ?: tag.value.extractCosDetailAttribute("src")
        }
        .map { it.toAbsoluteCosDetailUrl() }
        .filter { it.isNncosDetailImageUrl() }
        .filter { seenUrls.add(it) }
        .toList()
    return CosDetailResult(title, imageUrls)
}

private fun String.isNncosDetailImageUrl(): Boolean {
    return contains("img.nncos.com/", ignoreCase = true) ||
        contains("nncos.us/nnpic2/", ignoreCase = true)
}

private fun String.extractCosDetailAttribute(name: String): String? {
    return Regex("$name\\s*=\\s*([\"'])(.*?)\\1", RegexOption.IGNORE_CASE)
        .find(this)?.groupValues?.getOrNull(2)
}

private fun String.toAbsoluteCosDetailUrl(): String {
    return when {
        startsWith("//") -> "https:$this"
        startsWith("http://") || startsWith("https://") -> this
        startsWith("/") -> "$COS_DETAIL_HOST$this"
        else -> "$COS_DETAIL_HOST/$this"
    }
}

private fun String.decodeCosDetailHtmlEntities(): String {
    return replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")
}
