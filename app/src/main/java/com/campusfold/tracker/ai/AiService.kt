package com.campusfold.tracker.ai

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Isolated AI Engine Service running in android:process=":ai".
 * If LiteRT-LM crashes natively (e.g. SIGSEGV, SIGABRT), only this process dies;
 * the main CampusFold app remains running and displays "AI engine stopped" with a Retry option.
 */
class AiService : Service() {

    companion object {
        private const val TAG = "AiService"

        // Incoming message codes
        const val MSG_LOAD_MODEL = 101
        const val MSG_COMPLETE = 102
        const val MSG_CANCEL = 103
        const val MSG_RESET = 104
        const val MSG_CHECK_STATUS = 105

        // Outgoing message codes
        const val MSG_STATUS_UPDATE = 201
        const val MSG_COMPLETE_SUCCESS = 202
        const val MSG_COMPLETE_ERROR = 203

        // Bundle Keys
        const val KEY_MODEL_PATH = "key_model_path"
        const val KEY_MODEL_NAME = "key_model_name"
        const val KEY_REQUEST_ID = "key_request_id"
        const val KEY_PROMPT = "key_prompt"
        const val KEY_MAX_TOKENS = "key_max_tokens"
        const val KEY_RESULT_TEXT = "key_result_text"
        const val KEY_ERROR_MESSAGE = "key_error_message"
        const val KEY_STATUS_CODE = "key_status_code"
        const val KEY_STATUS_MESSAGE = "key_status_message"

        // Status codes
        const val STATUS_NOT_LOADED = 0
        const val STATUS_LOADING = 1
        const val STATUS_READY = 2
        const val STATUS_ERROR = 3
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lifecycleMutex = Mutex()

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var currentModelPath: String? = null
    private var currentModelName: String? = null

    private val activeJobs = ConcurrentHashMap<String, Job>()
    private lateinit var incomingMessenger: Messenger

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "AiService created in process: ${android.os.Process.myPid()}")
        incomingMessenger = Messenger(IncomingHandler(Looper.getMainLooper()))
    }

    override fun onBind(intent: Intent?): IBinder? {
        Log.d(TAG, "AiService onBind")
        return incomingMessenger.binder
    }

    private inner class IncomingHandler(looper: Looper) : Handler(looper) {
        override fun handleMessage(msg: Message) {
            val replyTo = msg.replyTo
            when (msg.what) {
                MSG_LOAD_MODEL -> {
                    val data = msg.data
                    val modelPath = data?.getString(KEY_MODEL_PATH) ?: ""
                    val modelName = data?.getString(KEY_MODEL_NAME) ?: "Local Model"
                    handleLoadModel(modelPath, modelName, replyTo)
                }
                MSG_COMPLETE -> {
                    val data = msg.data
                    val reqId = data?.getString(KEY_REQUEST_ID) ?: ""
                    val prompt = data?.getString(KEY_PROMPT) ?: ""
                    val maxTokens = data?.getInt(KEY_MAX_TOKENS, 1024) ?: 1024
                    handleComplete(reqId, prompt, maxTokens, replyTo)
                }
                MSG_CANCEL -> {
                    val reqId = msg.data?.getString(KEY_REQUEST_ID) ?: ""
                    handleCancel(reqId)
                }
                MSG_RESET -> {
                    handleReset(replyTo)
                }
                MSG_CHECK_STATUS -> {
                    sendStatus(replyTo)
                }
                else -> super.handleMessage(msg)
            }
        }
    }

    private fun handleLoadModel(modelPath: String, modelName: String, replyTo: Messenger?) {
        serviceScope.launch {
            lifecycleMutex.withLock {
                try {
                    val file = File(modelPath)
                    if (!file.exists()) {
                        sendReply(replyTo, MSG_STATUS_UPDATE) {
                            putInt(KEY_STATUS_CODE, STATUS_ERROR)
                            putString(KEY_STATUS_MESSAGE, "Model file not found: $modelPath")
                        }
                        return@withLock
                    }

                    // If already loaded with the same model, report ready immediately
                    if (engine != null && currentModelPath == modelPath && conversation != null) {
                        Log.i(TAG, "Model already loaded: $modelName")
                        sendReply(replyTo, MSG_STATUS_UPDATE) {
                            putInt(KEY_STATUS_CODE, STATUS_READY)
                            putString(KEY_MODEL_NAME, modelName)
                            putString(KEY_MODEL_PATH, modelPath)
                        }
                        return@withLock
                    }

                    sendReply(replyTo, MSG_STATUS_UPDATE) {
                        putInt(KEY_STATUS_CODE, STATUS_LOADING)
                        putString(KEY_STATUS_MESSAGE, "Initializing LiteRT-LM engine...")
                    }

                    // Reset previous engine/conversation if any
                    resetLockedQuietly()

                    val engineConfig = EngineConfig(
                        modelPath = modelPath,
                        backend = Backend.CPU(),
                        cacheDir = cacheDir.path
                    )

                    Log.i(TAG, "Initializing LiteRT Engine for $modelName at $modelPath")
                    val newEngine = Engine(engineConfig)
                    newEngine.initialize()

                    val conversationConfig = ConversationConfig(
                        systemInstruction = Contents.of("You are CampusFold AI, an intelligent campus financial assistant."),
                        samplerConfig = SamplerConfig(
                            topK = 40,
                            topP = 0.95,
                            temperature = 0.7
                        )
                    )

                    conversation = newEngine.createConversation(conversationConfig)
                    engine = newEngine
                    currentModelPath = modelPath
                    currentModelName = modelName

                    Log.i(TAG, "LiteRT-LM engine initialized successfully for $modelName")

                    sendReply(replyTo, MSG_STATUS_UPDATE) {
                        putInt(KEY_STATUS_CODE, STATUS_READY)
                        putString(KEY_MODEL_NAME, modelName)
                        putString(KEY_MODEL_PATH, modelPath)
                    }

                } catch (e: Throwable) {
                    Log.e(TAG, "Failed to initialize LiteRT engine", e)
                    resetLockedQuietly()
                    sendReply(replyTo, MSG_STATUS_UPDATE) {
                        putInt(KEY_STATUS_CODE, STATUS_ERROR)
                        putString(KEY_STATUS_MESSAGE, "LiteRT load error: ${e.message}")
                    }
                }
            }
        }
    }

    private fun handleComplete(reqId: String, prompt: String, maxTokens: Int, replyTo: Messenger?) {
        val job = serviceScope.launch {
            try {
                val conv = conversation
                if (conv == null || engine == null) {
                    sendReply(replyTo, MSG_COMPLETE_ERROR) {
                        putString(KEY_REQUEST_ID, reqId)
                        putString(KEY_ERROR_MESSAGE, "AI Engine not initialized")
                    }
                    return@launch
                }

                Log.d(TAG, "Generating response for reqId: $reqId")
                val chunks = conv.sendMessageAsync(prompt).toList()
                val resultText = chunks.joinToString("") { m ->
                    m.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
                }

                sendReply(replyTo, MSG_COMPLETE_SUCCESS) {
                    putString(KEY_REQUEST_ID, reqId)
                    putString(KEY_RESULT_TEXT, resultText)
                }

            } catch (e: Throwable) {
                Log.e(TAG, "Generation failed for reqId: $reqId", e)
                sendReply(replyTo, MSG_COMPLETE_ERROR) {
                    putString(KEY_REQUEST_ID, reqId)
                    putString(KEY_ERROR_MESSAGE, e.message ?: "Inference failed")
                }
            } finally {
                activeJobs.remove(reqId)
            }
        }

        activeJobs[reqId] = job
    }

    private fun handleCancel(reqId: String) {
        activeJobs.remove(reqId)?.cancel()
        Log.i(TAG, "Cancelled request: $reqId")
    }

    private fun handleReset(replyTo: Messenger?) {
        serviceScope.launch {
            lifecycleMutex.withLock {
                resetLockedQuietly()
                sendStatus(replyTo)
            }
        }
    }

    private fun resetLockedQuietly() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        runCatching { conversation?.close() }
        conversation = null
        runCatching { engine?.close() }
        engine = null
        currentModelPath = null
        currentModelName = null
    }

    private fun sendStatus(replyTo: Messenger?) {
        sendReply(replyTo, MSG_STATUS_UPDATE) {
            when {
                engine != null && conversation != null -> {
                    putInt(KEY_STATUS_CODE, STATUS_READY)
                    putString(KEY_MODEL_NAME, currentModelName ?: "Model")
                    putString(KEY_MODEL_PATH, currentModelPath ?: "")
                }
                else -> {
                    putInt(KEY_STATUS_CODE, STATUS_NOT_LOADED)
                }
            }
        }
    }

    private fun sendReply(replyTo: Messenger?, what: Int, builder: Bundle.() -> Unit) {
        if (replyTo == null) return
        try {
            val msg = Message.obtain(null, what)
            val bundle = Bundle().apply(builder)
            msg.data = bundle
            replyTo.send(msg)
        } catch (e: RemoteException) {
            Log.w(TAG, "Failed to send reply to client", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "AiService onDestroy")
        serviceScope.launch {
            lifecycleMutex.withLock {
                resetLockedQuietly()
            }
        }
    }
}
