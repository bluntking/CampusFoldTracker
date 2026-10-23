package com.campusfold.tracker

import com.campusfold.tracker.ai.CrashGuardStorage
import com.campusfold.tracker.ai.CrashLoopStateMachine
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class FakeCrashGuardStorage : CrashGuardStorage {
    private val stringMap = mutableMapOf<String, String>()
    private val intMap = mutableMapOf<String, Int>()

    override fun getString(key: String): String? = stringMap[key]

    override fun putString(key: String, value: String?) {
        if (value == null) stringMap.remove(key) else stringMap[key] = value
    }

    override fun getInt(key: String, defValue: Int): Int = intMap[key] ?: defValue

    override fun putInt(key: String, value: Int) {
        intMap[key] = value
    }

    override fun remove(key: String) {
        stringMap.remove(key)
        intMap.remove(key)
    }

    fun clear() {
        stringMap.clear()
        intMap.clear()
    }
}

class CrashLoopGuardTest {

    private lateinit var storage: FakeCrashGuardStorage
    private lateinit var stateMachine: CrashLoopStateMachine
    private var quarantinedFiles = mutableListOf<File>()

    @Before
    fun setup() {
        storage = FakeCrashGuardStorage()
        quarantinedFiles.clear()
        stateMachine = CrashLoopStateMachine(
            storage = storage,
            quarantineFileAction = { file ->
                val qFile = File(file.parentFile ?: File("."), "${file.name}.quarantined")
                quarantinedFiles.add(qFile)
                qFile
            }
        )
    }

    @Test
    fun testCleanStateDoesNotTriggerQuarantine() {
        val result = stateMachine.checkCrashLoopAndQuarantine()
        assertNull("Clean state should not trigger quarantine", result)
    }

    @Test
    fun testCrashLoopDetectionAndQuarantine() {
        val modelPath = "/data/user/0/com.campusfold.tracker/files/llm_models/model.litertlm"

        // 1. Simulate starting a load
        stateMachine.markLoadStarted(modelPath)
        assertEquals(modelPath, stateMachine.getLoadInProgress())

        // 2. Simulate process crash natively (the app restarts without markLoadSuccess being called)
        // CrashLoopGuard runs on new app launch:
        val quarantineResult = stateMachine.checkCrashLoopAndQuarantine()

        assertNotNull("Crash loop must be detected when load_in_progress is set on launch", quarantineResult)
        assertEquals(modelPath, quarantineResult?.originalPath)
        assertNull("load_in_progress must be cleared after quarantine", stateMachine.getLoadInProgress())
        assertNull("Saved model path must be cleared to prevent recurring crashes", stateMachine.getSavedModelPath())

        // 3. Subsequent launch check should be clean
        assertNull(stateMachine.checkCrashLoopAndQuarantine())
    }

    @Test
    fun testSuccessfulLoadLifecycle() {
        val modelPath = "/data/user/0/com.campusfold.tracker/files/llm_models/qwen_1.5b.litertlm"
        val modelName = "Qwen 2.5 (1.5B Instruct)"
        var dataStoreUpdated = false

        stateMachine.markLoadStarted(modelPath)
        assertEquals(modelPath, stateMachine.getLoadInProgress())

        // Load succeeds
        stateMachine.markLoadSuccess(modelPath, modelName) {
            dataStoreUpdated = true
        }

        assertNull("load_in_progress must be cleared on success", stateMachine.getLoadInProgress())
        assertEquals("Saved model path must be stored only after success", modelPath, stateMachine.getSavedModelPath())
        assertEquals("Saved model name must be stored only after success", modelName, stateMachine.getSavedModelName())
        assertTrue("DataStore callback must be invoked on success", dataStoreUpdated)

        // Engine ready, next launch should not trigger quarantine
        assertNull(stateMachine.checkCrashLoopAndQuarantine())
    }

    @Test
    fun testFailedLoadClearsInProgressWithoutSaving() {
        val modelPath = "/data/user/0/com.campusfold.tracker/files/llm_models/broken.litertlm"

        stateMachine.markLoadStarted(modelPath)
        assertEquals(modelPath, stateMachine.getLoadInProgress())

        stateMachine.markLoadFailed()

        assertNull("load_in_progress must be cleared on failure", stateMachine.getLoadInProgress())
        assertNull("Saved path must NOT be saved when load fails", stateMachine.getSavedModelPath())
    }

    @Test
    fun testOneTimeCleanupGatedOnVersion() {
        // Setup legacy state with saved path from older build (cleanup version = 0)
        storage.putString(CrashLoopStateMachine.KEY_SAVED_MODEL_PATH, "/old/path/model.task")
        storage.putString(CrashLoopStateMachine.KEY_SAVED_MODEL_NAME, "Legacy Model")
        storage.putString(CrashLoopStateMachine.KEY_LOAD_IN_PROGRESS, "/old/path/model.task")
        assertEquals(0, stateMachine.getCleanupVersion())

        var dataStoreCleared = false
        val executedFirstTime = stateMachine.performOneTimeCleanup {
            dataStoreCleared = true
        }

        assertTrue("One-time cleanup should execute when version < target", executedFirstTime)
        assertTrue("DataStore clear callback must be invoked", dataStoreCleared)
        assertNull("Legacy saved model path must be cleared", stateMachine.getSavedModelPath())
        assertNull("Legacy saved model name must be cleared", stateMachine.getSavedModelName())
        assertNull("Legacy in-progress flag must be cleared", stateMachine.getLoadInProgress())
        assertEquals("Cleanup version must be updated to 1", 1, stateMachine.getCleanupVersion())

        // Second invocation should do nothing (gated on version)
        dataStoreCleared = false
        val executedSecondTime = stateMachine.performOneTimeCleanup {
            dataStoreCleared = true
        }

        assertFalse("One-time cleanup must not execute again when version == target", executedSecondTime)
        assertFalse("DataStore should not be cleared again", dataStoreCleared)
    }
}
