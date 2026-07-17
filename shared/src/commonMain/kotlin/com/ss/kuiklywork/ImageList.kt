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

private const val NETBIAN_HOME_URL = "https://pic.netbian.com/"
private const val NETBIAN_HOST = "https://pic.netbian.com"
private const val MAX_RENDER_ITEMS = 500
private const val PAGE_TIMEOUT_MS = 15000
private const val LIST_HORIZONTAL_PADDING = 6f

data class ImageItem(
    val url: String,
    val title: String,
    val detailUrl: String,
    val downloaded: Boolean = false
)

data class CategoryItem(
    val name: String,
    val path: String
)

private val CATEGORY_LIST = listOf(
    CategoryItem("全部", "/"),
    CategoryItem("4K动漫", "/4kdongman/"),
    CategoryItem("4K游戏", "/4kyouxi/"),
    CategoryItem("4K美女", "/4kmeinv/"),
    CategoryItem("4K风景", "/4kfengjing/"),
    CategoryItem("4K剧照", "/4kjuzhao/"),
    CategoryItem("4K汽车", "/4kqiche/"),
    CategoryItem("4K动物", "/4kdongwu/"),
    CategoryItem("4K宗教", "/4kzongjiao/"),
    CategoryItem("4K背景", "/4kbeijing/"),
    CategoryItem("平板", "/pingban/"),
    CategoryItem("车机", "/cheji/"),
    CategoryItem("4K手机", "/shoujibizhi/"),
    CategoryItem("8K壁纸", "/search/2332-0.html")
)

data class CategoryCache(
    val items: List<ImageItem>,
    val pageIndex: Int,
    val noMore: Boolean
)

@Page("imageList")
internal class ImageListPage : BasePager() {

    var imageItems by observable(listOf<ImageItem>())
    var loading by observable(false)
    var loadingMore by observable(false)
    var noMore by observable(false)
    var statusMessage by observable("")
    var netbianLoggedIn by observable(false)
    var netbianLoginChecking by observable(false)
    var selectedCategoryIndex by observable(0)
    var pageIndex = 0
    private var requestId = 0
    private val categoryCache = mutableMapOf<String, CategoryCache>()
    private var downloadedImageUrls = emptySet<String>()

    val currentCategoryPath: String
        get() = CATEGORY_LIST[selectedCategoryIndex].path

    override fun created() {
        super.created()
        refreshLoginState()
        loadDownloadedRecords()
        refreshImages()
    }

    override fun pageDidAppear() {
        super.pageDidAppear()
        refreshLoginState()
        loadDownloadedRecords()
    }

    private fun loadDownloadedRecords() {
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).getNetbianDownloadRecords { response ->
            if (response?.optInt("code", -1) != 0) {
                return@getNetbianDownloadRecords
            }
            downloadedImageUrls = response.optString("urls", "")
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
            imageItems = applyDownloadedState(imageItems)
            val updatedCache = categoryCache.mapValues { (_, cache) ->
                cache.copy(items = applyDownloadedState(cache.items))
            }
            categoryCache.clear()
            categoryCache.putAll(updatedCache)
        }
    }

    fun refreshLoginState() {
        if (netbianLoginChecking) return
        netbianLoginChecking = true
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).getNetbianLoginState { response ->
            netbianLoginChecking = false
            netbianLoggedIn = response?.optBoolean("isLoggedIn", false) ?: false
        }
    }

    fun openNetbianLogin() {
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).openNetbianLogin { response ->
            netbianLoggedIn = response?.optBoolean("isLoggedIn", false) ?: false
        }
    }

    fun selectCategory(index: Int) {
        if (index == selectedCategoryIndex || loading) return
        selectedCategoryIndex = index
        val cached = categoryCache[currentCategoryPath]
        if (cached != null) {
            imageItems = cached.items
            pageIndex = cached.pageIndex
            noMore = cached.noMore
            statusMessage = ""
        } else {
            refreshImages()
        }
    }

    fun refreshImages() {
        val bridgeModule = acquireModule<BridgeModule>(BridgeModule.MODULE_NAME)
        bridgeModule.log("ImageList refreshImages category=${currentCategoryPath}")
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
                imageItems = applyDownloadedState(mergedItems.take(MAX_RENDER_ITEMS))
                pageIndex = page
                noMore = parsedItems.size < 10 || mergedItems.size >= MAX_RENDER_ITEMS
                statusMessage = ""
                categoryCache[currentCategoryPath] = CategoryCache(imageItems, pageIndex, noMore)
                bridgeModule.log("ImageList page applied page=$page total=${imageItems.size} noMore=$noMore")
            }
        }
    }

    private fun applyDownloadedState(items: List<ImageItem>): List<ImageItem> {
        return items.map { item ->
            item.copy(downloaded = item.url in downloadedImageUrls)
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
            // 分类菜单
            CategoryMenuBar(ctx)
            NetbianLoginBar(ctx)
            Scroller {
                attr {
                    flex(1f)
                    padding(LIST_HORIZONTAL_PADDING)
                }
                event {
                    scroll {
                        val threshold = 200f
                        if (it.offsetY + it.viewHeight >= it.contentHeight - threshold) {
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
                        width(maxOf(0f, ctx.pageData.pageViewWidth - LIST_HORIZONTAL_PADDING * 2f))
                    }
                    FixedWaterfallColumn(ctx, startIndex = 0, rightMargin = 3f, leftMargin = 0f)
                    FixedWaterfallColumn(ctx, startIndex = 1, rightMargin = 0f, leftMargin = 3f)
                }
                LoadMoreFooter(ctx)
            }
        }
    }
}

