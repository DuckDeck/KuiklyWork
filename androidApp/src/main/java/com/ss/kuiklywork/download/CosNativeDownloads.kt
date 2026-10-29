package com.ss.kuiklywork.download

import com.ss.kuiklywork.KRApplication
import com.ss.kuiklywork.module.KRBridgeModule
import android.webkit.CookieManager
import java.io.File
import java.util.concurrent.Executors

object CosNativeDownloads {
    val manager: CosDownloadTaskManager by lazy {
        CosDownloadTaskManager(
            CosDownloadTaskStore(File(KRApplication.application.filesDir, "cos_download_tasks/tasks.json")),
            CosImageFileDownloader(KRApplication.application) { imageUrl ->
                KRBridgeModule.isNncosLoggedIn() // Restore this site's cookies if necessary.
                CookieManager.getInstance().getCookie(imageUrl)
            },
            { url -> KRBridgeModule.saveCompletedCosGroup(url) },
            Executors.newSingleThreadExecutor(),
            { url -> KRBridgeModule.hasCompletedCosGroup(url) }
        )
    }
}

fun CosDownloadRecord.toBridgeSnapshot(): Map<String, Any> = mapOf(
    "taskId" to taskId, "detailUrl" to detailUrl, "title" to title, "status" to status,
    "completed" to completed, "total" to total, "message" to message
)
