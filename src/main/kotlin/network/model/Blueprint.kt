/*
 * Copyright 2020-2022 limbang and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/limbang/mirai-console-mcmod-plugin/blob/master/LICENSE
 */

package top.limbang.mcmod.network.model

data class Blueprint(
    val id: Int,
    val title: String,
    val author: String,
    val publishedDate: String,
    val description: String,
    val size: String,
    val stress: String,
    val minecraftVersion: String?,
    val createVersion: String?,
    val downloads: Int,
    val category: String?,
    val coverUrl: String?,
    val detailUrl: String,
)

data class BlueprintSearchPage(
    val items: List<Blueprint>,
    val totalResults: Int,
    val currentPage: Int,
    val totalPages: Int,
) {
    val hasNext: Boolean get() = currentPage < totalPages
}

data class BlueprintDetail(val parts: List<BlueprintDetailPart>)

sealed class BlueprintDetailPart {
    data class Text(val text: String) : BlueprintDetailPart()
    data class Image(val url: String) : BlueprintDetailPart()
}
