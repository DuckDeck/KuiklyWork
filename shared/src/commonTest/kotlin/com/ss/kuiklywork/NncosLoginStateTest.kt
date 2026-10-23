package com.ss.kuiklywork

import com.ss.kuiklywork.base.parseNncosLoginState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NncosLoginStateTest {
    @Test
    fun liveSiteLoginLinkIsLoggedOut() {
        assertEquals(false, parseNncosLoginState("""
            <a class="action-btn me-3" href="/login"><i class="iconfont icon-people"></i> 登录</a>
        """.trimIndent()))
    }

    @Test
    fun directIconTextUsesLoginRule() {
        assertEquals(false, parseNncosLoginState("<i class='icon-people'>登录</i>"))
        assertEquals(true, parseNncosLoginState("<i class='icon-people'>我的账号</i>"))
    }

    @Test
    fun nestedAccountNameIsLoggedIn() {
        assertEquals(true, parseNncosLoginState("""
            <a href="/user"><i class="iconfont icon-people"></i><span>会员名字</span></a>
        """.trimIndent()))
    }

    @Test
    fun nameContainingLoginIsNotTheLoginButton() {
        assertEquals(true, parseNncosLoginState("<a><i class='icon-people'></i>登录用户123</a>"))
    }

    @Test
    fun whitespaceAndEncodedLoginTextAreLoggedOut() {
        assertEquals(false, parseNncosLoginState("<a><i class='icon-people'></i>&nbsp;&#30331;&#24405;\n</a>"))
        assertEquals(false, parseNncosLoginState("<a><i class='icon-people'></i>&#x767b;&#x5f55;</a>"))
    }

    @Test
    fun missingOrEmptyAccountLabelIsUnknown() {
        assertNull(parseNncosLoginState("<p>VIP内容，请登录</p>"))
        assertNull(parseNncosLoginState("<a><i class='icon-people'></i>&nbsp;</a>"))
        assertNull(parseNncosLoginState("<i class='icon-people-extra'>账号</i>"))
    }

    @Test
    fun scriptsAndCommentsCannotOverrideAccountState() {
        assertEquals(false, parseNncosLoginState("""
            <script>const sample = '<i class="icon-people">测试用户</i>';</script>
            <!-- <i class="icon-people">测试用户</i> -->
            <a><i class="icon-people"></i>登录</a>
        """.trimIndent()))
    }

    @Test
    fun lockedVipContentDoesNotExpireAnAccountLabelledLoggedIn() {
        val result = parseNncosDetail("""
            <a><i class="icon-people"></i>会员名字</a>
            <article class="post-content"><div class="vip-hide-content">开通 VIP 解锁</div></article>
        """.trimIndent(), "https://www.nncos.com/27619.html")
        assertEquals(true, result.isLoggedIn)
        assertFalse(result.requiresLogin)
    }

    @Test
    fun lockedVipWithLoginButtonRequiresLogin() {
        val result = parseNncosDetail("""
            <a><i class="icon-people"></i>登录</a>
            <article class="post-content">
                <img src="https://img.nncos.com/preview.webp">
                <div class="vip-hide-content">VIP内容</div>
            </article>
        """.trimIndent(), "https://www.nncos.com/27619.html")
        assertEquals(false, result.isLoggedIn)
        assertEquals(1, result.imageUrls.size)
        assertTrue(result.requiresLogin)
    }

    @Test
    fun missingAccountMarkerDoesNotDeclareTheSessionExpired() {
        val result = parseNncosDetail("""
            <article class="post-content"><div class="vip-hide-content">VIP内容</div></article>
        """.trimIndent(), "https://www.nncos.com/27619.html")
        assertNull(result.isLoggedIn)
        assertFalse(result.requiresLogin)
    }

    @Test
    fun publicImagesRemainAvailableWhenAccountLabelSaysLogin() {
        val result = parseNncosDetail("""
            <a><i class="icon-people"></i>登录</a>
            <article class="post-content"><img src="https://img.nncos.com/public.gif"></article>
        """.trimIndent(), "https://www.nncos.com/27614.html/3")
        assertEquals(false, result.isLoggedIn)
        assertFalse(result.requiresLogin)
        assertEquals(listOf("https://img.nncos.com/public.gif"), result.imageUrls)
    }
}
