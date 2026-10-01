package com.monicauditya.june.modelhub

import org.koin.core.annotation.Single
import java.util.Locale
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max

enum class PerformanceLevel {
    FAST,
    BALANCED,
    SLOW,
    RISKY,
}

data class ModelRecommendation(
    val profile: VariantProfile,
    val isSupported: Boolean,
) {
    val model: ModelInfo
        get() = profile.model
    val performance: PerformanceLevel
        get() = profile.performance
    val estimatedRamMb: Int
        get() = profile.estimatedRamMb
    val memoryRatio: Float
        get() = profile.memoryRatio
    val overallScore: Float
        get() = profile.overallScore
    val whyRecommended: String
        get() = profile.whyRecommended
    val whyRecommendedCode: RecommendationReason
        get() = profile.whyRecommendedCode
    val confidence: Float
        get() = profile.capabilities.confidence
    val familyCanonical: String
        get() = profile.familyCanonical
    val groupCanonical: String
        get() = profile.groupCanonical
    val parameterSizeB: Float
        get() = profile.parameterSizeB
    val quantTier: QuantTier
        get() = profile.quantTier
    val latencyScore: Float
        get() = profile.latencyScore
}

data class RecommendationResult(
    val recommendations: List<ModelRecommendation>,
    val featured: FeaturedRecommendations,
)

@Single
class RecommendationEngine {

    fun recommend(
        deviceProfile: DeviceProfile,
        models: List<ModelInfo>,
        userPreference: UserPreference,
        installedModelIds: Set<String> = emptySet(),
        previousFeaturedOrder: Map<String, Int> = emptyMap(),
    ): RecommendationResult {
        val profiles = models.map { model ->
            buildVariantProfile(deviceProfile, model, userPreference, installedModelIds)
        }
        val recommendations = profiles
            .map { profile ->
                ModelRecommendation(
                    profile = profile,
                    isSupported = profile.estimatedRamMb <= profile.effectiveAvailableRamMb * 2
                )
            }
            .sortedWith(recommendationComparator(previousFeaturedOrder))

        return RecommendationResult(
            recommendations = recommendations,
            featured = selectFeaturedRecommendations(
                recommendations = recommendations,
                userPreference = userPreference,
                previousFeaturedOrder = previousFeaturedOrder,
                applyFirstRunBias = !userPreference.hasCompletedOnboarding || !userPreference.hasEditedPreferences,
            )
        )
    }

    private fun buildVariantProfile(
        deviceProfile: DeviceProfile,
        model: ModelInfo,
        userPreference: UserPreference,
        installedModelIds: Set<String>,
    ): VariantProfile {
        val familyCanonical = normalizeFamily(model)
        val subtype = resolveSubtype(model)
        val parameter = parseOrEstimateParameterSize(model)
        val quantTier = quantTierOf(model.quantization, model.fileName)
        val capabilities = inferCapabilities(model)
        val effectiveAvailableRamMb = deviceProfile.usableRamMb.coerceAtLeast(512)
        val weightsMb = (model.sizeGb * 1024f).toInt()
        val runtimeOverheadMb = (weightsMb * overheadMultiplier(quantTier)).toInt()
        val kvBase = kvBudgetMbPerB(userPreference.useCase)
        val kvCurve = (0.9f + 0.1f * log10(max(parameter.sizeB, 1f)))
        val kvCacheMb = (parameter.sizeB * kvBase * userPreference.sessionLengthProfile.factor * kvCurve).toInt()
        val estimatedRamMb = weightsMb + runtimeOverheadMb + kvCacheMb
        val memoryRatio = effectiveAvailableRamMb / estimatedRamMb.toFloat()
        val memoryFit = memoryFit(memoryRatio)
        val cpuTier = deviceProfile.cpuTier
        val cpuFitClass = cpuFitClass(cpuTier, parameter.sizeB, quantTier)
        val cpuFit = cpuFit(cpuFitClass, parameter.sizeB, cpuTier)
        val preferenceFit = preferenceFit(userPreference, parameter.sizeB, quantTier, capabilities, memoryFit)
        val trustFit = trustFit(model.downloads, model.lastUpdatedEpochMs, capabilities.confidence)
        val latencyScore = latencyScore(estimatedRamMb, effectiveAvailableRamMb, cpuFit)
        val overallScoreBase = (memoryFit * 0.30f) + (cpuFit * 0.24f) + (preferenceFit * 0.36f) + (trustFit * 0.10f)
        val overallScore = if (installedModelIds.contains(model.id)) {
            (overallScoreBase + 0.05f).coerceAtMost(1.1f)
        } else {
            overallScoreBase
        }
        val performance = performanceLevel(memoryFit, cpuFit, memoryRatio)
        val reason = recommendationReason(memoryFit, cpuFit, preferenceFit, trustFit, userPreference, capabilities)
        val whyRecommended = reasonMessage(reason)

        return VariantProfile(
            model = model,
            familyCanonical = familyCanonical,
            groupCanonical = "$familyCanonical|${parameter.sizeB.roundToIntString()}|${subtype.name.lowercase(Locale.US)}",
            subtype = subtype,
            parameterSizeB = parameter.sizeB,
            parameterSizeSource = parameter.source,
            quantTier = quantTier,
            capabilities = capabilities,
            cpuTier = cpuTier,
            cpuFitClass = cpuFitClass,
            weightsMb = weightsMb,
            runtimeOverheadMb = runtimeOverheadMb,
            kvCacheMb = kvCacheMb,
            estimatedRamMb = estimatedRamMb,
            effectiveAvailableRamMb = effectiveAvailableRamMb,
            memoryRatio = memoryRatio,
            memoryFit = memoryFit,
            cpuFit = cpuFit,
            preferenceFit = preferenceFit,
            trustFit = trustFit,
            latencyScore = latencyScore,
            overallScore = overallScore,
            performance = performance,
            whyRecommendedCode = reason,
            whyRecommended = whyRecommended,
        )
    }

