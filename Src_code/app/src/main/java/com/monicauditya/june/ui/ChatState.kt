package com.monicauditya.june.ui

data class Message(
    val id: String,
    val text: String,
    val isUser: Boolean
)

data class ChatHistoryItem(
    val id: Long,
    val title: String,
    val isActive: Boolean = false
)

enum class ModelState {
    Idle,
    Loading,
    Ready,
    Error
}

data class ChatState(
    val messages: List<Message> = emptyList(),
    val histories: List<ChatHistoryItem> = emptyList(),
    val activeChatId: Long? = null,
    val isGenerating: Boolean = false,
    val currentStreamingMessage: String = "",
    val modelState: ModelState = ModelState.Idle,
    val modelStatusMessage: String = "Select a GGUF model to begin.",
    val errorMessage: String? = null,
)

val ChatState.isThinking: Boolean
    get() = isGenerating && currentStreamingMessage.isEmpty()
