/*
 * Copyright 2020-2022 limbang and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/limbang/mirai-console-mcmod-plugin/blob/master/LICENSE
 */

package top.limbang.mcmod.service

import kotlinx.coroutines.withTimeoutOrNull
import net.mamoe.mirai.event.EventPriority
import net.mamoe.mirai.event.GlobalEventChannel
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.event.nextEvent
import net.mamoe.mirai.message.data.Message
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.message.data.buildForwardMessage
import net.mamoe.mirai.message.data.buildMessageChain
import net.mamoe.mirai.message.data.content
import top.limbang.mcmod.Mcmod
import top.limbang.mcmod.PluginConfig
import top.limbang.mcmod.PluginConfig.isMultipleSelectEnabled
import top.limbang.mcmod.network.Service
import top.limbang.mcmod.network.model.Blueprint
import top.limbang.mcmod.network.model.BlueprintDetailPart
import top.limbang.mcmod.network.model.BlueprintSearchPage
import top.limbang.mcmod.service.MiraiToMcmodService.readImage
import top.limbang.mcmod.utils.PagingStorage

object MiraiToBlueprintService {
    private val blueprintService = Service.getBlueprintService

    suspend fun MessageEvent.toBlueprintSearch(key: String): Message? {
        val firstPage = runCatching { getSearchPage(key, 1) }
            .getOrElse { return PlainText(formatRequestError(it)) }
        if (firstPage.items.isEmpty()) return PlainText("未查找到相关蓝图")
        if (firstPage.totalResults == 1) return firstPage.items.first().toMessage(this)

        val pagingStorage = PagingStorage<Blueprint>(PluginConfig.pageSize)
        pagingStorage.addAll(firstPage.items)
        var displayPage = 1
        var nextSitePage = 2
        var hasMoreSitePages = firstPage.hasNext

        do {
            val list = pagingStorage.getPageList(displayPage)
            val hasNextPage = pagingStorage.pageSizeOrZero(displayPage + 1) > 0 || hasMoreSitePages
            val listMessage = subject.sendMessage(list.toMessage(this, displayPage == 1, hasNextPage))
            val nextEvent = withTimeoutOrNull(REPLY_TIMEOUT_MILLIS) {
                GlobalEventChannel.nextEvent<MessageEvent>(EventPriority.MONITOR) { next ->
                    next.bot.id == bot.id &&
                        next.subject.id == subject.id &&
                        next.sender.id == sender.id
                }
            }
            if (nextEvent == null) {
                listMessage.recall()
                return null
            }

            val reply = nextEvent.message.content.trim()
            val selectedIndex = reply.toIntOrNull()
            val continueInteraction = when {
                reply.equals("n", true) -> {
                    var nextPageSize = pagingStorage.pageSizeOrZero(displayPage + 1)
                    while (nextPageSize == 0 && hasMoreSitePages) {
                        val sitePage = runCatching { getSearchPage(key, nextSitePage) }
                            .getOrElse {
                                listMessage.recall()
                                return PlainText(formatRequestError(it))
                            }
                        pagingStorage.addAll(sitePage.items)
                        hasMoreSitePages = sitePage.hasNext
                        nextSitePage++
                        nextPageSize = pagingStorage.pageSizeOrZero(displayPage + 1)
                    }
                    if (nextPageSize == 0) {
                        listMessage.recall()
                        return PlainText("没有更多蓝图")
                    }
                    displayPage++
                    true
                }
                reply.equals("p", true) -> {
                    if (displayPage > 1) displayPage--
                    true
                }
                selectedIndex != null -> {
                    if (selectedIndex !in list.indices) {
                        val error = if (selectedIndex < 0) "输入的序号过小" else "输入的序号过大"
                        listMessage.recall()
                        return PlainText(error)
                    }
                    val message = list[selectedIndex].toMessage(this)
                    if (!isMultipleSelectEnabled) {
                        listMessage.recall()
                        return message
                    }
                    subject.sendMessage(message)
                    true
                }
                else -> false
            }
            listMessage.recall()
        } while (continueInteraction)
        return null
    }

