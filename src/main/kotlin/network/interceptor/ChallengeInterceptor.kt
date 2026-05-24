/*
 * Copyright 2020-2022 limbang and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/limbang/mirai-console-mcmod-plugin/blob/master/LICENSE
 */

package top.limbang.mcmod.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import top.limbang.mcmod.Mcmod
import top.limbang.mcmod.network.McmodBlockedException

/**
 * ### mcmod 反爬虫挑战拦截器
 *
 * mcmod 站点对部分接口返回一段约 100~150 字节的 JS:
 * ```
 * <script>document.cookie = 'yxd_token=<token>'
 * window.location.href='<原路径>'</script>
 * ```
 * 浏览器执行后会带上 cookie 重新请求, 真实内容才会返回.
 * 此拦截器识别该响应, 自动写入 cookie 并重放原请求.
 */
class ChallengeInterceptor : Interceptor {
    companion object {
        private const val CHALLENGE_BODY_MAX_BYTES = 1024L
        private val TOKEN_REGEX = Regex("""yxd_token=([a-zA-Z0-9]+)""")
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        // 只对文本类响应做检测; 图片等二进制响应直接放行
        val contentType = response.body?.contentType()?.toString().orEmpty()
        if (!contentType.startsWith("text/")) return response

        // peek 限定 1024 字节, 不消费原始响应体
        val text = response.peekBody(CHALLENGE_BODY_MAX_BYTES).string()
        val match = TOKEN_REGEX.find(text)
        if (match == null || !text.contains("window.location.href")) return response

        val token = match.groupValues[1]
        Mcmod.logger.info("[Challenge] hit on ${request.url}, retrying with cookie")
        val existingCookie = request.header("Cookie")
        val newCookie = if (existingCookie.isNullOrBlank()) {
            "yxd_token=$token"
        } else {
            "$existingCookie; yxd_token=$token"
        }
        val newRequest = request.newBuilder()
            .header("Cookie", newCookie)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .header("Referer", request.url.toString())
            .build()
        response.close()
        val retryResponse = chain.proceed(newRequest)

        // 如果重放后仍然是挑战, 说明出口 IP 已被 mcmod 拉黑, 任何 cookie 都过不去
        val retryContentType = retryResponse.body?.contentType()?.toString().orEmpty()
        if (retryContentType.startsWith("text/")) {
            val retryText = retryResponse.peekBody(CHALLENGE_BODY_MAX_BYTES).string()
            if (TOKEN_REGEX.containsMatchIn(retryText) && retryText.contains("window.location.href")) {
                retryResponse.close()
                Mcmod.logger.warning("[Challenge] retry still returns challenge for ${request.url}, IP likely blocked")
                throw McmodBlockedException()
            }
        }
        return retryResponse
    }
}