    private fun selectFeaturedRecommendations(
        recommendations: List<ModelRecommendation>,
        userPreference: UserPreference,
        previousFeaturedOrder: Map<String, Int>,
        applyFirstRunBias: Boolean,
    ): FeaturedRecommendations {
        val recommendationPool = recommendations.filter { it.profile.estimatedRamMb <= it.profile.effectiveAvailableRamMb * 2 }
        val biasedPool = if (applyFirstRunBias) {
            recommendationPool.sortedWith(
                compareBy<ModelRecommendation> { it.performance == PerformanceLevel.RISKY }
                    .then(recommendationComparator(previousFeaturedOrder))
            )
        } else {
            recommendationPool.sortedWith(recommendationComparator(previousFeaturedOrder))
        }
        val nearSafePool = biasedPool.filter {
            it.profile.memoryRatio >= 0.85f && it.performance != PerformanceLevel.RISKY
        }
        val safePool = biasedPool.filter { it.profile.memoryRatio >= 1.0f }
        val preferredPool = safePool.filter { it.profile.memoryRatio >= 1.15f }
        val balancedSafePool = safePool.filter { it.performance == PerformanceLevel.BALANCED }
        val balancedPreferredPool = preferredPool.filter { it.performance == PerformanceLevel.BALANCED }
        val strongMatchedBalancedSafePool = balancedSafePool.filter { isStrongUseCaseMatch(it, userPreference.useCase) }
        val strongMatchedBalancedPreferredPool = balancedPreferredPool.filter { isStrongUseCaseMatch(it, userPreference.useCase) }
        val matchedBalancedSafePool = balancedSafePool.filter { isUseCaseMatch(it, userPreference.useCase) }
        val matchedBalancedPreferredPool = balancedPreferredPool.filter { isUseCaseMatch(it, userPreference.useCase) }
        val strongMatchedSafePool = safePool.filter { isStrongUseCaseMatch(it, userPreference.useCase) }
        val strongMatchedPreferredPool = preferredPool.filter { isStrongUseCaseMatch(it, userPreference.useCase) }
        val matchedSafePool = safePool.filter { isUseCaseMatch(it, userPreference.useCase) }
        val matchedPreferredPool = preferredPool.filter { isUseCaseMatch(it, userPreference.useCase) }
        val qualityCandidatePool = biasedPool.filter {
            it.profile.memoryRatio >= 0.78f && it.performance != PerformanceLevel.RISKY
        }
        val qualityStretchPreferredPool = preferredPool.filter {
            it.performance == PerformanceLevel.BALANCED || it.performance == PerformanceLevel.SLOW
        }
        val qualityStretchSafePool = qualityCandidatePool.filter {
            it.performance == PerformanceLevel.BALANCED || it.performance == PerformanceLevel.SLOW
        }
        val qualityStretchMatchedPreferredPool = qualityStretchPreferredPool.filter { isQualityUseCaseMatch(it, userPreference.useCase) }
        val qualityStretchMatchedSafePool = qualityStretchSafePool.filter { isQualityUseCaseMatch(it, userPreference.useCase) }

        val chosenIds = linkedSetOf<String>()
        val familyCounts = mutableMapOf<String, Int>()
        val chosenGroups = mutableSetOf<String>()
        val chosenByGroup = mutableMapOf<String, ModelRecommendation>()

        fun eligible(candidate: ModelRecommendation, allowFamilyRelaxation: Boolean = false, allowGroupRelaxation: Boolean = false): Boolean {
            if (!chosenIds.add(candidate.model.id)) return false
            chosenIds.remove(candidate.model.id)
            val familyCount = familyCounts[candidate.familyCanonical] ?: 0
            if (!allowFamilyRelaxation && familyCount >= 2) return false
            if (!allowGroupRelaxation && chosenGroups.contains(candidate.groupCanonical)) return false
            return true
        }

        fun register(candidate: ModelRecommendation?) {
            if (candidate == null) return
            if (chosenIds.contains(candidate.model.id)) return
            chosenIds.add(candidate.model.id)
            familyCounts[candidate.familyCanonical] = (familyCounts[candidate.familyCanonical] ?: 0) + 1
            chosenGroups.add(candidate.groupCanonical)
            chosenByGroup[candidate.groupCanonical] = candidate
        }

        fun eligibleQuality(candidate: ModelRecommendation, allowFamilyRelaxation: Boolean = false): Boolean {
            if (chosenIds.contains(candidate.model.id)) return false
            val familyCount = familyCounts[candidate.familyCanonical] ?: 0
            if (!allowFamilyRelaxation && familyCount >= 2) return false

            val existingSameGroup = chosenByGroup[candidate.groupCanonical]
            if (existingSameGroup != null) {
                return isMeaningfulQualityUpgrade(candidate, existingSameGroup)
            }

            return true
        }

        val fastestSelector: (List<ModelRecommendation>) -> List<ModelRecommendation> = {
            it.sortedWith(
                compareByDescending<ModelRecommendation> { it.latencyScore }
                    .thenBy { fastestPriority(it) }
                    .thenByDescending { it.overallScore }
                    .thenBy { previousFeaturedOrder[it.model.id] ?: Int.MAX_VALUE }
                    .thenBy { "${it.familyCanonical}|${it.groupCanonical}|${it.quantTier.name}|${it.model.id}" }
            )
        }
        val qualitySelector: (List<ModelRecommendation>) -> List<ModelRecommendation> = {
            it.sortedWith(
                compareByDescending<ModelRecommendation> { qualityRankScore(it, userPreference) }
                    .thenByDescending { it.overallScore }
                    .thenBy { previousFeaturedOrder[it.model.id] ?: Int.MAX_VALUE }
                    .thenBy { "${it.familyCanonical}|${it.groupCanonical}|${it.quantTier.name}|${it.model.id}" }
            )
        }
        val recommendedSelector: (List<ModelRecommendation>) -> List<ModelRecommendation> = {
            it.sortedWith(
                compareByDescending<ModelRecommendation> { recommendedRankScore(it, userPreference) }
                    .thenByDescending { it.overallScore }
                    .thenBy { previousFeaturedOrder[it.model.id] ?: Int.MAX_VALUE }
                    .thenBy { "${it.familyCanonical}|${it.groupCanonical}|${it.quantTier.name}|${it.model.id}" }
            )
        }

        val recommended = pickCandidate(
            listOf(
                strongMatchedBalancedPreferredPool,
                strongMatchedBalancedSafePool,
                matchedBalancedPreferredPool,
                matchedBalancedSafePool,
                balancedPreferredPool,
                balancedSafePool,
                strongMatchedPreferredPool,
                strongMatchedSafePool,
                matchedPreferredPool,
                matchedSafePool,
                preferredPool,
                safePool,
                nearSafePool,
            ),
            recommendedSelector,
            ::eligible
        )
        register(recommended)

        val fastest = pickCandidate(
            listOf(preferredPool, safePool, nearSafePool),
            fastestSelector,
            ::eligible
        ) ?: pickCandidate(
            listOf(preferredPool, safePool, nearSafePool),
            fastestSelector,
            { candidate -> eligible(candidate, allowGroupRelaxation = true) }
        ) ?: pickCandidate(
            listOf(preferredPool, safePool, nearSafePool),
            fastestSelector,
            { candidate -> eligible(candidate, allowFamilyRelaxation = true, allowGroupRelaxation = true) }
        ) ?: pickCandidate(
            listOf(preferredPool, safePool, nearSafePool),
            fastestSelector,
            { true }
        )
        register(fastest)

        val qualityPools = buildQualityCandidatePools(
            baseline = fastest,
            matchedPreferred = qualityStretchMatchedPreferredPool,
            matchedSafe = qualityStretchMatchedSafePool,
            preferred = qualityStretchPreferredPool,
            safe = qualityStretchSafePool,
        )
        val highestQuality = pickCandidate(
            qualityPools,
            qualitySelector,
            ::eligibleQuality
        ) ?: pickCandidate(
            qualityPools,
            qualitySelector,
            { candidate -> eligibleQuality(candidate, allowFamilyRelaxation = true) }
        )
        register(highestQuality)

        val resolvedRecommended = recommended ?: fastest
        val resolvedFastest = fastest ?: resolvedRecommended
        val diversifiedQuality = if (
            highestQuality != null &&
            resolvedRecommended != null &&
            highestQuality.familyCanonical == resolvedRecommended.familyCanonical
        ) {
            qualitySelector(qualityPools.flatten().distinctBy { it.model.id }).firstOrNull { candidate ->
                candidate.model.id != highestQuality.model.id &&
                    candidate.model.id != resolvedRecommended.model.id &&
                    candidate.model.id != resolvedFastest?.model?.id &&
                    candidate.familyCanonical != resolvedRecommended.familyCanonical &&
                    (resolvedFastest == null || candidate.familyCanonical != resolvedFastest.familyCanonical) &&
                    (resolvedFastest == null || isMeaningfulQualityUpgrade(candidate, resolvedFastest))
            } ?: highestQuality
        } else {
            highestQuality
        }

        return FeaturedRecommendations(
            recommended = resolvedRecommended,
            fastest = resolvedFastest,
            highestQuality = diversifiedQuality,
        )
    }

