package com.ss.kuiklywork

import com.ss.kuiklywork.base.BasePager
import com.ss.kuiklywork.base.BridgeModule
import com.ss.kuiklywork.base.setTimeout
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import com.tencent.kuikly.core.views.layout.Row

private const val NETBIAN_HOME_URL = "https://pic.netbian.com/"
private const val NETBIAN_HOST = "https://pic.netbian.com"
private const val MAX_RENDER_ITEMS = 120
private const val PAGE_TIMEOUT_MS = 15000

data class ImageItem(
    val url: String,
    val title: String
)

@Page("imageList")
internal class ImageListPage : BasePager() {

    var imageItems by observable(listOf<ImageItem>())
    var loading by observable(false)
    var loadingMore by observable(false)
    var noMore by observable(false)
    var statusMessage by observable("")
    var pageIndex = 0
    private var requestId = 0

    override fun created() {
        super.created()
        refreshImages()
    }

    fun refreshImages() {
        val bridgeModule = acquireModule<BridgeModule>(BridgeModule.MODULE_NAME)
        bridgeModule.log("ImageList refreshImages")
        pageIndex = 0
        noMore = false
        imageItems = emptyList()
        loadPage(page = 1, append = false)
    }

    fun loadMore() {
        if (loading || loadingMore || noMore) {
            return
        }
        loadPage(page = pageIndex + 1, append = true)
    }

    private fun loadPage(page: Int, append: Boolean) {
        val bridgeModule = acquireModule<BridgeModule>(BridgeModule.MODULE_NAME)
        val currentRequestId = ++requestId
        val pageUrl = netbianPageUrl(page)
        bridgeModule.log("ImageList loadPage start requestId=$currentRequestId page=$page append=$append url=$pageUrl")

        if (append) {
            loadingMore = true
        } else {
            loading = true
        }
        statusMessage = ""

        setTimeout(PAGE_TIMEOUT_MS) {
            if (currentRequestId == requestId && (loading || loadingMore)) {
                bridgeModule.log("ImageList request timeout requestId=$currentRequestId page=$page")
                loading = false
                loadingMore = false
                statusMessage = "\u8bf7\u6c42\u8d85\u65f6\uff0c\u8bf7\u91cd\u8bd5"
            }
        }

        bridgeModule.fetchHtml(pageUrl) { response ->
            bridgeModule.log("ImageList fetchHtml callback requestId=$currentRequestId current=$requestId responseNull=${response == null}")
            val code = response?.optInt("code", -1) ?: -1
            val message = response?.optString("message", "\u8bf7\u6c42\u5931\u8d25\uff0c\u8bf7\u91cd\u8bd5") ?: "\u8bf7\u6c42\u5931\u8d25\uff0c\u8bf7\u91cd\u8bd5"
            val html = response?.optString("body", "") ?: ""
            val parsedItems = if (code == 0) parseNetbianImages(html) else emptyList()
            bridgeModule.log("ImageList schedule apply page=$page code=$code parsed=${parsedItems.size}, htmlLength=${html.length}")

            setTimeout(0) {
                bridgeModule.log("ImageList apply state requestId=$currentRequestId current=$requestId page=$page parsed=${parsedItems.size}")
                if (currentRequestId != requestId) {
                    return@setTimeout
                }

                loading = false
                loadingMore = false

                if (response == null || code != 0) {
                    statusMessage = if (response == null) "\u8bf7\u6c42\u5931\u8d25\uff0c\u8bf7\u91cd\u8bd5" else message
                    return@setTimeout
                }

                if (parsedItems.isEmpty()) {
                    if (append) {
                        noMore = true
                        statusMessage = ""
                    } else {
                        statusMessage = "\u6ca1\u6709\u89e3\u6790\u5230\u56fe\u7247"
                    }
                    return@setTimeout
                }

                val mergedItems = if (append) mergeImageItems(imageItems, parsedItems) else parsedItems
                imageItems = mergedItems.take(MAX_RENDER_ITEMS)
                pageIndex = page
                noMore = parsedItems.size < 10 || mergedItems.size >= MAX_RENDER_ITEMS
                statusMessage = ""
                bridgeModule.log("ImageList page applied page=$page total=${imageItems.size} noMore=$noMore")
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
                    title = ctx.pagerData.params.optString("title", "\u5f7c\u5cb8\u56fe\u7f51")
                    backDisable = false
                }
            }
            Scroller {
                attr {
                    flex(1f)
                    padding(6f)
                }
                Text {
                    attr {
                        text(ctx.statusMessage)
                        height(if (ctx.statusMessage.isEmpty() || ctx.imageItems.isNotEmpty()) 0f else 48f)
                        fontSize(15f)
                        color(Color(0xFF666666))
                        textAlignCenter()
                    }
                    event {
                        click {
                            if (!ctx.loading && !ctx.loadingMore && ctx.imageItems.isEmpty()) {
                                ctx.refreshImages()
                            }
                        }
                    }
                }
                Row {
                    FixedWaterfallColumn(ctx, startIndex = 0, rightMargin = 3f, leftMargin = 0f)
                    FixedWaterfallColumn(ctx, startIndex = 1, rightMargin = 0f, leftMargin = 3f)
                }
                LoadMoreFooter(ctx)
            }
        }
    }
}

