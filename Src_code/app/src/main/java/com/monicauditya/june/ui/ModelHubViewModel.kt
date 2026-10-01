package com.monicauditya.june.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monicauditya.june.data.HFModelsAPI
import com.monicauditya.june.data.SharedPrefStore
import com.monicauditya.june.domain.ChatUseCase
import com.monicauditya.june.domain.LoadModelResult
import com.monicauditya.june.domain.ModelValidationResult
import com.monicauditya.june.modelhub.DeviceProfile
import com.monicauditya.june.modelhub.DeviceProfiler
import com.monicauditya.june.modelhub.DownloadState
import com.monicauditya.june.modelhub.DownloadPausedException
import com.monicauditya.june.modelhub.FeaturedRecommendations
import com.monicauditya.june.modelhub.HuggingFaceFile
import com.monicauditya.june.modelhub.HuggingFaceService
import com.monicauditya.june.modelhub.ModelDownloadManager
import com.monicauditya.june.modelhub.ModelDownloadStatus
import com.monicauditya.june.modelhub.ModelInfo
import com.monicauditya.june.modelhub.ModelRecommendation
import com.monicauditya.june.modelhub.PerformanceLevel
import com.monicauditya.june.modelhub.RecommendationEngine
import com.monicauditya.june.modelhub.SessionLengthProfile
import com.monicauditya.june.modelhub.UserPreference
import com.monicauditya.june.modelhub.UserUseCase
import com.monicauditya.hf_model_hub_api.HFModelInfo
import com.monicauditya.hf_model_hub_api.HFModelSearch
import com.monicauditya.hf_model_hub_api.HFModelTree
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.koin.android.annotation.KoinViewModel
import java.io.File
import java.util.LinkedHashMap
import java.util.Locale
import kotlin.math.abs

private const val PREF_USE_CASE = "model_pref_use_case"
private const val PREF_ONBOARDING_DONE = "model_pref_onboarding_done"
private const val PREF_HAS_EDITED = "model_pref_has_edited"
const val PREF_ACTIVE_MODEL_PATH = "active_model_path"
const val PREF_ACTIVE_MODEL_NAME = "active_model_name"
const val PREF_ACTIVE_MODEL_ID = "active_model_id"
private const val BROWSE_INITIAL_LIMIT_BLANK = 24
private const val BROWSE_INITIAL_LIMIT_QUERY = 32
private const val BROWSE_APPEND_STEP_BLANK = 24
private const val BROWSE_APPEND_STEP_QUERY = 16

data class ModelHubUiState(
    val deviceProfile: DeviceProfile? = null,
    val models: List<ModelInfo> = emptyList(),
    val recommendations: List<ModelRecommendation> = emptyList(),
    val featuredRecommendations: FeaturedRecommendations = FeaturedRecommendations(null, null, null),
    val recommendationWarning: String? = null,
    val downloadStatuses: Map<String, ModelDownloadStatus> = emptyMap(),
    val activeModelId: String? = null,
    val activeDownloadModelId: String? = null,
    val busyModelName: String? = null,
    val isLoading: Boolean = true,
    val browseQuery: String = "",
    val browseItems: List<BrowseRepoSummary> = emptyList(),
    val isBrowseLoading: Boolean = false,
    val isBrowseAppending: Boolean = false,
    val canLoadMoreBrowse: Boolean = true,
    val browseError: String? = null,
    val browseDetails: BrowseModelDetailsState = BrowseModelDetailsState(),
    val infoMessage: String? = null,
    val userPreference: UserPreference = UserPreference(),
    val navigateToChatToken: Long? = null,
)

data class BrowseRepoSummary(
    val modelId: String,
    val repoIds: List<String>,
    val title: String,
    val author: String,
    val description: String,
    val downloads: Long,
    val variantCount: Int,
    val family: String,
    val tags: List<String>,
    val fasterCount: Int = 0,
    val balancedCount: Int = 0,
    val slowCount: Int = 0,
    val riskCount: Int = 0,
)

