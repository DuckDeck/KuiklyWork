package com.ss.kuiklywork.module

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.tencent.kuikly.core.render.android.export.KuiklyRenderBaseModule
import com.tencent.kuikly.core.render.android.export.KuiklyRenderCallback
import com.ss.kuiklywork.KRApplication
import com.ss.kuiklywork.KuiklyRenderActivity
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset

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
                    setRequestProperty("User-Agent", "Mozilla/5.0 KuiklyWork")
                    setRequestProperty("Accept", "text/html,application/xhtml+xml")
                }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val bytes = stream?.readBytes() ?: ByteArray(0)
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