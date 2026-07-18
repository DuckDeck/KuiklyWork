package com.ss.kuiklywork.module

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import android.webkit.CookieManager
import com.tencent.kuikly.core.render.android.export.KuiklyRenderBaseModule
import com.tencent.kuikly.core.render.android.export.KuiklyRenderCallback
import com.ss.kuiklywork.KRApplication
import com.ss.kuiklywork.KuiklyRenderActivity
import com.ss.kuiklywork.NetbianLoginActivity
import com.ss.kuiklywork.NncosLoginActivity
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import android.content.Intent
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.io.File
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream

class KRBridgeModule : KuiklyRenderBaseModule() {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun call(method: String, params: String?, callback: KuiklyRenderCallback?): Any? {
        return when (method) {
            "ssoRequest" -> {
                ssoRequest(params, callback)
            }

            "showAlert" -> {
                showAlert(params, callback)
            }

            "closePage" -> {
                closePage(params)
            }

            "openPage" -> {
                openPage(params)
            }

            "copyToPasteboard" -> {
                copyToPasteboard(params)
            }

            "toast" -> {
                toast(params)
            }

            "log" -> {
                log(params)
            }

            "fetchHtml" -> {
                fetchHtml(params, callback)
            }

            "openNetbianLogin" -> {
                openNetbianLogin(callback)
            }

            "getNetbianLoginState" -> {
                getNetbianLoginState(callback)
            }

            "openNncosLogin" -> {
                openNncosLogin(callback)
            }

            "getNncosLoginState" -> {
                getNncosLoginState(callback)
            }

            "downloadNetbianImage" -> {
                downloadNetbianImage(params, callback)
            }

            "getNetbianDownloadRecords" -> {
                getNetbianDownloadRecords(callback)
            }

            "markNetbianImageDownloaded" -> {
                markNetbianImageDownloaded(params, callback)
            }

            "reportDT" -> {
                reportDT(params)
            }

            "reportRealtime" -> {
                reportRealtime(params)
            }

            "qqLiveSSORequest" -> {
                qqLiveSSORequest(params, callback)
            }

            "localServeTime" -> {
                localServeTime(params, callback)
            }

            "currentTimestamp" -> {
                currentTimestamp(params)
            }

            "dateFormatter" -> {
                dateFormatter(params)
            }

            else -> callback?.invoke(
                mapOf(
                    "code" to -1,
                    "message" to "方法不存在"
                )
            )
        }
    }

