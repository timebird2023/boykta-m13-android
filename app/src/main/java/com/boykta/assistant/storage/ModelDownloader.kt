package com.boykta.assistant.storage

import android.content.Context
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

object ModelDownloader {

    // Official Standard Arabic Fusha Vosk model (~318MB)
    private const val FUSHA_MGB2_URL =
        "https://alphacephei.com/vosk/models/vosk-model-ar-mgb2-0.4.zip"

    // Official Maghrebi/Darija Arabic small Vosk model (~158MB)
    private const val DARIJA_TN_URL =
        "https://alphacephei.com/vosk/models/vosk-model-small-ar-tn-0.1-linto.zip"

    fun downloadAndExtractArabicModel(
        context: Context,
        packId: String = SdCardModelLocator.PACK_FUSHA_MGB2,
        onProgress: (Int, String) -> Unit,
        onComplete: (Boolean, String) -> Unit
    ) {
        val downloadUrl = if (packId == SdCardModelLocator.PACK_DARIJA_TN) DARIJA_TN_URL else FUSHA_MGB2_URL
        val folderName = SdCardModelLocator.getFolderNameForPack(packId)

        Thread {
            try {
                val baseDir = SdCardModelLocator.getPreferredStorageBaseDir(context)
                val modelsRoot = File(baseDir, "models").apply { mkdirs() }
                val targetDir = File(modelsRoot, folderName)
                val zipFile = File(modelsRoot, "$packId-temp.zip")

                val conn = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                }
                conn.connect()

                if (conn.responseCode !in 200..299) {
                    onComplete(false, "تعذر التحميل من السيرفر (رمز ${conn.responseCode}).")
                    return@Thread
                }

                val totalBytes = conn.contentLength.toLong().coerceAtLeast(1L)
                var downloadedBytes = 0L

                BufferedInputStream(conn.inputStream).use { input ->
                    FileOutputStream(zipFile).use { output ->
                        val buf = ByteArray(16 * 1024)
                        var read: Int
                        while (input.read(buf).also { read = it } != -1) {
                            output.write(buf, 0, read)
                            downloadedBytes += read
                            val pct = ((downloadedBytes * 75L) / totalBytes).toInt().coerceIn(1, 75)
                            val mb = downloadedBytes / (1024 * 1024)
                            onProgress(pct, "تم تحميل ${mb}MB على بطاقة التخزين...")
                        }
                    }
                }

                onProgress(80, "جاري فك ضغط النموذج على بطاقة الذاكرة...")
                if (targetDir.exists()) targetDir.deleteRecursively()

                ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val relPath = entry.name.substringAfter("/", "")
                        if (relPath.isNotEmpty()) {
                            val outFile = File(targetDir, relPath)
                            if (entry.isDirectory) {
                                outFile.mkdirs()
                            } else {
                                outFile.parentFile?.mkdirs()
                                FileOutputStream(outFile).use { fos ->
                                    zis.copyTo(fos)
                                }
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }

                zipFile.delete()
                onProgress(100, "اكتمل التثبيت! بويكتا يعمل الآن 100% بدون إنترنت.")
                onComplete(true, "تم تثبيت الحزمة في: ${targetDir.absolutePath}")
            } catch (e: Exception) {
                onComplete(false, "فشل التحميل: ${e.localizedMessage ?: "تأكد من الاتصال بالواي فاي"}")
            }
        }.start()
    }
}
