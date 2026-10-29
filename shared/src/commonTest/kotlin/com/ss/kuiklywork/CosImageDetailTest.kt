package com.ss.kuiklywork

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull

class CosImageDetailTest {
    private val pageUrl = "https://www.nncos.com/27619.html"

    @Test fun loginOrMaintenanceHtmlIsNotACompleteDetailPage() {
        assertFalse(parseNncosDetail("<html><h1>维护中</h1><form>登录</form></html>", pageUrl).isRecognized)
        assertFalse(parseNncosDetail("<div class='recommend'><img src='https://img.nncos.com/banner.jpg'></div>", pageUrl).isRecognized)
        assertTrue(parseNncosDetail("<article class='post-content'><img src='https://img.nncos.com/1.jpg'></article>", pageUrl).isRecognized)
    }

    @Test fun unrecognizedSecondPageKeepsPartialAlbumUndownloadable() {
        val page = CosImageDetailPage()
        assertTrue(page.appendDetailPage(parseNncosDetail("<article class='post-content'><img src='https://img.nncos.com/1.jpg'></article>", pageUrl)))
        assertEquals(1, page.imageUrls.size)
        assertFalse(page.appendDetailPage(parseNncosDetail("<html><h1>需要登录</h1></html>", "$pageUrl/2")))
        assertEquals(1, page.imageUrls.size) // Keep available previews visible.
        assertNull(page.readyDownloadManifest())
    }

    @Test
    fun titleShowsAvailableImageCountAsPagesAreAdded() {
        val page = CosImageDetailPage()
        page.titleText = "壁纸"
        assertEquals("壁纸（0张）", page.navigationTitle())
        page.imageUrls.addAll(listOf("preview-1", "preview-2", "preview-3"))
        assertEquals("壁纸（3张）", page.navigationTitle())
        page.imageUrls.addAll((1..30).map { "member-$it" })
        assertEquals("壁纸（33张）", page.navigationTitle())
    }

    @Test
    fun truncatingLongTitlesDoesNotRemoveImageCount() {
        val page = CosImageDetailPage()
        page.titleText = "一二三四五六七八九十十一十二"
        page.imageUrls.add("preview")
        assertEquals("一二三四五六七八九十...（1张）", page.navigationTitle())
    }

    @Test
    fun eachImageLoadsIndependentlyUntilSuccess() {
        val page = CosImageDetailPage()
        page.imageUrls.addAll(listOf("first", "second"))
        assertEquals(CosDetailImageLoadState.LOADING, page.imageLoadState("first"))
        page.completeImageLoad("first", succeeded = true)
        assertEquals(CosDetailImageLoadState.LOADED, page.imageLoadState("first"))
        assertEquals(CosDetailImageLoadState.LOADING, page.imageLoadState("second"))
    }

    @Test
    fun failedImagesStopLoadingAndCanLaterSucceed() {
        val page = CosImageDetailPage()
        page.imageUrls.add("image")
        page.completeImageLoad("image", succeeded = false)
        assertEquals(CosDetailImageLoadState.FAILED, page.imageLoadState("image"))
        page.completeImageLoad("image", succeeded = true)
        assertEquals(CosDetailImageLoadState.LOADED, page.imageLoadState("image"))
    }

    @Test
    fun staleImageCallbacksCannotFinishCurrentLoad() {
        val page = CosImageDetailPage()
        page.imageUrls.add("image")
        page.completeImageLoad("image", succeeded = true, requestId = -1)
        assertEquals(CosDetailImageLoadState.LOADING, page.imageLoadState("image"))
    }

    @Test
    fun unlockedHyphenatedVipRegionKeepsAllThirtyThreeImages() {
        val previews = (1..3).joinToString("") { "<img src='https://img.nncos.com/preview-$it.webp'>" }
        val members = (1..30).joinToString("") { "<img data-src='https://img.nncos.com/member-$it.webp'>" }
        val result = parseNncosDetail("""
            <a><i class="icon-people"></i>我的账户</a>
            <article class="post-content">
                $previews
                <div class="vip-hide-content">
                    <span class="vip-hide-tips">VIP内容</span>
                    <div><div>$members</div></div>
                </div>
            </article>
        """.trimIndent(), "https://www.nncos.com/28131.html")

        assertEquals(33, result.imageUrls.size)
        assertFalse(result.hasHiddenVipContent)
        assertFalse(result.requiresLogin)
    }

