package com.campusfold.tracker.ai

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.campusfold.tracker.data.pref.AppPreferences
import java.io.File

interface CrashGuardStorage {
    fun getString(key: String): String?
    fun putString(key: String, value: String?)
    fun getInt(key: String, defValue: Int): Int
    fun putInt(key: String, value: Int)
    fun remove(key: String)
}

data class QuarantineResult(
    val originalPath: String,
    val quarantinedFile: File?,
    val reason: String
)

/**
 * Pure state machine for Crash-Loop Guard and One-Time Cleanup.
 * Completely testable with JVM unit tests without Android OS stubs.
 */
class CrashLoopStateMachine(
    private val storage: CrashGuardStorage,
    private val quarantineFileAction: (File) -> File? = { file ->
        val parent = file.parentFile ?: File(".")
        val dest = File(parent, "${file.name}.${System.currentTimeMillis()}.quarantined")
        if (file.renameTo(dest)) dest else null
    }
) {
    companion object {
        const val KEY_LOAD_IN_PROGRESS = "load_in_progress"
        const val KEY_SAVED_MODEL_PATH = "saved_model_path"
        const val KEY_SAVED_MODEL_NAME = "saved_model_name"
        const val KEY_CLEANUP_VERSION = "ai_cleanup_version"
        const val TARGET_CLEANUP_VERSION = 1
    }

    /**
     * Executes gated one-time cleanup to remove legacy saved model paths
     * that previously caused crash-loops. Gated on [TARGET_CLEANUP_VERSION].
     * Never touches Room database.
     */
    fun performOneTimeCleanup(onDataStoreClear: (() -> Unit)? = null): Boolean {
        val currentVersion = storage.getInt(KEY_CLEANUP_VERSION, 0)
        if (currentVersion < TARGET_CLEANUP_VERSION) {
            onDataStoreClear?.invoke()
            storage.remove(KEY_SAVED_MODEL_PATH)
            storage.remove(KEY_SAVED_MODEL_NAME)
            storage.remove(KEY_LOAD_IN_PROGRESS)
            storage.putInt(KEY_CLEANUP_VERSION, TARGET_CLEANUP_VERSION)
            return true
        }
        return false
    }

    /**
     * Checks if a previous model load crashed mid-flight without completing.
     * If the flag is still set, quarantines the model file, clears saved paths,
     * and returns a [QuarantineResult].
     */
    fun checkCrashLoopAndQuarantine(): QuarantineResult? {
        val inProgressPath = storage.getString(KEY_LOAD_IN_PROGRESS) ?: return null

        val modelFile = File(inProgressPath)
        var quarantinedFile: File? = null

        if (modelFile.exists()) {
            quarantinedFile = quarantineFileAction(modelFile)
        }

        // Clear in-progress flag and saved paths to prevent restart crash loop
        storage.remove(KEY_LOAD_IN_PROGRESS)
        storage.remove(KEY_SAVED_MODEL_PATH)
        storage.remove(KEY_SAVED_MODEL_NAME)

        val reason = "Model crashed natively during previous load. Quarantined file to prevent boot crash loop."
        return QuarantineResult(
            originalPath = inProgressPath,
            quarantinedFile = quarantinedFile,
            reason = reason
        )
    }

    /**
     * Mark that a load is starting. Written BEFORE touching the native engine.
     */
    fun markLoadStarted(modelPath: String) {
        storage.putString(KEY_LOAD_IN_PROGRESS, modelPath)
    }

    /**
     * Mark that a load succeeded. Clears the in-progress flag and ONLY now
     * saves the model path for future use.
     */
    fun markLoadSuccess(modelPath: String, modelName: String, onDataStoreSave: (() -> Unit)? = null) {
        storage.remove(KEY_LOAD_IN_PROGRESS)
        storage.putString(KEY_SAVED_MODEL_PATH, modelPath)
        storage.putString(KEY_SAVED_MODEL_NAME, modelName)
        onDataStoreSave?.invoke()
    }

    /**
     * Mark that a load failed gracefully. Clears in-progress flag without saving path.
     */
    fun markLoadFailed() {
        storage.remove(KEY_LOAD_IN_PROGRESS)
    }

    fun getSavedModelPath(): String? = storage.getString(KEY_SAVED_MODEL_PATH)
    fun getSavedModelName(): String? = storage.getString(KEY_SAVED_MODEL_NAME)
    fun getLoadInProgress(): String? = storage.getString(KEY_LOAD_IN_PROGRESS)
    fun getCleanupVersion(): Int = storage.getInt(KEY_CLEANUP_VERSION, 0)
}

