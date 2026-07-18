package com.ss.kuiklywork

import com.ss.kuiklywork.base.BasePager
import com.ss.kuiklywork.base.BridgeModule
import com.ss.kuiklywork.base.setTimeout
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.directives.vforLazy
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.List
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

private const val NNCOS_HOST = "https://www.nncos.com"
private const val NNCOS_MAX_RENDER_ITEMS = 120
private const val NNCOS_REQUEST_TIMEOUT_MS = 15000
private const val NNCOS_LIST_PADDING = 6f

private data class CosCategory(val name: String, val url: String)

internal data class CosImageItem(
    val imageUrl: String,
    val title: String,
    val detailUrl: String,
    val categoryName: String,
    val publishedAt: String,
    val downloaded: Boolean = false
)

private data class CosPageResult(
    val items: List<CosImageItem>,
    val hasNextPage: Boolean
)

private data class CosCategoryCache(
    val items: List<CosImageItem>,
    val pageIndex: Int,
    val noMore: Boolean
)

private data class CosImageRow(val left: CosImageItem, val right: CosImageItem?)

private val COS_CATEGORY_LIST = listOf(
    CosCategory("\u5168\u90e8", "$NNCOS_HOST/"),
    CosCategory("\u4e9a\u6d32", "$NNCOS_HOST/ac"),
    CosCategory("\u70ed\u95e8", "$NNCOS_HOST/month.html"),
    CosCategory("\u6b27\u7f8e", "$NNCOS_HOST/ec"),
    CosCategory("\u79c0\u4eba", "$NNCOS_HOST/xr"),
    CosCategory("\u65e5\u97e9", "$NNCOS_HOST/jk"),
    CosCategory("\u9ad8\u6e05", "$NNCOS_HOST/hd"),
    CosCategory("\u6c49\u670d", "$NNCOS_HOST/hf")
)

@Page("cosImageList")
internal class CosImageListPage : BasePager() {

    var imageItems by observable(listOf<CosImageItem>())
    private var imageRows by observableList<CosImageRow>()
    var loading by observable(false)
    var loadingMore by observable(false)
    var noMore by observable(false)
    var statusMessage by observable("")
    var selectedCategoryIndex by observable(0)
    var nncosLoggedIn by observable(false)
    var nncosLoginChecking by observable(false)

    private var pageIndex = 0
    private var requestId = 0
    private val categoryCache = mutableMapOf<String, CosCategoryCache>()
    private var downloadedDetailUrls = emptySet<String>()

    private val currentCategory: CosCategory
        get() = COS_CATEGORY_LIST[selectedCategoryIndex]

    override fun created() {
        super.created()
        refreshLoginState()
        loadDownloadedRecords { refreshImages() }
    }

    override fun pageDidAppear() {
        super.pageDidAppear()
        refreshLoginState()
        loadDownloadedRecords()
    }