    private suspend fun List<Blueprint>.toMessage(
        event: MessageEvent,
        isFirst: Boolean,
        hasNextPage: Boolean,
    ): Message = with(event) {
        buildForwardMessage {
            bot says "CMS 蓝图站：30秒内回复编号查看"
            for ((index, blueprint) in this@toMessage.withIndex()) {
                val summary = PlainText(buildString {
                    append("[$index] ${blueprint.title}")
                    if (blueprint.author.isNotBlank()) append("\n作者：${blueprint.author}")
                    if (blueprint.description.isNotBlank()) {
                        append("\n简介：${blueprint.description.toPreview(PREVIEW_DESCRIPTION_LENGTH)}")
                    }
                })
                val preview = blueprint.coverUrl?.let { url ->
                    runCatching { readImage(url) }.getOrNull()?.let { cover ->
                        buildMessageChain {
                            +cover
                            +"\n"
                            +summary
                        }
                    }
                }
                bot says (preview ?: summary)
            }
            when {
                !isFirst && hasNextPage -> bot says "回复:[P]上一页 [N]下一页"
                !isFirst -> bot says "回复:[P]上一页"
                hasNextPage -> bot says "回复:[N]下一页"
            }
        }
    }

    private suspend fun Blueprint.toMessage(event: MessageEvent): Message = with(event) {
        val blueprint = this@toMessage
        val detail = runCatching {
            Mcmod.logger.info("[CMS] 开始调用蓝图详情接口，id=${blueprint.id}")
            blueprintService.getDetail(blueprint.detailUrl)
        }.getOrNull()
        val cover = blueprint.coverUrl?.let { url ->
            runCatching { readImage(url) }.getOrNull()
        }
        val metadata = PlainText(buildString {
            appendLine(blueprint.title)
            if (blueprint.author.isNotBlank()) appendLine("作者：${blueprint.author}")
            blueprint.minecraftVersion?.takeIf(String::isNotBlank)?.let {
                appendLine("Minecraft：$it")
            }
            blueprint.createVersion?.takeIf(String::isNotBlank)?.let {
                appendLine("机械动力：$it")
            }
            if (blueprint.size.isNotBlank()) appendLine("尺寸：${blueprint.size}")
            if (blueprint.stress.isNotBlank()) appendLine("应力：${blueprint.stress}")
            append("详情与下载：${blueprint.detailUrl}")
        })
        val description = blueprint.description.takeIf(String::isNotBlank)?.let {
            PlainText("简介\n${it.toPreview(DETAIL_DESCRIPTION_LENGTH)}")
        }
        val detailParts = detail?.parts?.take(MAX_BLUEPRINT_DETAIL_PARTS)?.map { part ->
            when (part) {
                is BlueprintDetailPart.Text ->
                    PlainText(part.text.toPreview(BLUEPRINT_DETAIL_TEXT_LENGTH))
                is BlueprintDetailPart.Image ->
                    runCatching { readImage(part.url) }
                        .getOrElse { PlainText("详情图片：${part.url}") }
            }
        }.orEmpty()

        buildForwardMessage {
            cover?.let { bot says it }
            bot says metadata
            description?.let { bot says it }
            if (detailParts.isNotEmpty()) {
                bot says buildMessageChain {
                    +"蓝图详情\n"
                    for ((index, part) in detailParts.withIndex()) {
                        +part
                        if (index != detailParts.lastIndex) +"\n"
                    }
                }
            }
        }
    }

    private fun PagingStorage<Blueprint>.pageSizeOrZero(page: Int): Int =
        runCatching { getPageList(page).size }.getOrDefault(0)

    private suspend fun getSearchPage(key: String, page: Int): BlueprintSearchPage {
        Mcmod.logger.info("[CMS] 开始调用蓝图搜索接口，page=$page")
        return blueprintService.search(key, page)
    }

    private fun formatRequestError(error: Throwable): String =
        "蓝图站请求失败：${error.message ?: "未知错误"}"

    private fun String.toPreview(maxLength: Int): String {
        val normalized = replace(Regex("\\s+"), " ").trim()
        if (normalized.isEmpty()) return ""
        return if (normalized.length <= maxLength) {
            normalized
        } else {
            normalized.take(maxLength - 3) + "..."
        }
    }

    private const val REPLY_TIMEOUT_MILLIS = 30_000L
    private const val PREVIEW_DESCRIPTION_LENGTH = 100
    private const val DETAIL_DESCRIPTION_LENGTH = 800
    private const val BLUEPRINT_DETAIL_TEXT_LENGTH = 800
    private const val MAX_BLUEPRINT_DETAIL_PARTS = 12
}
