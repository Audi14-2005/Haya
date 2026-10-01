package com.monicauditya.june.modelhub

enum class QualityLevel {
    LOW,
    BALANCED,
    HIGH,
}

data class ModelInfo(
    val name: String,
    val groupName: String,
    val description: String,
    val capabilityTags: List<String> = emptyList(),
    val pipelineTag: String? = null,
    val sizeGb: Float,
    val ramRequiredMb: Int,
    val quantization: String,
    val supportsImage: Boolean,
    val quality: QualityLevel,
    val downloadUrl: String,
    val fileName: String,
    val downloads: Long = 0,
    val expectedSizeBytes: Long = 0,
    val lastUpdatedEpochMs: Long? = null,
) {
    val id: String
        get() = "$name::$fileName"
}

data class ModelGroup(
    val name: String,
    val variants: List<ModelInfo>,
)
