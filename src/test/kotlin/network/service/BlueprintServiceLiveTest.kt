/*
 * Copyright 2020-2022 limbang and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/limbang/mirai-console-mcmod-plugin/blob/master/LICENSE
 */

package top.limbang.mcmod.network.service

import kotlinx.coroutines.runBlocking
import top.limbang.mcmod.Mcmod
import top.limbang.mcmod.network.Service
import top.limbang.mcmod.network.model.BlueprintDetailPart
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BlueprintServiceLiveTest {
    @Test
    fun searchesLiveSiteWhenEnabled() {
        if (System.getenv("CMS_LIVE_TEST") != "true") return

        val page = runBlocking { BlueprintService().search("蒸汽") }
        assertTrue(page.items.isNotEmpty())
        assertTrue(page.items.all { it.detailUrl.startsWith("https://www.creativemechanicserver.com/detail/") })

        val coverUrl = assertNotNull(page.items.firstNotNullOfOrNull { it.coverUrl })
        val coverBytes = runBlocking { Service.getMcmodService.downloadFile(coverUrl).bytes() }
        Thread.currentThread().contextClassLoader = Mcmod::class.java.classLoader
        assertNotNull(ImageIO.read(coverBytes.inputStream()))

        val detail = runBlocking {
            BlueprintService().getDetail("https://www.creativemechanicserver.com/detail/1389/")
        }
        assertTrue(detail.parts.filterIsInstance<BlueprintDetailPart.Text>().any {
            it.text.contains("1gt内方块实体阶段")
        })

        val richDetail = runBlocking {
            BlueprintService().getDetail("https://www.creativemechanicserver.com/detail/1310/")
        }
        val detailImages = richDetail.parts.filterIsInstance<BlueprintDetailPart.Image>()
        assertTrue(detailImages.size == 3)
        val detailImageBytes = runBlocking {
            Service.getMcmodService.downloadFile(detailImages.first().url).bytes()
        }
        assertNotNull(ImageIO.read(detailImageBytes.inputStream()))
    }
}