    private fun loadDownloadedRecords(afterLoad: (() -> Unit)? = null) {
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).getImageDownloadRecords { response ->
            if (response?.optInt("code", -1) == 0) {
                downloadedDetailUrls = response.optString("urls", "")
                    .lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toSet()
                updateDownloadedItems()
            }
            afterLoad?.invoke()
        }
    }

    private fun updateDownloadedItems() {
        imageItems = imageItems.map { item ->
            item.copy(downloaded = item.detailUrl in downloadedDetailUrls)
        }
        syncImageRows()
        categoryCache.entries.forEach { entry ->
            entry.setValue(entry.value.copy(items = entry.value.items.map { item ->
                item.copy(downloaded = item.detailUrl in downloadedDetailUrls)
            }))
        }
    }

    private fun refreshLoginState() {
        if (nncosLoginChecking) return
        nncosLoginChecking = true
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).getNncosLoginState { response ->
            nncosLoginChecking = false
            nncosLoggedIn = response?.optBoolean("isLoggedIn", false) ?: false
        }
    }

    fun openNncosLogin() {
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).openNncosLogin { response ->
            nncosLoggedIn = response?.optBoolean("isLoggedIn", false) ?: false
            val message = response?.optString("message", "") ?: ""
            if (response?.optInt("code", 0) != 0 && message.isNotEmpty()) {
                acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).toast(message)
            }
        }
    }

    fun selectCategory(index: Int) {
        if (index == selectedCategoryIndex || loading || loadingMore) return
        selectedCategoryIndex = index
        val cached = categoryCache[currentCategory.url]
        if (cached == null) {
            refreshImages()
            return
        }
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME)
            .log("CosImageList restore cache category=${currentCategory.url} total=${cached.items.size}")
        imageItems = cached.items
        syncImageRows()
        pageIndex = cached.pageIndex
        noMore = cached.noMore
        statusMessage = ""
    }

    fun refreshImages() {
        pageIndex = 0
        noMore = false
        imageItems = emptyList()
        syncImageRows()
        loadPage(page = 1, append = false)
    }

    fun loadMore() {
        if (loading || loadingMore || noMore) return
        loadPage(page = pageIndex + 1, append = true)
    }

    private fun loadPage(page: Int, append: Boolean) {
        val bridgeModule = acquireModule<BridgeModule>(BridgeModule.MODULE_NAME)
        val currentRequestId = ++requestId
        val pageUrl = currentCategory.pageUrl(page)
        bridgeModule.log("CosImageList loadPage page=$page append=$append url=$pageUrl")
        if (append) loadingMore = true else loading = true
        statusMessage = ""

        setTimeout(NNCOS_REQUEST_TIMEOUT_MS) {
            if (currentRequestId == requestId && (loading || loadingMore)) {
                loading = false
                loadingMore = false
                statusMessage = "\u8bf7\u6c42\u8d85\u65f6\uff0c\u8bf7\u91cd\u8bd5"
            }
        }

        bridgeModule.fetchHtml(pageUrl) { response ->
            val code = response?.optInt("code", -1) ?: -1
            val html = response?.optString("body", "") ?: ""
            val result = if (code == 0) parseNncosImages(html) else CosPageResult(emptyList(), false)
            val errorMessage = response?.optString("message", "\u8bf7\u6c42\u5931\u8d25\uff0c\u8bf7\u91cd\u8bd5") ?: "\u8bf7\u6c42\u5931\u8d25\uff0c\u8bf7\u91cd\u8bd5"
            setTimeout(0) {
                if (currentRequestId != requestId) return@setTimeout
                loading = false
                loadingMore = false
                if (code != 0) {
                    statusMessage = errorMessage
                    return@setTimeout
                }
                if (result.items.isEmpty()) {
                    if (append) {
                        noMore = true
                        statusMessage = ""
                    } else {
                        statusMessage = "\u6ca1\u6709\u89e3\u6790\u5230\u56fe\u7247"
                    }
                    return@setTimeout
                }
                val merged = (if (append) mergeCosImageItems(imageItems, result.items) else result.items)
                    .map { item -> item.copy(downloaded = item.detailUrl in downloadedDetailUrls) }
                imageItems = merged.take(NNCOS_MAX_RENDER_ITEMS)
                syncImageRows()
                pageIndex = page
                noMore = !result.hasNextPage || merged.size >= NNCOS_MAX_RENDER_ITEMS
                statusMessage = ""
                categoryCache[currentCategory.url] = CosCategoryCache(imageItems, pageIndex, noMore)
                bridgeModule.log("CosImageList page applied page=$page total=${imageItems.size} noMore=$noMore")
            }
        }
    }

    private fun syncImageRows() {
        imageRows.clear()
        imageItems.chunked(2).forEach { items ->
            imageRows.add(CosImageRow(items.first(), items.getOrNull(1)))
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
                    title = ctx.pagerData.params.optString("title", "\u534a\u6b21\u5143\u56fe")
                    backDisable = false
                }
            }
            CosCategoryMenu(ctx)
            CosLoginBar(ctx)
            List {
                attr {
                    flex(1f)
                    padding(NNCOS_LIST_PADDING)
                    firstContentLoadMaxIndex(6)
                    preloadViewDistance(ctx.pageData.pageViewHeight)
                }
                event {
                    scroll {
                        if (it.offsetY + it.viewHeight >= it.contentHeight - 200f) {
                            ctx.loadMore()
                        }
                    }
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
                vforLazy({ ctx.imageRows }, maxLoadItem = 8) { row, _, _ ->
                    View {
                        attr {
                            width(maxOf(0f, ctx.pageData.pageViewWidth - NNCOS_LIST_PADDING * 2f))
                            flexDirectionRow()
                        }
                        CosImageCard(ctx, row.left, rightMargin = 3f, leftMargin = 0f)
                        CosImageCard(ctx, row.right, rightMargin = 0f, leftMargin = 3f)
                    }
                }
                CosLoadMoreFooter(ctx)
            }
        }
    }

    fun footerText(): String {
        return when {
            loading -> "\u6b63\u5728\u52a0\u8f7d..."
            loadingMore -> "\u6b63\u5728\u52a0\u8f7d\u7b2c ${pageIndex + 1} \u9875..."
            noMore && imageItems.isNotEmpty() -> "\u6ca1\u6709\u66f4\u591a\u4e86"
            statusMessage.isNotEmpty() && imageItems.isEmpty() -> "\u91cd\u8bd5"
            imageItems.isEmpty() -> ""
            else -> "\u52a0\u8f7d\u66f4\u591a"
        }
    }
}

