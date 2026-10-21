package com.ss.kuiklywork

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CosImageSearchTest {
    @Test
    fun searchEncodesChineseAndQueryDelimiters() {
        assertEquals(
            "https://www.nncos.com/?cat=&s=%E7%BE%8E%E7%BE%8E",
            nncosSearchUrl("  美美  ")
        )
        assertEquals(
            "https://www.nncos.com/?cat=&s=A%26B%20%2B%20%231%3F%3D%F0%9F%8C%B8",
            nncosSearchUrl("A&B + #1?=🌸")
        )
    }

    @Test
    fun parsesSearchCardsAndDecodesTitlesAndLinks() {
        val result = parseNncosImages("""
            <article class="post-item post-grid">
                <a class="media-img lazy" href="/6189.html" title="美美丘比酱&#038;雪兔查理 百合"
                   data-bg="https://i.ibb.co/example.jpg"></a>
                <div class="entry-category-dot"><a href="/ac">亚洲萌娘</a></div>
                <time class="pub-date">2025-05-30</time>
                <span class="entry-vip-icon"></span>
            </article>
            <article class="post-item post-grid">
                <a href="/11118.html" title="Ruby蘸酱美美 - Shinano"
                   data-bg="https://nncos.us/nnpic2/shinano.webp"></a>
            </article>
        """.trimIndent())

        assertEquals(2, result.items.size)
        val first = result.items.first()
        assertEquals("美美丘比酱&雪兔查理 百合", first.title)
        assertEquals("https://www.nncos.com/6189.html", first.detailUrl)
        assertEquals("https://i.ibb.co/example.jpg", first.imageUrl)
        assertEquals("亚洲萌娘", first.categoryName)
        assertEquals("2025-05-30", first.publishedAt)
        assertTrue(first.isVip)
        assertFalse(result.items.last().isVip)
        assertNull(result.nextPageUrl)
    }

    @Test
    fun nextPagePreservesSearchEvenWhenAttributeOrderChanges() {
        val result = parseNncosImages("""
            <a href="https://www.nncos.com/page/2?cat&#038;s=%E7%BE%8E"
               class="page-link page-next">下一页</a>
        """.trimIndent())
        assertEquals("https://www.nncos.com/page/2?cat&s=%E7%BE%8E", result.nextPageUrl)
    }

    @Test
    fun ordinaryCategoryPaginationStillWorks() {
        val result = parseNncosImages("""
            <a class="page-link page-next" href="/ac/page/2">下一页</a>
        """.trimIndent())
        assertEquals("https://www.nncos.com/ac/page/2", result.nextPageUrl)
    }

    @Test
    fun noMatchesDoesNotInventCardsOrPagination() {
        val result = parseNncosImages("""
            <body class="search search-no-results wp-theme-rimini-new">
                <h1>没有找到相关内容</h1>
            </body>
        """.trimIndent())
        assertTrue(result.items.isEmpty())
        assertNull(result.nextPageUrl)
    }
}
