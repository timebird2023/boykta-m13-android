package com.boykta.assistant.storage

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File

object SdCardModelLocator {

    const val PACK_MOONSHINE_AR = "moonshine-ar-base"
    const val PACK_FUSHA_MGB2 = "fusha-mgb2"
    const val PACK_DARIJA_TN = "darija-tn"
    const val PACK_TTS_MIRO = "tts-miro-ar"
    const val PACK_TTS_KAREEM = "tts-kareem-ar"

    private const val PREF_NAME = "boykta_voice_pack_prefs"
    private const val KEY_SELECTED_PACK = "selected_pack"
    private const val KEY_PREFER_SYSTEM_TTS = "prefer_system_tts"
    private const val DEFAULT_PACK = "moonshine-ar-base"

    fun hasAllFilesPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    fun getPersistentPublicRoot(): File {
        return File(Environment.getExternalStorageDirectory(), "BoyktaModels")
    }

    fun getFolderNameForPack(packId: String): String {
        return when (packId) {
            PACK_MOONSHINE_AR -> "moonshine-ar-base"
            PACK_TTS_MIRO -> "tts-ar-miro"
            PACK_TTS_KAREEM -> "tts-ar-kareem"
            PACK_DARIJA_TN -> "vosk-model-ar-darija"
            else -> "vosk-model-ar-fusha"
        }
    }