private fun CosCategory.pageUrl(page: Int): String {
    if (page <= 1) return url
    return "${url.trimEnd('/')}/page/$page"
}

private fun mergeCosImageItems(oldItems: List<CosImageItem>, newItems: List<CosImageItem>): List<CosImageItem> {
    val seenUrls = mutableSetOf<String>()
    return (oldItems + newItems).filter { item -> seenUrls.add(item.imageUrl) }
}

private fun parseNncosImages(html: String): CosPageResult {
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
            ?.decodeCosHtmlEntities()
            ?.trim()
            .orEmpty()
            .ifEmpty { "\u534a\u6b21\u5143\u56fe" }
        val categoryName = article.value.extractCosCategoryName()
        val publishedAt = article.value.extractCosPublishedAt()
        items.add(CosImageItem(imageUrl, title, detailUrl, categoryName, publishedAt))
    }
    val hasNextPage = Regex("<a\\b[^>]*class=[\"'][^\"']*page-next[^\"']*[\"'][^>]*href=", RegexOption.IGNORE_CASE)
        .containsMatchIn(html)
    return CosPageResult(items, hasNextPage)
}

private fun String.extractCosHtmlAttr(name: String): String? {
    val regex = Regex("$name\\s*=\\s*([\"'])(.*?)\\1", RegexOption.IGNORE_CASE)
    return regex.find(this)?.groupValues?.getOrNull(2)
}

private fun String.toAbsoluteCosUrl(): String {
    return when {
        startsWith("//") -> "https:$this"
        startsWith("http://") || startsWith("https://") -> this
        startsWith("/") -> "$NNCOS_HOST$this"
        else -> "$NNCOS_HOST/$this"
    }
}

private fun String.decodeCosHtmlEntities(): String {
    return replace("&amp;", "&")
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
        ?.decodeCosHtmlEntities()
        ?.trim()
        .orEmpty()
}

private fun String.extractCosPublishedAt(): String {
    return Regex("<time\\b[^>]*class=[\"'][^\"']*pub-date[^\"']*[\"'][^>]*>([\\s\\S]*?)</time>", RegexOption.IGNORE_CASE)
        .find(this)?.groupValues?.getOrNull(1)
        ?.replace(Regex("<[^>]+>"), "")
        ?.decodeCosHtmlEntities()
        ?.trim()
        .orEmpty()
}

private fun com.tencent.kuikly.core.base.ViewContainer<*, *>.CosCategoryMenu(ctx: CosImageListPage) {
    Scroller {
        attr {
            height(44f)
            backgroundColor(Color.WHITE)
            flexDirectionRow()
            alignItemsCenter()
        }
        COS_CATEGORY_LIST.forEachIndexed { index, category ->
            View {
                attr {
                    paddingLeft(16f)
                    paddingRight(16f)
                    height(44f)
                    allCenter()
                }
                Text {
                    attr {
                        text(category.name)
                        fontSize(14f)
                        color(if (index == ctx.selectedCategoryIndex) Color(0xFF1E6BFF) else Color(0xFF333333))
                        if (index == ctx.selectedCategoryIndex) fontWeightBold()
                    }
                }
                event {
                    click { ctx.selectCategory(index) }
                }
            }
        }
    }
}