    private fun fetchHtml(params: String?, callback: KuiklyRenderCallback?) {
        Log.i(TAG, "fetchHtml called, params=$params, callback=${callback != null}")
        if (params == null) {
            invokeFetchCallback(callback, mapOf("code" to -1, "message" to "missing params"))
            return
        }
        val requestUrl = runCatching { JSONObject(params).optString("url") }
            .onFailure { Log.e(TAG, "fetchHtml parse params failed", it) }
            .getOrDefault("")
        if (requestUrl.isEmpty()) {
            invokeFetchCallback(callback, mapOf("code" to -1, "message" to "missing url"))
            return
        }
        Thread {
            var connection: HttpURLConnection? = null
            try {
                Log.i(TAG, "fetchHtml start request: $requestUrl")
                connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 10000
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                    setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    setRequestProperty("Accept-Encoding", "identity")
                    setRequestProperty("Connection", "keep-alive")
                    setRequestProperty("Referer", browserRefererFor(requestUrl))
                    browserCookieFor(requestUrl)?.also {
                        setRequestProperty("Cookie", it)
                    }
                }
                val code = connection.responseCode
                val rawStream = if (code in 200..299) connection.inputStream else connection.errorStream
                val encoding = connection.contentEncoding?.lowercase() ?: ""
                val decompressedStream = when {
                    encoding.contains("gzip") -> GZIPInputStream(rawStream)
                    encoding.contains("deflate") -> InflaterInputStream(rawStream)
                    else -> rawStream
                }
                val bytes = decompressedStream?.readBytes() ?: ByteArray(0)
                Log.i(TAG, "fetchHtml response code=$code, bytes=${bytes.size}, contentType=${connection.contentType}")
                if (code !in 200..299) {
                    invokeFetchCallback(callback, mapOf("code" to -1, "message" to "http $code"))
                    return@Thread
                }
                val body = decodeHtml(bytes, connection.contentType)
                Log.i(TAG, "fetchHtml success, bodyLength=${body.length}")
                invokeFetchCallback(callback, mapOf("code" to 0, "body" to body))
            } catch (e: Throwable) {
                Log.e(TAG, "fetchHtml failed", e)
                invokeFetchCallback(callback, mapOf("code" to -1, "message" to (e.message ?: "request failed")))
            } finally {
                connection?.disconnect()
            }
        }.start()
    }

    private fun openNetbianLogin(callback: KuiklyRenderCallback?) {
        NetbianLoginCallbackStore.replace(callback)
        val ctx = activity ?: context ?: KRApplication.application
        val intent = Intent(ctx, NetbianLoginActivity::class.java)
        if (ctx !is android.app.Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
    }

    private fun getNetbianLoginState(callback: KuiklyRenderCallback?) {
        callback?.invoke(
            mapOf(
                "code" to 0,
                "isLoggedIn" to isNetbianLoggedIn(),
                "hasCookie" to ((CookieManager.getInstance().getCookie(NETBIAN_HOME_URL) ?: "").isNotEmpty())
            )
        )
    }

    private fun downloadNetbianImage(params: String?, callback: KuiklyRenderCallback?) {
        val json = runCatching { JSONObject(params ?: "{}") }.getOrDefault(JSONObject())
        val url = json.optString("url")
        val title = json.optString("title", "netbian")
        val referer = json.optString("referer", NETBIAN_HOME_URL).ifEmpty { NETBIAN_HOME_URL }
        if (url.isEmpty()) {
            callback?.invoke(mapOf("code" to -1, "message" to "missing url"))
            return
        }
        downloadNetbianImageUrl(url, title, referer, callback, 0)
    }

    private fun getNetbianDownloadRecords(callback: KuiklyRenderCallback?) {
        invokeDownloadCallback(
            callback,
            mapOf("code" to 0, "urls" to netbianDownloadPrefs().getString(KEY_NETBIAN_DOWNLOADED_URLS, "").orEmpty())
        )
    }

    private fun openNncosLogin(callback: KuiklyRenderCallback?) {
        NncosLoginCallbackStore.replace(callback)
        val ctx = activity ?: context ?: KRApplication.application
        val intent = Intent(ctx, NncosLoginActivity::class.java)
        if (ctx !is android.app.Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
    }

    private fun getNncosLoginState(callback: KuiklyRenderCallback?) {
        callback?.invoke(
            mapOf(
                "code" to 0,
                "isLoggedIn" to isNncosLoggedIn(),
                "hasCookie" to hasNncosCookie()
            )
        )
    }

    private fun markNetbianImageDownloaded(params: String?, callback: KuiklyRenderCallback?) {
        val url = runCatching { JSONObject(params ?: "{}").optString("url") }.getOrDefault("").trim()
        if (url.isEmpty()) {
            invokeDownloadCallback(callback, mapOf("code" to -1, "message" to "missing url"))
            return
        }
        val records = netbianDownloadPrefs().getString(KEY_NETBIAN_DOWNLOADED_URLS, "").orEmpty()
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != url }
            .take(MAX_NETBIAN_DOWNLOAD_RECORDS - 1)
            .toList()
        netbianDownloadPrefs().edit()
            .putString(KEY_NETBIAN_DOWNLOADED_URLS, (listOf(url) + records).joinToString("\n"))
            .apply()
        invokeDownloadCallback(callback, mapOf("code" to 0))
    }

    private fun downloadNetbianImageUrl(
        url: String,
        title: String,
        referer: String,
        callback: KuiklyRenderCallback?,
        redirectDepth: Int
    ) {
        Thread {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 30000
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                    setRequestProperty("Referer", referer)
                    setRequestProperty("Accept-Encoding", "identity")
                    browserCookieFor(url)?.also {
                        setRequestProperty("Cookie", it)
                    }
                }
                val code = connection.responseCode
                if (code !in 200..299) {
                    invokeDownloadCallback(callback, mapOf("code" to -1, "message" to "http $code"))
                    return@Thread
                }
                val contentType = connection.contentType ?: ""
                val bytes = connection.inputStream.readBytes()
                if (!looksLikeImage(contentType, bytes)) {
                    val html = decodeHtml(bytes, contentType)
                    val jsonDownload = parseNetbianDownloadJson(html, url)
                    if (jsonDownload != null) {
                        if (redirectDepth < 3 && jsonDownload.pic.isNotEmpty()) {
                            Log.i(TAG, "downloadNetbianImage retry with json pic=${jsonDownload.pic}")
                            downloadNetbianImageUrl(jsonDownload.pic, title, referer, callback, redirectDepth + 1)
                        } else {
                            invokeDownloadCallback(callback, mapOf("code" to -1, "message" to jsonDownload.message))
                        }
                        return@Thread
                    }
                    logNetbianDownloadHtml(url, contentType, html)
                    val nextUrl = parseNetbianDownloadRedirect(html, url)
                    if (redirectDepth < 3 && !nextUrl.isNullOrEmpty() && nextUrl != url) {
                        Log.i(TAG, "downloadNetbianImage retry with parsed url=$nextUrl")
                        downloadNetbianImageUrl(nextUrl, title, referer, callback, redirectDepth + 1)
                    } else {
                        invokeDownloadCallback(callback, mapOf("code" to -1, "message" to "\u672a\u83b7\u53d6\u5230\u56fe\u7247\u5185\u5bb9\uff0c\u5df2\u8f93\u51fa\u4e0b\u8f7dHTML/JS\u65e5\u5fd7"))
                    }
                    return@Thread
                }
                val mimeType = guessImageMimeType(contentType, bytes)
                val extension = imageExtension(mimeType)
                val savedUri = saveImageBytes(bytes, title, mimeType, extension)
                invokeDownloadCallback(
                    callback,
                    mapOf("code" to 0, "message" to "\u5df2\u4fdd\u5b58\u5230\u76f8\u518c", "uri" to savedUri)
                )
            } catch (e: Throwable) {
                Log.e(TAG, "downloadNetbianImage failed", e)
                invokeDownloadCallback(callback, mapOf("code" to -1, "message" to (e.message ?: "download failed")))
            } finally {
                connection?.disconnect()
            }
        }.start()
    }

    private fun invokeDownloadCallback(callback: KuiklyRenderCallback?, result: Map<String, Any>) {
        mainHandler.post {
            callback?.invoke(result)
        }
    }

    private data class NetbianDownloadJson(val pic: String, val message: String)

    private fun parseNetbianDownloadJson(body: String, baseUrl: String): NetbianDownloadJson? {
        val trimmed = body.trim()
        if (!trimmed.startsWith("{")) return null
        return runCatching {
            val json = JSONObject(trimmed)
            val pic = json.optString("pic", "")
                .decodeBasicHtmlEntities()
                .takeIf { it.isNotBlank() }
                ?.toAbsoluteNetbianUrl(baseUrl)
                ?: ""
            val msg = json.optInt("msg", -1)
            val info = json.optString("info", "")
                .decodeBasicHtmlEntities()
                .replace(Regex("<[^>]+>"), "")
                .trim()
            val message = when {
                pic.isNotEmpty() -> "ok"
                msg == 0 -> "\u8bf7\u5148\u767b\u5f55\u540e\u518d\u4e0b\u8f7d\u9ad8\u6e05\u539f\u56fe"
                info.isNotEmpty() -> info
                msg == 1 -> "\u4eca\u65e5\u4e0b\u8f7d\u91cf\u5df2\u7528\u5b8c"
                msg == 2 -> "\u4eca\u65e5\u4e0b\u8f7d\u6b21\u6570\u5df2\u8fbe\u4e0a\u9650"
                msg == 5 -> "\u7535\u8111\u514d\u8d39\u4e0b\u8f7d\u4e0d\u4e86\uff0c\u8bf7\u4f7f\u7528\u624b\u673a\u6216\u4f1a\u5458\u4e0b\u8f7d"
                else -> "\u672a\u83b7\u53d6\u5230\u9ad8\u6e05\u539f\u56fe\u5730\u5740"
            }
            NetbianDownloadJson(pic, message)
        }.getOrNull()
    }

    private fun parseNetbianDownloadRedirect(html: String, baseUrl: String): String? {
        val patterns = listOf(
            Regex("""<img\b[^>]*data-pic=['"]([^'"]+)['"][^>]*>""", RegexOption.IGNORE_CASE),
            Regex("""(?:window\.)?location(?:\.href)?\s*=\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE),
            Regex("""(?:url|href|src)\s*[:=]\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE),
            Regex("""<a\b[^>]*href=['"]([^'"]+)['"][^>]*>""", RegexOption.IGNORE_CASE),
            Regex("""https?://pic\.netbian\.com/(?:uploads/allimg|e/[^'"<>\s]*(?:Down|down|download|xiazai))[^'"<>\s]+""", RegexOption.IGNORE_CASE),
            Regex("""(/uploads/allimg/[^'"<>\s]+)""", RegexOption.IGNORE_CASE),
            Regex("""(/[^'"<>\s]+(?:Down|down|download|xiazai)[^'"<>\s]*)""", RegexOption.IGNORE_CASE)
        )
        val candidates = patterns.flatMap { pattern ->
            pattern.findAll(html).map { match ->
                match.groupValues.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: match.value
            }.toList()
        }.mapNotNull { candidate ->
            candidate.decodeBasicHtmlEntities()
                .substringBefore("\\")
                .takeIf { it.isNotBlank() }
                ?.toAbsoluteNetbianUrl(baseUrl)
        }.filter { candidate ->
            val lower = candidate.lowercase()
            lower.contains("pic.netbian.com") &&
                (lower.contains("/uploads/allimg/") || lower.contains("down") || lower.endsWith(".jpg") || lower.endsWith(".png") || lower.endsWith(".webp")) &&
                !lower.contains("/tupian/")
        }.distinct().sortedByDescending { candidate ->
            when {
                candidate.contains("/uploads/allimg/", ignoreCase = true) -> 100
                candidate.contains("Down", ignoreCase = true) || candidate.contains("download", ignoreCase = true) -> 80
                candidate.endsWith(".jpg", ignoreCase = true) || candidate.endsWith(".png", ignoreCase = true) || candidate.endsWith(".webp", ignoreCase = true) -> 40
                else -> 0
            }
        }
        Log.i(TAG, "NetbianDownload candidates=${candidates.joinToString(" | ").take(1200)}")
        return candidates.firstOrNull()
    }

    private fun logNetbianDownloadHtml(url: String, contentType: String, html: String) {
        Log.w(TAG, "NetbianDownload non-image url=$url contentType=$contentType htmlLength=${html.length}")
        Log.i(TAG, "NetbianDownload cookieKeys=${netbianCookieKeys()}")
        val keywordRegex = Regex(
            ".{0,120}(down|download|DownSoft|DownSys|onclick|script|location|href|uploads|login|会员|登录).{0,240}",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val snippets = keywordRegex.findAll(html)
            .take(12)
            .map { it.value.compactForLog() }
            .toList()
        snippets.forEachIndexed { index, snippet ->
            Log.i(TAG, "NetbianDownload snippet[$index]=$snippet")
        }
    }

    private fun netbianCookieKeys(): String {
        return CookieManager.getInstance().getCookie(NETBIAN_HOME_URL)
            ?.split(";")
            ?.map { it.substringBefore("=", "").trim() }
            ?.filter { it.isNotEmpty() }
            ?.joinToString(",")
            ?: ""
    }

    private fun browserCookieFor(requestUrl: String): String? {
        val host = runCatching { URL(requestUrl).host }.getOrNull() ?: return null
        val cookieUrl = when {
            host.endsWith(NETBIAN_HOST) -> NETBIAN_HOME_URL
            host.endsWith(NNCOS_HOST) -> NNCOS_HOME_URL
            else -> return null
        }
        return CookieManager.getInstance().getCookie(cookieUrl)?.takeIf { it.isNotBlank() }
    }

    private fun browserRefererFor(requestUrl: String): String {
        val host = runCatching { URL(requestUrl).host }.getOrDefault("")
        return if (host.endsWith(NNCOS_HOST)) NNCOS_HOME_URL else NETBIAN_HOME_URL
    }

    private fun safeFileName(title: String): String {
        val base = title.ifEmpty { "netbian_${System.currentTimeMillis()}" }
            .replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_")
            .trim('_')
            .take(80)
        return base.ifEmpty { "netbian_${System.currentTimeMillis()}" }
    }

    private fun looksLikeImage(contentType: String, bytes: ByteArray): Boolean {
        if (contentType.lowercase().startsWith("image/")) return true
        return bytes.size > 12 && (
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() ||
                bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() ||
                bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte()
            )
    }

    private fun guessImageMimeType(contentType: String, bytes: ByteArray): String {
        val lower = contentType.lowercase()
        return when {
            lower.startsWith("image/") -> lower.substringBefore(";")
            bytes.size > 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "image/jpeg"
            bytes.size > 4 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() -> "image/png"
            bytes.size > 4 && bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() -> "image/webp"
            else -> "image/jpeg"
        }
    }

    private fun imageExtension(mimeType: String): String {
        return when (mimeType.lowercase()) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
    }

    private fun saveImageBytes(bytes: ByteArray, title: String, mimeType: String, extension: String): String {
        val resolver = KRApplication.application.contentResolver
        val fileName = "${safeFileName(title)}_${System.currentTimeMillis()}.$extension"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/KuiklyWork")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("create media uri failed")
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw IllegalStateException("open media output failed")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri.toString()
        }
        val dir = File(KRApplication.application.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "KuiklyWork")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val file = File(dir, fileName)
        FileOutputStream(file).use { it.write(bytes) }
        return file.absolutePath
    }

    private fun String.decodeBasicHtmlEntities(): String {
        return replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
    }

    private fun String.toAbsoluteNetbianUrl(baseUrl: String): String {
        return when {
            startsWith("//") -> "https:$this"
            startsWith("http://") || startsWith("https://") -> this
            startsWith("/") -> "https://pic.netbian.com$this"
            else -> runCatching {
                val base = URL(baseUrl)
                "https://${base.host}/${this.trimStart('/')}"
            }.getOrDefault("https://pic.netbian.com/${this.trimStart('/')}")
        }
    }

    private fun String.compactForLog(): String {
        return replace(Regex("\\s+"), " ").take(1000)
    }

    private fun invokeFetchCallback(callback: KuiklyRenderCallback?, result: Map<String, Any>) {
        Log.i(TAG, "fetchHtml callback scheduled: code=${result["code"]}, keys=${result.keys}")
        mainHandler.post {
            try {
                callback?.invoke(result)
                Log.i(TAG, "fetchHtml callback invoked")
            } catch (e: Throwable) {
                Log.e(TAG, "fetchHtml callback failed", e)
            }
        }
    }

    private fun decodeHtml(bytes: ByteArray, contentType: String?): String {
        val charsetName = contentType
            ?.split(";")
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("charset=", ignoreCase = true) }
            ?.substringAfter("=")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        val candidates = listOfNotNull(charsetName, "UTF-8", "GB18030", "GBK")
        for (candidate in candidates.distinct()) {
            try {
                return String(bytes, Charset.forName(candidate))
            } catch (_: Throwable) {
            }
        }
        return String(bytes)
    }
    private fun reportRealtime(params: String?) {
    }

    private fun reportDT(params: String?) {
    }

    private fun log(params: String?) {
        if (params == null) {
            return
        }

        val paramJSON = JSONObject(params)
        Log.i("KuiklyRender", paramJSON.optString("content"))
    }

    private fun toast(params: String?) {
        if (params == null) {
            return
        }
        val paramJSON = JSONObject(params)
        Toast.makeText(
            KRApplication.application,
            paramJSON.optString("content"),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun copyToPasteboard(params: String?) {
        if (params == null) {
            return
        }

        val paramJSON = JSONObject(params)
        (context?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.also {
            it.setPrimaryClip(ClipData.newPlainText(MODULE_NAME, paramJSON.optString("content")))
        }
    }

    private fun openPage(params: String?) {
        if (params == null) {
            return
        }
        val ctx = context ?: return
        val paramJSON = JSONObject(params)
        val url = paramJSON.optString("url")
    }

    private fun closePage(params: String?) {
        activity?.finish()
    }

    private fun showAlert(params: String?, callback: KuiklyRenderCallback?) {
        if (params == null) {
            return
        }
        val paramJSON = JSONObject(params)
        val titleText = paramJSON.optString("title")
        val message = paramJSON.optString("message")
        val buttons = paramJSON.optJSONArray("buttons") ?: JSONArray()
    }

    private fun ssoRequest(params: String?, callback: KuiklyRenderCallback?) {}

    private fun qqLiveSSORequest(params: String?, callback: KuiklyRenderCallback?) {
    }

    private fun localServeTime(params: String?, callback: KuiklyRenderCallback?) {
        val time = (System.currentTimeMillis() / 1000.0)
        callback?.invoke(
            mapOf(
                "time" to time
            )
        )
    }

    private fun currentTimestamp(params: String?): String {
        return (System.currentTimeMillis()).toString()
    }

    private fun dateFormatter(params: String?): String {
        val paramJSONObject = JSONObject(params ?: "{}")
        val data = Date(paramJSONObject.optLong("timeStamp"))
        val format = SimpleDateFormat(paramJSONObject.optString("format"))
        return format.format(data)
    }

    companion object {
        const val MODULE_NAME = "HRBridgeModule"
        private const val TAG = "KuiklyWork"
        private const val NETBIAN_HOST = "pic.netbian.com"
        private const val NETBIAN_HOME_URL = "https://pic.netbian.com/"
        private const val NNCOS_HOST = "nncos.com"
        private const val NNCOS_HOME_URL = "https://www.nncos.com/"
        private var netbianLoginSucceeded = false
        private var nncosLoginSucceeded = false

        fun markNetbianLoginSucceeded() {
            netbianLoginSucceeded = true
            netbianPrefs().edit().putBoolean(KEY_NETBIAN_LOGIN_SUCCEEDED, true).apply()
        }

        fun isNetbianLoggedIn(): Boolean {
            if (hasNetbianLoginCookie()) {
                markNetbianLoginSucceeded()
                return true
            }
            val hasCookie = hasNetbianCookie()
            if (!hasCookie) {
                netbianLoginSucceeded = false
                netbianPrefs().edit().putBoolean(KEY_NETBIAN_LOGIN_SUCCEEDED, false).apply()
                return false
            }
            return netbianLoginSucceeded || netbianPrefs().getBoolean(KEY_NETBIAN_LOGIN_SUCCEEDED, false)
        }

        fun hasNetbianLoginCookie(): Boolean {
            val cookie = CookieManager.getInstance().getCookie(NETBIAN_HOME_URL) ?: return false
            return isNetbianLoginCookie(cookie)
        }

        fun hasNetbianCookie(): Boolean {
            return !CookieManager.getInstance().getCookie(NETBIAN_HOME_URL).isNullOrBlank()
        }

        fun isNetbianLoginCookie(cookie: String): Boolean {
            val loginKeys = setOf(
                "ecmsmluserid",
                "ecmsmlusername",
                "ecmsmlgroupid",
                "ecmsmlrnd",
                "mluserid",
                "mlusername",
                "mlgroupid",
                "mlrnd",
                "enewsuserid",
                "enewsusername",
                "userid",
                "username"
            )
            return cookie.split(";")
                .map { it.trim() }
                .mapNotNull {
                    val key = it.substringBefore("=", "").trim().lowercase()
                    val value = it.substringAfter("=", "").trim()
                    if (key.isNotEmpty() && value.isNotEmpty()) key else null
                }
                .any { it in loginKeys }
        }

        fun isNetbianLoginSuccessUrl(url: String): Boolean {
            return url.contains("/e/memberconnect/qq/loginend.php", ignoreCase = true) ||
                url.contains("/e/member/cp/", ignoreCase = true)
        }

        fun markNncosLoginSucceeded() {
            nncosLoginSucceeded = true
            nncosPrefs().edit().putBoolean(KEY_NNCOS_LOGIN_SUCCEEDED, true).apply()
        }

        fun isNncosLoggedIn(): Boolean {
            if (hasNncosLoginCookie()) {
                markNncosLoginSucceeded()
                return true
            }
            if (!hasNncosCookie()) {
                nncosLoginSucceeded = false
                nncosPrefs().edit().putBoolean(KEY_NNCOS_LOGIN_SUCCEEDED, false).apply()
                return false
            }
            return nncosLoginSucceeded || nncosPrefs().getBoolean(KEY_NNCOS_LOGIN_SUCCEEDED, false)
        }

        fun hasNncosLoginCookie(): Boolean {
            val cookie = CookieManager.getInstance().getCookie(NNCOS_HOME_URL) ?: return false
            return cookie.split(";").any { value ->
                val key = value.substringBefore("=").trim().lowercase()
                key.startsWith("wordpress_logged_in_")
            }
        }

        fun hasNncosCookie(): Boolean {
            return !CookieManager.getInstance().getCookie(NNCOS_HOME_URL).isNullOrBlank()
        }

        private fun netbianPrefs(): SharedPreferences {
            return KRApplication.application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }

        private fun nncosPrefs(): SharedPreferences {
            return KRApplication.application.getSharedPreferences(NNCOS_PREFS_NAME, Context.MODE_PRIVATE)
        }

        private const val PREFS_NAME = "netbian_login"
        private const val KEY_NETBIAN_LOGIN_SUCCEEDED = "login_succeeded"
        private const val NNCOS_PREFS_NAME = "nncos_login"
        private const val KEY_NNCOS_LOGIN_SUCCEEDED = "login_succeeded"
        private const val DOWNLOAD_PREFS_NAME = "netbian_download_records"
        private const val KEY_NETBIAN_DOWNLOADED_URLS = "downloaded_urls"
        private const val MAX_NETBIAN_DOWNLOAD_RECORDS = 500

        private fun netbianDownloadPrefs(): SharedPreferences {
            return KRApplication.application.getSharedPreferences(DOWNLOAD_PREFS_NAME, Context.MODE_PRIVATE)
        }
    }
}

object NetbianLoginCallbackStore {
    private var callback: KuiklyRenderCallback? = null

    fun replace(newCallback: KuiklyRenderCallback?) {
        callback?.invoke(mapOf("code" to -1, "isLoggedIn" to KRBridgeModule.isNetbianLoggedIn(), "message" to "login replaced"))
        callback = newCallback
    }

    fun finish(isLoggedIn: Boolean, message: String = "") {
        callback?.invoke(mapOf("code" to 0, "isLoggedIn" to isLoggedIn, "message" to message))
        callback = null
    }
}

object NncosLoginCallbackStore {
    private var callback: KuiklyRenderCallback? = null

    fun replace(newCallback: KuiklyRenderCallback?) {
        callback?.invoke(mapOf("code" to -1, "isLoggedIn" to KRBridgeModule.isNncosLoggedIn(), "message" to "login replaced"))
        callback = newCallback
    }

    fun finish(isLoggedIn: Boolean, message: String = "") {
        callback?.invoke(mapOf("code" to 0, "isLoggedIn" to isLoggedIn, "message" to message))
        callback = null
    }
}

private fun JSONObject.toMap(): Map<Any, Any> {
    val map = mutableMapOf<Any, Any>()
    val keys = keys()
    while (keys.hasNext()) {
        val key = keys.next()
        when (val v = opt(key)) {
            is JSONObject -> {
                map[key] = v.toMap()
            }

            else -> {
                v?.also {
                    map[key] = it
                }
            }
        }
    }
    return map
}
