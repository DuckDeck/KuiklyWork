package com.ss.kuiklywork

import com.ss.kuiklywork.base.BasePager
import com.ss.kuiklywork.base.BridgeModule
import com.ss.kuiklywork.base.setTimeout
import com.ss.kuiklywork.base.parseNncosLoginState
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.directives.vforLazy
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Input
import com.tencent.kuikly.core.views.InputView
import com.tencent.kuikly.core.views.List
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

private const val NNCOS_REQUEST_TIMEOUT_MS = 15000
private const val NNCOS_LIST_PADDING = 6f

private data class CosCategory(val name: String, val url: String)

private data class CosCategoryCache(
    val items: List<CosImageItem>,
    val pageIndex: Int,
    val noMore: Boolean,
    val nextPageUrl: String?
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
    var nncosLoginKnown by observable(false)
    var nncosLoginChecking by observable(false)
    var searchVisible by observable(false)
    var searchInputText by observable("")
    var searchKeyword by observable("")
        private set
    var searchInput: InputView? = null

    private var pageIndex = 0
    private var nextPageUrl: String? = null
    private var requestId = 0
    private var loginStateRequestId = 0
    private val categoryCache = mutableMapOf<String, CosCategoryCache>()
    private var downloadedDetailUrls = emptySet<String>()
    private var downloadTask by observable<CosDownloadSnapshot?>(null)
    private var lastCompletedTaskId = ""
    private val downloadPoller by lazy {
        CosDownloadPoller(
            query = { done -> acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).getCosDownloadTask { done(parseCosDownloadSnapshot(it)) } },
            schedule = { delay, callback -> setTimeout(delay, callback) },
            update = { task ->
                val old = downloadTask
                downloadTask = task
                val finishedRunning = old?.status == "running" && (task.taskId != old.taskId || task.status != "running")
                if (finishedRunning || task.status == "completed" && task.taskId != lastCompletedTaskId) {
                    if (task.status == "completed") lastCompletedTaskId = task.taskId
                    loadDownloadedRecords()
                }
            }
        )
    }

    private val currentCategory: CosCategory
        get() = COS_CATEGORY_LIST[selectedCategoryIndex]

    private val currentListUrl: String
        get() = if (searchKeyword.isEmpty()) currentCategory.url else nncosSearchUrl(searchKeyword)

    override fun created() {
        super.created()
        refreshLoginState()
        loadDownloadedRecords { refreshImages() }
    }

    override fun pageDidAppear() {
        super.pageDidAppear()
        refreshLoginState()
        loadDownloadedRecords()
        downloadPoller.start()
    }

    override fun pageDidDisappear() { downloadPoller.stop(); super.pageDidDisappear() }
    override fun pageWillDestroy() { downloadPoller.stop(); super.pageWillDestroy() }

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
        val currentLoginRequestId = ++loginStateRequestId
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).getNncosLoginState { response ->
            if (currentLoginRequestId != loginStateRequestId) return@getNncosLoginState
            nncosLoginChecking = false
            if (response?.optInt("code", -1) == 0) {
                nncosLoggedIn = response.optBoolean("isLoggedIn", false)
                nncosLoginKnown = true
            }
        }
    }

    fun openNncosLogin() {
        val currentLoginRequestId = ++loginStateRequestId
        nncosLoginChecking = true
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).openNncosLogin { response ->
            if (currentLoginRequestId != loginStateRequestId) return@openNncosLogin
            nncosLoginChecking = false
            if (response?.optInt("code", -1) == 0) {
                nncosLoggedIn = response.optBoolean("isLoggedIn", false)
                nncosLoginKnown = true
            }
            val message = response?.optString("message", "") ?: ""
            if (response?.optInt("code", 0) != 0 && message.isNotEmpty()) {
                acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).toast(message)
            }
        }
    }

    fun selectCategory(index: Int) {
        if (index == selectedCategoryIndex && searchKeyword.isEmpty()) return
        searchInput?.blur()
        searchInput = null
        searchKeyword = ""
        searchInputText = ""
        searchVisible = false
        selectedCategoryIndex = index
        restoreCurrentList()
    }

    fun toggleSearch() {
        if (searchVisible) {
            searchInput?.blur()
            searchInput = null
            searchVisible = false
            searchInputText = ""
            if (searchKeyword.isNotEmpty()) {
                searchKeyword = ""
                restoreCurrentList()
            }
        } else {
            searchInputText = searchKeyword
            searchVisible = true
        }
    }

    fun submitSearch() {
        val keyword = searchInputText.trim()
        if (keyword.isEmpty()) {
            acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).toast("请输入搜索关键词")
            return
        }
        searchInput?.blur()
        searchKeyword = keyword
        refreshImages()
    }

    private fun restoreCurrentList() {
        // Ignore responses and timeouts from the previous category or search.
        requestId++
        loading = false
        loadingMore = false
        val cached = categoryCache[currentListUrl]
        if (cached == null) {
            refreshImages()
            return
        }
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME)
            .log("CosImageList restore cache url=$currentListUrl total=${cached.items.size}")
        imageItems = cached.items
        syncImageRows()
        pageIndex = cached.pageIndex
        noMore = cached.noMore
        nextPageUrl = cached.nextPageUrl
        statusMessage = ""
    }

    fun refreshImages() {
        pageIndex = 0
        nextPageUrl = null
        loading = false
        loadingMore = false
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
        val sourceUrl = currentListUrl
        val pageUrl = if (append) nextPageUrl ?: return else sourceUrl
        val bridgeModule = acquireModule<BridgeModule>(BridgeModule.MODULE_NAME)
        val currentRequestId = ++requestId
        val currentLoginRequestId = loginStateRequestId
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
            if (currentRequestId != requestId) return@fetchHtml
            val result = if (code == 0) parseNncosImages(html) else CosPageResult(emptyList(), null)
            val errorMessage = response?.optString("message", "\u8bf7\u6c42\u5931\u8d25\uff0c\u8bf7\u91cd\u8bd5") ?: "\u8bf7\u6c42\u5931\u8d25\uff0c\u8bf7\u91cd\u8bd5"
            setTimeout(0) {
                if (currentRequestId != requestId) return@setTimeout
                loading = false
                loadingMore = false
                if (code != 0) {
                    statusMessage = errorMessage
                    return@setTimeout
                }
                if (currentLoginRequestId == loginStateRequestId) {
                    parseNncosLoginState(html)?.let { loggedIn ->
                        loginStateRequestId++
                        nncosLoginChecking = false
                        nncosLoggedIn = loggedIn
                        nncosLoginKnown = true
                    }
                }
                applyPageResult(result, page, append, pageUrl, sourceUrl)
                bridgeModule.log("CosImageList page applied page=$page total=${imageItems.size} noMore=$noMore")
            }
        }
    }

    internal fun applyPageResult(
        result: CosPageResult,
        page: Int,
        append: Boolean,
        pageUrl: String,
        sourceUrl: String = currentListUrl
    ) {
        if (result.items.isEmpty()) {
            nextPageUrl = null
            noMore = true
            statusMessage = if (append) "" else if (searchKeyword.isNotEmpty()) {
                "没有找到相关图片，试试其他关键词"
            } else "没有解析到图片"
            return
        }
        val merged = (if (append) mergeCosImageItems(imageItems, result.items) else result.items)
            .map { item -> item.copy(downloaded = item.detailUrl in downloadedDetailUrls) }
        // Native views are already bounded by vforLazy. Keep all fetched records
        // and let the server's next-page link determine when pagination ends.
        imageItems = merged
        syncImageRows()
        pageIndex = page
        nextPageUrl = result.nextPageUrl?.takeIf { it != pageUrl }
        noMore = nextPageUrl == null
        statusMessage = ""
        categoryCache[sourceUrl] = CosCategoryCache(imageItems, pageIndex, noMore, nextPageUrl)
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
            vif({ ctx.searchVisible }) {
                CosSearchBar(ctx)
            }
            vif({ ctx.searchKeyword.isEmpty() }) {
                CosCategoryMenu(ctx)
            }
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
            View {
                attr {
                    width(64f)
                    height(44f)
                    absolutePosition(top = ctx.pageData.statusBarHeight, right = 0f)
                    allCenter()
                }
                event {
                    click { ctx.toggleSearch() }
                }
                Text {
                    attr {
                        text(if (ctx.searchVisible) "取消" else "搜索")
                        fontSize(14f)
                        color(Color(0xFF1E6BFF))
                        fontWeightBold()
                    }
                }
            }
            vif({ ctx.downloadTask?.visible == true }) {
                CosDownloadProgress(
                    snapshot = { ctx.downloadTask },
                    bottom = { maxOf(ctx.pageData.safeAreaInsets.bottom, ctx.pageData.androidBottomBavBarHeight) + 24f },
                    onClick = {
                        ctx.downloadTask?.let { task ->
                            ctx.acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage("cosImageDetail", JSONObject().apply {
                                put("title", task.title); put("detailUrl", task.detailUrl)
                            })
                        }
                    }
                )
            }
        }
    }

    fun footerText(): String {
        return when {
            loading -> "\u6b63\u5728\u52a0\u8f7d..."
            loadingMore -> "\u6b63\u5728\u52a0\u8f7d\u7b2c ${pageIndex + 1} \u9875..."
            noMore && imageItems.isNotEmpty() -> "\u6ca1\u6709\u66f4\u591a\u4e86"
            noMore && searchKeyword.isNotEmpty() && imageItems.isEmpty() -> ""
            statusMessage.isNotEmpty() && imageItems.isEmpty() -> "\u91cd\u8bd5"
            imageItems.isEmpty() -> ""
            else -> "\u52a0\u8f7d\u66f4\u591a"
        }
    }
}