private fun com.tencent.kuikly.core.base.ViewContainer<*, *>.NetbianLoginBar(ctx: ImageListPage) {
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
                text(if (ctx.netbianLoggedIn) "\u5f7c\u5cb8\u56fe\u7f51\uff1a\u5df2\u767b\u5f55" else "\u5f7c\u5cb8\u56fe\u7f51\uff1a\u672a\u767b\u5f55")
                fontSize(13f)
                color(Color(0xFF666666))
            }
        }
        Text {
            attr {
                text(if (ctx.netbianLoggedIn) "\u91cd\u65b0\u767b\u5f55" else "\u767b\u5f55\u83b7\u53d6\u9ad8\u6e05\u56fe")
                fontSize(13f)
                color(Color(0xFF1E6BFF))
                fontWeightBold()
            }
            event {
                click {
                    ctx.openNetbianLogin()
                }
            }
        }
        event {
            click {
                ctx.openNetbianLogin()
            }
        }
    }
}

private fun ImageListPage.netbianPageUrl(page: Int): String {
    val basePath = currentCategoryPath
    val searchMatch = Regex("^/search/(\\d+)-\\d+\\.html$", RegexOption.IGNORE_CASE).find(basePath)
    return when {
        basePath == "/" && page <= 1 -> NETBIAN_HOME_URL
        basePath == "/" -> "${NETBIAN_HOST}/index_${page}.html"
        searchMatch != null -> {
            val searchId = searchMatch.groupValues[1]
            "${NETBIAN_HOST}/search/$searchId-${page - 1}.html"
        }
        page <= 1 -> "${NETBIAN_HOST}${basePath}"
        else -> "${NETBIAN_HOST}${basePath}index_${page}.html"
    }
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
        val detailPath = anchor.extractHtmlAttr("href") ?: return@forEach
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
        items.add(ImageItem(url, title.decodeBasicHtmlEntities().trim(), detailPath.toAbsoluteNetbianUrl()))
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
                height(if (ctx.imageItems.getOrNull(index)?.downloaded == true) 24f else 0f)
                paddingLeft(7f)
                paddingRight(7f)
                backgroundColor(Color(0xCC17803D))
                borderRadius(12f)
                absolutePosition(top = 7f, right = 7f)
                allCenter()
            }
            Text {
                attr {
                    text(if (ctx.imageItems.getOrNull(index)?.downloaded == true) "已下载" else "")
                    fontSize(11f)
                    color(Color.WHITE)
                    fontWeightBold()
                }
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
        event {
            click {
                ctx.imageItems.getOrNull(index)?.also { item ->
                    val pageData = JSONObject()
                    pageData.put("title", item.title)
                    pageData.put("imageUrl", item.url)
                    pageData.put("listImageUrl", item.url)
                    pageData.put("detailUrl", item.detailUrl)
                    ctx.acquireModule<RouterModule>(RouterModule.MODULE_NAME)
                        .openPage("imageDetail", pageData)
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
        loadingMore -> "\u6b63\u5728\u52a0\u8f7d\u7b2c ${pageIndex + 2} \u9875..."
        noMore && imageItems.isNotEmpty() -> "\u6ca1\u6709\u66f4\u591a\u4e86"
        statusMessage.isNotEmpty() && imageItems.isEmpty() -> "\u91cd\u8bd5"
        imageItems.isEmpty() -> ""
        else -> "\u6b63\u5728\u52a0\u8f7d\u7b2c ${pageIndex + 2} \u9875"
    }
}

private fun com.tencent.kuikly.core.base.ViewContainer<*, *>.CategoryMenuBar(ctx: ImageListPage) {
    Scroller {
        attr {
            height(44f)
            backgroundColor(Color.WHITE)
            flexDirectionRow()
            alignItemsCenter()
        }
        CATEGORY_LIST.forEachIndexed { index, category ->
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
                        if (index == ctx.selectedCategoryIndex) {
                            fontWeightBold()
                        }
                    }
                }
                event {
                    click {
                        ctx.selectCategory(index)
                    }
                }
            }
        }
    }
}
