package com.example.ai

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/**
 * Device capability detection for picking a default model.
 *
 * Every API used here is permission-free and works on minSdk 24:
 * total/free RAM, CPU cores, SoC model, ABI. No device allowlist — the tier
 * is computed from RAM, and the actual load is gated on free RAM at the time.
 */
enum class DeviceTier {
    LOW, MID, HIGH;

    val label: String
        get() = when (this) {
            LOW -> "Low-end"
            MID -> "Mid-range"
            HIGH -> "High-end"
        }
}

data class DeviceSpecs(
    val totalRamBytes: Long,
    val availRamBytes: Long,
    val cpuCores: Int,
    /** e.g. "SM8650" or a Build.HARDWARE fallback. "Unknown" when unavailable. */
    val socModel: String,
    val abi: String,
    val androidVersion: Int
) {
    fun formatRam(bytes: Long): String = "%.1f GB".format(bytes / 1_000_000_000.0)
    val totalRamLabel: String get() = formatRam(totalRamBytes)
    val freeRamLabel: String get() = formatRam(availRamBytes)
}

object DeviceTiers {

    private const val GB = 1_000_000_000L

    fun detectSpecs(context: Context): DeviceSpecs {
        val activityManager =
            context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL ?: Build.HARDWARE
        } else {
            Build.HARDWARE
        }
        return DeviceSpecs(
            totalRamBytes = memInfo.totalMem,
            availRamBytes = memInfo.availMem,
            cpuCores = Runtime.getRuntime().availableProcessors(),
            socModel = soc?.takeIf { it.isNotBlank() } ?: "Unknown",
            abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "Unknown",
            androidVersion = Build.VERSION.SDK_INT
        )
    }

    /** RAM is the primary signal. 8 GB counts as High (Gemma 4 E2B needs 6 GB+). */
    fun tierFor(specs: DeviceSpecs): DeviceTier = when {
        specs.totalRamBytes >= 8 * GB -> DeviceTier.HIGH
        specs.totalRamBytes > 4 * GB -> DeviceTier.MID
        else -> DeviceTier.LOW
    }

    fun recommendedLlm(tier: DeviceTier): AiModel = when (tier) {
        DeviceTier.HIGH -> ModelCatalog.GEMMA_4_E2B
        DeviceTier.MID -> ModelCatalog.QWEN3_1_7B
        DeviceTier.LOW -> ModelCatalog.QWEN3_0_6B
    }

    /**
     * The load-time gate: never let the user OOM-crash the app. Checked with
     * the CURRENT free RAM, not the tier — the OS typically wires 2–4 GB.
     */
    fun canLoadNow(model: AiModel, specs: DeviceSpecs): Boolean =
        model.noDownload || specs.availRamBytes >= model.minFreeRamBytes

    fun fitsDevice(model: AiModel, specs: DeviceSpecs): Boolean =
        model.noDownload || specs.totalRamBytes >= model.minTotalRamBytes
}
