package com.monicauditya.june.modelhub

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.monicauditya.june.MainActivity
import com.monicauditya.june.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.core.annotation.Single
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

enum class DownloadState {
    IDLE,
    DOWNLOADING,
    PAUSED,
    DOWNLOADED,
    CANCELLED,
    FAILED,
}

data class ModelDownloadStatus(
    val state: DownloadState = DownloadState.IDLE,
    val progress: Int = 0,
    val localPath: String? = null,
    val errorMessage: String? = null,
)

class DownloadPausedException : CancellationException("Download paused.")

@Single
class ModelDownloadManager(private val context: Context) {
    private val client = OkHttpClient()
    private val notificationManager = NotificationManagerCompat.from(context)
    private val appLaunchIntent by lazy {
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    private val largeLogo by lazy { BitmapFactory.decodeResource(context.resources, R.drawable.haya_logo) }

    @Volatile
    private var activeCall: Call? = null

    @Volatile
    private var activeStopReason: StopReason = StopReason.NONE

    private enum class StopReason {
        NONE,
        PAUSE,
        CANCEL,
    }

    init {
        ensureNotificationChannel()
    }

    fun getModelsDirectory(): File = File(context.filesDir, "models").apply { mkdirs() }

    fun getLocalFile(model: ModelInfo): File {
        val uniqueSuffix = model.downloadUrl.hashCode().toUInt().toString(16)
        val safeName = "${model.name}_${model.fileName}_$uniqueSuffix"
            .removeSuffix(".gguf")
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
        return File(getModelsDirectory(), "$safeName.gguf")
    }

    private fun getLegacyLocalFile(model: ModelInfo): File {
        val safeName = model.fileName
            .removeSuffix(".gguf")
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
        return File(getModelsDirectory(), "$safeName.gguf")
    }

    fun getDownloadStatus(model: ModelInfo): ModelDownloadStatus {
        val file = getLocalFile(model).takeIf { it.exists() } ?: getLegacyLocalFile(model)
        val expectedSize = model.expectedSizeBytes.takeIf { it > 0L }
        val looksValid = file.exists() &&
            file.length() > 0L &&
            (expectedSize == null || file.length() == expectedSize)
        return if (looksValid) {
            ModelDownloadStatus(
                state = DownloadState.DOWNLOADED,
                progress = 100,
                localPath = file.absolutePath
            )
        } else {
            ModelDownloadStatus()
        }
    }

    fun cancelActiveDownload() {
        activeStopReason = StopReason.CANCEL
        activeCall?.cancel()
    }

    fun pauseActiveDownload() {
        activeStopReason = StopReason.PAUSE
        activeCall?.cancel()
    }

    suspend fun startDownload(
        model: ModelInfo,
        onProgress: (Int) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        val targetFile = getLocalFile(model)
        val tempFile = File(targetFile.absolutePath + ".part")
        val expectedSize = model.expectedSizeBytes.takeIf { it > 0L }
        activeStopReason = StopReason.NONE
        showDownloadNotification(model.name, 0)

        runCatching {
            var resumeBytes = tempFile.takeIf { it.exists() }?.length() ?: 0L
            val requestBuilder = Request.Builder().url(model.downloadUrl)
            if (resumeBytes > 0L) {
                requestBuilder.header("Range", "bytes=$resumeBytes-")
            }
            val request = requestBuilder.build()
            val call = client.newCall(request)
            activeCall = call

            call.execute().use { response ->
                ensureActive()
                if (!response.isSuccessful) {
                    error("Download failed with HTTP ${response.code}")
                }

                val body = response.body ?: error("Download response was empty.")
                val appendToTemp = resumeBytes > 0L && response.code == 206
                if (resumeBytes > 0L && !appendToTemp) {
                    tempFile.delete()
                    resumeBytes = 0L
                }

                val contentLength = when {
                    expectedSize != null -> expectedSize
                    body.contentLength() > 0L && appendToTemp -> resumeBytes + body.contentLength()
                    body.contentLength() > 0L -> body.contentLength()
                    else -> -1L
                }

                if (resumeBytes > 0L && contentLength > 0L) {
                    val resumedProgress = ((resumeBytes * 100) / contentLength).toInt().coerceIn(0, 100)
                    onProgress(resumedProgress)
                    showDownloadNotification(model.name, resumedProgress)
                }

                body.byteStream().use { input ->
                    FileOutputStream(tempFile, appendToTemp).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var downloadedBytes = resumeBytes

                        while (true) {
                            ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) break

                            output.write(buffer, 0, read)
                            downloadedBytes += read

                            if (contentLength > 0) {
                                val progress = ((downloadedBytes * 100) / contentLength)
                                    .toInt()
                                    .coerceIn(0, 100)
                                onProgress(progress)
                                showDownloadNotification(model.name, progress)
                            }
                        }
                        output.flush()
                    }
                }

                if (targetFile.exists()) {
                    targetFile.delete()
                }
                if (!tempFile.renameTo(targetFile)) {
                    error("Could not finalize the downloaded model.")
                }
                if (expectedSize != null && targetFile.length() != expectedSize) {
                    targetFile.delete()
                    error("Downloaded file size does not match the expected model size.")
                }
                onProgress(100)
                showFinishedNotification(model.name, "Ready to use in Downloads")
                targetFile
            }
        }.recoverCatching { error ->
            when {
                error is IOException && activeCall?.isCanceled() == true && activeStopReason == StopReason.PAUSE -> {
                    showFinishedNotification(model.name, "Paused. Open Downloads to resume.")
                    throw DownloadPausedException()
                }
                error is IOException && activeCall?.isCanceled() == true -> {
                    if (tempFile.exists()) {
                        tempFile.delete()
                    }
                    cancelDownloadNotification()
                    throw CancellationException("Download cancelled.")
                }
            }

            if (tempFile.exists()) {
                tempFile.delete()
            }
            if (error is CancellationException) {
                cancelDownloadNotification()
                throw error
            }
            showFinishedNotification(model.name, error.message ?: "Download failed. Open Downloads to download again.")
            throw error
        }.also {
            activeCall = null
            activeStopReason = StopReason.NONE
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                DOWNLOAD_CHANNEL_ID,
                "Model downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress for model downloads"
            }
            context.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun showDownloadNotification(modelName: String, progress: Int) {
        if (!canPostNotifications()) return
        val clampedProgress = progress.coerceIn(0, 100)
        notificationManager.notify(
            DOWNLOAD_NOTIFICATION_ID,
            NotificationCompat.Builder(context, DOWNLOAD_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_haya_notification)
                .setLargeIcon(largeLogo)
                .setColor(Color.parseColor("#CEBDFF"))
                .setColorized(true)
                .setSubText("Haya downloads")
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setContentIntent(appLaunchIntent)
                .setContentTitle(modelName)
                .setContentText("$clampedProgress% complete")
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "Downloading $modelName\n$clampedProgress% complete • Saved into your Haya library when finished."
                    )
                )
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setSilent(true)
                .setProgress(100, clampedProgress, false)
                .build()
        )
    }

    private fun showFinishedNotification(modelName: String, message: String) {
        if (!canPostNotifications()) return
        notificationManager.notify(
            DOWNLOAD_NOTIFICATION_ID,
            NotificationCompat.Builder(context, DOWNLOAD_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_haya_notification)
                .setLargeIcon(largeLogo)
                .setColor(Color.parseColor("#CEBDFF"))
                .setSubText("Haya downloads")
                .setContentIntent(appLaunchIntent)
                .setContentTitle(modelName)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setAutoCancel(true)
                .setOngoing(false)
                .build()
        )
    }

    private fun cancelDownloadNotification() {
        notificationManager.cancel(DOWNLOAD_NOTIFICATION_ID)
    }

    companion object {
        private const val DOWNLOAD_CHANNEL_ID = "model_downloads"
        private const val DOWNLOAD_NOTIFICATION_ID = 2001
    }
}
