

package com.monicauditya.redhat1406

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GGUFReader {
    companion object {
        init {
            System.loadLibrary("ggufreader")
        }
    }

    private var nativeHandle: Long = 0L

    suspend fun load(modelPath: String) =
        withContext(Dispatchers.IO) { nativeHandle = getGGUFContextNativeHandle(modelPath) }

    fun getContextSize(): Long? {
        assert(nativeHandle != 0L) { "Use GGUFReader.load() to initialize the reader" }
        val contextSize = getContextSize(nativeHandle)
        return if (contextSize == -1L) {
            null
        } else {
            contextSize
        }
    }

    fun getChatTemplate(): String? {
        assert(nativeHandle != 0L) { "Use GGUFReader.load() to initialize the reader" }
        val chatTemplate = getChatTemplate(nativeHandle)
        return chatTemplate.ifEmpty { null }
    }

    fun close() {
        if (nativeHandle != 0L) {
            close(nativeHandle)
            nativeHandle = 0L
        }
    }

    /** Returns the native handle (pointer to gguf_context created on the native side) */
    private external fun getGGUFContextNativeHandle(modelPath: String): Long

    /** Read the context size (in no. of tokens) from the GGUF file, given the native handle */
    private external fun getContextSize(nativeHandle: Long): Long

    /** Read the chat template from the GGUF file, given the native handle */
    private external fun getChatTemplate(nativeHandle: Long): String

    /** Releases the native gguf_context handle */
    private external fun close(nativeHandle: Long)
}