data class BrowseModelDetailsState(
    val modelId: String? = null,
    val title: String = "",
    val author: String = "",
    val downloads: Long = 0,
    val likes: Long = 0,
    val tags: List<String> = emptyList(),
    val description: String = "",
    val variants: List<ModelRecommendation> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

private data class RecommendationLoadResult(
    val models: List<ModelInfo>,
    val warning: String? = null,
)

private data class BrowseRepoSource(
    val modelId: String,
    val author: String,
    val description: String,
    val downloads: Long,
    val tags: List<String>,
    val variants: List<ModelInfo>,
    val likes: Long = 0L,
)

@KoinViewModel
class ModelHubViewModel(
    private val deviceProfiler: DeviceProfiler,
    private val recommendationEngine: RecommendationEngine,
    private val huggingFaceService: HuggingFaceService,
    private val hfModelsAPI: HFModelsAPI,
    private val modelDownloadManager: ModelDownloadManager,
    private val chatUseCase: ChatUseCase,
    private val sharedPrefStore: SharedPrefStore,
) : ViewModel() {

    private val _state = MutableStateFlow(ModelHubUiState())
    val state: StateFlow<ModelHubUiState> = _state.asStateFlow()

    private var refreshJob: Job? = null
    private var browseJob: Job? = null
    private var browseDetailsJob: Job? = null
    private var activeDownloadJob: Job? = null
    private var activateModelJob: Job? = null
    private var previousFeaturedOrder: Map<String, Int> = emptyMap()
    private var refreshGeneration: Long = 0
    private var browseRequestedLimit: Int = BROWSE_INITIAL_LIMIT_BLANK
    private var browsePaginationQuery: String = ""

    private val searchCache = cappedCache<String, List<HFModelSearch.ModelSearchResult>>(20)
    private val treeCache = cappedCache<String, List<HFModelTree.HFModelFile>>(100)
    private val infoCache = cappedCache<String, HFModelInfo.ModelInfo>(100)
    private val variantCache = cappedCache<String, List<ModelInfo>>(100)
    private val recommendationSeedCache = cappedCache<String, List<HFModelSearch.ModelSearchResult>>(12)

    init {
        refresh()
    }

    fun refresh(reloadBrowse: Boolean = true) {
        refreshJob?.cancel()
        val generation = ++refreshGeneration
        refreshJob = viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true) }

            val deviceProfile = _state.value.deviceProfile ?: deviceProfiler.getDeviceProfile()
            val preference = loadUserPreference()
            val loadResult = loadRecommendationModels(preference)
            ensureLatestRefresh(generation)
            val models = loadResult.models
            val downloadStatuses = models.associate { model ->
                model.id to modelDownloadManager.getDownloadStatus(model)
            }
            val installedModelIds = (_state.value.downloadStatuses + downloadStatuses)
                .filterValues { it.state == DownloadState.DOWNLOADED }
                .keys
            val result = recommendationEngine.recommend(
                deviceProfile = deviceProfile,
                models = models,
                userPreference = preference,
                installedModelIds = installedModelIds,
                previousFeaturedOrder = previousFeaturedOrder,
            )
            ensureLatestRefresh(generation)
            val nextFeaturedOrder = listOfNotNull(
                result.featured.recommended,
                result.featured.fastest,
                result.featured.highestQuality
            ).mapIndexed { index, recommendation -> recommendation.model.id to index }.toMap()
            _state.update { current ->
                val mergedDownloadStatuses = current.downloadStatuses + downloadStatuses
                previousFeaturedOrder = nextFeaturedOrder
                current.copy(
                    deviceProfile = deviceProfile,
                    userPreference = preference,
                    models = models,
                    recommendations = result.recommendations,
                    featuredRecommendations = result.featured,
                    recommendationWarning = loadResult.warning,
                    downloadStatuses = mergedDownloadStatuses,
                    isLoading = false,
                    activeModelId = current.activeModelId?.takeIf { activeId ->
                        mergedDownloadStatuses[activeId]?.state == DownloadState.DOWNLOADED
                    }
                )
            }

            if (reloadBrowse) {
                refreshBrowse()
            }
        }
    }

    fun savePreference(useCase: UserUseCase, completedOnboarding: Boolean = true, hasEdited: Boolean = true) {
        val openDetailId = _state.value.browseDetails.modelId
        val updatedPreference = UserPreference(
            useCase = useCase,
            hasCompletedOnboarding = completedOnboarding,
            hasEditedPreferences = hasEdited,
            sessionLengthProfile = SessionLengthProfile.NORMAL,
        )
        sharedPrefStore.put(PREF_USE_CASE, useCase.name)
        sharedPrefStore.put(PREF_ONBOARDING_DONE, completedOnboarding)
        sharedPrefStore.put(PREF_HAS_EDITED, hasEdited)
        previousFeaturedOrder = emptyMap()
        _state.update { it.copy(userPreference = updatedPreference) }
        refresh(reloadBrowse = false)
        openDetailId?.let { loadBrowseDetails(it, updatedPreference) }
    }

    fun skipPreferenceOnboarding() {
        val openDetailId = _state.value.browseDetails.modelId
        val updatedPreference = UserPreference(
            useCase = UserUseCase.MIXED,
            hasCompletedOnboarding = true,
            hasEditedPreferences = false,
            sessionLengthProfile = SessionLengthProfile.NORMAL,
        )
        sharedPrefStore.put(PREF_USE_CASE, UserUseCase.MIXED.name)
        sharedPrefStore.put(PREF_ONBOARDING_DONE, true)
        sharedPrefStore.put(PREF_HAS_EDITED, false)
        previousFeaturedOrder = emptyMap()
        _state.update { it.copy(userPreference = updatedPreference) }
        refresh(reloadBrowse = false)
        openDetailId?.let { loadBrowseDetails(it, updatedPreference) }
    }

    fun updateBrowseQuery(query: String) {
        _state.update { it.copy(browseQuery = query) }
        refreshBrowse(reset = true)
    }

    fun loadMoreBrowse() {
        val current = _state.value
        if (current.isBrowseLoading || current.isBrowseAppending || !current.canLoadMoreBrowse) return
        val query = current.browseQuery.trim()
        if (browsePaginationQuery != query) {
            browsePaginationQuery = query
            browseRequestedLimit = initialBrowseLimit(query)
        }
        browseRequestedLimit += browseAppendStep(query)
        refreshBrowse(reset = false)
    }

    fun loadBrowseDetails(modelId: String, preferenceOverride: UserPreference? = null) {
        val deviceProfile = _state.value.deviceProfile ?: return
        val preference = preferenceOverride ?: _state.value.userPreference
        val installedIds = _state.value.downloadStatuses.filterValues { it.state == DownloadState.DOWNLOADED }.keys
        val summary = _state.value.browseItems.firstOrNull { it.modelId == modelId }
        val repoIds = summary?.repoIds ?: listOf(modelId)
        browseDetailsJob?.cancel()
        browseDetailsJob = viewModelScope.launch(Dispatchers.IO) {
            _state.update {
                it.copy(
                    browseDetails = BrowseModelDetailsState(
                        modelId = modelId,
                        isLoading = true
                    )
                )
            }

            runCatching {
                val repoSources = coroutineScope {
                    repoIds.map { repoId ->
                        async {
                            val info = getModelInfoCached(repoId)
                            val variants = getVariantsForRepo(
                                modelId = repoId,
                                repoName = info.modelId.substringAfterLast('/'),
                                description = buildBrowseDescription(info),
                                capabilityTags = info.tags,
                                pipelineTag = null,
                                downloads = info.numDownloads,
                                lastUpdatedEpochMs = info.lastModified.toEpochMillis()
                            )
                            BrowseRepoSource(
                                modelId = repoId,
                                author = info.author,
                                description = variants.firstOrNull()?.description ?: buildBrowseDescription(info),
                                downloads = info.numDownloads.toLong(),
                                tags = filterDisplayTags(info.tags),
                                variants = variants,
                                likes = info.numLikes.toLong(),
                            )
                        }
                    }.awaitAll()
                }.filter { it.variants.isNotEmpty() }

                if (repoSources.isEmpty()) {
                    error("No runnable GGUF variants found for this model group.")
                }

                val mergedVariants = dedupeRecommendationVariants(repoSources.flatMap { it.variants })
                val recommendations = recommendationEngine.recommend(
                    deviceProfile = deviceProfile,
                    models = mergedVariants,
                    userPreference = preference,
                    installedModelIds = installedIds,
                ).recommendations
                Triple(summary, repoSources, recommendations)
            }.fold(
                onSuccess = { (summaryData, repoSources, recommendations) ->
                    val primarySource = repoSources.maxByOrNull { it.downloads } ?: repoSources.first()
                    val detailStatuses = recommendations.associate { recommendation ->
                        recommendation.model.id to modelDownloadManager.getDownloadStatus(recommendation.model)
                    }
                    _state.update { current ->
                        if (current.browseDetails.modelId != modelId) {
                            current
                        } else {
                            current.copy(
                                browseDetails = BrowseModelDetailsState(
                                    modelId = modelId,
                                    title = summaryData?.title ?: recommendations.firstOrNull()?.model?.groupName.orEmpty(),
                                    author = summaryData?.author ?: primarySource.author,
                                    downloads = summaryData?.downloads ?: repoSources.sumOf { it.downloads },
                                    likes = repoSources.sumOf { it.likes },
                                    tags = repoSources.flatMap { it.tags }.distinct().take(8),
                                    description = summaryData?.description ?: primarySource.description,
                                    variants = recommendations,
                                    isLoading = false
                                ),
                                downloadStatuses = current.downloadStatuses + detailStatuses
                            )
                        }
                    }
                },
                onFailure = { error ->
                    _state.update { current ->
                        if (current.browseDetails.modelId != modelId) {
                            current
                        } else {
                            current.copy(
                            browseDetails = BrowseModelDetailsState(
                                modelId = modelId,
                                isLoading = false,
                                errorMessage = error.message ?: "Unable to load model details."
                            )
                        )
                        }
                    }
                }
            )
        }
    }

    fun clearBrowseDetails() {
        _state.update { it.copy(browseDetails = BrowseModelDetailsState()) }
    }

    fun clearInfoMessage() {
        _state.update { it.copy(infoMessage = null) }
    }

    fun consumeNavigateToChat() {
        _state.update { it.copy(navigateToChatToken = null) }
    }

    private fun ensureLatestRefresh(generation: Long) {
        if (generation != refreshGeneration) throw CancellationException("Superseded refresh")
    }

    fun startDownload(model: ModelInfo) {
        val activeDownload = _state.value.activeDownloadModelId
        if (activeDownload != null) {
            if (activeDownload != model.id) {
                _state.update { it.copy(infoMessage = "Another download is already in progress.") }
            }
            return
        }

        activeDownloadJob = viewModelScope.launch(Dispatchers.IO) {
            _state.update { current ->
                current.copy(
                    activeDownloadModelId = model.id,
                    busyModelName = model.id,
                    infoMessage = null,
                    downloadStatuses = current.downloadStatuses + (
                        model.id to ModelDownloadStatus(state = DownloadState.DOWNLOADING)
                    )
                )
            }

            try {
                val result = modelDownloadManager.startDownload(model) { progress ->
                    _state.update { current ->
                        current.copy(
                            downloadStatuses = current.downloadStatuses + (
                                model.id to ModelDownloadStatus(
                                    state = DownloadState.DOWNLOADING,
                                    progress = progress
                                )
                            )
                        )
                    }
                }

                result.fold(
                    onSuccess = { file ->
                        when (val validation = chatUseCase.validateModel(file.absolutePath)) {
                            is ModelValidationResult.Success -> {
                                _state.update { current ->
                                    current.copy(
                                        activeDownloadModelId = null,
                                        busyModelName = null,
                                        downloadStatuses = current.downloadStatuses + (
                                            model.id to ModelDownloadStatus(
                                                state = DownloadState.DOWNLOADED,
                                                progress = 100,
                                                localPath = file.absolutePath
                                            )
                                        ),
                                        infoMessage = "${model.name} downloaded."
                                    )
                                }
                                refresh(reloadBrowse = false)
                            }
                            is ModelValidationResult.Failure -> {
                                file.delete()
                                _state.update { current ->
                                    current.copy(
                                        activeDownloadModelId = null,
                                        busyModelName = null,
                                        downloadStatuses = current.downloadStatuses + (
                                            model.id to ModelDownloadStatus(
                                                state = DownloadState.FAILED,
                                                errorMessage = validation.message
                                            )
                                        ),
                                        infoMessage = validation.message
                                    )
                                }
                            }
                        }
                    },
                    onFailure = { error ->
                        _state.update { current ->
                            current.copy(
                                activeDownloadModelId = null,
                                busyModelName = null,
                                downloadStatuses = current.downloadStatuses + (
                                    model.id to ModelDownloadStatus(
                                        state = DownloadState.FAILED,
                                        errorMessage = error.message ?: "Download failed."
                                    )
                                ),
                                infoMessage = error.message ?: "Download failed."
                            )
                        }
                    }
                )
            } catch (_: DownloadPausedException) {
                val pausedProgress = _state.value.downloadStatuses[model.id]?.progress ?: 0
                _state.update { current ->
                    current.copy(
                        activeDownloadModelId = null,
                        busyModelName = null,
                        downloadStatuses = current.downloadStatuses + (
                            model.id to ModelDownloadStatus(
                                state = DownloadState.PAUSED,
                                progress = pausedProgress,
                                errorMessage = "Download paused."
                            )
                        ),
                        infoMessage = "Download paused."
                    )
                }
            } catch (_: CancellationException) {
                val cancelledProgress = _state.value.downloadStatuses[model.id]?.progress ?: 0
                _state.update { current ->
                    current.copy(
                        activeDownloadModelId = null,
                        busyModelName = null,
                        downloadStatuses = current.downloadStatuses + (
                            model.id to ModelDownloadStatus(
                                state = DownloadState.CANCELLED,
                                progress = cancelledProgress,
                                errorMessage = "Download cancelled."
                            )
                        ),
                        infoMessage = "Download cancelled."
                    )
                }
            }
        }
    }

    fun cancelDownload(modelId: String) {
        if (_state.value.activeDownloadModelId != modelId) return
        modelDownloadManager.cancelActiveDownload()
    }

    fun pauseDownload(modelId: String) {
        if (_state.value.activeDownloadModelId != modelId) return
        modelDownloadManager.pauseActiveDownload()
    }

    fun useModel(model: ModelInfo) {
        val localPath = _state.value.downloadStatuses[model.id]?.localPath ?: return
        activateDownloadedModel(model.id, model.name, localPath)
    }

    fun useDownloadedModelById(modelId: String) {
        val localPath = _state.value.downloadStatuses[modelId]?.localPath ?: return
        val modelName = resolveDownloadedModelName(modelId, localPath)
        activateDownloadedModel(modelId, modelName, localPath)
    }

    private fun loadUserPreference(): UserPreference {
        val useCase = runCatching {
            UserUseCase.valueOf(sharedPrefStore.get(PREF_USE_CASE, UserUseCase.MIXED.name))
        }.getOrDefault(UserUseCase.MIXED)
        val onboardingDone = sharedPrefStore.get(PREF_ONBOARDING_DONE, false)
        val hasEdited = sharedPrefStore.get(PREF_HAS_EDITED, false)
        return UserPreference(
            useCase = useCase,
            hasCompletedOnboarding = onboardingDone,
            hasEditedPreferences = hasEdited,
            sessionLengthProfile = SessionLengthProfile.NORMAL,
        )
    }

    private fun activateDownloadedModel(modelId: String, modelName: String, localPath: String) {
        if (_state.value.activeModelId == modelId) {
            _state.update { current ->
                current.copy(
                    infoMessage = "$modelName is already active.",
                    navigateToChatToken = System.currentTimeMillis()
                )
            }
            return
        }

        val targetFile = File(localPath)
        val profile = _state.value.deviceProfile ?: deviceProfiler.getDeviceProfile()
        if (targetFile.exists()) {
            val fileSizeMb = targetFile.length().toFloat() / (1024f * 1024f)
            val estimatedRuntimeMb = when {
                fileSizeMb < 1024f -> fileSizeMb * 1.55f + 320f
                fileSizeMb < 3072f -> fileSizeMb * 1.7f + 420f
                else -> fileSizeMb * 1.85f + 520f
            }
            if (estimatedRuntimeMb > profile.usableRamMb * 1.02f) {
                _state.update { current ->
                    current.copy(
                        busyModelName = null,
                        infoMessage = "$modelName is too heavy to switch safely on this device right now."
                    )
                }
                return
            }
        }

        activateModelJob?.cancel()
        activateModelJob = viewModelScope.launch(Dispatchers.IO) {
            _state.update { current ->
                current.copy(
                    busyModelName = modelId,
                    infoMessage = "Loading $modelName..."
                )
            }

            if (chatUseCase.isGenerationRunning()) {
                chatUseCase.stopGeneration()
            }

            chatUseCase.setModelPath(localPath)
            when (val result = chatUseCase.loadModel(forceReload = true)) {
                is LoadModelResult.Success -> {
                    sharedPrefStore.put(PREF_ACTIVE_MODEL_PATH, localPath)
                    sharedPrefStore.put(PREF_ACTIVE_MODEL_NAME, modelName)
                    sharedPrefStore.put(PREF_ACTIVE_MODEL_ID, modelId)
                    _state.update { current ->
                        current.copy(
                            activeModelId = modelId,
                            busyModelName = null,
                            infoMessage = "$modelName is ready to use.",
                            navigateToChatToken = System.currentTimeMillis()
                        )
                    }
                }
                is LoadModelResult.Failure -> {
                    _state.update { current ->
                        current.copy(
                            activeModelId = null,
                            busyModelName = null,
                            infoMessage = result.message
                        )
                    }
                }
            }
        }
    }

    private fun resolveDownloadedModelName(modelId: String, localPath: String?): String {
        val model = _state.value.models.firstOrNull { it.id == modelId }
            ?: _state.value.recommendations.firstOrNull { it.model.id == modelId }?.model
            ?: _state.value.browseDetails.variants.firstOrNull { it.model.id == modelId }?.model
        return model?.name
            ?: modelId.substringBefore("::").ifBlank {
                localPath?.let { File(it).nameWithoutExtension }
                    ?: "Model"
            }
    }

    private suspend fun loadRecommendationModels(preference: UserPreference): RecommendationLoadResult {
        return runCatching {
            val seedItems = getRecommendationSeedModelsCached(preference.useCase, 16)
                .filterNot(::isMultimodalOrNonChatRepo)
            val broadFilter = when (preference.useCase) {
                UserUseCase.CODING -> "gguf"
                UserUseCase.GENERAL -> "gguf,conversational"
                UserUseCase.MIXED -> "gguf"
            }
            val broadItems = getTopModelsCached(
                query = "",
                limit = if (preference.useCase == UserUseCase.MIXED) 18 else 16,
                filter = broadFilter,
            )
                .filterNot(::isMultimodalOrNonChatRepo)
            val recentSupportItems = getTopModelsCached(
                query = "",
                limit = 6,
                filter = broadFilter,
                sort = HFModelSearch.ModelSortParam.CREATED_AT,
            ).filterNot(::isMultimodalOrNonChatRepo)
            val fastSupportItems = getTopModelsCached(
                query = "",
                limit = 10,
                filter = "gguf",
            ).filterNot(::isMultimodalOrNonChatRepo)
            val primaryModels = buildRecommendationShortlist(
                models = loadVariantsForSearchItems(
                    searchItems = (
                        seedItems +
                            broadItems.take(
                                when (preference.useCase) {
                                    UserUseCase.MIXED -> 8
                                    UserUseCase.CODING -> 5
                                    UserUseCase.GENERAL -> 5
                                }
                            ) +
                            recentSupportItems +
                            fastSupportItems.take(3)
                        ).distinctBy { it.id }
                ).let { dedupeRecommendationVariants(it, preference.useCase) },
                useCase = preference.useCase,
                limit = 32,
            )

            val models = if (primaryModels.isNotEmpty()) {
                primaryModels
            } else {
                val supportItems = when (preference.useCase) {
                    UserUseCase.CODING -> getRecommendationSeedModelsCached(UserUseCase.GENERAL, 8)
                    UserUseCase.GENERAL -> getRecommendationSeedModelsCached(UserUseCase.CODING, 6)
                    UserUseCase.MIXED -> emptyList()
                }.filterNot(::isMultimodalOrNonChatRepo)
                val fallbackTopItems = getTopModelsCached("", 24, "gguf")
                    .filterNot(::isMultimodalOrNonChatRepo)
                buildRecommendationShortlist(
                    models = loadVariantsForSearchItems(
                        searchItems = (
                            supportItems +
                                fallbackTopItems.take(14) +
                                getTopModelsCached("", 6, "gguf", HFModelSearch.ModelSortParam.CREATED_AT)
                                    .filterNot(::isMultimodalOrNonChatRepo)
                            ).distinctBy { it.id }
                    ).let { dedupeRecommendationVariants(it, preference.useCase) },
                    useCase = preference.useCase,
                    limit = 32,
                )
            }

            RecommendationLoadResult(
                models = models,
                warning = if (models.isEmpty()) "Live recommendations are unavailable right now." else null,
            )
        }.getOrElse { error ->
            val currentModels = _state.value.models
            RecommendationLoadResult(
                models = currentModels,
                warning = if (currentModels.isNotEmpty()) {
                    "Live recommendation discovery failed. Reusing the last live recommendation set. ${error.message.orEmpty()}".trim()
                } else {
                    "Live recommendation discovery failed and no previous live models are available. ${error.message.orEmpty()}".trim()
                }
            )
        }
    }

    private suspend fun loadVariantsForSearchItems(
        searchItems: List<HFModelSearch.ModelSearchResult>,
        maxConcurrency: Int = 6,
    ): List<ModelInfo> = coroutineScope {
        val semaphore = Semaphore(maxConcurrency)
        searchItems.map { searchItem ->
            async {
                semaphore.withPermit {
                    ensureActive()
                    val info = runCatching { getModelInfoCached(searchItem.id) }.getOrNull() ?: return@withPermit emptyList()
                    runCatching {
                        getVariantsForRepo(
                            modelId = searchItem.id,
                            repoName = info.modelId.substringAfterLast('/'),
                            description = buildBrowseDescription(info),
                            capabilityTags = info.tags,
                            pipelineTag = null,
                            downloads = info.numDownloads,
                            lastUpdatedEpochMs = info.lastModified.toEpochMillis()
                        )
                    }.getOrElse { emptyList() }
                }
            }
        }.awaitAll().flatten()
    }

    private fun refreshBrowse(reset: Boolean = false) {
        browseJob?.cancel()
        browseJob = viewModelScope.launch(Dispatchers.IO) {
            val query = _state.value.browseQuery.trim()
            val deviceProfile = _state.value.deviceProfile ?: return@launch
            val preference = _state.value.userPreference
            val installedIds = _state.value.downloadStatuses.filterValues { it.state == DownloadState.DOWNLOADED }.keys
            if (reset || browsePaginationQuery != query) {
                browsePaginationQuery = query
                browseRequestedLimit = initialBrowseLimit(query)
            }
            val requestLimit = browseRequestedLimit
            val appendMode = !reset && _state.value.browseItems.isNotEmpty()
            delay(120)
            _state.update { current ->
                current.copy(
                    isBrowseLoading = !appendMode,
                    isBrowseAppending = appendMode,
                    browseError = null,
                )
            }

            try {
                val rawSearchResults = getTopModelsCached(query, requestLimit)
                val searchResults = rawSearchResults
                    .filterNot(::isMultimodalOrNonChatRepo)

                val semaphore = Semaphore(6)
                val repoSources = coroutineScope {
                    searchResults.map { searchItem ->
                        async {
                            semaphore.withPermit {
                                ensureActive()
                                val variants = getVariantsForRepo(
                                    modelId = searchItem.id,
                                    repoName = searchItem.modelId.substringAfterLast('/'),
                                    description = buildBrowseDescription(searchItem),
                                    capabilityTags = searchItem.tags,
                                    pipelineTag = searchItem.pipelineTag,
                                    downloads = searchItem.numDownloads.toLong(),
                                    lastUpdatedEpochMs = null
                                )
                                if (variants.isEmpty()) {
                                    null
                                } else {
                                    BrowseRepoSource(
                                        modelId = searchItem.id,
                                        author = searchItem.id.substringBefore('/'),
                                        description = variants.first().description,
                                        downloads = searchItem.numDownloads.toLong(),
                                        tags = filterDisplayTags(searchItem.tags),
                                        variants = variants,
                                    )
                                }
                            }
                        }
                    }.awaitAll()
                        .filterNotNull()
                }

                val items = repoSources
                    .groupBy { browseGroupKey(it.variants.first().groupName) }
                    .values
                    .map { group ->
                        val mergedVariants = dedupeRecommendationVariants(group.flatMap { it.variants })
                        val categorizedVariants = recommendationEngine.recommend(
                            deviceProfile = deviceProfile,
                            models = mergedVariants,
                            userPreference = preference,
                            installedModelIds = installedIds,
                        ).recommendations
                        val primary = group.maxByOrNull { it.downloads } ?: group.first()
                        val authors = group.map { it.author }.distinct()
                        val title = mergedVariants.firstOrNull()?.groupName ?: primary.modelId.substringAfterLast('/')
                        BrowseRepoSummary(
                            modelId = browseGroupKey(title),
                            repoIds = group.map { it.modelId }.distinct(),
                            title = title,
                            author = if (authors.size == 1) authors.first() else "${authors.size} sources",
                            description = primary.description,
                            downloads = group.sumOf { it.downloads },
                            variantCount = categorizedVariants.size,
                            family = title.substringBefore(' '),
                            tags = group.flatMap { it.tags }.distinct().take(6),
                            fasterCount = categorizedVariants.count { it.performance == PerformanceLevel.FAST },
                            balancedCount = categorizedVariants.count { it.performance == PerformanceLevel.BALANCED },
                            slowCount = categorizedVariants.count { it.performance == PerformanceLevel.SLOW },
                            riskCount = categorizedVariants.count { it.performance == PerformanceLevel.RISKY }
                        )
                    }
                    .sortedByDescending { it.downloads }

                ensureActive()
                _state.update { current ->
                    current.copy(
                        browseItems = items,
                        isBrowseLoading = false,
                        isBrowseAppending = false,
                        canLoadMoreBrowse = rawSearchResults.size >= requestLimit,
                        browseError = when {
                            items.isNotEmpty() -> null
                            query.isBlank() -> null
                            else -> "No runnable GGUF chat models matched \"$query\"."
                        }
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update { current ->
                    current.copy(
                        isBrowseLoading = false,
                        isBrowseAppending = false,
                        browseError = if (current.browseItems.isEmpty()) {
                            error.message ?: "Unable to load browse models."
                        } else {
                            error.message ?: "Unable to load more models right now."
                        }
                    )
                }
            }
        }
    }

    private fun initialBrowseLimit(query: String): Int =
        if (query.isBlank()) BROWSE_INITIAL_LIMIT_BLANK else BROWSE_INITIAL_LIMIT_QUERY

    private fun browseAppendStep(query: String): Int =
        if (query.isBlank()) BROWSE_APPEND_STEP_BLANK else BROWSE_APPEND_STEP_QUERY

    private suspend fun getTopModelsCached(
        query: String,
        limit: Int = 24,
        filter: String = "gguf,conversational",
        sort: HFModelSearch.ModelSortParam = HFModelSearch.ModelSortParam.DOWNLOADS,
    ): List<HFModelSearch.ModelSearchResult> {
        val cacheKey = "$query|$limit|$filter|${sort.name}"
        searchCache[cacheKey]?.let { return it }
        val results = hfModelsAPI.getTopModels(query = query, limit = limit, filter = filter, sort = sort)
        searchCache[cacheKey] = results
        return results
    }

    private suspend fun getRecommendationSeedModelsCached(
        useCase: UserUseCase,
        limit: Int,
    ): List<HFModelSearch.ModelSearchResult> {
        val cacheKey = "${useCase.name}|$limit"
        recommendationSeedCache[cacheKey]?.let { return it }
        val results = hfModelsAPI.getRecommendationSeedModels(useCase = useCase, limit = limit)
        recommendationSeedCache[cacheKey] = results
        return results
    }

    private suspend fun getModelInfoCached(modelId: String): HFModelInfo.ModelInfo {
        infoCache[modelId]?.let { return it }
        return hfModelsAPI.getModelInfo(modelId).also { infoCache[modelId] = it }
    }

    private suspend fun getModelTreeCached(modelId: String): List<HFModelTree.HFModelFile> {
        treeCache[modelId]?.let { return it }
        return hfModelsAPI.getModelTree(modelId).also { treeCache[modelId] = it }
    }

    private suspend fun getVariantsForRepo(
        modelId: String,
        repoName: String,
        description: String,
        capabilityTags: List<String>,
        pipelineTag: String?,
        downloads: Long,
        lastUpdatedEpochMs: Long?,
    ): List<ModelInfo> {
        val tagsKey = capabilityTags.sorted().joinToString("|")
        val cacheKey = "$modelId|$repoName|$downloads|$lastUpdatedEpochMs|$pipelineTag|${tagsKey.hashCode()}"
        variantCache[cacheKey]?.let { return it }
        val tree = getModelTreeCached(modelId)
        val variants = huggingFaceService.mapRepoFilesToModelInfo(
            modelId = modelId,
            repoName = repoName,
            description = description,
            capabilityTags = capabilityTags,
            pipelineTag = pipelineTag,
            downloads = downloads,
            files = tree.map { file ->
                HuggingFaceFile(
                    name = file.path.substringAfterLast('/'),
                    sizeBytes = file.size
                )
            },
            lastUpdatedEpochMs = lastUpdatedEpochMs,
        )
        variantCache[cacheKey] = variants
        return variants
    }

    private fun buildBrowseDescription(info: HFModelInfo.ModelInfo): String {
        val tagSummary = prioritizeCapabilityTags(filterDisplayTags(info.tags))
            .take(6)
            .joinToString(", ")
        return when {
            tagSummary.isNotBlank() -> tagSummary
            else -> info.modelId.substringAfterLast('/')
        }
    }

    private fun buildBrowseDescription(searchItem: HFModelSearch.ModelSearchResult): String {
        val tagSummary = prioritizeCapabilityTags(filterDisplayTags(searchItem.tags))
            .take(5)
            .joinToString(", ")
        return when {
            tagSummary.isNotBlank() -> tagSummary
            else -> searchItem.modelId.substringAfterLast('/')
        }
    }

    private fun filterDisplayTags(tags: List<String>): List<String> =
        tags.map { it.lowercase(Locale.US) }
            .filterNot {
                it.startsWith("base_model:") ||
                    it.startsWith("license:") ||
                    it.startsWith("region:") ||
                    it.startsWith("endpoints_") ||
                    it.contains("image-text-to-text") ||
                    it.contains("vision") ||
                    it.contains("mmproj")
            }
            .distinct()

    private fun prioritizeCapabilityTags(tags: List<String>): List<String> =
        tags.sortedWith(
            compareByDescending<String> { tag ->
                tag.contains("coder") ||
                    tag.contains("code") ||
                    tag.contains("fim") ||
                    tag.contains("instruct") ||
                    tag.contains("chat") ||
                    tag.contains("assistant") ||
                    tag.contains("conversational")
            }.thenBy { it }
        )

    private fun isMultimodalOrNonChatRepo(searchItem: HFModelSearch.ModelSearchResult): Boolean {
        val pipeline = searchItem.pipelineTag?.lowercase().orEmpty()
        val tags = searchItem.tags.map { it.lowercase(Locale.US) }
        return pipeline == "image-text-to-text" ||
            pipeline == "image-to-text" ||
            pipeline == "image-to-video" ||
            pipeline == "translation" ||
            tags.any {
                it.contains("image-text-to-text") ||
                    it.contains("vision") ||
                    it.contains("mmproj") ||
                    it.contains("clip") ||
                    it.contains("encoder") ||
                    it.contains("embedding") ||
                    it.contains("vl") ||
                    it.contains("image-to-video")
            }
    }

    private fun dedupeRecommendationVariants(models: List<ModelInfo>): List<ModelInfo> =
        models.groupBy {
            buildRecommendationVariantKey(it)
        }
            .values
            .map { variants ->
                variants.maxWithOrNull(
                    compareByDescending<ModelInfo> { it.downloads }
                        .thenByDescending { it.expectedSizeBytes }
                        .thenByDescending { it.lastUpdatedEpochMs ?: 0L }
                ) ?: variants.first()
            }

    private fun dedupeRecommendationVariants(
        models: List<ModelInfo>,
        useCase: UserUseCase,
    ): List<ModelInfo> =
        models.groupBy {
            buildRecommendationVariantKey(it)
        }
            .values
            .flatMap { variants ->
                val saferRepresentative = variants.maxWithOrNull(
                    compareByDescending<ModelInfo> { recommendationSeedRelevance(it, useCase) }
                        .thenByDescending { it.downloads }
                        .thenBy { it.expectedSizeBytes }
                        .thenByDescending { it.lastUpdatedEpochMs ?: 0L }
                ) ?: variants.first()
                val stretchRepresentative = variants.maxWithOrNull(
                    compareByDescending<ModelInfo> { recommendationSeedRelevance(it, useCase) }
                        .thenByDescending { it.downloads }
                        .thenByDescending { it.expectedSizeBytes }
                        .thenByDescending { it.lastUpdatedEpochMs ?: 0L }
                ) ?: variants.first()

                val sizeGapIsMeaningful = when {
                    saferRepresentative.expectedSizeBytes <= 0L || stretchRepresentative.expectedSizeBytes <= 0L -> false
                    saferRepresentative.id == stretchRepresentative.id -> false
                    else -> stretchRepresentative.expectedSizeBytes >= (saferRepresentative.expectedSizeBytes * 1.15f)
                }

                if (sizeGapIsMeaningful) {
                    listOf(saferRepresentative, stretchRepresentative)
                } else {
                    listOf(saferRepresentative)
                }
            }

    private fun buildRecommendationShortlist(
        models: List<ModelInfo>,
        useCase: UserUseCase,
        limit: Int,
    ): List<ModelInfo> {
        val ranked = models.sortedWith(
            compareByDescending<ModelInfo> { recommendationSeedRelevance(it, useCase) }
                .thenByDescending { it.downloads }
                .thenByDescending { it.sizeGb }
                .thenBy { it.id }
        )
        val balancedLane = models.sortedWith(
            compareBy<ModelInfo> { recommendationBalanceDistance(it, useCase) }
                .thenByDescending { recommendationSeedRelevance(it, useCase) }
                .thenByDescending { it.downloads }
                .thenByDescending { it.sizeGb }
                .thenBy { it.id }
        ).take((limit / 3).coerceAtLeast(8))
        val fastLane = models.sortedWith(
            compareBy<ModelInfo> { it.sizeGb }
                .thenByDescending { recommendationSeedRelevance(it, useCase) }
                .thenByDescending { it.downloads }
                .thenBy { it.id }
        ).take((limit / 6).coerceAtLeast(3))

        return takeRecommendationShortlistWithFamilyCap(
            models = (ranked + balancedLane + fastLane).distinctBy { it.id },
            limit = limit,
        )
    }

    private fun recommendationBalanceDistance(model: ModelInfo, useCase: UserUseCase): Float {
        val targetGb = when (useCase) {
            UserUseCase.CODING -> 3.8f
            UserUseCase.GENERAL -> 3.4f
            UserUseCase.MIXED -> 3.6f
        }
        val tinyPenalty = when {
            model.sizeGb < 1.2f -> 1.7f
            model.sizeGb < 1.8f -> 0.9f
            else -> 0f
        }
        return abs(model.sizeGb - targetGb) + tinyPenalty
    }

    private fun takeRecommendationShortlistWithFamilyCap(
        models: List<ModelInfo>,
        limit: Int,
        perFamilyCap: Int = 3,
    ): List<ModelInfo> {
        val picked = mutableListOf<ModelInfo>()
        val familyCounts = mutableMapOf<String, Int>()
        val overflow = mutableListOf<ModelInfo>()

        models.forEach { model ->
            val family = recommendationFamilyKey(model)
            if ((familyCounts[family] ?: 0) < perFamilyCap) {
                picked += model
                familyCounts[family] = (familyCounts[family] ?: 0) + 1
            } else {
                overflow += model
            }
        }

        return (picked + overflow).take(limit)
    }

    private fun normalizeGroupKey(value: String): String =
        value.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

    private fun buildRecommendationVariantKey(model: ModelInfo): String =
        "${normalizeGroupKey(model.groupName)}|${inferParameterKey(model)}|${inferSubtypeKey(model.name, model.description, model.capabilityTags)}|${model.quantization.uppercase(Locale.US)}"

    private fun inferParameterKey(model: ModelInfo): String {
        val source = "${model.fileName} ${model.name} ${model.groupName}"
        val parsed = Regex("""(\d+(?:\.\d+)?)\s*b""", RegexOption.IGNORE_CASE)
            .find(source)
            ?.groupValues
            ?.getOrNull(1)
        if (!parsed.isNullOrBlank()) return parsed

        val sizeGb = if (model.expectedSizeBytes > 0L) {
            model.expectedSizeBytes.toFloat() / (1024f * 1024f * 1024f)
        } else {
            model.sizeGb
        }
        return "size-${String.format(Locale.US, "%.1f", sizeGb)}"
    }

    private fun recommendationFamilyKey(model: ModelInfo): String {
        val normalized = normalizeGroupKey("${model.groupName} ${model.name}")
        return normalized.substringBefore(' ').ifBlank { "other" }
    }

    private fun browseGroupKey(value: String): String =
        value.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')

    private fun inferSubtypeKey(
        title: String,
        description: String,
        tags: List<String>,
    ): String {
        val text = buildString {
            append(title.lowercase(Locale.US))
            append(' ')
            append(description.lowercase(Locale.US))
            append(' ')
            append(tags.joinToString(" ").lowercase(Locale.US))
        }
        return when {
            text.contains("coder") ||
                text.contains("fim") ||
                text.contains("fill-in-the-middle") ||
                text.contains("codegen") ||
                text.contains("code-completion") ||
                text.contains("source-code") -> "coder"
            text.contains("instruct") || text.contains("instruction") || text.contains("aligned") -> "instruct"
            text.contains("chat") || text.contains("assistant") || text.contains("conversational") -> "chat"
            else -> "base"
        }
    }

    private fun recommendationSeedRelevance(model: ModelInfo, useCase: UserUseCase): Float {
        val text = buildString {
            append(model.groupName.lowercase(Locale.US))
            append(' ')
            append(model.name.lowercase(Locale.US))
            append(' ')
            append(model.description.lowercase(Locale.US))
            append(' ')
            append(model.pipelineTag.orEmpty().lowercase(Locale.US))
            append(' ')
            append(model.capabilityTags.joinToString(" ").lowercase(Locale.US))
        }
        val coder = text.contains("coder") ||
            text.contains(" code ") ||
            text.contains("codegen") ||
            text.contains("code completion") ||
            text.contains("code-completion") ||
            text.contains("source code") ||
            text.contains("source-code") ||
            text.contains("programming") ||
            text.contains("fim") ||
            text.contains("fill-in-the-middle")
        val chat = text.contains("instruct") ||
            text.contains("instruction") ||
            text.contains("chat") ||
            text.contains("assistant") ||
            text.contains("conversational") ||
            text.contains("text-generation")

        return when (useCase) {
            UserUseCase.CODING -> when {
                coder && chat -> 1.0f
                coder -> 0.95f
                chat -> 0.45f
                else -> 0.15f
            }
            UserUseCase.GENERAL -> when {
                chat && coder -> 1.0f
                chat -> 0.95f
                coder -> 0.35f
                else -> 0.15f
            }
            UserUseCase.MIXED -> when {
                coder && chat -> 1.0f
                coder || chat -> 0.75f
                else -> 0.2f
            }
        }
    }

    private fun <K, V> cappedCache(maxSize: Int): LinkedHashMap<K, V> =
        object : LinkedHashMap<K, V>(maxSize, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > maxSize
        }

    private fun java.time.LocalDateTime.toEpochMillis(): Long =
        atZone(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
}
