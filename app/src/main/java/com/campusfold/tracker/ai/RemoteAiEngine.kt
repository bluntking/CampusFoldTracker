package com.campusfold.tracker.ai

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.util.Log
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

class RemoteAiEngine(
    private val context: Context
) : LocalAiEngine {

    companion object {
        private const val TAG = "RemoteAiEngine"
    }

    private val _state = MutableStateFlow<AiState>(AiState.NotLoaded)
    override val state: StateFlow<AiState> = _state.asStateFlow()

    // Single queue lock: ensures inference requests are queued sequentially and never run concurrently
    private val executionQueueMutex = Mutex()

    private var serviceMessenger: Messenger? = null
    private var isBound = false
    private val clientMessenger = Messenger(ClientHandler(Looper.getMainLooper()))

    private var lastRequestedPath: String? = null
    private var lastRequestedName: String? = null

    private val pendingCompletions = ConcurrentHashMap<String, CompletableDeferred<String>>()
    private var loadModelContinuation: CancellableContinuation<Boolean>? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.i(TAG, "Connected to isolated AI service")
            serviceMessenger = Messenger(service)
            isBound = true

            // If we were waiting to load a model upon connecting
            val path = lastRequestedPath
            val modelName = lastRequestedName
            if (path != null && modelName != null && _state.value is AiState.Loading) {
                dispatchLoadModelMessage(path, modelName)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.e(TAG, "Isolated AI service disconnected! (Native crash or killed by OS)")
            serviceMessenger = null
            isBound = false
            _state.value = AiState.Stopped("AI engine stopped")
            AiDiagnostics.recordEvent(
                type = "ENGINE_DIED",
                message = "AI service process terminated unexpectedly (native crash isolated).",
                details = "Main application state preserved. Showing Retry."
            )
            failAllPending("AI engine stopped unexpectedly")
        }

        override fun onBindingDied(name: ComponentName?) {
            Log.e(TAG, "Isolated AI service binding died!")
            serviceMessenger = null
            isBound = false
            _state.value = AiState.Stopped("AI engine stopped")
            failAllPending("AI engine binding died")
        }
    }

    fun bindService() {
        if (!isBound) {
            val intent = Intent(context, AiService::class.java)
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private inner class ClientHandler(looper: Looper) : Handler(looper) {
        override fun handleMessage(msg: Message) {
            val data = msg.data ?: return
            when (msg.what) {
                AiService.MSG_STATUS_UPDATE -> {
                    val statusCode = data.getInt(AiService.KEY_STATUS_CODE, AiService.STATUS_NOT_LOADED)
                    val statusMsg = data.getString(AiService.KEY_STATUS_MESSAGE) ?: ""
                    val modelName = data.getString(AiService.KEY_MODEL_NAME) ?: "Model"
                    val modelPath = data.getString(AiService.KEY_MODEL_PATH) ?: ""

                    when (statusCode) {
                        AiService.STATUS_NOT_LOADED -> {
                            _state.value = AiState.NotLoaded
                            loadModelContinuation?.resume(false)
                            loadModelContinuation = null
                        }
                        AiService.STATUS_LOADING -> {
                            _state.value = AiState.Loading(statusMsg)
                        }
                        AiService.STATUS_READY -> {
                            _state.value = AiState.Ready(modelName, modelPath)
                            loadModelContinuation?.resume(true)
                            loadModelContinuation = null
                        }
                        AiService.STATUS_ERROR -> {
                            _state.value = AiState.Error(statusMsg)
                            loadModelContinuation?.resume(false)
                            loadModelContinuation = null
                        }
                    }
                }
                AiService.MSG_COMPLETE_SUCCESS -> {
                    val reqId = data.getString(AiService.KEY_REQUEST_ID) ?: ""
                    val result = data.getString(AiService.KEY_RESULT_TEXT) ?: ""
                    pendingCompletions.remove(reqId)?.complete(result)
                }
                AiService.MSG_COMPLETE_ERROR -> {
                    val reqId = data.getString(AiService.KEY_REQUEST_ID) ?: ""
                    val err = data.getString(AiService.KEY_ERROR_MESSAGE) ?: "Inference error"
                    pendingCompletions.remove(reqId)?.completeExceptionally(Exception(err))
                }
            }
        }
    }

    /**
     * Loads a model in the isolated :ai process.
     */
    suspend fun loadModel(modelPath: String, modelName: String): Result<Unit> = withContext(Dispatchers.IO) {
        lastRequestedPath = modelPath
        lastRequestedName = modelName
        _state.value = AiState.Loading("Initializing LiteRT-LM in isolated process...", 10)

        bindService()

        suspendCancellableCoroutine { cont ->
            loadModelContinuation = cont
            if (serviceMessenger != null) {
                dispatchLoadModelMessage(modelPath, modelName)
            }
        }.let { success ->
            if (success) Result.success(Unit)
            else Result.failure(Exception((_state.value as? AiState.Error)?.message ?: "Failed to load model"))
        }
    }

    private fun dispatchLoadModelMessage(modelPath: String, modelName: String) {
        val messenger = serviceMessenger ?: return
        try {
            val msg = Message.obtain(null, AiService.MSG_LOAD_MODEL)
            msg.replyTo = clientMessenger
            msg.data = Bundle().apply {
                putString(AiService.KEY_MODEL_PATH, modelPath)
                putString(AiService.KEY_MODEL_NAME, modelName)
            }
            messenger.send(msg)
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to send load model message to AiService", e)
            _state.value = AiState.Error("Remote communication error: ${e.message}")
        }
    }

    /**
     * Executes prompt completion through a single queue, supporting cancellation and timeout.
     * Strictly runs off the main thread (Dispatchers.IO).
     */
    override suspend fun complete(
        prompt: String,
        maxTokens: Int,
        timeoutMs: Long
    ): Result<String> = withContext(Dispatchers.IO) {
        val currentState = _state.value
        if (currentState !is AiState.Ready) {
            return@withContext Result.failure(IllegalStateException("AI Engine not ready (current state: $currentState)"))
        }

        val messenger = serviceMessenger ?: return@withContext Result.failure(
            IllegalStateException("AI Engine service not connected")
        )

        // Queue requests sequentially
        executionQueueMutex.withLock {
            val reqId = UUID.randomUUID().toString()
            val deferred = CompletableDeferred<String>()
            pendingCompletions[reqId] = deferred

            try {
                val msg = Message.obtain(null, AiService.MSG_COMPLETE)
                msg.replyTo = clientMessenger
                msg.data = Bundle().apply {
                    putString(AiService.KEY_REQUEST_ID, reqId)
                    putString(AiService.KEY_PROMPT, prompt)
                    putInt(AiService.KEY_MAX_TOKENS, maxTokens)
                }
                messenger.send(msg)

                // Enforce timeout and support cancellation
                val result = withTimeout(timeoutMs) {
                    deferred.await()
                }
                Result.success(result)

            } catch (e: Throwable) {
                // If cancelled or timed out, instruct service to abort ongoing generation
                cancelRemoteRequest(reqId)
                pendingCompletions.remove(reqId)
                Result.failure(e)
            }
        }
    }

    private fun cancelRemoteRequest(reqId: String) {
        val messenger = serviceMessenger ?: return
        try {
            val msg = Message.obtain(null, AiService.MSG_CANCEL)
            msg.data = Bundle().apply {
                putString(AiService.KEY_REQUEST_ID, reqId)
            }
            messenger.send(msg)
        } catch (e: RemoteException) {
            Log.w(TAG, "Failed to send cancel message for $reqId", e)
        }
    }

    /**
     * Retry loading the last requested model after an engine stop or crash.
     */
    suspend fun retry(): Result<Unit> {
        val path = lastRequestedPath
        val name = lastRequestedName
        if (path == null || name == null) {
            return Result.failure(IllegalStateException("No previous model to retry"))
        }
        bindService()
        return loadModel(path, name)
    }

    fun reset() {
        val messenger = serviceMessenger
        if (messenger != null) {
            try {
                val msg = Message.obtain(null, AiService.MSG_RESET)
                msg.replyTo = clientMessenger
                messenger.send(msg)
            } catch (e: RemoteException) {
                Log.w(TAG, "Failed to send reset message", e)
            }
        }
        _state.value = AiState.NotLoaded
    }

    private fun failAllPending(reason: String) {
        pendingCompletions.forEach { (_, deferred) ->
            deferred.completeExceptionally(Exception(reason))
        }
        pendingCompletions.clear()
        loadModelContinuation?.resume(false)
        loadModelContinuation = null
    }

    fun unbind() {
        if (isBound) {
            try {
                context.unbindService(serviceConnection)
            } catch (ignored: Exception) {}
            isBound = false
        }
    }
}