/**
 * Android Context-backed CrashLoopGuard singleton using [CrashLoopStateMachine].
 */
object CrashLoopGuard {

    private const val TAG = "CrashLoopGuard"
    const val PREFS_NAME = "campusfold_ai_prefs"

    private fun getStorage(context: Context): CrashGuardStorage {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return object : CrashGuardStorage {
            override fun getString(key: String): String? = prefs.getString(key, null)
            override fun putString(key: String, value: String?) {
                prefs.edit().apply {
                    if (value == null) remove(key) else putString(key, value)
                }.apply()
            }
            override fun getInt(key: String, defValue: Int): Int = prefs.getInt(key, defValue)
            override fun putInt(key: String, value: Int) {
                prefs.edit().putInt(key, value).apply()
            }
            override fun remove(key: String) {
                prefs.edit().remove(key).apply()
            }
        }
    }

    private fun getStateMachine(context: Context): CrashLoopStateMachine {
        return CrashLoopStateMachine(
            storage = getStorage(context),
            quarantineFileAction = { file ->
                val quarantineDir = File(context.filesDir, "quarantined_models").apply { mkdirs() }
                val destFile = File(quarantineDir, "${file.name}.${System.currentTimeMillis()}.quarantined")
                if (file.renameTo(destFile)) destFile else null
            }
        )
    }

    suspend fun performOneTimeCleanupIfNeeded(
        context: Context,
        appPreferences: AppPreferences
    ): Boolean {
        val sm = getStateMachine(context)
        val cleaned = sm.performOneTimeCleanup {
            kotlinx.coroutines.runBlocking {
                appPreferences.setSavedAiModelPath(null)
            }
        }
        if (cleaned) {
            Log.i(TAG, "One-time cleanup executed: cleared saved model paths (gated on v${CrashLoopStateMachine.TARGET_CLEANUP_VERSION})")
            AiDiagnostics.recordEvent(
                type = "CLEANUP",
                message = "One-time cleanup executed: cleared saved model paths (gated on v${CrashLoopStateMachine.TARGET_CLEANUP_VERSION})",
                details = "Room database untouched"
            )
        }
        return cleaned
    }

    fun checkCrashLoopAndQuarantine(context: Context): QuarantineResult? {
        val sm = getStateMachine(context)
        val result = sm.checkCrashLoopAndQuarantine()
        if (result != null) {
            AiDiagnostics.recordEvent(
                type = "CRASH_GUARD",
                message = "Model load crash-loop prevented: quarantined file ${result.originalPath}",
                details = result.reason
            )
        }
        return result
    }

    fun markLoadStarted(context: Context, modelPath: String) {
        val sm = getStateMachine(context)
        sm.markLoadStarted(modelPath)
        AiDiagnostics.recordEvent(
            type = "LOAD_START",
            message = "Model load initiated: ${File(modelPath).name}",
            details = modelPath
        )
    }

    suspend fun markLoadSuccess(
        context: Context,
        appPreferences: AppPreferences,
        modelPath: String,
        modelName: String
    ) {
        val sm = getStateMachine(context)
        sm.markLoadSuccess(modelPath, modelName) {
            kotlinx.coroutines.runBlocking {
                appPreferences.setSavedAiModelPath(modelPath)
            }
        }
        AiDiagnostics.recordEvent(
            type = "LOAD_OK",
            message = "Model loaded successfully and verified ready: $modelName",
            details = modelPath
        )
    }

    fun markLoadFailed(context: Context, modelPath: String, errorReason: String) {
        val sm = getStateMachine(context)
        sm.markLoadFailed()
        AiDiagnostics.recordEvent(
            type = "LOAD_FAIL",
            message = "Model load failed: ${File(modelPath).name}",
            details = errorReason
        )
    }
}
