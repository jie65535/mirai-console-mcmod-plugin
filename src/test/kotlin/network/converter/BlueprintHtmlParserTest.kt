/*
 * Copyright 2020-2022 limbang and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/limbang/mirai-console-mcmod-plugin/blob/master/LICENSE
 */

package top.limbang.mcmod.network.converter

import top.limbang.mcmod.network.model.BlueprintDetailPart
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlueprintHtmlParserTest {
    @Test
    fun parsesSearchCardsAndPagination() {
        val html = """
            <a href="/detail/1380/" class="list_result">
              <div class="cover"><img src="/upload/cover.webp"></div>
              <h2 class="title">宽轨蒸汽机车</h2>
              <time class="time">2026年8月21日</time>
              <div class="author">作者：huanyyu</div>
              <div class="size">尺寸：42✖9✖7</div>
              <div class="desc">宽轨的 2-8-2 轮式三气缸机车</div>
              <div class="stress">应力：内置</div>
              <div class="version">
                <div class="tip_box"><div class="t">1.20.1</div></div>
                <div class="tip_box"><div class="t">6.0.8</div></div>
              </div>
              <div class="download">下载量：1,234</div>
              <div class="function"><span class="c_box">铁路系统&gt;列车设计</span></div>
            </a>
            <div id="receive_msg">54个结果，3页</div>
        """.trimIndent()

        val page = BlueprintHtmlParser.parse(
            html,
            "https://www.creativemechanicserver.com/",
            requestedPage = 1,
        )

        assertEquals(54, page.totalResults)
        assertEquals(3, page.totalPages)
        assertTrue(page.hasNext)
        assertEquals(1, page.items.size)
        with(page.items.single()) {
            assertEquals(1380, id)
            assertEquals("宽轨蒸汽机车", title)
            assertEquals("huanyyu", author)
            assertEquals("42✖9✖7", size)
            assertEquals("内置", stress)
            assertEquals("1.20.1", minecraftVersion)
            assertEquals("6.0.8", createVersion)
            assertEquals(1234, downloads)
            assertEquals("https://www.creativemechanicserver.com/upload/cover.webp", coverUrl)
            assertEquals("https://www.creativemechanicserver.com/detail/1380/", detailUrl)
        }
    }

    @Test
    fun parsesEmptyResult() {
        val page = BlueprintHtmlParser.parse(
            "<div id=\"receive_msg\">0个结果，0页</div>",
            "https://www.creativemechanicserver.com/",
            requestedPage = 1,
        )

        assertTrue(page.items.isEmpty())
        assertEquals(0, page.totalResults)
        assertFalse(page.hasNext)
    }

    @Test
    fun parsesTextUnderBlueprintDetailHeading() {
        val html = """
            <article>
              <h1>蓝图详情</h1>
              <hr>
              <div class="content"><p>于1gt内方块实体阶段，让掉落物逐个位移。</p></div>
              <div class="blueprints"><h1>蓝图</h1><a href="/download/2097/">下载</a></div>
            </article>
        """.trimIndent()

        val detail = BlueprintHtmlParser.parseDetail(
            html,
            "https://www.creativemechanicserver.com/detail/1389/",
        )

        assertEquals(
            listOf(BlueprintDetailPart.Text("于1gt内方块实体阶段，让掉落物逐个位移。")),
            detail.parts,
        )
    }

    @Test
    fun keepsBlueprintDetailTextAndImagesInOrder() {
        val html = """
            <article>
              <h1>蓝图详情</h1>
              <hr>
              <div class="content">
                <p>侧视图</p>
                <p><img src="../../upload/post/side.webp"></p>
                <p>俯视图<img src="../../upload/post/top.webp"></p>
                <p>机器优化方案设置图</p>
                <p><img src="../../upload/post/settings.webp"></p>
              </div>
              <div><h1>蓝图</h1></div>
            </article>
        """.trimIndent()

        val detail = BlueprintHtmlParser.parseDetail(
            html,
            "https://www.creativemechanicserver.com/detail/1310/",
        )

        assertEquals(
            listOf(
                BlueprintDetailPart.Text("侧视图"),
                BlueprintDetailPart.Image("https://www.creativemechanicserver.com/upload/post/side.webp"),
                BlueprintDetailPart.Text("俯视图"),
                BlueprintDetailPart.Image("https://www.creativemechanicserver.com/upload/post/top.webp"),
                BlueprintDetailPart.Text("机器优化方案设置图"),
                BlueprintDetailPart.Image("https://www.creativemechanicserver.com/upload/post/settings.webp"),
            ),
            detail.parts,
        )
    }
}