private fun netbianPageUrl(page: Int): String {
    return if (page <= 1) NETBIAN_HOME_URL else "${NETBIAN_HOST}/index_${page}.html"
}

private fun mergeImageItems(oldItems: List<ImageItem>, newItems: List<ImageItem>): List<ImageItem> {
    val seenUrls = mutableSetOf<String>()
    val result = mutableListOf<ImageItem>()
    (oldItems + newItems).forEach { item ->
        if (seenUrls.add(item.url)) {
            result.add(item)
        }
    }
    return result
}

private fun parseNetbianImages(html: String): List<ImageItem> {
    val items = mutableListOf<ImageItem>()
    val seenUrls = mutableSetOf<String>()
    val itemRegex = Regex("<li\\b[\\s\\S]*?</li>", RegexOption.IGNORE_CASE)
    val anchorRegex = Regex("<a\\b[^>]*href=[\"']/tupian/[^\"']+[\"'][^>]*>", RegexOption.IGNORE_CASE)
    val imageRegex = Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE)

    itemRegex.findAll(html).forEach { itemMatch ->
        val itemHtml = itemMatch.value
        val anchor = anchorRegex.find(itemHtml)?.value ?: return@forEach
        val image = imageRegex.find(itemHtml)?.value ?: return@forEach
        val src = image.extractHtmlAttr("src") ?: return@forEach
        if (!src.contains("/uploads/allimg/")) {
            return@forEach
        }
        val url = src.toAbsoluteNetbianUrl()
        if (!seenUrls.add(url)) {
            return@forEach
        }
        val title = anchor.extractHtmlAttr("title")
            ?: image.extractHtmlAttr("alt")
            ?: "\u5f7c\u5cb8\u56fe\u7f51\u56fe\u7247"
        items.add(ImageItem(url, title.decodeBasicHtmlEntities().trim()))
    }
    return items
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

private fun com.tencent.kuikly.core.base.ViewContainer<*, *>.FixedWaterfallColumn(
    ctx: ImageListPage,
    startIndex: Int,
    rightMargin: Float,
    leftMargin: Float
) {
    View {
        attr {
            flex(1f)
            if (rightMargin > 0f) {
                marginRight(rightMargin)
            }
            if (leftMargin > 0f) {
                marginLeft(leftMargin)
            }
        }
        for (slot in 0 until MAX_RENDER_ITEMS / 2) {
            WallpaperCard(ctx, startIndex + slot * 2)
        }
    }
}

private fun com.tencent.kuikly.core.base.ViewContainer<*, *>.WallpaperCard(ctx: ImageListPage, index: Int) {
    View {
        attr {
            backgroundColor(Color.WHITE)
            borderRadius(6f)
            height(if (ctx.imageItems.getOrNull(index) == null) 0f else 178f)
            marginBottom(if (ctx.imageItems.getOrNull(index) == null) 0f else 8f)
        }
        Image {
            attr {
                src(ctx.imageItems.getOrNull(index)?.url ?: "")
                height(if (ctx.imageItems.getOrNull(index) == null) 0f else 126f)
                resizeCover()
            }
        }
        View {
            attr {
                padding(if (ctx.imageItems.getOrNull(index) == null) 0f else 8f)
            }
            Text {
                attr {
                    text(ctx.imageItems.getOrNull(index)?.title ?: "")
                    fontSize(12f)
                    color(Color(0xFF333333))
                }
            }
        }
    }
}

private fun com.tencent.kuikly.core.base.ViewContainer<*, *>.LoadMoreFooter(ctx: ImageListPage) {
    View {
        attr {
            height(if (ctx.footerText().isEmpty()) 0f else 52f)
            marginTop(if (ctx.footerText().isEmpty()) 0f else 2f)
            marginBottom(if (ctx.footerText().isEmpty()) 0f else 12f)
            allCenter()
        }
        event {
            click {
                if (ctx.statusMessage.isNotEmpty() && ctx.imageItems.isEmpty()) {
                    ctx.refreshImages()
                } else {
                    ctx.loadMore()
                }
            }
        }
        Text {
            attr {
                text(ctx.footerText())
                fontSize(15f)
                color(if (ctx.noMore) Color(0xFF999999) else Color(0xFF1E6BFF))
            }
            event {
                click {
                    if (ctx.statusMessage.isNotEmpty() && ctx.imageItems.isEmpty()) {
                        ctx.refreshImages()
                    } else {
                        ctx.loadMore()
                    }
                }
            }
        }
    }
}

private fun ImageListPage.footerText(): String {
    return when {
        loading -> "\u6b63\u5728\u52a0\u8f7d..."
        loadingMore -> "\u52a0\u8f7d\u66f4\u591a..."
        noMore && imageItems.isNotEmpty() -> "\u6ca1\u6709\u66f4\u591a\u4e86"
        statusMessage.isNotEmpty() && imageItems.isEmpty() -> "\u91cd\u8bd5"
        imageItems.isEmpty() -> ""
        else -> "\u52a0\u8f7d\u66f4\u591a"
    }
}
