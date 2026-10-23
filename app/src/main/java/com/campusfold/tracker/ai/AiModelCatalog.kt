package com.campusfold.tracker.ai

import android.app.ActivityManager
import android.content.Context

data class AiModelInfo(
    val id: String,
    val displayName: String,
    val repo: String,
    val fileName: String,
    val downloadUrl: String,
    val hfWebUrl: String,
    val expectedSizeBytes: Long,
    val pinnedSha256: String,
    val isGated: Boolean,
    val hasLitertlmOnMain: Boolean,
    val minTotalRamBytes: Long,
    val minAvailRamBytes: Long,
    val preferenceRank: Int,
    val description: String
)

object AiModelCatalog {

    /**
     * Preference 1: Qwen 2.5 (1.5B)
     * Best at structured JSON extraction and campus finance reasoning.
     * Verified .litertlm on litert-community main branch.
     * Anonymous direct download supported.
     */
    val QWEN_1_5B = AiModelInfo(
        id = "qwen2.5_1.5b",
        displayName = "Qwen 2.5 (1.5B Instruct)",
        repo = "litert-community/Qwen2.5-1.5B-Instruct",
        fileName = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
        downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
        hfWebUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct",
        expectedSizeBytes = 1_597_922_856L,
        pinnedSha256 = "c4a9b7289365ab80fe599b844b77ad253eb3c53fb0e5d963d699146a6f01255e",
        isGated = false,
        hasLitertlmOnMain = true,
        minTotalRamBytes = 6L * 1024L * 1024L * 1024L, // 6 GB
        minAvailRamBytes = 2_400L * 1024L * 1024L,     // 2.4 GB available
        preferenceRank = 1,
        description = "Highest accuracy for JSON parsing & student spend coaching. Requires ~2.5 GB free RAM."
    )

    /**
     * Preference 2: Qwen 2.5 (0.5B)
     * Checked against litert-community/Qwen2.5-0.5B-Instruct on main branch.
     * NOTE: As of Hugging Face query, this repo on main branch only provides .task and .tflite files,
     * no official .litertlm file exists on main. Thus flagged hasLitertlmOnMain = false.
     */
    val QWEN_0_5B = AiModelInfo(
        id = "qwen2.5_0.5b",
        displayName = "Qwen 2.5 (0.5B Instruct)",
        repo = "litert-community/Qwen2.5-0.5B-Instruct",
        fileName = "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        hfWebUrl = "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct",
        expectedSizeBytes = 547_000_000L,
        pinnedSha256 = "",
        isGated = false,
        hasLitertlmOnMain = false, // No .litertlm on main branch
        minTotalRamBytes = 3L * 1024L * 1024L * 1024L,
        minAvailRamBytes = 1_000L * 1024L * 1024L,
        preferenceRank = 2,
        description = "Ultra-lightweight 0.5B model. (Only .task available on main, no .litertlm)."
    )

    /**
     * Preference 3: Gemma 3 (1B IT)
     * Verified .litertlm on litert-community main branch:
     * Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm (584,417,280 bytes).
     * Gated model requiring Hugging Face agreement and web login.
     * Anonymous direct download is rejected with HTTP 401/403.
     * Uses "Import model file" browser flow.
     */
    val GEMMA3_1B = AiModelInfo(
        id = "gemma3_1b",
        displayName = "Gemma 3 (1B IT)",
        repo = "litert-community/Gemma3-1B-IT",
        fileName = "Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm",
        downloadUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT",
        hfWebUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT",
        expectedSizeBytes = 584_417_280L,
        pinnedSha256 = "", // Gated on HF, verified upon manual import
        isGated = true,
        hasLitertlmOnMain = true,
        minTotalRamBytes = 4L * 1024L * 1024L * 1024L, // 4 GB
        minAvailRamBytes = 1_400L * 1024L * 1024L,     // 1.4 GB available
        preferenceRank = 3,
        description = "Compact 1B instruction-tuned model. Gated on HF (download via browser and import)."
    )

    val ALL_MODELS = listOf(QWEN_1_5B, QWEN_0_5B, GEMMA3_1B)

    fun getMemoryInfo(context: Context): ActivityManager.MemoryInfo {
        val memInfo = ActivityManager.MemoryInfo()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        am?.getMemoryInfo(memInfo)
        return memInfo
    }

    /**
     * Checks if the device has enough available and total RAM to safely run a model.
     */
    fun canDeviceRun(memInfo: ActivityManager.MemoryInfo, model: AiModelInfo): Boolean {
        if (memInfo.lowMemory) return false
        // Allow running if total RAM is sufficient or available RAM has enough headroom
        return memInfo.totalMem >= model.minTotalRamBytes && memInfo.availMem >= model.minAvailRamBytes
    }

    /**
     * Returns models that the device can run, filtering out options the device cannot run.
     * Models without a usable .litertlm on main are also excluded from direct LiteRT downloads.
     */
    fun getSupportedModelsForDevice(context: Context): List<AiModelInfo> {
        val mem = getMemoryInfo(context)
        return ALL_MODELS.filter { model ->
            // Must have .litertlm on main and device must have capacity
            model.hasLitertlmOnMain && canDeviceRun(mem, model)
        }
    }

    /**
     * Recommends the best model for the current device based on RAM.
     * If device has >= 6GB RAM and >= 2.4GB available: Qwen2.5-1.5B (rank 1 default).
     * If device has 4GB-6GB RAM: Gemma3-1B.
     */
    fun recommendModel(context: Context): AiModelInfo? {
        val mem = getMemoryInfo(context)
        if (canDeviceRun(mem, QWEN_1_5B)) {
            return QWEN_1_5B
        }
        if (canDeviceRun(mem, GEMMA3_1B)) {
            return GEMMA3_1B
        }
        return null
    }

    /**
     * Validates RAM before model load. Returns null if OK, or a descriptive error message if too low.
     */
    fun checkRamForLoad(context: Context, model: AiModelInfo): String? {
        val mem = getMemoryInfo(context)
        val availMb = mem.availMem / (1024 * 1024)
        val reqMb = model.minAvailRamBytes / (1024 * 1024)
        val totalMb = mem.totalMem / (1024 * 1024)

        if (mem.lowMemory) {
            return "Device is currently in low-memory state ($availMb MB available of $totalMb MB). Refusing to load ${model.displayName} to prevent out-of-memory crashes."
        }
        if (mem.availMem < model.minAvailRamBytes) {
            return "Insufficient available RAM for ${model.displayName}: $availMb MB available, but $reqMb MB is required to run inference safely."
        }
        return null
    }
}
