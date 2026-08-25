/*
 * Copyright 2020-2022 limbang and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/limbang/mirai-console-mcmod-plugin/blob/master/LICENSE
 */

package top.limbang.mcmod.network.converter

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import top.limbang.mcmod.network.model.Blueprint
import top.limbang.mcmod.network.model.BlueprintDetail
import top.limbang.mcmod.network.model.BlueprintDetailPart
import top.limbang.mcmod.network.model.BlueprintSearchPage

internal object BlueprintHtmlParser {
    private val resultSummaryPattern = Regex("(\\d+)个结果，(\\d+)页")

    fun parse(html: String, baseUrl: String, requestedPage: Int): BlueprintSearchPage {
        val document = Jsoup.parse(html, baseUrl)
        val items = document.select("a.list_result").mapNotNull { element ->
            val detailUrl = element.absUrl("href")
            val id = detailUrl.trimEnd('/').substringAfterLast('/').toIntOrNull()
                ?: return@mapNotNull null
            val versions = element.select(".version .tip_box > .t")
            Blueprint(
                id = id,
                title = element.selectFirst(".title")?.text().orEmpty(),
                author = element.selectFirst(".author")?.text().orEmpty().removePrefix("作者："),
                publishedDate = element.selectFirst(".time")?.text().orEmpty(),
                description = element.selectFirst(".desc")?.text().orEmpty(),
                size = element.selectFirst(".size")?.text().orEmpty().removePrefix("尺寸："),
                stress = element.selectFirst(".stress")?.text().orEmpty().removePrefix("应力："),
                minecraftVersion = versions.getOrNull(0)?.text()?.takeIf(String::isNotBlank),
                createVersion = versions.getOrNull(1)?.text()?.takeIf(String::isNotBlank),
                downloads = element.selectFirst(".download")?.text()
                    ?.filter(Char::isDigit)?.toIntOrNull() ?: 0,
                category = element.selectFirst(".function .c_box")?.text()?.takeIf(String::isNotBlank),
                coverUrl = element.selectFirst(".cover img")?.absUrl("src")?.takeIf(String::isNotBlank),
                detailUrl = detailUrl,
            )
        }

        val summary = document.selectFirst("#receive_msg")?.text().orEmpty()
        val summaryMatch = resultSummaryPattern.find(summary)
        val totalResults = summaryMatch?.groupValues?.get(1)?.toIntOrNull() ?: items.size
        val totalPages = summaryMatch?.groupValues?.get(2)?.toIntOrNull()
            ?: if (items.isEmpty()) 0 else requestedPage
        return BlueprintSearchPage(items, totalResults, requestedPage, totalPages)
    }

    fun parseDetail(html: String, baseUrl: String): BlueprintDetail {
        val document = Jsoup.parse(html, baseUrl)
        val heading = document.select("h1, h2, h3, h4")
            .firstOrNull { it.text().trim() == "蓝图详情" }
            ?: return BlueprintDetail(emptyList())
        val content = heading.nextElementSiblings()
            .firstOrNull { it.hasClass("content") }
            ?: return BlueprintDetail(emptyList())
        val parts = mutableListOf<BlueprintDetailPart>()
        content.childNodes().forEach { collectDetailParts(it, parts) }
        return BlueprintDetail(parts)
    }

    private fun collectDetailParts(node: Node, parts: MutableList<BlueprintDetailPart>) {
        when (node) {
            is TextNode -> addTextPart(node.text(), parts)
            is Element -> if (node.tagName() == "img") {
                node.absUrl("src").takeIf(String::isNotBlank)?.let {
                    parts.add(BlueprintDetailPart.Image(it))
                }
            } else {
                node.childNodes().forEach { collectDetailParts(it, parts) }
            }
        }
    }

    private fun addTextPart(text: String, parts: MutableList<BlueprintDetailPart>) {
        val normalized = text.replace(Regex("\\s+"), " ").trim()
        if (normalized.isEmpty()) return
        val previous = parts.lastOrNull()
        if (previous is BlueprintDetailPart.Text) {
            parts[parts.lastIndex] = BlueprintDetailPart.Text("${previous.text} $normalized")
        } else {
            parts.add(BlueprintDetailPart.Text(normalized))
        }
    }
}