private fun mergeCosImageItems(oldItems: List<CosImageItem>, newItems: List<CosImageItem>): List<CosImageItem> {
    val seenUrls = mutableSetOf<String>()
    return (oldItems + newItems).filter { item -> seenUrls.add(item.imageUrl) }
}

private fun com.tencent.kuikly.core.base.ViewContainer<*, *>.CosSearchBar(ctx: CosImageListPage) {
    View {
        attr {
            height(52f)
            paddingLeft(12f)
            paddingRight(12f)
            flexDirectionRow()
            alignItemsCenter()
            backgroundColor(Color.WHITE)
        }
        View {
            attr {
                flex(1f)
                height(36f)
                paddingLeft(12f)
                paddingRight(12f)
                borderRadius(8f)
                backgroundColor(Color(0xFFF2F2F7))
            }
            Input {
                ref { ctx.searchInput = it.view }
                attr {
                    flex(1f)
                    height(36f)
                    fontSize(14f)
                    color(Color(0xFF333333))
                    placeholder("输入关键词搜索图片")
                    placeholderColor(Color(0xFF999999))
                    text(ctx.searchInputText)
                    returnKeyTypeSearch()
                    autofocus(true)
                }
                event {
                    textDidChange { ctx.searchInputText = it.text }
                    inputReturn {
                        ctx.searchInputText = it.text
                        ctx.submitSearch()
                    }
                }
            }
        }
        View {
            attr {
                width(56f)
                height(44f)
                allCenter()
            }
            event {
                click { ctx.submitSearch() }
            }
            Text {
                attr {
                    text("搜索")
                    fontSize(14f)
                    color(Color(0xFF1E6BFF))
                    fontWeightBold()
                }
            }
        }
    }
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
                text(when {
                    ctx.nncosLoginChecking -> "半次元图：正在检查登录状态"
                    !ctx.nncosLoginKnown -> "半次元图：登录状态待确认"
                    ctx.nncosLoggedIn -> "半次元图：已登录"
                    else -> "半次元图：未登录"
                })
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
                width(if (item?.isVip == true) 38f else 0f)
                height(if (item?.isVip == true) 24f else 0f)
                backgroundColor(Color(0xCCE88B20))
                borderRadius(4f)
                absolutePosition(top = 7f, left = 7f)
                allCenter()
            }
            Text {
                attr {
                    text(if (item?.isVip == true) "VIP" else "")
                    fontSize(11f)
                    color(Color.WHITE)
                    fontWeightBold()
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