    private fun pickCandidate(
        pools: List<List<ModelRecommendation>>,
        selector: (List<ModelRecommendation>) -> List<ModelRecommendation>,
        eligible: (ModelRecommendation) -> Boolean,
    ): ModelRecommendation? {
        pools.forEach { pool ->
            selector(pool).firstOrNull(eligible)?.let { return it }
        }
        return null
    }

    private fun recommendationComparator(previousOrder: Map<String, Int>): Comparator<ModelRecommendation> =
        Comparator { left, right ->
            val scoreDiff = right.overallScore - left.overallScore
            when {
                abs(scoreDiff) >= 0.02f -> scoreDiff.signAsComparator()
                previousOrder.containsKey(left.model.id) && previousOrder.containsKey(right.model.id) ->
                    (previousOrder[left.model.id] ?: Int.MAX_VALUE).compareTo(previousOrder[right.model.id] ?: Int.MAX_VALUE)
                else -> "${left.familyCanonical}|${left.groupCanonical}|${left.quantTier.name}|${left.model.id}"
                    .compareTo("${right.familyCanonical}|${right.groupCanonical}|${right.quantTier.name}|${right.model.id}")
            }
        }

    private fun buildQualityCandidatePools(
        baseline: ModelRecommendation?,
        matchedPreferred: List<ModelRecommendation>,
        matchedSafe: List<ModelRecommendation>,
        preferred: List<ModelRecommendation>,
        safe: List<ModelRecommendation>,
    ): List<List<ModelRecommendation>> {
        return listOf(
            matchedPreferred.relativeToBaseline(baseline),
            preferred.relativeToBaseline(baseline),
            matchedSafe.relativeToBaseline(baseline),
            safe.relativeToBaseline(baseline),
        ).filter { it.isNotEmpty() }
    }

