package com.boykta.assistant.storage

import android.content.Context
import java.io.File

object SdCardModelLocator {

    const val PACK_FUSHA_MGB2 = "fusha-mgb2"
    const val PACK_DARIJA_TN = "darija-tn"
    const val PACK_TTS_KAREEM = "tts-kareem-ar"

    private const val PREF_NAME = "boykta_voice_pack_prefs"
    private const val KEY_SELECTED_PACK = "selected_pack"
    private const val DEFAULT_PACK = "fusha-mgb2"

    fun getFolderNameForPack(packId: String): String {
        return when (packId) {
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

    fun getPreferredStorageBaseDir(context: Context): File {
        val dirs = context.getExternalFilesDirs(null).filterNotNull()
        return if (dirs.size > 1) dirs[1] else dirs.firstOrNull() ?: context.filesDir
    }

    fun isPackInstalled(context: Context, packId: String): Boolean {
        return if (packId == PACK_TTS_KAREEM) {
            findKareemTtsDir(context) != null
        } else {
            findModelDirForPack(context, packId) != null
        }
    }

    fun isKareemTtsInstalled(context: Context): Boolean = findKareemTtsDir(context) != null

    fun findKareemTtsDir(context: Context): File? {
        val folderName = getFolderNameForPack(PACK_TTS_KAREEM)
        val candidates = mutableListOf<File>()
        candidates.add(File("/storage/sdcard1/Android/data/com.boykta.assistant/files/models/$folderName"))
        context.getExternalFilesDirs(null).filterNotNull().forEach { dir ->
            candidates.add(File(dir, "models/$folderName"))
            candidates.add(File(dir, folderName))
        }
        candidates.add(File(context.filesDir, "models/$folderName"))

        for (dir in candidates) {
            if (isValidKareemDir(dir)) return dir
            val nested = dir.listFiles()?.firstOrNull { it.isDirectory && isValidKareemDir(it) }
            if (nested != null) return nested
        }
        return null
    }

    private fun isValidKareemDir(dir: File): Boolean {
        return dir.exists() &&
            dir.isDirectory &&
            File(dir, "ar_JO-kareem-low.onnx").exists() &&
            File(dir, "tokens.txt").exists() &&
            File(dir, "espeak-ng-data").exists()
    }

    private fun findModelDirForPack(context: Context, packId: String): File? {
        val folderName = getFolderNameForPack(packId)
        val candidates = mutableListOf<File>()

        candidates.add(File("/storage/sdcard1/Android/data/com.boykta.assistant/files/models/$folderName"))
        context.getExternalFilesDirs(null).filterNotNull().forEach { dir ->
            candidates.add(File(dir, "models/$folderName"))
            candidates.add(File(dir, folderName))
        }
        candidates.add(File(context.filesDir, "models/$folderName"))

        return candidates.firstOrNull { dir ->
            dir.exists() && dir.isDirectory && File(dir, "am").exists()
        }
    }

    fun findActiveVoskModelDir(context: Context): File? {
        val selected = getSelectedPack(context)
        return findModelDirForPack(context, selected)
            ?: findModelDirForPack(context, PACK_DARIJA_TN)
            ?: findModelDirForPack(context, PACK_FUSHA_MGB2)
    }
}
