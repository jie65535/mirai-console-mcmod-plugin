package top.limbang.mcmod.mirai

import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.event.events.NudgeEvent
import net.mamoe.mirai.event.globalEventChannel
import net.mamoe.mirai.event.nextEventOrNull
import net.mamoe.mirai.event.subscribeMessages
import net.mamoe.mirai.message.data.content
import top.limbang.mcmod.mirai.service.Filter
import top.limbang.mcmod.mirai.service.MessageHandle
import top.limbang.mcmod.mirai.service.MinecraftModService
import top.limbang.mcmod.mirai.service.SearchResult


object MiraiConsoleMcmodPlugin : KotlinPlugin(
    JvmPluginDescription(
        id = "top.limbang.mirai-console-mcmod-plugin",
        version = "1.2.2",
    ) {
        author("limbang")
        info("""mc百科查询""")
    }
) {
    private val service = MinecraftModService()

    override fun onEnable() {
        McmodPluginData.reload()
        McmodPluginCompositeCommand.register()
        // 读取查询自定义命令
        val module = McmodPluginData.queryCommand[Filter.MODULE] ?: "百科模组"
        val data = McmodPluginData.queryCommand[Filter.DATA] ?: "百科资料"
        val courseOfStudy = McmodPluginData.queryCommand[Filter.COURSE_OF_STUDY] ?: "百科教程"
        val integrationPackage = McmodPluginData.queryCommand[Filter.INTEGRATION_PACKAGE] ?: "百科整合包"
        val server = McmodPluginData.queryCommand[Filter.SERVER] ?: "百科服务器"

        globalEventChannel().subscribeMessages {
            (startsWith(module) or startsWith(data) or startsWith(courseOfStudy) or
                    startsWith(integrationPackage) or startsWith(server)) reply {
                val msg = it.trim()
                val searchMessage = when {
                    msg.startsWith(module) -> SearchMessage(Filter.MODULE, msg.substringAfter(module))
                    msg.startsWith(data) -> SearchMessage(Filter.DATA, msg.substringAfter(data))
                    msg.startsWith(courseOfStudy) -> SearchMessage(
                        Filter.COURSE_OF_STUDY,
                        msg.substringAfter(courseOfStudy)
                    )
                    msg.startsWith(integrationPackage) -> SearchMessage(
                        Filter.INTEGRATION_PACKAGE,
                        msg.substringAfter(integrationPackage)
                    )
                    msg.startsWith(server) -> SearchMessage(Filter.SERVER,msg.substringAfter(server))
                    else -> return@reply "未知错误!!!"
                }
                if (searchMessage.key.isEmpty()) return@reply "查询内容不能为空!!!"

                service.clear()
                var nextEvent: MessageEvent
                var list: List<SearchResult>
                do {
                    list = service.getSearchList(searchMessage.key, searchMessage.filter, 15)
                    if (list.isEmpty()) return@reply "未搜索到结果，请更换关键字重试。"
                    else if (list.size == 1) return@reply getSearchResults(0, list, this)

                    subject.sendMessage(service.searchListToString(list, subject, bot))
                    // 获取下一条消息事件
                    nextEvent = nextEventOrNull(30000) { next -> next.sender == sender } ?: return@reply "等待回复超时,请重新查询。"
                    if (!service.getNextPage() && service.getSearchResultsListSize() <= 0) break
                } while (nextEvent.message.content.equals("p", true))

                try {
                    return@reply getSearchResults(nextEvent.message.content.toInt(), list, this)
                } catch (e: NumberFormatException) {
                    return@reply "请正确回复序号,此次查询已取消,请重新查询。"
                }
            }
        }

        if (McmodPluginData.nudgeEnabled) {
            // 监听戳一戳消息并回复帮助
            globalEventChannel().subscribeAlways<NudgeEvent> {
                if (target.id == bot.id) {
                    subject.sendMessage(
                        "Minecraft百科查询插件使用说明:\n" +
                                "查询物品:$data 加物品名称\n" +
                                "查询模组:$module 加模组名称\n" +
                                "查询教程:$courseOfStudy 加教程名称\n" +
                                "查询整合包:$integrationPackage 加整合包名称\n" +
                                "查询服务器:$server 加服务器名称\n" +
                                "可私聊机器人查询，避免群内刷屏 :)\n" +
                                "资料均来自:mcmod.cn"
                    )
                }
            }
        }
    }
}

suspend fun getSearchResults(serialNumber: Int, list: List<SearchResult>, event: MessageEvent): Any {
    if (serialNumber >= list.size) return "输入序号大于可查询数据,此次查询已取消,请重新查询。"

    val searchResults = list[serialNumber]
    return when (searchResults.filter) {
        Filter.MODULE -> MessageHandle.moduleHandle(searchResults.url, event)
        Filter.DATA -> MessageHandle.dataHandle(searchResults.url, event)
        Filter.COURSE_OF_STUDY -> MessageHandle.courseOfStudyHandle(searchResults.url, event)
        Filter.INTEGRATION_PACKAGE -> MessageHandle.integrationPackageHandle(searchResults.url, event)
        Filter.SERVER -> MessageHandle.service(searchResults.url, event)
        else -> Unit
    }
}

data class SearchMessage(val filter: Filter, val key: String)

