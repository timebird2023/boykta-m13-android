package com.boykta.assistant.storage

import android.content.Context
import java.io.File

object SdCardModelLocator {

    const val PACK_FUSHA_MGB2 = "fusha-mgb2"
    const val PACK_DARIJA_TN = "darija-tn"
    private const val PREF_NAME = "boykta_voice_pack_prefs"
    private const val KEY_SELECTED_PACK = "selected_pack"
    private const val DEFAULT_PACK = "fusha-mgb2"

    fun getFolderNameForPack(packId: String): String {
        return if (packId == PACK_DARIJA_TN) "vosk-model-ar-darija" else "vosk-model-ar-fusha"
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
        // Index 1 is the removable 8GB SD card when mounted on Galaxy M13
        return if (dirs.size > 1) dirs[1] else dirs.firstOrNull() ?: context.filesDir
    }

    fun isPackInstalled(context: Context, packId: String): Boolean {
        return findModelDirForPack(context, packId) != null
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
            ?: findModelDirForPack(context, PACK_FUSHA_MGB2)
            ?: findModelDirForPack(context, PACK_DARIJA_TN)
    }
}
