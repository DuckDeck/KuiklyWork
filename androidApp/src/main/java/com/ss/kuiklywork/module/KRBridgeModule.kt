package com.ss.kuiklywork.module

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import android.webkit.CookieManager
import com.tencent.kuikly.core.render.android.export.KuiklyRenderBaseModule
import com.tencent.kuikly.core.render.android.export.KuiklyRenderCallback
import com.ss.kuiklywork.KRApplication
import com.ss.kuiklywork.KuiklyRenderActivity
import com.ss.kuiklywork.NetbianLoginActivity
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import android.content.Intent
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
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
                    setRequestProperty("Referer", "https://pic.netbian.com/")
                    netbianCookieFor(requestUrl)?.also {
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
                "isLoggedIn" to hasNetbianLoginCookie(),
                "hasCookie" to ((CookieManager.getInstance().getCookie(NETBIAN_HOME_URL) ?: "").isNotEmpty())
            )
        )
    }

    private fun netbianCookieFor(requestUrl: String): String? {
        val host = runCatching { URL(requestUrl).host }.getOrNull() ?: return null
        if (!host.endsWith(NETBIAN_HOST)) {
            return null
        }
        return CookieManager.getInstance().getCookie(NETBIAN_HOME_URL)?.takeIf { it.isNotBlank() }
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

        fun hasNetbianLoginCookie(): Boolean {
            val cookie = CookieManager.getInstance().getCookie(NETBIAN_HOME_URL) ?: return false
            return isNetbianLoginCookie(cookie)
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
    }
}

object NetbianLoginCallbackStore {
    private var callback: KuiklyRenderCallback? = null

    fun replace(newCallback: KuiklyRenderCallback?) {
        callback?.invoke(mapOf("code" to -1, "isLoggedIn" to KRBridgeModule.hasNetbianLoginCookie(), "message" to "login replaced"))
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
