package top.limbang.mcmod.network.converter

import okhttp3.ResponseBody
import retrofit2.Converter
import top.limbang.mcmod.network.model.Item
import top.limbang.mcmod.network.utils.labelReplacement
import top.limbang.mcmod.network.utils.parse

/**
 * ### 把 html 响应解析成 [Item]
 */
class ItemResponseBodyConverter : Converter<ResponseBody, Item> {
    override fun convert(value: ResponseBody): Item {
        val document = value.parse()
        return Item(
            iconUrl = document.select("td > img").attr("src") ?: document.select("td > a > img").attr("src"),
            name = document.select(".name").text(),
            introduction = document.select("[class=item-content common-text font14]").labelReplacement(),
            tabUrl = "https://www.mcmod.cn" + document.select("[class=common-icon-text item-table] > a").attr("href")
        )
    }
}