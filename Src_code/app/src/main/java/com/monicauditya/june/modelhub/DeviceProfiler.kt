package com.monicauditya.june.modelhub

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import org.koin.core.annotation.Single

data class DeviceProfile(
    val totalRamMb: Int,
    val availableRamMb: Int,
    val usableRamMb: Int,
    val cpuCores: Int,
    val cpuTier: CpuTier,
    val abi: String,
    val isEmulator: Boolean,
)

@Single
class DeviceProfiler(private val context: Context) {

    fun getDeviceProfile(): DeviceProfile {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
        val totalRamMb = bytesToMb(memoryInfo.totalMem)
        val availableRamMb = bytesToMb(memoryInfo.availMem)
        val cpuCores = Runtime.getRuntime().availableProcessors()
        val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        val isEmulator = isProbablyEmulator()
        val usableRamMb = computeUsableRam(
            totalRamMb = totalRamMb,
            availableRamMb = availableRamMb,
            isEmulator = isEmulator,
        )

        return DeviceProfile(
            totalRamMb = totalRamMb,
            availableRamMb = availableRamMb,
            usableRamMb = usableRamMb,
            cpuCores = cpuCores,
            cpuTier = classifyCpuTier(
                cpuCores = cpuCores,
                abi = abi,
                usableRamMb = usableRamMb,
                isEmulator = isEmulator,
            ),
            abi = abi,
            isEmulator = isEmulator
        )
    }

    private fun bytesToMb(bytes: Long): Int = (bytes / (1024L * 1024L)).toInt()

    private fun computeUsableRam(
        totalRamMb: Int,
        availableRamMb: Int,
        isEmulator: Boolean,
    ): Int {
        val reserveMb = when {
            isEmulator && availableRamMb >= 12 * 1024 -> 1024
            isEmulator -> 768
            totalRamMb >= 16 * 1024 -> 1280
            totalRamMb >= 8 * 1024 -> 896
            totalRamMb >= 4 * 1024 -> 640
            else -> 448
        }
        return (availableRamMb - reserveMb).coerceAtLeast(512)
    }

    private fun classifyCpuTier(
        cpuCores: Int,
        abi: String,
        usableRamMb: Int,
        isEmulator: Boolean,
    ): CpuTier {
        var tier = when {
            cpuCores <= 4 -> CpuTier.LOW
            cpuCores <= 8 -> CpuTier.MID
            else -> CpuTier.HIGH
        }

        if (!isEmulator && !abi.contains("arm64", ignoreCase = true)) {
            tier = downgrade(tier)
        }

        if (usableRamMb < 4096) {
            tier = downgrade(tier)
        }

        if (isEmulator && abi.contains("x86_64", ignoreCase = true) && usableRamMb >= 8192 && tier == CpuTier.LOW) {
            tier = CpuTier.MID
        }

        return tier
    }

    private fun downgrade(tier: CpuTier): CpuTier = when (tier) {
        CpuTier.HIGH -> CpuTier.MID
        CpuTier.MID -> CpuTier.LOW
        CpuTier.LOW -> CpuTier.LOW
    }

    private fun isProbablyEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT.orEmpty().lowercase()
        val model = Build.MODEL.orEmpty().lowercase()
        val manufacturer = Build.MANUFACTURER.orEmpty().lowercase()
        val brand = Build.BRAND.orEmpty().lowercase()
        val device = Build.DEVICE.orEmpty().lowercase()
        val product = Build.PRODUCT.orEmpty().lowercase()
        val hardware = Build.HARDWARE.orEmpty().lowercase()

        return fingerprint.contains("generic") ||
            fingerprint.contains("emulator") ||
            model.contains("emulator") ||
            model.contains("sdk") ||
            manufacturer.contains("genymotion") ||
            brand.startsWith("generic") ||
            device.startsWith("generic") ||
            product.contains("sdk") ||
            hardware.contains("ranchu") ||
            hardware.contains("goldfish")
    }
}