    private fun normalizeFamily(model: ModelInfo): String {
        val normalized = "${model.groupName} ${model.name} ${model.fileName}"
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "")
        return when {
            normalized.contains("qwen") -> "qwen"
            normalized.contains("tinyllama") -> "tinyllama"
            normalized.contains("redhat1406") -> "redhat1406"
            normalized.contains("llama") -> "llama"
            normalized.contains("mixtral") || normalized.contains("mistral") || normalized.contains("ministral") -> "mistral"
            normalized.contains("tinygemma") || normalized.contains("gemma") -> "gemma"
            normalized.contains("phi") -> "phi"
            normalized.contains("deepseek") -> "deepseek"
            else -> normalized.takeWhile { !it.isDigit() }.ifBlank { "other" }
        }
    }

    private fun resolveSubtype(model: ModelInfo): ModelSubtype {
        val normalized = buildCapabilityText(model)
        return when {
            normalized.contains("coder") || normalized.contains("fim") || normalized.contains("fill in the middle") -> ModelSubtype.CODER
            normalized.contains("instruct") || normalized.contains("instruction") || normalized.contains("aligned") -> ModelSubtype.INSTRUCT
            normalized.contains("chat") || normalized.contains("assistant") || normalized.contains("conversational") -> ModelSubtype.CHAT
            else -> ModelSubtype.BASE
        }
    }

    private fun parseOrEstimateParameterSize(model: ModelInfo): ParameterEstimate {
        val regex = Regex("""(\d+(?:\.\d+)?)\s*b""", RegexOption.IGNORE_CASE)
        val fileMatch = regex.find(model.fileName)
        val repoMatch = regex.find("${model.name} ${model.groupName}")
        val parsed = fileMatch?.groupValues?.get(1)?.toFloatOrNull()
            ?: repoMatch?.groupValues?.get(1)?.toFloatOrNull()
        if (parsed != null) {
            return ParameterEstimate(parsed, ParameterSizeSource.PARSED)
        }
        return ParameterEstimate(
            estimateParameterSizeFromFile(model.sizeGb, quantTierOf(model.quantization, model.fileName)),
            ParameterSizeSource.ESTIMATED
        )
    }

    private fun estimateParameterSizeFromFile(sizeGb: Float, quantTier: QuantTier): Float = when (quantTier) {
        QuantTier.Q2_Q3 -> when {
            sizeGb < 1.0f -> 1f
            sizeGb < 2.2f -> 3f
            sizeGb < 4.5f -> 7f
            sizeGb < 9f -> 13f
            else -> 30f
        }
        QuantTier.Q4, QuantTier.UNKNOWN -> when {
            sizeGb < 1.4f -> 1f
            sizeGb < 3.2f -> 3f
            sizeGb < 6.5f -> 7f
            sizeGb < 12f -> 13f
            else -> 30f
        }
        QuantTier.Q5_Q6 -> when {
            sizeGb < 1.8f -> 1f
            sizeGb < 4.0f -> 3f
            sizeGb < 8.0f -> 7f
            sizeGb < 14f -> 13f
            else -> 30f
        }
        QuantTier.Q8 -> when {
            sizeGb < 2.6f -> 1f
            sizeGb < 5.5f -> 3f
            sizeGb < 10f -> 7f
            sizeGb < 18f -> 13f
            else -> 30f
        }
        QuantTier.F16_BF16 -> when {
            sizeGb < 4f -> 1f
            sizeGb < 8f -> 3f
            sizeGb < 15f -> 7f
            sizeGb < 26f -> 13f
            else -> 30f
        }
    }

    private fun quantTierOf(quantization: String, fileName: String): QuantTier {
        val value = "$quantization $fileName".lowercase(Locale.US)
        return when {
            value.contains("bf16") || value.contains("f16") -> QuantTier.F16_BF16
            value.contains("q8") -> QuantTier.Q8
            value.contains("q6") || value.contains("q5") -> QuantTier.Q5_Q6
            value.contains("iq4") || value.contains("q4") -> QuantTier.Q4
            value.contains("iq3") || value.contains("q3") || value.contains("iq2") || value.contains("q2") -> QuantTier.Q2_Q3
            else -> QuantTier.UNKNOWN
        }
    }

    private fun inferCapabilities(model: ModelInfo): ModelCapabilities {
        val sources = buildCapabilitySources(model)
        val coderSignals = sources.count { source ->
            source.contains("coder") ||
                source.contains("code") ||
                source.contains("fim") ||
                source.contains("fill-in-the-middle") ||
                source.contains("programming")
        }
        val chatSignals = sources.count { source ->
            source.contains("chat") ||
                source.contains("assistant") ||
                source.contains("conversational") ||
                source.contains("conversation") ||
                source.contains("text-generation")
        }
        val instructSignals = sources.count { source ->
            source.contains("instruct") ||
                source.contains("instruction") ||
                source.contains("aligned")
        }
        val mixedSignalKinds = listOf(coderSignals > 0, chatSignals > 0, instructSignals > 0).count { it }
        val confidence = (0.2f +
            (coderSignals.coerceAtMost(2) * 0.2f) +
            (chatSignals.coerceAtMost(2) * 0.2f) +
            (instructSignals.coerceAtMost(2) * 0.2f))
            .coerceAtMost(1.0f)
            .let { if (mixedSignalKinds >= 2) minOf(it, 0.75f) else it }
        val coderAffinity = signalAffinity(coderSignals)
        val chatAffinity = signalAffinity(chatSignals)
        val instructAffinity = signalAffinity(instructSignals)

        return ModelCapabilities(
            isCoder = coderSignals > 0,
            isChatOptimized = chatSignals > 0,
            isInstruct = instructSignals > 0,
            confidence = confidence,
            coderAffinity = coderAffinity,
            chatAffinity = chatAffinity,
            instructAffinity = instructAffinity,
        )
    }

    private fun signalAffinity(signalCount: Int): Float = when {
        signalCount >= 3 -> 1.0f
        signalCount == 2 -> 0.75f
        signalCount == 1 -> 0.45f
        else -> 0f
    }

    private fun kvBudgetMbPerB(useCase: UserUseCase): Float = when (useCase) {
        UserUseCase.CODING -> 220f
        UserUseCase.GENERAL -> 140f
        UserUseCase.MIXED -> 180f
    }

    private fun overheadMultiplier(quantTier: QuantTier): Float = when (quantTier) {
        QuantTier.Q2_Q3 -> 0.15f
        QuantTier.Q4 -> 0.25f
        QuantTier.Q5_Q6 -> 0.32f
        QuantTier.Q8 -> 0.45f
        QuantTier.F16_BF16 -> 0.70f
        QuantTier.UNKNOWN -> 0.30f
    }

    private fun cpuTier(deviceProfile: DeviceProfile, effectiveAvailableRamMb: Int): CpuTier {
        if (deviceProfile.isEmulator) {
            return when {
                deviceProfile.cpuCores >= 8 && effectiveAvailableRamMb >= 16384 -> CpuTier.HIGH
                effectiveAvailableRamMb >= 8192 -> CpuTier.MID
                else -> CpuTier.LOW
            }
        }

        var tier = when {
            deviceProfile.cpuCores <= 4 -> CpuTier.LOW
            deviceProfile.cpuCores <= 8 -> CpuTier.MID
            else -> CpuTier.HIGH
        }
        if (effectiveAvailableRamMb < 4096 || !deviceProfile.abi.contains("arm64", true)) {
            tier = downgradeCpuTier(tier)
        }
        if (tier == CpuTier.MID && deviceProfile.cpuCores >= 8 && effectiveAvailableRamMb >= 8192) {
            tier = CpuTier.HIGH
        }
        return tier
    }

    private fun downgradeCpuTier(tier: CpuTier): CpuTier = when (tier) {
        CpuTier.HIGH -> CpuTier.MID
        CpuTier.MID -> CpuTier.LOW
        CpuTier.LOW -> CpuTier.LOW
    }

    private fun cpuFitClass(cpuTier: CpuTier, parameterSizeB: Float, quantTier: QuantTier): CpuFitClass = when (cpuTier) {
        CpuTier.LOW -> when {
            parameterSizeB >= 13f || quantTier == QuantTier.F16_BF16 -> CpuFitClass.EXTREME
            parameterSizeB >= 7f && quantRank(quantTier) >= quantRank(QuantTier.Q5_Q6) -> CpuFitClass.HEAVY
            parameterSizeB > 3f && quantTier == QuantTier.Q8 -> CpuFitClass.HEAVY
            quantRank(quantTier) >= quantRank(QuantTier.Q5_Q6) -> CpuFitClass.OK
            parameterSizeB > 3f -> CpuFitClass.OK
            else -> CpuFitClass.EASY
        }
        CpuTier.MID -> when {
            parameterSizeB >= 30f || quantTier == QuantTier.F16_BF16 -> CpuFitClass.EXTREME
            parameterSizeB >= 13f -> CpuFitClass.HEAVY
            parameterSizeB >= 7f && quantTier == QuantTier.Q8 -> CpuFitClass.HEAVY
            parameterSizeB >= 7f -> CpuFitClass.OK
            parameterSizeB <= 3f && quantTier == QuantTier.Q8 -> CpuFitClass.EASY
            else -> CpuFitClass.EASY
        }
        CpuTier.HIGH -> when {
            parameterSizeB >= 30f -> CpuFitClass.EXTREME
            parameterSizeB >= 13f || quantTier == QuantTier.F16_BF16 -> CpuFitClass.HEAVY
            parameterSizeB >= 7f -> CpuFitClass.OK
            parameterSizeB <= 3f && quantTier == QuantTier.Q8 -> CpuFitClass.EASY
            quantTier == QuantTier.Q8 -> CpuFitClass.OK
            else -> CpuFitClass.EASY
        }
    }

    private fun cpuFit(cpuFitClass: CpuFitClass, parameterSizeB: Float, cpuTier: CpuTier): Float {
        var fit = when (cpuFitClass) {
            CpuFitClass.EASY -> 1.0f
            CpuFitClass.OK -> 0.7f
            CpuFitClass.HEAVY -> 0.4f
            CpuFitClass.EXTREME -> 0.1f
        }
        if (parameterSizeB >= 13f && cpuTier != CpuTier.HIGH) fit *= 0.80f
        else if (parameterSizeB >= 7f && cpuTier != CpuTier.HIGH) fit *= 0.85f
        return fit.coerceIn(0f, 1f)
    }

    private fun memoryFit(memoryRatio: Float): Float = when {
        memoryRatio >= 1.7f -> 0.92f
        memoryRatio >= 1.35f -> 0.84f
        memoryRatio >= 1.05f -> 0.72f
        memoryRatio >= 0.85f -> 0.50f
        memoryRatio >= 0.70f -> 0.30f
        else -> 0.12f
    }

    private fun preferenceFit(
        preference: UserPreference,
        parameterSizeB: Float,
        quantTier: QuantTier,
        capabilities: ModelCapabilities,
        memoryFit: Float,
    ): Float {
        val sizeTierFit = when {
            memoryFit < 0.30f -> 0.10f
            parameterSizeB in 2.5f..7.5f && quantRank(quantTier) <= quantRank(QuantTier.Q5_Q6) -> 0.95f
            parameterSizeB <= 3f -> 0.8f
            parameterSizeB >= 7f && quantRank(quantTier) <= quantRank(QuantTier.Q5_Q6) -> 0.8f
            else -> 0.5f
        }

        val useCaseBase = useCaseAffinity(capabilities, preference.useCase)

        val confidenceWeight = if (useCaseBase >= 0.8f) {
            0.7f + (0.3f * capabilities.confidence)
        } else {
            0.5f + (0.5f * capabilities.confidence)
        }
        val capabilityFit = (useCaseBase * confidenceWeight).coerceIn(0f, 1f)
        return ((sizeTierFit * 0.35f) + (capabilityFit * 0.65f)).coerceIn(0f, 1f)
    }

    private fun trustFit(downloads: Long, lastUpdatedEpochMs: Long?, confidence: Float): Float {
        val popularityFit = ((log10(max(downloads, 1L).toDouble()) / 6.0).toFloat()).coerceIn(0f, 1f)
        val maintenanceFit = if (lastUpdatedEpochMs == null) {
            0.5f
        } else {
            val days = ((System.currentTimeMillis() - lastUpdatedEpochMs) / MILLIS_PER_DAY).coerceAtLeast(0)
            when {
                days < 30 -> 1.0f
                days < 90 -> 0.8f
                days < 180 -> 0.6f
                days < 365 -> 0.4f
                else -> 0.2f
            }
        }
        return ((popularityFit * 0.7f) + (maintenanceFit * 0.3f)) * (0.7f + (0.3f * confidence))
    }

    private fun latencyScore(estimatedRamMb: Int, effectiveAvailableRamMb: Int, cpuFit: Float): Float {
        val minRam = 200f
        val maxRam = max(effectiveAvailableRamMb * 2f, minRam + 1f)
        val normalizedRam = ((estimatedRamMb - minRam) / (maxRam - minRam)).coerceIn(0f, 1f)
        val inverseMemory = 1f - normalizedRam
        return (inverseMemory * 0.6f) + (cpuFit * 0.4f)
    }

    private fun qualityRankScore(
        recommendation: ModelRecommendation,
        preference: UserPreference,
    ): Float {
        val capabilities = recommendation.profile.capabilities
        val useCaseAffinity = useCaseAffinity(capabilities, preference.useCase)
        val paramFit = when {
            recommendation.parameterSizeB >= 30f -> 1.0f
            recommendation.parameterSizeB >= 13f -> 0.92f
            recommendation.parameterSizeB >= 9f -> 0.85f
            recommendation.parameterSizeB >= 7f -> 0.75f
            recommendation.parameterSizeB >= 3f -> 0.55f
            else -> 0.2f
        }
        val quantFit = (quantRank(recommendation.quantTier) / 4f).coerceIn(0f, 1f)
        val tradeoffFit = when (recommendation.performance) {
            PerformanceLevel.SLOW -> 1.0f
            PerformanceLevel.BALANCED -> 0.88f
            PerformanceLevel.FAST -> 0.25f
            PerformanceLevel.RISKY -> 0.2f
        }
        val useCaseBias = when {
            isStrongUseCaseMatch(recommendation, preference.useCase) -> 0.24f
            useCaseAffinity >= 0.60f -> 0.12f
            useCaseAffinity >= 0.45f -> 0.04f
            else -> -0.08f
        }
        val stretchBias = when {
            recommendation.performance == PerformanceLevel.SLOW &&
                recommendation.memoryRatio >= 0.78f -> 0.06f
            recommendation.performance == PerformanceLevel.BALANCED &&
                recommendation.parameterSizeB >= 7f &&
                recommendation.memoryRatio >= 0.90f -> 0.04f
            recommendation.performance == PerformanceLevel.BALANCED &&
                recommendation.parameterSizeB >= 4f &&
                recommendation.memoryRatio >= 0.85f -> 0.02f
            else -> 0f
        }
        return (
            (paramFit * 0.48f) +
                (quantFit * 0.25f) +
                (recommendation.profile.memoryFit * 0.10f) +
                (tradeoffFit * 0.17f) +
                useCaseBias +
                stretchBias
            ).coerceIn(0f, 1.1f)
    }

    private fun recommendedRankScore(
        recommendation: ModelRecommendation,
        preference: UserPreference,
    ): Float {
        val useCaseAffinity = useCaseAffinity(recommendation.profile.capabilities, preference.useCase)
        val useCaseBias = when {
            isStrongUseCaseMatch(recommendation, preference.useCase) -> 0.20f
            useCaseAffinity >= 0.65f -> 0.10f
            useCaseAffinity >= 0.45f -> 0.03f
            else -> -0.08f
        }
        val performanceBias = when (recommendation.performance) {
            PerformanceLevel.BALANCED -> 0.12f
            PerformanceLevel.FAST -> 0.00f
            PerformanceLevel.SLOW -> 0.03f
            PerformanceLevel.RISKY -> -0.12f
        }
        val balanceBias = when {
            recommendation.memoryRatio >= 1.6f &&
                recommendation.parameterSizeB in 4f..9f -> 0.09f
            recommendation.memoryRatio >= 1.35f &&
                recommendation.parameterSizeB in 3.5f..7.5f -> 0.06f
            recommendation.memoryRatio >= 1.15f &&
                recommendation.parameterSizeB in 3.5f..7f -> 0.03f
            recommendation.performance == PerformanceLevel.SLOW &&
                recommendation.parameterSizeB >= 4f &&
                recommendation.memoryRatio >= 1.0f -> 0.04f
            recommendation.parameterSizeB < 2f &&
                recommendation.memoryRatio >= 1.35f -> -0.07f
            recommendation.parameterSizeB <= 3f &&
                recommendation.memoryRatio >= 1.45f -> -0.03f
            recommendation.parameterSizeB < 3f &&
                recommendation.memoryRatio >= 1.2f -> -0.04f
            recommendation.performance == PerformanceLevel.FAST &&
                recommendation.parameterSizeB <= 2.5f &&
                recommendation.memoryRatio >= 1.35f -> -0.05f
            else -> 0f
        }
        val codingBias = when {
            preference.useCase == UserUseCase.CODING &&
                recommendation.profile.capabilities.coderAffinity >= 0.45f &&
                recommendation.parameterSizeB >= 3.5f &&
                recommendation.memoryRatio >= 1.0f -> 0.07f
            preference.useCase == UserUseCase.CODING &&
                recommendation.parameterSizeB < 3f &&
                recommendation.memoryRatio >= 1.3f -> -0.05f
            else -> 0f
        }
        return (recommendation.overallScore + useCaseBias + performanceBias + balanceBias + codingBias).coerceIn(0f, 1.25f)
    }

    private fun fastestPriority(recommendation: ModelRecommendation): Int = when (recommendation.performance) {
        PerformanceLevel.FAST -> 0
        PerformanceLevel.BALANCED -> 1
        PerformanceLevel.SLOW -> 2
        PerformanceLevel.RISKY -> 3
    }

    private fun performanceLevel(memoryFit: Float, cpuFit: Float, memoryRatio: Float): PerformanceLevel = when {
        memoryFit >= 0.85f && (cpuFit >= 0.75f || (cpuFit >= 0.7f && memoryRatio >= 2.5f)) -> PerformanceLevel.FAST
        memoryFit >= 0.48f && cpuFit >= 0.4f -> PerformanceLevel.BALANCED
        memoryFit >= 0.30f -> PerformanceLevel.SLOW
        else -> PerformanceLevel.RISKY
    }

    private fun List<ModelRecommendation>.relativeToBaseline(baseline: ModelRecommendation?): List<ModelRecommendation> {
        baseline ?: return this
        return filter { candidate ->
            candidate.parameterSizeB > baseline.parameterSizeB ||
                quantRank(candidate.quantTier) > quantRank(baseline.quantTier) ||
                candidate.estimatedRamMb >= (baseline.estimatedRamMb * 1.10f)
        }
    }

    private fun isMeaningfulQualityUpgrade(
        candidate: ModelRecommendation,
        baseline: ModelRecommendation,
    ): Boolean {
        return candidate.parameterSizeB > baseline.parameterSizeB ||
            quantRank(candidate.quantTier) > quantRank(baseline.quantTier) ||
            candidate.estimatedRamMb >= (baseline.estimatedRamMb * 1.10f)
    }

    private fun isUseCaseMatch(
        recommendation: ModelRecommendation,
        useCase: UserUseCase,
    ): Boolean {
        val capabilities = recommendation.profile.capabilities
        val generalAffinity = generalAffinity(capabilities)
        return when (useCase) {
            UserUseCase.CODING -> useCaseAffinity(capabilities, useCase) >= 0.35f || capabilities.coderAffinity >= 0.45f
            UserUseCase.GENERAL -> useCaseAffinity(capabilities, useCase) >= 0.35f || generalAffinity >= 0.45f
            UserUseCase.MIXED -> capabilities.coderAffinity >= 0.30f || generalAffinity >= 0.30f
        }
    }

    private fun isStrongUseCaseMatch(
        recommendation: ModelRecommendation,
        useCase: UserUseCase,
    ): Boolean {
        val capabilities = recommendation.profile.capabilities
        val generalAffinity = generalAffinity(capabilities)
        return when (useCase) {
            UserUseCase.CODING ->
                capabilities.coderAffinity >= 0.60f &&
                    capabilities.coderAffinity >= (generalAffinity + 0.05f)
            UserUseCase.GENERAL ->
                generalAffinity >= 0.60f &&
                    generalAffinity >= (capabilities.coderAffinity + 0.05f)
            UserUseCase.MIXED ->
                capabilities.coderAffinity >= 0.45f &&
                    generalAffinity >= 0.45f &&
                    abs(capabilities.coderAffinity - generalAffinity) <= 0.25f
        }
    }

    private fun isQualityUseCaseMatch(
        recommendation: ModelRecommendation,
        useCase: UserUseCase,
    ): Boolean {
        val capabilities = recommendation.profile.capabilities
        val generalAffinity = generalAffinity(capabilities)
        return when (useCase) {
            UserUseCase.CODING ->
                capabilities.coderAffinity >= 0.40f ||
                    (generalAffinity >= 0.70f && capabilities.coderAffinity >= 0.20f)
            UserUseCase.GENERAL ->
                generalAffinity >= 0.40f ||
                    (capabilities.coderAffinity >= 0.70f && generalAffinity >= 0.20f)
            UserUseCase.MIXED -> capabilities.coderAffinity >= 0.30f || generalAffinity >= 0.30f
        }
    }

    private fun generalAffinity(capabilities: ModelCapabilities): Float =
        max(capabilities.chatAffinity, capabilities.instructAffinity)

    private fun useCaseAffinity(capabilities: ModelCapabilities, useCase: UserUseCase): Float {
        val generalAffinity = generalAffinity(capabilities)
        return when (useCase) {
            UserUseCase.CODING -> (capabilities.coderAffinity - (0.35f * generalAffinity)).coerceIn(0f, 1f)
            UserUseCase.GENERAL -> (generalAffinity - (0.30f * capabilities.coderAffinity)).coerceIn(0f, 1f)
            UserUseCase.MIXED -> (
                minOf(capabilities.coderAffinity, generalAffinity) +
                    (0.25f * max(capabilities.coderAffinity, generalAffinity))
                ).coerceIn(0f, 1f)
        }
    }

    private fun recommendationReason(
        memoryFit: Float,
        cpuFit: Float,
        preferenceFit: Float,
        trustFit: Float,
        userPreference: UserPreference,
        capabilities: ModelCapabilities,
    ): RecommendationReason {
        val weightedMemory = memoryFit * 0.5f
        val weightedPreference = preferenceFit * 0.2f
        val weightedCpu = cpuFit * 0.2f
        val weightedTrust = trustFit * 0.1f
        return when {
            capabilities.isCoder && userPreference.useCase == UserUseCase.CODING && preferenceFit >= 0.35f ->
                RecommendationReason.CODING_MATCH
            (capabilities.isInstruct || capabilities.isChatOptimized) && userPreference.useCase != UserUseCase.CODING && preferenceFit >= 0.35f ->
                RecommendationReason.CHAT_MATCH
            weightedMemory >= weightedPreference && weightedMemory >= weightedCpu && weightedMemory >= weightedTrust -> RecommendationReason.RAM_FIT
            weightedCpu >= weightedTrust -> RecommendationReason.CPU_FIT
            weightedTrust > 0.06f -> RecommendationReason.TRUST_SIGNAL
            else -> RecommendationReason.BEST_OVERALL
        }
    }

    private fun reasonMessage(reason: RecommendationReason): String = when (reason) {
        RecommendationReason.RAM_FIT -> "Fits your RAM comfortably"
        RecommendationReason.CPU_FIT -> "Runs efficiently on your CPU"
        RecommendationReason.CODING_MATCH -> "Optimized for coding tasks"
        RecommendationReason.CHAT_MATCH -> "Well suited for chat and instruction use"
        RecommendationReason.TRUST_SIGNAL -> "Popular and actively maintained"
        RecommendationReason.BEST_OVERALL -> "Best overall fit for your device"
    }

    private fun buildCapabilitySources(model: ModelInfo): List<String> =
        listOf(
            "${model.name} ${model.groupName}".lowercase(Locale.US),
            model.fileName.lowercase(Locale.US),
            model.description.lowercase(Locale.US),
            model.pipelineTag.orEmpty().lowercase(Locale.US),
            model.capabilityTags.joinToString(" ").lowercase(Locale.US),
        ).filter { it.isNotBlank() }

    private fun buildCapabilityText(model: ModelInfo): String =
        buildCapabilitySources(model).joinToString(" ")

    private fun quantRank(quantTier: QuantTier): Int = when (quantTier) {
        QuantTier.Q2_Q3 -> 0
        QuantTier.Q4 -> 1
        QuantTier.Q5_Q6 -> 2
        QuantTier.Q8 -> 3
        QuantTier.F16_BF16 -> 4
        QuantTier.UNKNOWN -> 1
    }

    private fun Float.roundToIntString(): String {
        val whole = toInt()
        return if (this == whole.toFloat()) {
            whole.toString()
        } else {
            String.format(Locale.US, "%.1f", this)
        }
    }

    private fun Float.signAsComparator(): Int = when {
        this > 0f -> 1
        this < 0f -> -1
        else -> 0
    }

    private data class ParameterEstimate(
        val sizeB: Float,
        val source: ParameterSizeSource,
    )

    private companion object {
        const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
    }
}
