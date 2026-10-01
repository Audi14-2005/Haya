package com.monicauditya.june.domain

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import com.monicauditya.redhat1406.RedHat1406
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

private const val LOG_TAG = "JUNE"
private const val GGUF_HEADER = "GGUF"

sealed class ModelValidationResult {
    data class Success(val file: File) : ModelValidationResult()
    data class Failure(val message: String) : ModelValidationResult()
}

sealed class LoadModelResult {
    data class Success(val path: String) : LoadModelResult()
    data class Failure(val message: String) : LoadModelResult()
}

sealed class GenerateResult {
    data class Token(val value: String) : GenerateResult()
    data class Failure(val message: String) : GenerateResult()
    data object Cancelled : GenerateResult()
}

@Single
class ChatUseCase(
    context: Context,
    initialInferenceEngine: RedHat1406
) {
    private val appContext = context.applicationContext
    private val internalModelFile = File(context.filesDir, "model.gguf")
    private val isLoaded = AtomicBoolean(false)
    private val isGenerating = AtomicBoolean(false)
    private val stopRequested = AtomicBoolean(false)
    private val modelMutex = Mutex()
    private var inferenceEngine: RedHat1406 = initialInferenceEngine

    @Volatile
    private var loadedModelPath: String? = null

    @Volatile
    private var configuredModelPath: String = internalModelFile.absolutePath

    fun setModelPath(newPath: String) {
        configuredModelPath = newPath
        Log.d(LOG_TAG, "Configured model path updated to: $newPath")
    }

    fun isModelLoaded(): Boolean = isLoaded.get()

    fun isGenerationRunning(): Boolean = isGenerating.get()

    fun getModelPath(): String = configuredModelPath

    fun validateModel(path: String = configuredModelPath): ModelValidationResult {
        val file = File(path)
        if (!file.exists()) {
            return ModelValidationResult.Failure("The selected model file does not exist.")
        }
        if (!file.isFile) {
            return ModelValidationResult.Failure("The selected path is not a file.")
        }
        if (file.length() <= 0L) {
            return ModelValidationResult.Failure("The selected model file is empty.")
        }
        return try {
            FileInputStream(file).use { input ->
                val header = ByteArray(4)
                val bytesRead = input.read(header)
                if (bytesRead != 4 || String(header, Charsets.US_ASCII) != GGUF_HEADER) {
                    ModelValidationResult.Failure("The selected file is not a valid GGUF model.")
                } else {
                    ModelValidationResult.Success(file)
                }
            }
        } catch (e: IOException) {
            Log.e(LOG_TAG, "Model validation failed: ${e.message}", e)
            ModelValidationResult.Failure("The selected model could not be read.")
        }
    }

    suspend fun loadModel(forceReload: Boolean = false): LoadModelResult {
        return modelMutex.withLock {
            Log.d(LOG_TAG, "Model load requested. path=$configuredModelPath forceReload=$forceReload")

            val validation = validateModel(configuredModelPath)
            if (validation is ModelValidationResult.Failure) {
                isLoaded.set(false)
                loadedModelPath = null
                return@withLock LoadModelResult.Failure(validation.message)
            }

            if (isGenerating.get()) {
                return@withLock LoadModelResult.Failure("Please wait for the current response to finish before loading another model.")
            }

            val targetPath = (validation as ModelValidationResult.Success).file.absolutePath
            val targetFile = validation.file
            val estimatedRuntimeMb = estimateRuntimeMb(targetFile)
            val safeLoadBudgetMb = (computeUsableRamMb() * 0.96f).toInt()
            if (estimatedRuntimeMb > safeLoadBudgetMb) {
                return@withLock LoadModelResult.Failure(
                    "This model is too heavy to load safely on this device right now."
                )
            }
            val shouldReload = forceReload || !isLoaded.get() || loadedModelPath != targetPath

            if (!shouldReload) {
                Log.d(LOG_TAG, "Model already ready at path=$targetPath")
                return@withLock LoadModelResult.Success(targetPath)
            }

            return@withLock try {
                inferenceEngine.close()
                isLoaded.set(false)
                Log.d(LOG_TAG, "Loading model from $targetPath")
                inferenceEngine.load(targetPath)
                configuredModelPath = targetPath
                loadedModelPath = targetPath
                isLoaded.set(true)
                Log.d(LOG_TAG, "Model loaded successfully from $targetPath")
                LoadModelResult.Success(targetPath)
            } catch (e: CancellationException) {
                Log.d(LOG_TAG, "Model loading cancelled")
                isLoaded.set(false)
                loadedModelPath = null
                throw e
            } catch (e: Throwable) {
                Log.e(LOG_TAG, "Model loading failed: ${e.message}", e)
                inferenceEngine.close()
                inferenceEngine = RedHat1406()
                isLoaded.set(false)
                loadedModelPath = null
                LoadModelResult.Failure(
                    e.message ?: "The model could not be loaded. Please try another GGUF file."
                )
            }
        }
    }

    fun generate(prompt: String): Flow<GenerateResult> = flow {
        if (!isLoaded.get()) {
            emit(GenerateResult.Failure("Model not loaded. Please select a GGUF model first."))
            return@flow
        }

        if (!isGenerating.compareAndSet(false, true)) {
            emit(GenerateResult.Failure("A response is already being generated."))
            return@flow
        }

        stopRequested.set(false)
        Log.d(LOG_TAG, "Inference started")
        try {
            inferenceEngine.getResponseAsFlow(prompt).collect { token ->
                emit(GenerateResult.Token(token))
            }
            if (stopRequested.get()) {
                Log.d(LOG_TAG, "Inference stopped")
                emit(GenerateResult.Cancelled)
            } else {
                Log.d(LOG_TAG, "Inference completed")
            }
        } catch (e: CancellationException) {
            if (stopRequested.get()) {
                Log.d(LOG_TAG, "Inference cancelled after stop request")
                emit(GenerateResult.Cancelled)
            } else {
                Log.d(LOG_TAG, "Inference cancelled")
                throw e
            }
        } catch (e: Exception) {
            if (stopRequested.get()) {
                Log.d(LOG_TAG, "Inference stopped with native interruption: ${e.message}")
                emit(GenerateResult.Cancelled)
            } else {
                Log.e(LOG_TAG, "Inference error: ${e.message}", e)
                emit(GenerateResult.Failure("Something went wrong while generating the response."))
            }
        } finally {
            isGenerating.set(false)
            stopRequested.set(false)
        }
    }.flowOn(Dispatchers.IO)

    fun stopGeneration() {
        Log.d(LOG_TAG, "Stop generation requested")
        stopRequested.set(true)
        inferenceEngine.stopGeneration()
    }

    fun unloadModel() {
        Log.d(LOG_TAG, "Unloading model")
        stopGeneration()
        runBlocking(Dispatchers.IO) {
            modelMutex.withLock {
                inferenceEngine.close()
                isLoaded.set(false)
                loadedModelPath = null
            }
        }
    }

    private fun computeUsableRamMb(): Int {
        val activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
        val totalRamMb = (memoryInfo.totalMem / (1024L * 1024L)).toInt()
        val availableRamMb = (memoryInfo.availMem / (1024L * 1024L)).toInt()
        val reserveMb = when {
            totalRamMb >= 16 * 1024 -> 896
            totalRamMb >= 8 * 1024 -> 640
            totalRamMb >= 4 * 1024 -> 448
            else -> 320
        }
        return (availableRamMb - reserveMb).coerceAtLeast(512)
    }

    private fun estimateRuntimeMb(file: File): Int {
        val fileSizeMb = file.length().toFloat() / (1024f * 1024f)
        return when {
            fileSizeMb < 1024f -> (fileSizeMb * 1.9f + 420f).toInt()
            fileSizeMb < 3072f -> (fileSizeMb * 2.15f + 760f).toInt()
            else -> (fileSizeMb * 2.4f + 1200f).toInt()
        }
    }
}
