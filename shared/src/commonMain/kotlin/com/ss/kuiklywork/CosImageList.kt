package com.ss.kuiklywork

import com.ss.kuiklywork.base.BasePager
import com.ss.kuiklywork.base.BridgeModule
import com.ss.kuiklywork.base.setTimeout
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import com.tencent.kuikly.core.views.layout.Row

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

private val COS_CATEGORY_LIST = listOf(
    CosCategory("全部", "$NNCOS_HOST/"),
    CosCategory("亚洲", "$NNCOS_HOST/ac"),
    CosCategory("热门", "$NNCOS_HOST/month.html"),
    CosCategory("欧美", "$NNCOS_HOST/ec"),
    CosCategory("秀人", "$NNCOS_HOST/xr"),
    CosCategory("日韩", "$NNCOS_HOST/jk"),
    CosCategory("高清", "$NNCOS_HOST/hd"),
    CosCategory("汉服", "$NNCOS_HOST/hf")
)

@Page("cosImageList")
internal class CosImageListPage : BasePager() {

    var imageItems by observable(listOf<CosImageItem>())
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
        pageIndex = cached.pageIndex
        noMore = cached.noMore
        statusMessage = ""
    }

    fun refreshImages() {
        pageIndex = 0
        noMore = false
        imageItems = emptyList()
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
                statusMessage = "请求超时，请重试"
            }
        }

        bridgeModule.fetchHtml(pageUrl) { response ->
            val code = response?.optInt("code", -1) ?: -1
            val html = response?.optString("body", "") ?: ""
            val result = if (code == 0) parseNncosImages(html) else CosPageResult(emptyList(), false)
            val errorMessage = response?.optString("message", "请求失败，请重试") ?: "请求失败，请重试"
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
                        statusMessage = "没有解析到图片"
                    }
                    return@setTimeout
                }
                val merged = (if (append) mergeCosImageItems(imageItems, result.items) else result.items)
                    .map { item -> item.copy(downloaded = item.detailUrl in downloadedDetailUrls) }
                imageItems = merged.take(NNCOS_MAX_RENDER_ITEMS)
                pageIndex = page
                noMore = !result.hasNextPage || merged.size >= NNCOS_MAX_RENDER_ITEMS
                statusMessage = ""
                categoryCache[currentCategory.url] = CosCategoryCache(imageItems, pageIndex, noMore)
                bridgeModule.log("CosImageList page applied page=$page total=${imageItems.size} noMore=$noMore")
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
                    title = ctx.pagerData.params.optString("title", "半次元图")
                    backDisable = false
                }
            }
            CosCategoryMenu(ctx)
            CosLoginBar(ctx)
            Scroller {
                attr {
                    flex(1f)
                    padding(NNCOS_LIST_PADDING)
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
                Row {
                    attr {
                        width(maxOf(0f, ctx.pageData.pageViewWidth - NNCOS_LIST_PADDING * 2f))
                    }
                    CosWaterfallColumn(ctx, startIndex = 0, rightMargin = 3f, leftMargin = 0f)
                    CosWaterfallColumn(ctx, startIndex = 1, rightMargin = 0f, leftMargin = 3f)
                }
                CosLoadMoreFooter(ctx)
            }
        }
    }

    fun footerText(): String {
        return when {
            loading -> "正在加载..."
            loadingMore -> "正在加载第 ${pageIndex + 1} 页..."
            noMore && imageItems.isNotEmpty() -> "没有更多了"
            statusMessage.isNotEmpty() && imageItems.isEmpty() -> "重试"
            imageItems.isEmpty() -> ""
            else -> "加载更多"
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
            .ifEmpty { "半次元图" }
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
                text(if (ctx.nncosLoggedIn) "半次元图：已登录" else "半次元图：未登录")
                fontSize(13f)
                color(Color(0xFF666666))
            }
        }
        Text {
            attr {
                text(if (ctx.nncosLoggedIn) "重新登录" else "登录")
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

private fun com.tencent.kuikly.core.base.ViewContainer<*, *>.CosWaterfallColumn(
    ctx: CosImageListPage,
    startIndex: Int,
    rightMargin: Float,
    leftMargin: Float
) {
    View {
        attr {
            flex(1f)
            if (rightMargin > 0f) marginRight(rightMargin)
            if (leftMargin > 0f) marginLeft(leftMargin)
        }
        for (slot in 0 until NNCOS_MAX_RENDER_ITEMS / 2) {
            CosImageCard(ctx, startIndex + slot * 2)
        }
    }
}

private fun com.tencent.kuikly.core.base.ViewContainer<*, *>.CosImageCard(ctx: CosImageListPage, index: Int) {
    View {
        attr {
            val item = ctx.imageItems.getOrNull(index)
            backgroundColor(Color.WHITE)
            borderRadius(6f)
            height(if (item == null) 0f else 220f)
            marginBottom(if (item == null) 0f else 8f)
        }
        event {
            click {
                val item = ctx.imageItems.getOrNull(index) ?: return@click
                val pageData = JSONObject()
                pageData.put("title", item.title)
                pageData.put("detailUrl", item.detailUrl)
                pageData.put("imageUrl", item.imageUrl)
                ctx.acquireModule<RouterModule>(RouterModule.MODULE_NAME)
                    .openPage("cosImageDetail", pageData)
            }
        }
        Image {
            attr {
                src(ctx.imageItems.getOrNull(index)?.imageUrl ?: "")
                height(if (ctx.imageItems.getOrNull(index) == null) 0f else 176f)
                resizeCover()
            }
        }
        View {
            attr {
                height(if (ctx.imageItems.getOrNull(index) == null) 0f else 32f)
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
                    text(ctx.imageItems.getOrNull(index)?.categoryName ?: "")
                    fontSize(11f)
                    color(Color.WHITE)
                    lines(1)
                }
            }
            Text {
                attr {
                    text(ctx.imageItems.getOrNull(index)?.publishedAt ?: "")
                    fontSize(11f)
                    color(Color(0xFFE8E8E8))
                    lines(1)
                }
            }
        }
        Text {
            attr {
                val item = ctx.imageItems.getOrNull(index)
                text(if (item?.downloaded == true) "\u5df2\u4e0b\u8f7d" else "")
                width(if (item?.downloaded == true) 52f else 0f)
                height(if (item?.downloaded == true) 24f else 0f)
                backgroundColor(Color(0xCC198754))
                borderRadius(4f)
                fontSize(11f)
                color(Color.WHITE)
                textAlignCenter()
                absolutePosition(top = 7f, right = 7f)
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