    fun getSelectedPack(context: Context): String {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SELECTED_PACK, DEFAULT_PACK) ?: DEFAULT_PACK
    }

    fun setSelectedPack(context: Context, packId: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SELECTED_PACK, packId)
            .apply()
    }

    fun getPreferSystemTts(context: Context): Boolean {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PREFER_SYSTEM_TTS, false)
    }

    fun setPreferSystemTts(context: Context, prefer: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PREFER_SYSTEM_TTS, prefer)
            .apply()
    }

    fun getPreferredStorageBaseDir(context: Context): File {
        if (hasAllFilesPermission(context)) {
            val persistent = getPersistentPublicRoot()
            if (persistent.exists() || persistent.mkdirs()) {
                return persistent
            }
        }
        val dirs = context.getExternalFilesDirs(null).filterNotNull()
        return if (dirs.size > 1) dirs[1] else dirs.firstOrNull() ?: context.filesDir
    }

    fun isPackInstalled(context: Context, packId: String): Boolean {
        return when (packId) {
            PACK_TTS_MIRO -> findMiroTtsDir(context) != null
            PACK_TTS_KAREEM -> findKareemTtsDir(context) != null
            PACK_MOONSHINE_AR -> findMoonshineModelDir(context) != null
            else -> findModelDirForPack(context, packId) != null
        }
    }

    fun isMiroTtsInstalled(context: Context): Boolean = findMiroTtsDir(context) != null

    fun isKareemTtsInstalled(context: Context): Boolean = findKareemTtsDir(context) != null

    fun findBestOfflineTtsDir(context: Context): File? {
        return findMiroTtsDir(context) ?: findKareemTtsDir(context)
    }

    fun findVitsOnnxFile(dir: File): File? {
        val preferredNames = listOf(
            "ar_JO-SA_miro_V2-high.onnx",
            "ar_JO-kareem-medium.onnx",
            "ar_JO-kareem-low.onnx"
        )
        for (name in preferredNames) {
            val f = File(dir, name)
            if (f.exists()) return f
        }
        return dir.listFiles()?.firstOrNull { it.isFile && it.name.endsWith(".onnx") }
    }

    fun findMiroTtsDir(context: Context): File? {
        val folderName = getFolderNameForPack(PACK_TTS_MIRO)
        val candidates = buildCandidates(context, folderName)
        for (dir in candidates) {
            if (isValidVitsTtsDir(dir)) return dir
            val nested = dir.listFiles()?.firstOrNull { it.isDirectory && isValidVitsTtsDir(it) }
            if (nested != null) return nested
        }
        return null
    }

    fun findKareemTtsDir(context: Context): File? {
        val folderName = getFolderNameForPack(PACK_TTS_KAREEM)
        val candidates = buildCandidates(context, folderName)
        for (dir in candidates) {
            if (isValidVitsTtsDir(dir)) return dir
            val nested = dir.listFiles()?.firstOrNull { it.isDirectory && isValidVitsTtsDir(it) }
            if (nested != null) return nested
        }
        return null
    }

    private fun isValidVitsTtsDir(dir: File): Boolean {
        return dir.exists() &&
            dir.isDirectory &&
            findVitsOnnxFile(dir) != null &&
            File(dir, "tokens.txt").exists() &&
            File(dir, "espeak-ng-data").exists()
    }

    fun findMoonshineModelDir(context: Context): File? {
        val folderName = getFolderNameForPack(PACK_MOONSHINE_AR)
        val candidates = buildCandidates(context, folderName)
        for (dir in candidates) {
            if (isValidMoonshineDir(dir)) return dir
            val nested = dir.listFiles()?.firstOrNull { it.isDirectory && isValidMoonshineDir(it) }
            if (nested != null) return nested
        }
        return null
    }

    private fun isValidMoonshineDir(dir: File): Boolean {
        return dir.exists() &&
            dir.isDirectory &&
            File(dir, "encoder_model.ort").exists() &&
            File(dir, "decoder_model_merged.ort").exists() &&
            File(dir, "tokens.txt").exists()
    }

    private fun findModelDirForPack(context: Context, packId: String): File? {
        val folderName = getFolderNameForPack(packId)
        val candidates = buildCandidates(context, folderName)
        return candidates.firstOrNull { dir ->
            dir.exists() && dir.isDirectory && File(dir, "am").exists()
        }
    }

    private fun buildCandidates(context: Context, folderName: String): List<File> {
        val list = mutableListOf<File>()
        val publicRoot = getPersistentPublicRoot()
        list.add(File(publicRoot, "models/$folderName"))
        list.add(File(publicRoot, folderName))
        list.add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "BoyktaModels/$folderName"))
        list.add(File("/storage/sdcard1/Android/data/com.boykta.assistant/files/models/$folderName"))
        context.getExternalFilesDirs(null).filterNotNull().forEach { dir ->
            list.add(File(dir, "models/$folderName"))
            list.add(File(dir, folderName))
        }
        list.add(File(context.filesDir, "models/$folderName"))
        return list
    }

    fun findActiveVoskModelDir(context: Context): File? {
        val selected = getSelectedPack(context)
        return findModelDirForPack(context, selected)
            ?: findModelDirForPack(context, PACK_DARIJA_TN)
            ?: findModelDirForPack(context, PACK_FUSHA_MGB2)
    }

    @Synchronized
    fun migrateAppPrivateModelsToPersistentStorage(context: Context): Int {
        if (!hasAllFilesPermission(context)) return 0
        val persistentModelsRoot = File(getPersistentPublicRoot(), "models").apply { mkdirs() }
        var copiedPacks = 0

        val miroSrc = findMiroTtsDir(context)
        val miroDst = File(persistentModelsRoot, getFolderNameForPack(PACK_TTS_MIRO))
        if (miroSrc != null &&
            miroSrc.canonicalPath != miroDst.canonicalPath &&
            !isValidVitsTtsDir(miroDst)
        ) {
            if (copyDirRecursively(miroSrc, miroDst)) copiedPacks++
        }

        val kareemSrc = findKareemTtsDir(context)
        val kareemDst = File(persistentModelsRoot, getFolderNameForPack(PACK_TTS_KAREEM))
        if (kareemSrc != null &&
            kareemSrc.canonicalPath != kareemDst.canonicalPath &&
            !isValidVitsTtsDir(kareemDst)
        ) {
            if (copyDirRecursively(kareemSrc, kareemDst)) copiedPacks++
        }

        val moonSrc = findMoonshineModelDir(context)
        val moonDst = File(persistentModelsRoot, getFolderNameForPack(PACK_MOONSHINE_AR))
        if (moonSrc != null &&
            moonSrc.canonicalPath != moonDst.canonicalPath &&
            !isValidMoonshineDir(moonDst)
        ) {
            if (copyDirRecursively(moonSrc, moonDst)) copiedPacks++
        }

        for (packId in listOf(PACK_DARIJA_TN, PACK_FUSHA_MGB2)) {
            val src = findModelDirForPack(context, packId)
            val dst = File(persistentModelsRoot, getFolderNameForPack(packId))
            if (src != null &&
                src.canonicalPath != dst.canonicalPath &&
                !File(dst, "am").exists()
            ) {
                if (copyDirRecursively(src, dst)) copiedPacks++
            }
        }
        return copiedPacks
    }

    private fun copyDirRecursively(src: File, dst: File): Boolean {
        return try {
            if (!src.exists()) return false
            if (src.isDirectory) {
                dst.mkdirs()
                src.listFiles()?.forEach { child ->
                    copyDirRecursively(child, File(dst, child.name))
                }
            } else {
                dst.parentFile?.mkdirs()
                src.inputStream().use { input ->
                    dst.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            true
        } catch (_: Throwable) {
            false
        }
    }
}
