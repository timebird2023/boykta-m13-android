package com.boykta.assistant.storage

import android.content.Context
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

object ModelDownloader {

    // Neural Moonshine Base Arabic Quantized ASR (~114MB)
    private const val MOONSHINE_AR_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-moonshine-base-ar-quantized-2026-02-27.tar.bz2"

    // Official Standard Arabic Fusha Vosk model (~318MB)
    private const val FUSHA_MGB2_URL =
        "https://alphacephei.com/vosk/models/vosk-model-ar-mgb2-0.4.zip"

    // Official Maghrebi/Darija Arabic small Vosk model (~158MB)
    private const val DARIJA_TN_URL =
        "https://alphacephei.com/vosk/models/vosk-model-small-ar-tn-0.1-linto.zip"

    // High-Clarity Offline Male Arabic Fusha Voice (Piper Miro V2 High ~64MB)
    private const val MIRO_V2_TTS_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-ar_JO-SA_miro_V2-high.tar.bz2"

    // Legacy Kareem Offline Male Arabic Voice (~64MB)
    private const val KAREEM_TTS_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-ar_JO-kareem-low.tar.bz2"

    fun downloadAndExtractArabicModel(
        context: Context,
        packId: String = SdCardModelLocator.PACK_MOONSHINE_AR,
        onProgress: (Int, String) -> Unit,
        onComplete: (Boolean, String) -> Unit
    ) {
        val isTarBz2 = packId == SdCardModelLocator.PACK_TTS_MIRO ||
            packId == SdCardModelLocator.PACK_TTS_KAREEM ||
            packId == SdCardModelLocator.PACK_MOONSHINE_AR

        val downloadUrl = when (packId) {
            SdCardModelLocator.PACK_TTS_MIRO -> MIRO_V2_TTS_URL
            SdCardModelLocator.PACK_TTS_KAREEM -> KAREEM_TTS_URL
            SdCardModelLocator.PACK_MOONSHINE_AR -> MOONSHINE_AR_URL
            SdCardModelLocator.PACK_DARIJA_TN -> DARIJA_TN_URL
            else -> FUSHA_MGB2_URL
        }
        val folderName = SdCardModelLocator.getFolderNameForPack(packId)

        Thread {
            try {
                val baseDir = SdCardModelLocator.getPreferredStorageBaseDir(context)
                val modelsRoot = File(baseDir, "models").apply { mkdirs() }
                val targetDir = File(modelsRoot, folderName)
                val ext = if (isTarBz2) "tar.bz2" else "zip"
                val archiveFile = File(modelsRoot, "$packId-temp.$ext")

                val conn = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20000
                    readTimeout = 45000
                    instanceFollowRedirects = true
                }
                conn.connect()

                if (conn.responseCode !in 200..299) {
                    onComplete(false, "تعذر التحميل من الخادم (رمز ${conn.responseCode}).")
                    return@Thread
                }

                val totalBytes = conn.contentLength.toLong().coerceAtLeast(1L)
                var downloadedBytes = 0L

                BufferedInputStream(conn.inputStream).use { input ->
                    FileOutputStream(archiveFile).use { output ->
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

                onProgress(80, "جاري فك ضغط الحزمة على بطاقة التخزين...")
                if (targetDir.exists()) targetDir.deleteRecursively()
                targetDir.mkdirs()

                if (isTarBz2) {
                    extractTarBz2(archiveFile, targetDir)
                } else {
                    extractZip(archiveFile, targetDir)
                }

                archiveFile.delete()
                onProgress(100, "اكتمل التثبيت، بويكتا يعمل الآن بدون إنترنت.")
                onComplete(true, "تم التثبيت بنجاح في: ${targetDir.absolutePath}")
            } catch (e: Exception) {
                onComplete(false, "فشل التحميل: ${e.localizedMessage ?: "تأكد من الاتصال بالواي فاي"}")
            }
        }.start()
    }

    private fun extractZip(zipFile: File, targetDir: File) {
        val canonicalTarget = targetDir.canonicalPath
        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val relPath = entry.name.substringAfter("/", "")
                if (relPath.isNotEmpty()) {
                    val outFile = File(targetDir, relPath)
                    if (!outFile.canonicalPath.startsWith(canonicalTarget)) {
                        throw SecurityException("Invalid zip entry path")
                    }
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
    }

    private fun extractTarBz2(tarBz2File: File, targetDir: File) {
        val canonicalTarget = targetDir.canonicalPath
        BufferedInputStream(tarBz2File.inputStream()).use { bis ->
            BZip2CompressorInputStream(bis, true).use { bzIn ->
                TarArchiveInputStream(bzIn).use { tarIn ->
                    var entry = tarIn.nextEntry
                    while (entry != null) {
                        val rawName = entry.name.trimStart('.', '/')
                        val relPath = rawName.substringAfter("/", "")
                        if (relPath.isNotEmpty()) {
                            val outFile = File(targetDir, relPath)
                            if (!outFile.canonicalPath.startsWith(canonicalTarget)) {
                                throw SecurityException("Invalid tar entry path")
                            }
                            if (entry.isDirectory) {
                                outFile.mkdirs()
                            } else {
                                outFile.parentFile?.mkdirs()
                                FileOutputStream(outFile).use { fos ->
                                    tarIn.copyTo(fos)
                                }
                            }
                        }
                        entry = tarIn.nextEntry
                    }
                }
            }
        }
    }
}
