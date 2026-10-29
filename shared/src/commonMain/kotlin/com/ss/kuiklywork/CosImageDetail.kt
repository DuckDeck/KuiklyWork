package com.ss.kuiklywork

import com.ss.kuiklywork.base.BasePager
import com.ss.kuiklywork.base.BridgeModule
import com.ss.kuiklywork.base.parseNncosLoginState
import com.ss.kuiklywork.base.setTimeout
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewBuilder
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

internal enum class CosDetailImageLoadState { LOADING, LOADED, FAILED }

@Page("cosImageDetail")
internal class CosImageDetailPage : BasePager() {

    var titleText by observable("")
    var detailUrl by observable("")
    var imageUrls by observableList<String>()
    var imageAspectRatios by observable(emptyMap<String, Float>())
    private var imageLoadStates by observable(emptyMap<String, CosDetailImageLoadState>())
    var statusText by observable("")
    var downloading by observable(false)
    var downloadingIndex by observable(0)
    var downloaded by observable(false)
    private var detailLoadRequestId = 0
    private var loginExpired by observable(false)
    private var loginRenewing by observable(false)
    private var loginStateClearing = false
    private var contentNotice = ""
    private var detailPagesLoading = false
    private var detailLoadFailed = false
    private var downloadTask by observable<CosDownloadSnapshot?>(null)
    private var downloadSubmitting by observable(false)
    private var downloadUiGeneration = 0
    private var lastCompletedTaskId = ""
    private val downloadPoller by lazy {
        CosDownloadPoller(
            query = { done -> acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).getCosDownloadTask(detailUrl) { done(parseCosDownloadSnapshot(it)) } },
            schedule = { delay, callback -> setTimeout(delay, callback) },
            update = { applyDownloadTask(it) }
        )
    }

    override fun pageDidAppear() {
        super.pageDidAppear()
        downloadSubmitting = false
        downloadPoller.start()
    }
    override fun pageDidDisappear() { downloadUiGeneration++; downloadPoller.stop(); super.pageDidDisappear() }
    override fun pageWillDestroy() { downloadUiGeneration++; downloadPoller.stop(); super.pageWillDestroy() }

    private fun applyDownloadTask(task: CosDownloadSnapshot) {
        if (task.status != "idle" && task.detailUrl != detailUrl) return
        downloadTask = task
        downloading = task.status == "running"
        downloadingIndex = task.completed
        if (task.status == "completed" && task.taskId != lastCompletedTaskId) {
            lastCompletedTaskId = task.taskId
            loadDownloadState()
        }
    }

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
            detailLoadFailed = true
            statusText = "\u6ca1\u6709\u56fe\u7247\u8be6\u60c5\u5730\u5740"
            return
        }
        val requestId = ++detailLoadRequestId
        detailPagesLoading = true
        detailLoadFailed = false
        loginExpired = false
        contentNotice = ""
        statusText = "\u6b63\u5728\u52a0\u8f7d..."
        imageUrls.clear()
        imageAspectRatios = emptyMap()
        imageLoadStates = emptyMap()
        loadDetailPages(
            pendingUrls = mutableListOf(detailUrl),
            loadedUrls = mutableSetOf(),
            requestId = requestId
        )
    }

    private fun loadDetailPages(
        pendingUrls: MutableList<String>,
        loadedUrls: MutableSet<String>,
        requestId: Int
    ) {
        if (pendingUrls.isEmpty()) {
            detailPagesLoading = false
            statusText = if (detailLoadFailed) "部分页面加载失败，点击重试" else if (imageUrls.isEmpty()) "\u6ca1\u6709\u89e3\u6790\u5230\u56fe\u7247" else ""
            return
        }
        val pageUrl = pendingUrls.removeAt(0)
        if (!loadedUrls.add(pageUrl)) {
            loadDetailPages(pendingUrls, loadedUrls, requestId)
            return
        }
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).fetchHtml(pageUrl) { response ->
            if (requestId != detailLoadRequestId) return@fetchHtml
            val html = response?.optString("body", "") ?: ""
            if (response?.optInt("code", -1) != 0 || html.isEmpty()) {
                detailLoadFailed = true
                if (imageUrls.isEmpty() && pendingUrls.isEmpty()) {
                    statusText = response?.optString("message", "\u8be6\u60c5\u52a0\u8f7d\u5931\u8d25") ?: "\u8be6\u60c5\u52a0\u8f7d\u5931\u8d25"
                }
                loadDetailPages(pendingUrls, loadedUrls, requestId)
                return@fetchHtml
            }
            val result = parseNncosDetail(html, pageUrl)
            if (!appendDetailPage(result)) {
                loadDetailPages(pendingUrls, loadedUrls, requestId)
                return@fetchHtml
            }
            if (result.hasHiddenVipContent) {
                detailPagesLoading = false
                pendingUrls.clear()
                contentNotice = when (result.isLoggedIn) {
                    true -> "当前账号无法查看完整会员内容，以上为可查看的图片"
                    false -> "请重新登录或开通会员查看完整图片"
                    null -> "无法确认登录状态，以上为可查看的图片，点击重试"
                }
                if (result.requiresLogin) {
                    markNncosLoginExpired()
                } else {
                    statusText = contentNotice
                }
                return@fetchHtml
            }
            result.pageUrls
                .filter { it !in loadedUrls && it !in pendingUrls }
                .forEach { pendingUrls.add(it) }
            if (pendingUrls.isEmpty()) {
                detailPagesLoading = false
                statusText = if (detailLoadFailed) "部分页面加载失败，点击重试" else if (imageUrls.isEmpty()) "\u6ca1\u6709\u89e3\u6790\u5230\u56fe\u7247" else ""
            } else {
                statusText = "\u6b63\u5728\u52a0\u8f7d\u7b2c ${loadedUrls.size + 1} \u9875..."
                loadDetailPages(pendingUrls, loadedUrls, requestId)
            }
        }
    }

    internal fun appendDetailPage(result: CosDetailResult): Boolean {
        if (!result.isRecognized) { detailLoadFailed = true; return false }
        if (result.title.isNotEmpty()) titleText = result.title
        // Preview images remain useful even when the member section is locked.
        val knownImageUrls = imageUrls.toHashSet()
        imageUrls.addAll(result.imageUrls.filter { knownImageUrls.add(it) })
        return true
    }

    internal fun readyDownloadManifest(): List<String>? =
        cosDownloadManifest(imageUrls, detailPagesLoading, detailLoadFailed)

    private fun markNncosLoginExpired() {
        if (loginExpired) return
        loginExpired = true
        statusText = contentNotice
        loginStateClearing = true
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).invalidateNncosLogin {
            loginStateClearing = false
        }
    }

    private fun renewNncosLogin() {
        if (loginRenewing || loginStateClearing) return
        loginRenewing = true
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).openNncosLogin { response ->
            loginRenewing = false
            if (response?.optInt("code", -1) == 0 && response.optBoolean("isLoggedIn", false)) {
                loadDetail()
            }
        }
    }

    fun downloadAllImages() {
        if (loginExpired) {
            renewNncosLogin()
            return
        }
        if (downloading || downloadSubmitting) return
        val manifest = readyDownloadManifest()
        if (manifest == null && downloadTask?.status != "interrupted") {
            acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).toast("图片清单尚未加载完整，请加载完成后再下载")
            return
        }
        downloadSubmitting = true
        val generation = downloadUiGeneration
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).startCosDownloadTask(detailUrl, titleText, manifest.orEmpty(), detailUrl) { response ->
            if (generation != downloadUiGeneration) return@startCosDownloadTask
            downloadSubmitting = false
            if (response?.optInt("code", -1) != 0) {
                acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).toast(response?.optString("message", "无法开始下载") ?: "无法开始下载")
                return@startCosDownloadTask
            }
            parseCosDownloadSnapshot(response)?.let { applyDownloadTask(it) }
        }
    }

    private fun imageHeight(imageUrl: String): Float {
        val ratio = imageAspectRatios[imageUrl] ?: COS_DETAIL_FALLBACK_IMAGE_HEIGHT_RATIO
        return pageData.pageViewWidth * ratio
    }

    internal fun navigationTitle(): String {
        val shortTitle = if (titleText.length <= 10) titleText else "${titleText.take(10)}..."
        return "$shortTitle（${imageUrls.size}张）"
    }

    internal fun imageLoadState(imageUrl: String): CosDetailImageLoadState =
        imageLoadStates[imageUrl] ?: CosDetailImageLoadState.LOADING

    internal fun completeImageLoad(imageUrl: String, succeeded: Boolean, requestId: Int = detailLoadRequestId) {
        if (requestId != detailLoadRequestId || imageUrl !in imageUrls) return
        val state = if (succeeded) CosDetailImageLoadState.LOADED else CosDetailImageLoadState.FAILED
        if (imageLoadStates[imageUrl] != state) imageLoadStates = imageLoadStates + (imageUrl to state)
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
            vif({ ctx.downloadTask?.visible == true }) {
                View {
                    attr {
                        height(if (ctx.downloadTask?.status == "interrupted") 52f else 36f)
                        flexDirectionRow()
                        allCenter()
                    }
                    vif({ ctx.downloading }) {
                        ActivityIndicator {
                            attr {
                                isGrayStyle(true)
                                marginRight(8f)
                            }
                        }
                    }
                    Text {
                        attr {
                            text(if (ctx.downloadTask?.status == "interrupted") "已暂停 ${ctx.downloadingIndex}/${ctx.downloadTask?.total ?: 0}：${ctx.downloadTask?.message.orEmpty()}" else "已下载 ${ctx.downloadingIndex}/${ctx.downloadTask?.total ?: 0} 张（${ctx.downloadTask?.percent ?: 0}%）")
                            fontSize(13f)
                            lines(2)
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
                                ctx.loginRenewing -> "登录中"
                                ctx.loginExpired -> "重新登录"
                                ctx.downloadSubmitting -> "准备中"
                                else -> (ctx.downloadTask ?: CosDownloadSnapshot()).buttonText(ctx.downloaded)
                            }
                        )
                        fontSize(14f)
                        color(Color(0xFF1E6BFF))
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
                vforLazy({ ctx.imageUrls }, maxLoadItem = 6) { imageUrl, _, _ ->
                    val requestId = ctx.detailLoadRequestId
                    View {
                        attr {
                            width(ctx.pageData.pageViewWidth)
                            height(ctx.imageHeight(imageUrl))
                        }
                        Image {
                            attr {
                                src(imageUrl)
                                width(ctx.pageData.pageViewWidth)
                                height(ctx.imageHeight(imageUrl))
                                resizeContain()
                            }
                            event {
                                loadSuccess { ctx.completeImageLoad(imageUrl, succeeded = true, requestId = requestId) }
                                loadFailure { ctx.completeImageLoad(imageUrl, succeeded = false, requestId = requestId) }
                                loadResolution { resolution ->
                                    if (requestId == ctx.detailLoadRequestId) {
                                        ctx.updateImageAspectRatio(imageUrl, resolution.width, resolution.height)
                                    }
                                }
                            }
                        }
                        vif({ ctx.imageLoadState(imageUrl) != CosDetailImageLoadState.LOADED }) {
                            View {
                                attr {
                                    absolutePosition(top = 0f, left = 0f)
                                    width(ctx.pageData.pageViewWidth)
                                    height(ctx.imageHeight(imageUrl))
                                    allCenter()
                                }
                                vif({ ctx.imageLoadState(imageUrl) == CosDetailImageLoadState.LOADING }) {
                                    ActivityIndicator {
                                        attr { isGrayStyle(false) }
                                    }
                                }
                                Text {
                                    attr {
                                        text(if (ctx.imageLoadState(imageUrl) == CosDetailImageLoadState.FAILED) "图片加载失败" else "加载中...")
                                        marginTop(8f)
                                        fontSize(13f)
                                        color(Color(0xFFB5B5B5))
                                    }
                                }
                            }
                        }
                    }
                }
                View {
                    attr {
                        height(if (ctx.statusText.isNotEmpty()) 72f else 0f)
                        paddingLeft(16f)
                        paddingRight(16f)
                        allCenter()
                    }
                    event {
                        click {
                            if (ctx.loginExpired) {
                                ctx.renewNncosLogin()
                            } else if (!ctx.downloading) {
                                ctx.loadDetail()
                            }
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
            }
        }
    }
}

internal data class CosDetailResult(
    val title: String,
    val imageUrls: List<String>,
    val pageUrls: List<String>,
    val hasHiddenVipContent: Boolean,
    val isLoggedIn: Boolean?,
    val isRecognized: Boolean
) {
    val requiresLogin: Boolean
        get() = hasHiddenVipContent && isLoggedIn == false
}

internal fun parseNncosDetail(html: String, pageUrl: String): CosDetailResult {
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
    val content = (postContent ?: entryContent).replace(
        Regex("<!--[\\s\\S]*?-->|<(script|style)\\b[^>]*>[\\s\\S]*?</\\1\\s*>", RegexOption.IGNORE_CASE),
        ""
    )
    val imageUrls = content.cosDetailImageUrls()
    return CosDetailResult(
        title = title,
        imageUrls = imageUrls,
        pageUrls = parseNncosDetailPageUrls(content, pageUrl),
        hasHiddenVipContent = content.hasLockedCosVipContent(),
        isLoggedIn = parseNncosLoginState(html),
        isRecognized = (postContent != null || contentStart >= 0) && (imageUrls.isNotEmpty() || content.hasLockedCosVipContent())
    )
}

private fun String.cosDetailImageUrls(): List<String> {
    return Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(this)
        .mapNotNull { tag ->
            tag.value.extractCosDetailAttribute("data-src")
                ?: tag.value.extractCosDetailAttribute("data-original")
                ?: tag.value.extractCosDetailAttribute("src")
        }
        .map { it.toAbsoluteCosDetailUrl() }
        .filter { it.isNncosDetailImageUrl() }
        .filterNot { it.substringBefore('?').substringBefore('#').equals("https://img.nncos.com/v.png", ignoreCase = true) }
        .distinct()
        .toList()
}

private fun String.hasLockedCosVipContent(): Boolean {
    val tags = Regex("<([a-z][\\w:-]*)\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(this).toList()
    val regions = tags.filter { tag ->
        val classes = tag.value.cosDetailClasses()
        "vip-hide-content" in classes || "viphidecontent" in classes
    }
    if (regions.isEmpty()) return tags.any { "vip-hide-tips" in it.value.cosDetailClasses() }
    return regions.any { opening ->
        // Locked and unlocked sections share the same classes. Inspect each section,
        // not the article-wide preview count, and account for nested wrapper elements.
        val name = opening.groupValues[1]
        var depth = 1
        var end = length
        for (tag in Regex("</?$name\\b[^>]*>", RegexOption.IGNORE_CASE)
            .findAll(this, opening.range.last + 1)) {
            if (tag.value.startsWith("</")) depth--
            else if (!tag.value.endsWith("/>")) depth++
            if (depth == 0) {
                end = tag.range.first
                break
            }
        }
        val region = substring(opening.range.last + 1, end)
        val hasUnlockButton = Regex("<a\\b[^>]*>", RegexOption.IGNORE_CASE)
            .findAll(region).any { "zb-vip-btn" in it.value.cosDetailClasses() }
        hasUnlockButton || region.cosDetailImageUrls().isEmpty()
    }
}

private fun String.cosDetailClasses(): List<String> =
    extractCosDetailAttribute("class")?.lowercase()?.split(Regex("\\s+")) ?: emptyList()

/** Returns the page links emitted by WordPress's in-post pagination widget. */
private fun parseNncosDetailPageUrls(html: String, currentPageUrl: String): List<String> {
    val pageUrls = mutableListOf<String>()
    Regex("<a\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(html).forEach { match ->
        val tag = match.value
        val className = tag.extractCosDetailAttribute("class") ?: return@forEach
        if (!className.contains("page-numbers", ignoreCase = true)) return@forEach
        val href = tag.extractCosDetailAttribute("href") ?: return@forEach
        href.toAbsoluteCosDetailUrl(currentPageUrl).takeIf { it != currentPageUrl }?.let(pageUrls::add)
    }
    return pageUrls.distinct()
}

private fun String.isNncosDetailImageUrl(): Boolean {
    return contains("img.nncos.com/", ignoreCase = true) ||
        contains("nncos.us/nnpic2/", ignoreCase = true)
}

private fun String.extractCosDetailAttribute(name: String): String? {
    return Regex("$name\\s*=\\s*([\"'])(.*?)\\1", RegexOption.IGNORE_CASE)
        .find(this)?.groupValues?.getOrNull(2)
}

private fun String.toAbsoluteCosDetailUrl(baseUrl: String = COS_DETAIL_HOST): String {
    val url = decodeCosDetailHtmlEntities()
    return when {
        url.startsWith("//") -> "https:$url"
        url.startsWith("http://") || url.startsWith("https://") -> url
        url.startsWith("/") -> "$COS_DETAIL_HOST$url"
        url.startsWith("?") -> baseUrl.substringBefore('?').substringBefore('#') + url
        baseUrl == COS_DETAIL_HOST -> "$COS_DETAIL_HOST/$url"
        else -> baseUrl.substringBefore('?').substringBefore('#').substringBeforeLast('/') + "/$url"
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
