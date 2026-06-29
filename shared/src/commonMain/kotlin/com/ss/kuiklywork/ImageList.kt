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

data class ImageItem(
    val url: String,
    val title: String
)

@Page("imageList")
internal class ImageListPage : BasePager() {

    var imageItems by observable(listOf<ImageItem>())
    private var loading by observable(false)
    private var statusMessage by observable("\u6b63\u5728\u52a0\u8f7d...")
    private var requestId = 0

    override fun created() {
        super.created()
        loadImages()
    }

    private fun loadImages() {
        val bridgeModule = acquireModule<BridgeModule>(BridgeModule.MODULE_NAME)
        val currentRequestId = ++requestId
        bridgeModule.log("ImageList loadImages start requestId=$currentRequestId")
        loading = true
        statusMessage = "\u6b63\u5728\u52a0\u8f7d..."
        setTimeout(15000) {
            if (loading && currentRequestId == requestId) {
                bridgeModule.log("ImageList request timeout requestId=$currentRequestId")
                loading = false
                statusMessage = "\u8bf7\u6c42\u8d85\u65f6\uff0c\u8bf7\u91cd\u8bd5"
            }
        }
        bridgeModule.fetchHtml(NETBIAN_HOME_URL) { response ->
            bridgeModule.log("ImageList fetchHtml callback requestId=$currentRequestId current=$requestId responseNull=${response == null}")
            val code = response?.optInt("code", -1) ?: -1
            val message = response?.optString("message", "\u8bf7\u6c42\u5931\u8d25\uff0c\u8bf7\u91cd\u8bd5") ?: "\u8bf7\u6c42\u5931\u8d25\uff0c\u8bf7\u91cd\u8bd5"
            val html = response?.optString("body", "") ?: ""
            val parsedItems = if (code == 0) parseNetbianImages(html) else emptyList()
            bridgeModule.log("ImageList schedule apply code=$code parsed=${parsedItems.size}, htmlLength=${html.length}")
            setTimeout(0) {
                bridgeModule.log("ImageList apply state requestId=$currentRequestId current=$requestId parsed=${parsedItems.size}")
                if (currentRequestId != requestId) {
                    return@setTimeout
                }
                imageItems = parsedItems
                loading = false
                statusMessage = when {
                    response == null -> "\u8bf7\u6c42\u5931\u8d25\uff0c\u8bf7\u91cd\u8bd5"
                    code != 0 -> message
                    parsedItems.isEmpty() -> "\u6ca1\u6709\u89e3\u6790\u5230\u56fe\u7247"
                    else -> ""
                }
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
                        height(52f)
                        fontSize(15f)
                        color(Color(0xFF666666))
                        textAlignCenter()
                    }
                    event {
                        click {
                            if (!ctx.loading && ctx.imageItems.isEmpty()) {
                                ctx.loadImages()
                            }
                        }
                    }
                }
                Row {
                    attr {
                        flex(1f)
                    }
                    FixedWaterfallColumn(ctx, startIndex = 0, rightMargin = 3f, leftMargin = 0f)
                    FixedWaterfallColumn(ctx, startIndex = 1, rightMargin = 0f, leftMargin = 3f)
                }
            }
        }
    }
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

private fun com.tencent.kuikly.core.base.ViewContainer<*, *>.UnusedCenterMessage(message: String) {
    View {
        attr {
            flex(1f)
            allCenter()
        }
        Text {
            attr {
                text(message)
                fontSize(15f)
                color(Color(0xFF666666))
            }
        }
    }
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
        for (slot in 0 until 10) {
            WallpaperCard(ctx, startIndex + slot * 2)
        }
    }
}

private fun com.tencent.kuikly.core.base.ViewContainer<*, *>.WallpaperCard(ctx: ImageListPage, index: Int) {
    View {
        attr {
            backgroundColor(Color.WHITE)
            borderRadius(6f)
            marginBottom(8f)
        }
        Image {
            attr {
                src(ctx.imageItems.getOrNull(index)?.url ?: "")
                height(126f)
                resizeCover()
            }
        }
        View {
            attr {
                padding(8f)
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