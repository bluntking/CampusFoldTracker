package com.campusfold.tracker

import android.app.ActivityManager
import com.campusfold.tracker.ai.AiModelCatalog
import org.junit.Assert.*
import org.junit.Test

class AiModelCatalogTest {

    @Test
    fun testModelCatalogOrderOfPreference() {
        val models = AiModelCatalog.ALL_MODELS.sortedBy { it.preferenceRank }
        assertEquals("Qwen 2.5 1.5B must be top preference (rank 1)", "qwen2.5_1.5b", models[0].id)
        assertEquals("Qwen 2.5 0.5B must be second preference (rank 2)", "qwen2.5_0.5b", models[1].id)
        assertEquals("Gemma 3 1B must be third preference (rank 3)", "gemma3_1b", models[2].id)
    }

    @Test
    fun testQwen15BModelIntegrityConfig() {
        val qwen15 = AiModelCatalog.QWEN_1_5B
        assertTrue("Qwen 1.5B must have usable .litertlm on main branch", qwen15.hasLitertlmOnMain)
        assertFalse("Qwen 1.5B is non-gated and supports anonymous download", qwen15.isGated)
        assertEquals("c4a9b7289365ab80fe599b844b77ad253eb3c53fb0e5d963d699146a6f01255e", qwen15.pinnedSha256)
        assertEquals(1_597_922_856L, qwen15.expectedSizeBytes)
        assertTrue("Download URL must point to litert-community main branch .litertlm file",
            qwen15.downloadUrl.endsWith("Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm"))
    }

    @Test
    fun testQwen05BMainBranchLitertlmVerification() {
        val qwen05 = AiModelCatalog.QWEN_0_5B
        assertFalse("Qwen 0.5B does not have .litertlm file on main branch (only .task/.tflite)", qwen05.hasLitertlmOnMain)
    }

    @Test
    fun testGemma31BIsGated() {
        val gemma = AiModelCatalog.GEMMA3_1B
        assertTrue("Gemma 3 1B must be marked as gated (requires browser HF agreement flow)", gemma.isGated)
        assertTrue("Gemma 3 1B has .litertlm file on main branch", gemma.hasLitertlmOnMain)
    }

    @Test
    fun testRamValidation() {
        val mem = ActivityManager.MemoryInfo().apply {
            totalMem = 8L * 1024L * 1024L * 1024L // 8 GB
            availMem = 3L * 1024L * 1024L * 1024L // 3 GB
            lowMemory = false
        }

        assertTrue("8GB device with 3GB free should be able to run Qwen 1.5B",
            AiModelCatalog.canDeviceRun(mem, AiModelCatalog.QWEN_1_5B))
        assertTrue("8GB device with 3GB free should be able to run Gemma 1B",
            AiModelCatalog.canDeviceRun(mem, AiModelCatalog.GEMMA3_1B))

        val lowMem = ActivityManager.MemoryInfo().apply {
            totalMem = 4L * 1024L * 1024L * 1024L // 4 GB
            availMem = 1_500L * 1024L * 1024L     // 1.5 GB
            lowMemory = false
        }

        assertFalse("4GB device should NOT be recommended Qwen 1.5B",
            AiModelCatalog.canDeviceRun(lowMem, AiModelCatalog.QWEN_1_5B))
        assertTrue("4GB device with 1.5GB free can run Gemma 1B",
            AiModelCatalog.canDeviceRun(lowMem, AiModelCatalog.GEMMA3_1B))
    }
}