private fun com.tencent.kuikly.core.base.ViewContainer<*, *>.CosLoginBar(ctx: CosImageListPage) {
    View {
        attr {
            height(40f)
            backgroundColor(Color.WHITE)
            paddingLeft(16f)
            paddingRight(16f)
            flexDirectionRow()
            alignItemsCenter()
            justifyContentSpaceBetween()
        }
        Text {
            attr {
                text(if (ctx.nncosLoggedIn) "\u534a\u6b21\u5143\u56fe\uff1a\u5df2\u767b\u5f55" else "\u534a\u6b21\u5143\u56fe\uff1a\u672a\u767b\u5f55")
                fontSize(13f)
                color(Color(0xFF666666))
            }
        }
        Text {
            attr {
                text(if (ctx.nncosLoggedIn) "\u91cd\u65b0\u767b\u5f55" else "\u767b\u5f55")
                fontSize(13f)
                color(Color(0xFF1E6BFF))
                fontWeightBold()
            }
            event {
                click { ctx.openNncosLogin() }
            }
        }
        event {
            click { ctx.openNncosLogin() }
        }
    }
}

private fun com.tencent.kuikly.core.base.ViewContainer<*, *>.CosImageCard(
    ctx: CosImageListPage,
    item: CosImageItem?,
    rightMargin: Float,
    leftMargin: Float
) {
    View {
        attr {
            flex(1f)
            backgroundColor(Color.WHITE)
            borderRadius(6f)
            height(if (item == null) 0f else 220f)
            marginBottom(if (item == null) 0f else 8f)
            if (rightMargin > 0f) marginRight(rightMargin)
            if (leftMargin > 0f) marginLeft(leftMargin)
        }
        event {
            click {
                val cardItem = item ?: return@click
                val pageData = JSONObject()
                pageData.put("title", cardItem.title)
                pageData.put("detailUrl", cardItem.detailUrl)
                pageData.put("imageUrl", cardItem.imageUrl)
                ctx.acquireModule<RouterModule>(RouterModule.MODULE_NAME)
                    .openPage("cosImageDetail", pageData)
            }
        }
        Image {
            attr {
                src(item?.imageUrl ?: "")
                height(if (item == null) 0f else 176f)
                resizeCover()
            }
        }
        View {
            attr {
                height(if (item == null) 0f else 32f)
                paddingLeft(8f)
                paddingRight(8f)
                backgroundColor(Color(0x99000000))
                absolutePosition(left = 0f, right = 0f, top = 144f)
                flexDirectionRow()
                alignItemsCenter()
                justifyContentSpaceBetween()
            }
            Text {
                attr {
                    text(item?.categoryName ?: "")
                    fontSize(11f)
                    color(Color.WHITE)
                    lines(1)
                }
            }
            Text {
                attr {
                    text(item?.publishedAt ?: "")
                    fontSize(11f)
                    color(Color(0xFFE8E8E8))
                    lines(1)
                }
            }
        }
        View {
            attr {
                width(if (item?.downloaded == true) 52f else 0f)
                height(if (item?.downloaded == true) 24f else 0f)
                backgroundColor(Color(0xCC198754))
                borderRadius(4f)
                absolutePosition(top = 7f, right = 7f)
                allCenter()
            }
            Text {
                attr {
                    text(if (item?.downloaded == true) "\u5df2\u4e0b\u8f7d" else "")
                    fontSize(11f)
                    color(Color.WHITE)
                    lines(1)
                }
            }
        }
        View {
            attr {
                padding(if (item == null) 0f else 8f)
            }
            Text {
                attr {
                    text(item?.title ?: "")
                    fontSize(12f)
                    color(Color(0xFF333333))
                    lines(2)
                }
            }
        }
    }
}
private fun com.tencent.kuikly.core.base.ViewContainer<*, *>.CosLoadMoreFooter(ctx: CosImageListPage) {
    View {
        attr {
            height(if (ctx.footerText().isEmpty()) 0f else 52f)
            marginTop(if (ctx.footerText().isEmpty()) 0f else 2f)
            marginBottom(if (ctx.footerText().isEmpty()) 0f else 12f)
            allCenter()
        }
        event {
            click {
                if (ctx.statusMessage.isNotEmpty() && ctx.imageItems.isEmpty()) ctx.refreshImages() else ctx.loadMore()
            }
        }
        Text {
            attr {
                text(ctx.footerText())
                fontSize(15f)
                color(if (ctx.noMore) Color(0xFF999999) else Color(0xFF1E6BFF))
            }
        }
    }
}
