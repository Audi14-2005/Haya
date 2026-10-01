package com.monicauditya.june.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monicauditya.june.data.AppDB
import com.monicauditya.june.data.Chat
import com.monicauditya.june.domain.ChatUseCase
import com.monicauditya.june.domain.GenerateResult
import com.monicauditya.june.domain.LoadModelResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

private const val LOG_TAG = "JUNE"

@KoinViewModel
class ChatViewModel(
    private val chatUseCase: ChatUseCase,
    private val appDB: AppDB
) : ViewModel() {

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private var generationJob: Job? = null
    private var modelLoadingJob: Job? = null
    private var historyJob: Job? = null
    private var messagesJob: Job? = null
    private var activeChat: Chat? = null

    private fun isTransientLoadFailure(message: String): Boolean {
        val lower = message.lowercase()
        return "too heavy to load safely" in lower ||
            "right now" in lower ||
            "please wait for the current response" in lower
    }

    init {
        initializeChats()
        loadExistingModelIfAvailable()
    }

    private fun initializeChats() {
        val defaultChat = appDB.loadDefaultChat()
        activeChat = defaultChat
        observeChatHistory()
        observeChatMessages(defaultChat.id)
    }

    private fun observeChatHistory() {
        historyJob?.cancel()
        historyJob = viewModelScope.launch(Dispatchers.IO) {
            appDB.getChats().collectLatest { chats ->
                val activeId = _state.value.activeChatId
                _state.update { current ->
                    current.copy(
                        histories = chats.map { chat ->
                            ChatHistoryItem(
                                id = chat.id,
                                title = deriveChatTitle(chat),
                                isActive = chat.id == (activeId ?: activeChat?.id)
                            )
                        }
                    )
                }
            }
        }
    }

    private fun observeChatMessages(chatId: Long) {
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch(Dispatchers.IO) {
            val chat = appDB.getChat(chatId)
            activeChat = chat.copy(dateUsed = java.util.Date()).also { appDB.updateChat(it) }
            appDB.getMessages(chatId).collectLatest { dbMessages ->
                _state.update { current ->
                    current.copy(
                        activeChatId = chatId,
                        messages = dbMessages.map { message ->
                            Message(
                                id = message.id.toString(),
                                text = message.message,
                                isUser = message.isUserMessage
                            )
                        }
                    )
                }
            }
        }
    }

    private fun deriveChatTitle(chat: Chat): String {
        val trimmed = chat.name.trim()
        return if (trimmed.isBlank()) "Untitled" else trimmed
    }

    private fun maybeRenameUntitledChat(userText: String) {
        val chat = activeChat ?: return
        if (!chat.name.startsWith("Untitled", ignoreCase = true)) return
        val nextTitle = userText.trim()
            .replace(Regex("""\s+"""), " ")
            .take(26)
            .trim()
            .ifBlank { return }
        activeChat = chat.copy(name = nextTitle, dateUsed = java.util.Date()).also { appDB.updateChat(it) }
    }

    fun createNewChat() {
        if (_state.value.isGenerating) {
            stopGeneration()
        }
        val chatNumber = appDB.getChatsCount() + 1
        val newChat = appDB.addChat("Untitled $chatNumber")
        observeChatMessages(newChat.id)
    }

    fun openChat(chatId: Long) {
        if (_state.value.isGenerating) {
            stopGeneration()
        }
        observeChatMessages(chatId)
    }

    fun deleteChat(chatId: Long) {
        if (_state.value.isGenerating && _state.value.activeChatId == chatId) {
            stopGeneration()
        }
        val existing = _state.value.histories
        val remaining = existing.filterNot { it.id == chatId }
        val deletedWasActive = _state.value.activeChatId == chatId
        val deletedChat = runCatching { appDB.getChat(chatId) }.getOrNull()
        if (deletedChat != null) {
            appDB.deleteMessages(chatId)
            appDB.deleteChat(deletedChat)
        }
        when {
            remaining.isEmpty() -> {
                val chatNumber = appDB.getChatsCount() + 1
                val newChat = appDB.addChat("Untitled $chatNumber")
                observeChatMessages(newChat.id)
            }
            deletedWasActive -> {
                observeChatMessages(remaining.first().id)
            }
        }
    }

    private fun loadExistingModelIfAvailable() {
        if (chatUseCase.isModelLoaded()) {
            Log.d(LOG_TAG, "Model already loaded before chat startup")
            clearError()
            updateModelState(ModelState.Ready, "Model ready.")
            return
        }
        val existingModel = File(chatUseCase.getModelPath())
        if (!existingModel.exists()) {
            Log.d(LOG_TAG, "No existing internal model found at startup")
            return
        }
        modelLoadingJob = viewModelScope.launch(Dispatchers.IO) {
            updateModelState(ModelState.Loading, "Loading previously selected model...")
            when (val result = chatUseCase.loadModel()) {
                is LoadModelResult.Success -> {
                    Log.d(LOG_TAG, "Startup model load completed")
                    updateModelState(ModelState.Ready, "Model ready.")
                }
                is LoadModelResult.Failure -> {
                    Log.e(LOG_TAG, "Startup model load failed: ${result.message}")
                    if (isTransientLoadFailure(result.message)) {
                        updateModelState(ModelState.Idle, "Ask anything...")
                        clearError()
                    } else {
                        updateModelState(ModelState.Error, result.message, result.message)
                    }
                }
            }
        }
    }

    fun importModel(context: Context, uri: Uri) {
        if (_state.value.isGenerating) {
            stopGeneration()
        }

        modelLoadingJob?.cancel()
        modelLoadingJob = viewModelScope.launch(Dispatchers.IO) {
            updateModelState(ModelState.Loading, "Copying selected model...")
            try {
                val outputFile = File(context.filesDir, "model.gguf")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(outputFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: throw IllegalStateException("Unable to open the selected model file.")

                Log.d(LOG_TAG, "Model copied to ${outputFile.absolutePath}")
                chatUseCase.setModelPath(outputFile.absolutePath)

                updateModelState(ModelState.Loading, "Loading model...")
                when (val result = chatUseCase.loadModel(forceReload = true)) {
                    is LoadModelResult.Success -> {
                        clearError()
                        updateModelState(ModelState.Ready, "Model ready.")
                    }
                    is LoadModelResult.Failure -> {
                        updateModelState(ModelState.Error, result.message, result.message)
                    }
                }
            } catch (e: CancellationException) {
                Log.d(LOG_TAG, "Model import cancelled")
                throw e
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Error importing model: ${e.message}", e)
                updateModelState(
                    ModelState.Error,
                    "The selected model could not be imported.",
                    e.message ?: "The selected model could not be imported."
                )
            }
        }
    }

    fun switchModel(modelPath: String, onFinished: (Boolean) -> Unit = {}) {
        if (_state.value.isGenerating) {
            stopGeneration()
        }

        val previousPath = chatUseCase.getModelPath()
        modelLoadingJob?.cancel()
        modelLoadingJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                chatUseCase.setModelPath(modelPath)
                updateModelState(ModelState.Loading, "")
                when (val result = chatUseCase.loadModel(forceReload = true)) {
                    is LoadModelResult.Success -> {
                        clearError()
                        updateModelState(ModelState.Ready, "Model ready.")
                        onFinished(true)
                    }
                    is LoadModelResult.Failure -> {
                        if (isTransientLoadFailure(result.message)) {
                            chatUseCase.setModelPath(previousPath)
                            clearError()
                            updateModelState(
                                if (chatUseCase.isModelLoaded()) ModelState.Ready else ModelState.Idle,
                                if (chatUseCase.isModelLoaded()) "Model ready." else "Ask anything..."
                            )
                            onFinished(false)
                        } else {
                            updateModelState(ModelState.Error, result.message, result.message)
                            onFinished(false)
                        }
                    }
                }
            } catch (e: CancellationException) {
                Log.d(LOG_TAG, "Model switch cancelled")
                throw e
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Error switching model: ${e.message}", e)
                updateModelState(
                    ModelState.Error,
                    "The selected model could not be loaded.",
                    e.message ?: "The selected model could not be loaded."
                )
                onFinished(false)
            }
        }
    }

    fun sendMessage(text: String) {
        val snapshot = _state.value
        if (text.isBlank() || snapshot.isGenerating || snapshot.modelState != ModelState.Ready) {
            return
        }
        val chatId = snapshot.activeChatId ?: activeChat?.id

        if (chatId != null) {
            appDB.addUserMessage(chatId, text)
            maybeRenameUntitledChat(text)
        }

        _state.update { currentState ->
            currentState.copy(
                isGenerating = true,
                currentStreamingMessage = "",
                errorMessage = null
            )
        }

        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            Log.d(LOG_TAG, "Collecting inference stream")
            try {
                chatUseCase.generate(text).collect { result ->
                    when (result) {
                        is GenerateResult.Token -> {
                            _state.update { currentState ->
                                currentState.copy(
                                    currentStreamingMessage = currentState.currentStreamingMessage + result.value
                                )
                            }
                        }
                        is GenerateResult.Failure -> {
                            finalizeMessage(result.message, wasCancelled = false)
                        }
                        GenerateResult.Cancelled -> {
                            finalizeMessage("Generation stopped.", wasCancelled = true)
                        }
                    }
                }

                finalizeMessage(null, wasCancelled = false)
            } catch (e: CancellationException) {
                Log.d(LOG_TAG, "Generation coroutine cancelled")
                finalizeMessage("Generation stopped.", wasCancelled = true)
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Unexpected generation failure: ${e.message}", e)
                finalizeMessage("Something went wrong while generating the response.", wasCancelled = false)
            }
        }
    }

    fun stopGeneration() {
        if (!_state.value.isGenerating) return

        Log.d(LOG_TAG, "Stopping active generation")
        _state.update { currentState -> currentState.copy(errorMessage = null) }
        chatUseCase.stopGeneration()
    }

    private fun finalizeMessage(fallbackMessage: String?, wasCancelled: Boolean) {
        _state.update { currentState ->
            if (!currentState.isGenerating) return@update currentState

            val streamContent = currentState.currentStreamingMessage.trim()
            val finalText = when {
                streamContent.isNotEmpty() -> streamContent
                !fallbackMessage.isNullOrBlank() -> fallbackMessage
                else -> "No response generated."
            }
            val chatId = currentState.activeChatId ?: activeChat?.id
            val shouldPersistAssistantMessage =
                chatId != null && finalText.isNotBlank() && (!wasCancelled || streamContent.isNotBlank())

            if (shouldPersistAssistantMessage) {
                appDB.addAssistantMessage(chatId, finalText)
            }

            val newMessages = if (wasCancelled && streamContent.isBlank()) {
                currentState.messages
            } else {
                currentState.messages + Message(
                    id = UUID.randomUUID().toString(),
                    text = finalText,
                    isUser = false
                )
            }

            currentState.copy(
                messages = newMessages,
                isGenerating = false,
                currentStreamingMessage = "",
                errorMessage = when {
                    wasCancelled -> null
                    fallbackMessage != null && streamContent.isBlank() -> fallbackMessage
                    else -> currentState.errorMessage
                }
            )
        }
    }

    private fun updateModelState(
        modelState: ModelState,
        statusMessage: String,
        errorMessage: String? = null
    ) {
        _state.update { currentState ->
            currentState.copy(
                modelState = modelState,
                modelStatusMessage = statusMessage,
                errorMessage = errorMessage,
                isGenerating = if (modelState == ModelState.Loading) false else currentState.isGenerating,
                currentStreamingMessage = if (modelState == ModelState.Loading) "" else currentState.currentStreamingMessage
            )
        }
    }

    private fun clearError() {
        _state.update { currentState -> currentState.copy(errorMessage = null) }
    }

    override fun onCleared() {
        stopGeneration()
        modelLoadingJob?.cancel()
        chatUseCase.unloadModel()
        super.onCleared()
    }
}
