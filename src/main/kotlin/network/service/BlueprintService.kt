/*
 * Copyright 2020-2022 limbang and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/limbang/mirai-console-mcmod-plugin/blob/master/LICENSE
 */

package top.limbang.mcmod.network.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import org.jsoup.Jsoup
import top.limbang.mcmod.network.InMemoryCookieJar
import top.limbang.mcmod.network.converter.BlueprintHtmlParser
import top.limbang.mcmod.network.interceptor.UserAgentInterceptor
import top.limbang.mcmod.network.model.BlueprintDetail
import top.limbang.mcmod.network.model.BlueprintSearchPage
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Creative Mechanic Server 蓝图站客户端. */
class BlueprintService internal constructor(
    private val client: OkHttpClient = createClient(),
    private val baseUrl: HttpUrl = BASE_URL.toHttpUrl(),
) {
    private val sessionMutex = Mutex()
    private var csrfToken: String? = null

    suspend fun search(query: String, page: Int = 1): BlueprintSearchPage {
        require(query.isNotBlank()) { "搜索关键字不能为空" }
        require(page > 0) { "页码必须大于 0" }

        return withContext(Dispatchers.IO) {
            sessionMutex.withLock {
                var response = postSearch(query, page, getCsrfToken())
                if (response.code == 403) {
                    csrfToken = null
                    response = postSearch(query, page, getCsrfToken())
                }
                ensureSuccess(response, "搜索蓝图")
                BlueprintHtmlParser.parse(response.body, baseUrl.toString(), page)
            }
        }
    }

    suspend fun getDetail(detailUrl: String): BlueprintDetail = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(detailUrl).get().build()
        val response = execute(request)
        ensureSuccess(response, "获取蓝图详情")
        BlueprintHtmlParser.parseDetail(response.body, detailUrl)
    }

    private fun getCsrfToken(): String = csrfToken ?: run {
        val request = Request.Builder().url(baseUrl).get().build()
        val response = execute(request)
        ensureSuccess(response, "初始化蓝图站会话")
        Jsoup.parse(response.body, baseUrl.toString())
            .selectFirst("input[name=csrfmiddlewaretoken]")
            ?.attr("value")
            ?.takeIf(String::isNotBlank)
            ?: throw IOException("蓝图站未返回 CSRF Token")
    }.also { csrfToken = it }

    private fun postSearch(query: String, page: Int, token: String): HttpResponse {
        val form = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("csrfmiddlewaretoken", token)
            .addFormDataPart("search_type", "t")
            .addFormDataPart("q", query)
            .addFormDataPart("consume", "")
            .addFormDataPart("product", "")
            .addFormDataPart("loader_type", "any")
            .addFormDataPart("mc_type", "any")
            .addFormDataPart("create_type", "any")
            .addFormDataPart("z_size", "")
            .addFormDataPart("x_size", "")
            .addFormDataPart("y_size", "")
            .addFormDataPart("function", "")
            .addFormDataPart("grid_col", "1")
            .addFormDataPart("sort", "time")
            .addFormDataPart("order", "down")
            .addFormDataPart("page", page.toString())
            .build()
        val searchUrl = baseUrl.resolve("search/") ?: throw IOException("蓝图站搜索地址无效")
        val request = Request.Builder()
            .url(searchUrl)
            .header("Referer", baseUrl.toString())
            .post(form)
            .build()
        return execute(request)
    }

    private fun execute(request: Request): HttpResponse =
        client.newCall(request).execute().use { response ->
            HttpResponse(response.code, response.body?.string().orEmpty())
        }

    private fun ensureSuccess(response: HttpResponse, action: String) {
        if (response.code in 200..299) return
        val explanation = Jsoup.parse(response.body).text().take(160)
        val suffix = explanation.takeIf(String::isNotBlank)?.let { " - $it" }.orEmpty()
        throw IOException("$action 失败: HTTP ${response.code}$suffix")
    }

    private data class HttpResponse(val code: Int, val body: String)

    companion object {
        private const val BASE_URL = "https://www.creativemechanicserver.com/"

        private fun createClient(): OkHttpClient = OkHttpClient.Builder()
            .protocols(listOf(Protocol.HTTP_1_1))
            .cookieJar(InMemoryCookieJar())
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(UserAgentInterceptor())
            .build()
    }
}