    @Test
    fun lockedRegionKeepsThreePreviewsWithoutVipDecorationImages() {
        val result = parseNncosDetail("""
            <a><i class="icon-people"></i>我的账户</a>
            <article class="post-content">
                <img src="https://img.nncos.com/preview-1.webp">
                <img src="https://img.nncos.com/preview-2.webp">
                <img src="https://img.nncos.com/preview-3.gif">
                <div class="vip-hide-content">
                    <span class="vip-hide-tips">VIP内容</span>
                    <img src="https://img.nncos.com/v.png">
                    <a class="zb-vip-btn" href="/pay-vip/">开通 VIP 解锁</a>
                </div>
            </article>
        """.trimIndent(), pageUrl)

        assertEquals(listOf(
            "https://img.nncos.com/preview-1.webp",
            "https://img.nncos.com/preview-2.webp",
            "https://img.nncos.com/preview-3.gif"
        ), result.imageUrls)
        assertTrue(result.hasHiddenVipContent)
        assertFalse(result.requiresLogin)
    }

    @Test
    fun lockedRegionIsNotUnlockedByImagesInAnotherMemberRegion() {
        val result = parseNncosDetail("""
            <article class="post-content">
                <div class="vip-hide-content"><img src="https://img.nncos.com/member.webp"></div>
                <div class="vip-hide-content"><span class="vip-hide-tips">VIP内容</span>请登录查看</div>
            </article>
        """.trimIndent(), pageUrl)
        assertEquals(1, result.imageUrls.size)
        assertTrue(result.hasHiddenVipContent)
    }

    @Test
    fun vipMarkupInsideScriptsDoesNotHidePublicImages() {
        val result = parseNncosDetail("""
            <article class="post-content">
                <script>const template = '<div class="vip-hide-content">请登录</div>';</script>
                <img src="https://img.nncos.com/public.webp">
            </article>
        """.trimIndent(), pageUrl)
        assertEquals(1, result.imageUrls.size)
        assertFalse(result.hasHiddenVipContent)
    }

    @Test
    fun lockedVipContentRequiresLoginEvenWithPreviewImages() {
        val result = parseNncosDetail("""
            <article class="post-content post-27619">
                <img src="https://img.nncos.com/2026/10/19/k1DMxdoI.webp">
                <img src="https://img.nncos.com/2026/10/19/FXySRcW5.gif">
                <img src="https://img.nncos.com/2026/10/19/h2K2KnJR.webp">
                <div class="vip-hide-content">
                    <span class="vip-hide-tips">VIP内容</span>
                    <a class="zb-vip-btn" href="/pay-vip/">开通 VIP 解锁</a>
                </div>
            </article>
        """.trimIndent(), pageUrl)

        assertEquals(3, result.imageUrls.size)
        assertTrue(result.hasHiddenVipContent)
    }

    @Test
    fun lockedVipContentWithAdditionalClassesRequiresLogin() {
        val result = parseNncosDetail("""
            <article class="post-content">
                <div class='vip-hide-content zb-vipbox'>请重新登录</div>
            </article>
        """.trimIndent(), pageUrl)
        assertTrue(result.hasHiddenVipContent)
    }

    @Test
    fun unlockedVipImagesAreNotExpiredBecauseOfSharedStyles() {
        val result = parseNncosDetail("""
            <article class="post-content">
                <style>.vip-hide-content{padding:10px}.viphidecontent{margin:0}</style>
                <div class="viphidecontent">
                    <img src="https://img.nncos.com/member.webp">
                    <img src="https://img.nncos.com/member.gif">
                </div>
            </article>
        """.trimIndent(), pageUrl)
        assertEquals(2, result.imageUrls.size)
        assertFalse(result.hasHiddenVipContent)
    }

    @Test
    fun vipBadgesOutsideArticleDoNotExpirePublicContent() {
        val result = parseNncosDetail("""
            <article class="post-content"><img src="https://img.nncos.com/public.webp"></article>
            <aside><div class="vip-hide-content">其他推荐内容</div></aside>
        """.trimIndent(), pageUrl)
        assertEquals(1, result.imageUrls.size)
        assertFalse(result.hasHiddenVipContent)
    }

    @Test
    fun emptyPublicArticleIsNotAnExpiredLogin() {
        val result = parseNncosDetail("<article class='post-content'>暂无图片</article>", pageUrl)
        assertTrue(result.imageUrls.isEmpty())
        assertFalse(result.hasHiddenVipContent)
    }

    @Test
    fun legacyHiddenVipContentStillRequiresLogin() {
        val result = parseNncosDetail("""
            <article class="post-content"><div class="viphidecontent">请登录查看</div></article>
        """.trimIndent(), pageUrl)
        assertTrue(result.hasHiddenVipContent)
    }
}
