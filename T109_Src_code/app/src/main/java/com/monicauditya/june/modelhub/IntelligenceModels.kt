package com.monicauditya.june.modelhub

enum class ParameterSizeSource {
    PARSED,
    ESTIMATED,
}

enum class QuantTier {
    Q2_Q3,
    Q4,
    Q5_Q6,
    Q8,
    F16_BF16,
    UNKNOWN,
}

enum class CpuTier {
    LOW,
    MID,
    HIGH,
}

enum class CpuFitClass {
    EASY,
    OK,
    HEAVY,
    EXTREME,
}

enum class ModelSubtype {
    CODER,
    INSTRUCT,
    CHAT,
    BASE,
}

enum class RecommendationReason {
    RAM_FIT,
    CPU_FIT,
    CODING_MATCH,
    CHAT_MATCH,
    TRUST_SIGNAL,
    BEST_OVERALL,
}

enum class UserUseCase {
    CODING,
    GENERAL,
    MIXED,
}

enum class SessionLengthProfile(val factor: Float) {
    SHORT(0.8f),
    NORMAL(1.0f),
    LONG(1.3f),
}

data class UserPreference(
    val useCase: UserUseCase = UserUseCase.MIXED,
    val hasCompletedOnboarding: Boolean = false,
    val hasEditedPreferences: Boolean = false,
    val sessionLengthProfile: SessionLengthProfile = SessionLengthProfile.NORMAL,
)

data class ModelCapabilities(
    val isCoder: Boolean,
    val isChatOptimized: Boolean,
    val isInstruct: Boolean,
    val confidence: Float,
    val coderAffinity: Float,
    val chatAffinity: Float,
    val instructAffinity: Float,
)

data class VariantProfile(
    val model: ModelInfo,
    val familyCanonical: String,
    val groupCanonical: String,
    val subtype: ModelSubtype,
    val parameterSizeB: Float,
    val parameterSizeSource: ParameterSizeSource,
    val quantTier: QuantTier,
    val capabilities: ModelCapabilities,
    val cpuTier: CpuTier,
    val cpuFitClass: CpuFitClass,
    val weightsMb: Int,
    val runtimeOverheadMb: Int,
    val kvCacheMb: Int,
    val estimatedRamMb: Int,
    val effectiveAvailableRamMb: Int,
    val memoryRatio: Float,
    val memoryFit: Float,
    val cpuFit: Float,
    val preferenceFit: Float,
    val trustFit: Float,
    val latencyScore: Float,
    val overallScore: Float,
    val performance: PerformanceLevel,
    val whyRecommendedCode: RecommendationReason,
    val whyRecommended: String,
)

data class FeaturedRecommendations(
    val recommended: ModelRecommendation?,
    val fastest: ModelRecommendation?,
    val highestQuality: ModelRecommendation?,
)
