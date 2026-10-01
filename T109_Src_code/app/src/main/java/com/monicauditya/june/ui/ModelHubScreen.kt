@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.monicauditya.june.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.monicauditya.june.modelhub.DownloadState
import com.monicauditya.june.modelhub.ModelDownloadStatus
import com.monicauditya.june.modelhub.ModelInfo
import com.monicauditya.june.modelhub.ModelRecommendation
import com.monicauditya.june.modelhub.PerformanceLevel
import com.monicauditya.june.modelhub.UserUseCase
import com.monicauditya.june.domain.ChatUseCase
import org.koin.compose.koinInject
import org.koin.androidx.compose.koinViewModel
import java.io.File
import java.util.Locale
import kotlinx.coroutines.flow.distinctUntilChanged

private enum class ModelHubTopLevelTab(val label: String) {
    RECOMMENDED("Recommended"),
    BROWSE("Browse"),
    DOWNLOADS("Downloads"),
    IMPORT("Import"),
}

private val HubBackground = Color(0xFF0B0B0F)
private val HubSurface = Color(0xFF17171D)
private val HubSurfaceRaised = Color(0xFF1F1F26)
private val HubOutline = Color(0xFF3A3447)
private val HubTextPrimary = Color(0xFFF4F1F8)
private val HubTextSecondary = Color(0xFFB9B2C8)
private val HubPrimary = Color(0xFFCEBDFF)
private val HubPrimaryStrong = Color(0xFF9B7FED)
private val HubTertiary = Color(0xFFFFB869)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModelHubScreen(
    modifier: Modifier = Modifier,
    onMenuClick: () -> Unit = {},
    viewModel: ModelHubViewModel = koinViewModel()
) {
    val chatUseCase: ChatUseCase = koinInject()
    val uiState by viewModel.state.collectAsState()
    val deviceProfile = uiState.deviceProfile
    val snackbarHostState = remember { SnackbarHostState() }
    var dialogState by remember { mutableStateOf<ModelHubDialogState?>(null) }
    var selectedTab by rememberSaveable { mutableStateOf(ModelHubTopLevelTab.RECOMMENDED) }
    var showDeviceProfile by rememberSaveable { mutableStateOf(false) }
    val recommendedListState = rememberLazyListState()
    val browseListState = rememberLazyListState()
    val downloadsListState = rememberLazyListState()
    val importListState = rememberLazyListState()
    val detailListState = rememberLazyListState()
    val browseDetails = uiState.browseDetails
    val recommendedVariant = uiState.featuredRecommendations.recommended
    val fastestVariant = uiState.featuredRecommendations.fastest
    val highestQualityVariant = uiState.featuredRecommendations.highestQuality
    val fastestMatchesRecommended = recommendedVariant != null &&
        fastestVariant != null &&
        recommendedVariant.model.id == fastestVariant.model.id
    val detailOpen = browseDetails.modelId != null
    val manualModelPath = chatUseCase.getModelPath()
    val manualModelLoaded = chatUseCase.isModelLoaded()
    val manualModelExists = remember(manualModelPath) { manualModelPath.isNotBlank() && File(manualModelPath).exists() }
    val knownModels = remember(uiState.models, uiState.recommendations, browseDetails.variants) {
        buildMap {
            uiState.models.forEach { put(it.id, it) }
            uiState.recommendations.forEach { put(it.model.id, it.model) }
            browseDetails.variants.forEach { put(it.model.id, it.model) }
        }
    }
    val downloadEntries = remember(uiState.downloadStatuses, knownModels, uiState.activeModelId) {
        uiState.downloadStatuses.entries
            .filter { (_, status) -> status.state != DownloadState.IDLE }
            .sortedByDescending { (_, status) ->
                when (status.state) {
                    DownloadState.DOWNLOADING -> 4
                    DownloadState.PAUSED -> 3
                    DownloadState.DOWNLOADED -> 2
                    DownloadState.FAILED -> 1
                    DownloadState.CANCELLED -> 0
                    DownloadState.IDLE -> 0
                }
            }
            .map { (modelId, status) ->
                val model = knownModels[modelId]
                DownloadListItem(
                    modelId = modelId,
                    model = model,
                    title = model?.name ?: modelId.substringAfterLast('/').ifBlank { "Model" },
                    subtitle = model?.let { "${formatSize(it.sizeGb)} | ${it.quantization}" }
                        ?: status.localPath?.let { File(it).name }
                        ?: "Downloaded model",
                    status = status,
                    isActive = uiState.activeModelId == modelId
                )
            }
    }
    val manualModelManagedByLibrary = remember(manualModelPath, downloadEntries) {
        if (manualModelPath.isBlank()) {
            false
        } else {
            val normalizedManualPath = File(manualModelPath).absolutePath
            downloadEntries.any { entry ->
                entry.status.localPath?.let { File(it).absolutePath == normalizedManualPath } == true
            }
        }
    }
    val detailVariantSections = remember(browseDetails.variants) {
        buildVariantSections(browseDetails.variants)
    }
    val activeDownloadEntry = remember(downloadEntries) {
        downloadEntries.firstOrNull { it.status.state == DownloadState.DOWNLOADING }
    }
    val libraryEntries = remember(downloadEntries) {
        downloadEntries.filter { it.status.state != DownloadState.DOWNLOADING }
    }
    val activeListState = when {
        detailOpen -> detailListState
        selectedTab == ModelHubTopLevelTab.BROWSE -> browseListState
        selectedTab == ModelHubTopLevelTab.DOWNLOADS -> downloadsListState
        selectedTab == ModelHubTopLevelTab.IMPORT -> importListState
        else -> recommendedListState
    }

    LaunchedEffect(uiState.infoMessage) {
        val message = uiState.infoMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearInfoMessage()
    }

    LaunchedEffect(detailOpen) {
        if (detailOpen) {
            selectedTab = ModelHubTopLevelTab.BROWSE
        }
    }

    LaunchedEffect(
        browseListState,
        selectedTab,
        detailOpen,
        uiState.browseItems.size,
        uiState.isBrowseLoading,
        uiState.isBrowseAppending,
        uiState.canLoadMoreBrowse,
    ) {
        if (selectedTab != ModelHubTopLevelTab.BROWSE || detailOpen) return@LaunchedEffect
        snapshotFlow {
            val lastVisible = browseListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = browseListState.layoutInfo.totalItemsCount
            total > 0 && lastVisible >= total - 4
        }
            .distinctUntilChanged()
            .collect { shouldLoadMore ->
                if (shouldLoadMore && !uiState.isBrowseLoading && !uiState.isBrowseAppending && uiState.canLoadMoreBrowse) {
                    viewModel.loadMoreBrowse()
                }
            }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(HubBackground)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val spacing = 24.dp.toPx()
            val radius = 1.dp.toPx()
            var x = spacing / 2f
            while (x < size.width) {
                var y = spacing / 2f
                while (y < size.height) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.045f),
                        radius = radius,
                        center = androidx.compose.ui.geometry.Offset(x, y)
                    )
                    y += spacing
                }
                x += spacing
            }
        }

        val showFloatingTopBar = !uiState.isLoading && deviceProfile != null && !detailOpen && !showDeviceProfile

        Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (!uiState.isLoading && deviceProfile != null && detailOpen) {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Model Details", color = HubTextPrimary)
                            Text(
                                "Live variants for this grouped repo",
                                style = MaterialTheme.typography.labelSmall,
                                color = HubTextSecondary
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearBrowseDetails() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to browse",
                                tint = HubTextPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                        titleContentColor = HubTextPrimary,
                        navigationIconContentColor = HubTextPrimary
                    )
                )
            }
        },
        bottomBar = {
            if (!uiState.isLoading && deviceProfile != null && !detailOpen && !showDeviceProfile) {
                BottomNavigationBar(
                    selectedTab = selectedTab,
                    onSelect = { tab ->
                        selectedTab = tab
                        if (tab == ModelHubTopLevelTab.RECOMMENDED) {
                            viewModel.clearBrowseDetails()
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        if (uiState.isLoading || deviceProfile == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text("Loading curated Hugging Face models...", color = HubTextPrimary)
                }
            }
        } else if (showDeviceProfile) {
            DeviceProfileOverview(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                deviceProfile = deviceProfile,
                onContinue = { showDeviceProfile = false }
            )
        } else {
            LazyColumn(
                state = activeListState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(
                    start = 14.dp,
                    top = if (showFloatingTopBar) 86.dp else 14.dp,
                    end = 14.dp,
                    bottom = 14.dp
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (detailOpen) {
                    if (browseDetails.isLoading) {
                        item("detail_loading") {
                            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    } else if (browseDetails.errorMessage != null) {
                        item("detail_error") {
                            EmptyBrowseState(
                                title = "Nothing downloadable here",
                                body = browseDetails.errorMessage ?: "No runnable GGUF variants found for this repo."
                            )
                        }
                    } else {
                        item("detail_card") {
                            BrowseDetailHero(
                                title = browseDetails.title,
                                author = browseDetails.author,
                                downloads = browseDetails.downloads,
                                likes = browseDetails.likes,
                                description = browseDetails.description,
                                tags = browseDetails.tags
                            )
                        }
                        item("detail_section") { Header("Available Variants", "All runnable variants for this group, ranked by device fit.") }
                    }
                    detailVariantSections.forEach { section ->
                        item("detail_section_${section.key}") {
                            Header(section.title, section.subtitle)
                        }
                        items(section.variants, key = { "${section.key}_${it.model.id}" }) { variant ->
                            VariantCard(
                                recommendation = variant,
                                downloadStatus = uiState.downloadStatuses[variant.model.id] ?: ModelDownloadStatus(),
                                isActiveModel = uiState.activeModelId == variant.model.id,
                                isBusy = uiState.busyModelName == variant.model.id,
                                activeDownloadModelId = uiState.activeDownloadModelId,
                                onPrimaryAction = {
                                    onVariantAction(
                                        recommendation = variant,
                                        downloadStatus = uiState.downloadStatuses[variant.model.id] ?: ModelDownloadStatus(),
                                        onUse = { viewModel.useModel(variant.model) },
                                        onDownload = { viewModel.startDownload(variant.model) },
                                        onDialog = { dialogState = it },
                                        onCancel = { viewModel.cancelDownload(variant.model.id) }
                                    )
                                }
                            )
                        }
                    }
                } else if (selectedTab == ModelHubTopLevelTab.BROWSE) {
                    item("browse_header") {
                        PageHero(
                            title = "Browse Models",
                            subtitle = ""
                        )
                    }
                    item("browse_search") {
                        OutlinedTextField(
                            value = uiState.browseQuery,
                            onValueChange = {
                                viewModel.updateBrowseQuery(it)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(20.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = HubSurface,
                                unfocusedContainerColor = HubSurface,
                                focusedBorderColor = HubPrimary,
                                unfocusedBorderColor = HubOutline,
                                focusedTextColor = HubTextPrimary,
                                unfocusedTextColor = HubTextPrimary,
                                cursorColor = HubPrimary,
                                focusedLabelColor = HubTextSecondary,
                                unfocusedLabelColor = HubTextSecondary,
                                focusedPlaceholderColor = HubTextSecondary,
                                unfocusedPlaceholderColor = HubTextSecondary,
                            ),
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = HubTextSecondary) },
                            placeholder = { Text("Search the collection...") }
                        )
                    }
                    if (uiState.isBrowseLoading && uiState.browseItems.isEmpty()) {
                        items(3, key = { "browse_loading_$it" }) {
                            BrowseRepoSkeleton()
                        }
                    } else if (uiState.browseError != null && uiState.browseItems.isEmpty()) {
                        item("browse_error") {
                            EmptyBrowseState(
                                title = "Browse is unavailable",
                                body = uiState.browseError ?: "We couldn't load model repos right now."
                            )
                        }
                    } else {
                        items(uiState.browseItems, key = { it.modelId }) { item ->
                            BrowseRepoCard(
                                title = item.title,
                                author = item.author,
                                description = item.description,
                                downloads = item.downloads,
                                variantCount = item.variantCount,
                                family = item.family,
                                tags = item.tags,
                                fasterCount = item.fasterCount,
                                balancedCount = item.balancedCount,
                                slowCount = item.slowCount,
                                riskCount = item.riskCount,
                                onOpen = {
                                    viewModel.loadBrowseDetails(item.modelId)
                                }
                            )
                        }
                        if (uiState.isBrowseAppending || (uiState.isBrowseLoading && uiState.browseItems.isNotEmpty())) {
                            item("browse_append_loading") {
                                BrowseAppendLoadingCard()
                            }
                        } else if (uiState.browseError != null) {
                            item("browse_append_error") {
                                EmptyBrowseState(
                                    title = "Could not load more models",
                                    body = uiState.browseError ?: "We couldn't fetch more model groups right now."
                                )
                            }
                        } else if (uiState.canLoadMoreBrowse) {
                            item("browse_append_hint") {
                                InlineFeaturedNote("Scroll down to load more models.")
                            }
                        }
                    }
                } else if (selectedTab == ModelHubTopLevelTab.DOWNLOADS) {
                    item("downloads_header") {
                        PageHero(
                            "Downloads",
                            ""
                        )
                    }
                    activeDownloadEntry?.let { active ->
                        item("downloads_active") {
                            ActiveDownloadHero(
                                entry = active,
                                onPause = { viewModel.pauseDownload(active.modelId) },
                                onCancel = { viewModel.cancelDownload(active.modelId) }
                            )
                        }
                    }
                    if (manualModelExists && !manualModelManagedByLibrary) {
                        item("downloads_manual") {
                            ManualModelCard(
                                path = manualModelPath,
                                isLoaded = manualModelLoaded
                            )
                        }
                    }
                    if (libraryEntries.isEmpty() && (!manualModelExists || manualModelManagedByLibrary)) {
                        item("downloads_empty") {
                            EmptyBrowseState(
                                title = "No downloaded models yet",
                                body = "Downloaded Hugging Face models and the current local GGUF path will appear here."
                            )
                        }
                    } else {
                        items(libraryEntries, key = { it.modelId }) { entry ->
                            DownloadedModelCard(
                                entry = entry,
                                onUse = { entry.model?.let(viewModel::useModel) },
                                onDownload = { entry.model?.let(viewModel::startDownload) }
                            )
                        }
                    }
                } else if (selectedTab == ModelHubTopLevelTab.IMPORT) {
                    item("import_header") {
                        PageHero(
                            "Import",
                            "Import your model from local storage in the (.gguf) format. Get models from repos like Hugging Face."
                        )
                    }
                    item("import_guide") {
                        ImportGuideCard()
                    }
                } else {
                    item("prefine_inline") {
                        InlinePreferenceSection(
                            currentUseCase = uiState.userPreference.useCase,
                            hasCompletedOnboarding = uiState.userPreference.hasCompletedOnboarding,
                            onSelect = { useCase ->
                                if (useCase != uiState.userPreference.useCase || !uiState.userPreference.hasCompletedOnboarding) {
                                    viewModel.savePreference(useCase, completedOnboarding = true, hasEdited = true)
                                }
                            }
                        )
                    }
                    uiState.recommendationWarning?.let { warning ->
                        item("recommendation_warning") {
                            EmptyBrowseState(
                                title = "Recommendation feed degraded",
                                body = warning
                            )
                        }
                    }
                    item("recommended_header") { RecommendationSectionHeader("Most Recommended") }
                    if (recommendedVariant != null) {
                        item("recommended_card") {
                            FeaturedVariantCard(
                                title = "Most Recommended",
                                recommendation = recommendedVariant,
                                downloadStatus = uiState.downloadStatuses[recommendedVariant.model.id] ?: ModelDownloadStatus(),
                                isActiveModel = uiState.activeModelId == recommendedVariant.model.id,
                                isBusy = uiState.busyModelName == recommendedVariant.model.id,
                                activeDownloadModelId = uiState.activeDownloadModelId,
                                accent = HubPrimary,
                                onPrimaryAction = {
                                    onVariantAction(
                                        recommendation = recommendedVariant,
                                        downloadStatus = uiState.downloadStatuses[recommendedVariant.model.id] ?: ModelDownloadStatus(),
                                        onUse = { viewModel.useModel(recommendedVariant.model) },
                                        onDownload = { viewModel.startDownload(recommendedVariant.model) },
                                        onDialog = { dialogState = it },
                                        onCancel = { viewModel.cancelDownload(recommendedVariant.model.id) }
                                    )
                                }
                            )
                        }
                    } else {
                        item("recommended_empty") {
                            EmptyBrowseState(
                                title = "No best-fit pick available",
                                body = "We could not find a confident overall recommendation from the current live model set."
                            )
                        }
                    }
                    item("fast_header") { RecommendationSectionHeader("Fastest Option") }
                    if (fastestVariant != null && !fastestMatchesRecommended) {
                        item("fast_card") {
                            FeaturedVariantCard(
                                title = "Fastest",
                                recommendation = fastestVariant,
                                downloadStatus = uiState.downloadStatuses[fastestVariant.model.id] ?: ModelDownloadStatus(),
                                isActiveModel = uiState.activeModelId == fastestVariant.model.id,
                                isBusy = uiState.busyModelName == fastestVariant.model.id,
                                activeDownloadModelId = uiState.activeDownloadModelId,
                                accent = Color(0xFF8FC8FF),
                                onPrimaryAction = {
                                    onVariantAction(
                                        recommendation = fastestVariant,
                                        downloadStatus = uiState.downloadStatuses[fastestVariant.model.id] ?: ModelDownloadStatus(),
                                        onUse = { viewModel.useModel(fastestVariant.model) },
                                        onDownload = { viewModel.startDownload(fastestVariant.model) },
                                        onDialog = { dialogState = it },
                                        onCancel = { viewModel.cancelDownload(fastestVariant.model.id) }
                                    )
                                }
                            )
                        }
                    } else if (fastestMatchesRecommended) {
                        item("fast_note") {
                            InlineFeaturedNote("Your most recommended pick is also the fastest safe option right now.")
                        }
                    } else {
                        item("fast_empty") {
                            EmptyBrowseState(
                                title = "No fast safe pick available",
                                body = "We could not find a clearly fast safe model from the current live model set."
                            )
                        }
                    }
                    item("quality_header") { RecommendationSectionHeader("Higher Quality Option") }
                    if (highestQualityVariant != null) {
                        item("quality_card") {
                            FeaturedVariantCard(
                                title = "Higher Quality",
                                recommendation = highestQualityVariant,
                                downloadStatus = uiState.downloadStatuses[highestQualityVariant.model.id] ?: ModelDownloadStatus(),
                                isActiveModel = uiState.activeModelId == highestQualityVariant.model.id,
                                isBusy = uiState.busyModelName == highestQualityVariant.model.id,
                                activeDownloadModelId = uiState.activeDownloadModelId,
                                accent = HubTertiary,
                                onPrimaryAction = {
                                    onVariantAction(
                                        recommendation = highestQualityVariant,
                                        downloadStatus = uiState.downloadStatuses[highestQualityVariant.model.id] ?: ModelDownloadStatus(),
                                        onUse = { viewModel.useModel(highestQualityVariant.model) },
                                        onDownload = { viewModel.startDownload(highestQualityVariant.model) },
                                        onDialog = { dialogState = it },
                                        onCancel = { viewModel.cancelDownload(highestQualityVariant.model.id) }
                                    )
                                }
                            )
                        }
                    } else {
                        item("quality_empty") {
                            EmptyBrowseState(
                                title = "No stretch quality pick available",
                                body = "We could not find a heavier quality-focused model that still looked like a reasonable stretch for this device."
                            )
                        }
                    }
                }
            }
        }
    }

    if (showFloatingTopBar) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(132.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            HubBackground.copy(alpha = 0.96f),
                            HubBackground.copy(alpha = 0.90f),
                            HubBackground.copy(alpha = 0.78f),
                            Color.Transparent
                        )
                    )
                )
        )
    }

    if (showFloatingTopBar) {
        HubTopBar(
            score = computeDeviceScore(deviceProfile),
            onOpenProfile = { showDeviceProfile = true },
            onMenuClick = onMenuClick
        )
    }

    dialogState?.let { state ->
        ModelHubDialog(
            state = state,
            onDismiss = { dialogState = null },
            onConfirm = { dialogState = null; viewModel.startDownload(state.model) }
        )
    }
    }
}

@Composable
private fun PageHero(title: String, subtitle: String) = Column(
    verticalArrangement = Arrangement.spacedBy(if (subtitle.isBlank()) 0.dp else 10.dp)
) {
    Text(
        title,
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold,
        color = HubTextPrimary
    )
    if (subtitle.isNotBlank()) {
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = HubTextSecondary
        )
    }
}

@Composable
private fun RecommendationSectionHeader(title: String) {
    val tone = when (title) {
        "Most Recommended" -> HubPrimary
        "Fastest Option" -> Color(0xFF8FC8FF)
        else -> HubTertiary
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (title) {
            "Most Recommended" -> Surface(
                modifier = Modifier.size(6.dp),
                shape = RoundedCornerShape(999.dp),
                color = tone
            ) {}
            "Fastest Option" -> Icon(Icons.Default.Bolt, contentDescription = null, tint = tone)
            else -> Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = tone)
        }
        Text(
            title.uppercase(Locale.US),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = HubTextSecondary
        )
    }
}

@Composable private fun Header(title: String, subtitle: String) = Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(
        title,
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        color = HubTextPrimary
    )
    Text(
        subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = HubTextSecondary
    )
}

@Composable
private fun HubTopBar(
    score: Int,
    onOpenProfile: () -> Unit,
    onMenuClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 2.dp)
    ) {
        Surface(
            modifier = Modifier.align(Alignment.CenterStart),
            shape = RoundedCornerShape(16.dp),
            color = HubSurfaceRaised.copy(alpha = 0.92f),
            border = BorderStroke(1.dp, HubOutline.copy(alpha = 0.75f))
        ) {
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Open navigation",
                    tint = HubTextPrimary
                )
            }
        }
        Box(
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            ScoreChipRow(
                score = score,
                onOpenProfile = onOpenProfile,
                compact = true
            )
        }
    }
}

@Composable
private fun DeviceProfileOverview(
    modifier: Modifier = Modifier,
    deviceProfile: com.monicauditya.june.modelhub.DeviceProfile,
    onContinue: () -> Unit,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = HubSurface,
            border = BorderStroke(1.dp, HubOutline.copy(alpha = 0.85f))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Device Analysis",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = HubTextPrimary
                )
                Text(
                    "A quick snapshot of what this device can realistically run on-device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HubTextSecondary
                )
                ScoreChipRow(score = computeDeviceScore(deviceProfile), onOpenProfile = {}, compact = false, interactiveHint = false)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatBadge("Total RAM", formatRam(deviceProfile.totalRamMb), HubTextPrimary)
                    StatBadge("Available RAM", formatRam(deviceProfile.availableRamMb), HubTextPrimary)
                    StatBadge("Usable RAM", formatRam(deviceProfile.usableRamMb), HubPrimary)
                }
                MetaLine("CPU", "${deviceProfile.cpuCores} cores | ${deviceProfile.cpuTier.name.lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) }} tier")
                MetaLine("ABI", deviceProfile.abi)
                if (deviceProfile.isEmulator) {
                    Text(
                        "Emulator detected. Device-fit estimates are adjusted for a desktop-backed test environment.",
                        style = MaterialTheme.typography.bodySmall,
                        color = HubPrimary
                    )
                }
                Button(onClick = onContinue) { Text("Continue") }
            }
        }
    }
}

@Composable
private fun ScoreChipRow(
    score: Int,
    onOpenProfile: () -> Unit,
    compact: Boolean = true,
    interactiveHint: Boolean = true,
) {
    Surface(
        onClick = onOpenProfile,
        shape = RoundedCornerShape(999.dp),
        color = HubSurfaceRaised,
        border = BorderStroke(1.dp, HubOutline.copy(alpha = 0.9f))
    ) {
        if (compact) {
            Row(
                modifier = Modifier
                    .widthIn(min = 94.dp)
                    .padding(start = 5.dp, end = 8.dp, top = 3.dp, bottom = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(24.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = HubPrimaryStrong.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, HubPrimary.copy(alpha = 0.35f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            score.toString(),
                            color = HubPrimary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    "Hardware",
                    style = MaterialTheme.typography.labelSmall,
                    color = HubTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        } else {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(34.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = HubPrimaryStrong.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, HubPrimary.copy(alpha = 0.35f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            score.toString(),
                            color = HubPrimary,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        "Compute score",
                        style = MaterialTheme.typography.labelSmall,
                        color = HubTextSecondary
                    )
                    Text(
                        if (interactiveHint) "Tap to reopen device profile" else "Device profile summary",
                        style = MaterialTheme.typography.labelSmall,
                        color = HubTextPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionPanel(
    modifier: Modifier = Modifier,
    accent: Color = HubOutline,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(26.dp),
        color = HubSurface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.24f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
private fun SectionEyebrow(label: String, tone: Color) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = tone.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, tone.copy(alpha = 0.18f))
    ) {
        Text(
            label.uppercase(Locale.US),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = tone
        )
    }
}

@Composable
private fun MetaLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = HubTextSecondary)
        Text(value, style = MaterialTheme.typography.bodySmall, color = HubTextPrimary, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun StatBadge(label: String, value: String, tone: Color = HubTextSecondary) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = HubSurfaceRaised,
        border = BorderStroke(1.dp, HubOutline.copy(alpha = 0.8f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = HubTextSecondary)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = tone)
        }
    }
}

@Composable
private fun TopLevelTabStrip(
    selectedTab: ModelHubTopLevelTab,
    onSelect: (ModelHubTopLevelTab) -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ModelHubTopLevelTab.entries.forEach { tab ->
            val selected = tab == selectedTab
            Surface(
                onClick = { onSelect(tab) },
                shape = RoundedCornerShape(999.dp),
                color = if (selected) HubPrimaryStrong.copy(alpha = 0.22f) else HubSurfaceRaised,
                border = BorderStroke(1.dp, if (selected) HubPrimary.copy(alpha = 0.45f) else HubOutline.copy(alpha = 0.85f))
            ) {
                Text(
                    text = tab.label.uppercase(Locale.US),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) HubPrimary else HubTextSecondary
                )
            }
        }
    }
}

@Composable
private fun BottomNavigationBar(
    selectedTab: ModelHubTopLevelTab,
    onSelect: (ModelHubTopLevelTab) -> Unit,
) {
    Surface(
        color = HubBackground.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, HubOutline.copy(alpha = 0.32f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModelHubTopLevelTab.entries.forEach { tab ->
                val selected = tab == selectedTab
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        onClick = { onSelect(tab) },
                        color = if (selected) HubSurfaceRaised else Color.Transparent,
                        shape = RoundedCornerShape(16.dp),
                        border = if (selected) BorderStroke(1.dp, HubOutline.copy(alpha = 0.78f)) else null
                    ) {
                        Column(
                            modifier = Modifier.padding(
                                horizontal = if (selected) 12.dp else 4.dp,
                                vertical = 8.dp
                            ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                modifier = Modifier.size(20.dp),
                                imageVector = when (tab) {
                                    ModelHubTopLevelTab.RECOMMENDED -> Icons.Default.AutoAwesome
                                    ModelHubTopLevelTab.BROWSE -> Icons.Default.Explore
                                    ModelHubTopLevelTab.DOWNLOADS -> Icons.Default.Download
                                    ModelHubTopLevelTab.IMPORT -> Icons.Default.Publish
                                },
                                contentDescription = null,
                                tint = if (selected) HubPrimary else HubTextSecondary
                            )
                            Text(
                                text = tab.label,
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) HubPrimary else HubTextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportGuideCard() {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = HubSurface,
        border = BorderStroke(1.dp, HubOutline.copy(alpha = 0.75f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = RoundedCornerShape(20.dp),
                color = HubSurfaceRaised,
                border = BorderStroke(1.dp, HubOutline.copy(alpha = 0.8f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Publish, contentDescription = null, tint = HubTextSecondary)
                }
            }
            Text("Select your model file", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = HubTextPrimary)
            Text(
                "Select your file, load it, and start chatting instantly.\nGet models from trusted repos like Hugging Face.",
                style = MaterialTheme.typography.bodyMedium,
                color = HubTextSecondary
            )
            Button(onClick = {}, shape = RoundedCornerShape(999.dp)) {
                Text("Select File  +")
            }
        }
    }
}

@Composable
private fun InlinePreferenceSection(
    currentUseCase: UserUseCase,
    hasCompletedOnboarding: Boolean,
    onSelect: (UserUseCase) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!hasCompletedOnboarding) {
            Text(
                "Choose how you mainly use AI",
                style = MaterialTheme.typography.bodySmall,
                color = HubTextSecondary
            )
        }
        PreferenceChips(options = UserUseCase.entries, selected = currentUseCase, onSelect = onSelect)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun <T : Enum<T>> PreferenceChips(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val color = if (option == selected) HubPrimary else HubTextSecondary
            Surface(
                onClick = { onSelect(option) },
                color = if (option == selected) HubPrimaryStrong.copy(alpha = 0.22f) else HubSurfaceRaised,
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, if (option == selected) HubPrimary.copy(alpha = 0.45f) else HubOutline.copy(alpha = 0.8f))
            ) {
                Text(
                    option.name.replace('_', ' ').uppercase(Locale.US),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    color = color,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun BrowseRepoCard(title: String, author: String, description: String, downloads: Long, variantCount: Int, family: String, tags: List<String>, fasterCount: Int, balancedCount: Int, slowCount: Int, riskCount: Int, onOpen: () -> Unit) =
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = HubSurface,
        border = BorderStroke(1.dp, HubOutline.copy(alpha = 0.85f))
    ) {
        Column(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = HubTextPrimary, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = HubTextSecondary, maxLines = 2)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Badge("$variantCount variants", HubPrimary)
                Badge("${formatDownloads(downloads)} downloads", HubTextSecondary)
                tags.take(1).forEach { tag ->
                    Badge(tag, HubTertiary)
                }
                val extraTagCount = (tags.size - 1).coerceAtLeast(0)
                if (extraTagCount > 0) {
                    Badge("+$extraTagCount", HubTextSecondary)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (fasterCount > 0) Badge("$fasterCount faster", toneFor(PerformanceLevel.FAST))
                if (balancedCount > 0) Badge("$balancedCount balanced", toneFor(PerformanceLevel.BALANCED))
                if (slowCount > 0) Badge("$slowCount heavier", toneFor(PerformanceLevel.SLOW))
                if (riskCount > 0) Badge("$riskCount try at risk", toneFor(PerformanceLevel.RISKY))
            }
            Text(author, style = MaterialTheme.typography.labelSmall, color = HubTextSecondary)
        }
    }

@Composable private fun BrowseRepoSkeleton() = Surface(
    shape = RoundedCornerShape(22.dp),
    color = HubSurface,
    border = BorderStroke(1.dp, HubOutline.copy(alpha = 0.85f))
) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Loading models...", style = MaterialTheme.typography.titleMedium, color = HubTextPrimary, fontWeight = FontWeight.SemiBold)
        Text("Checking runnable GGUF variants and descriptions.", style = MaterialTheme.typography.bodySmall, color = HubTextSecondary)
    }
}

@Composable private fun BrowseAppendLoadingCard() = Surface(
    shape = RoundedCornerShape(22.dp),
    color = HubSurface,
    border = BorderStroke(1.dp, HubOutline.copy(alpha = 0.85f))
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator()
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Loading more models...", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = HubTextPrimary)
            Text(
                "Fetching more runnable GGUF repos from Hugging Face.",
                style = MaterialTheme.typography.bodySmall,
                color = HubTextSecondary
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun BrowseDetailHero(title: String, author: String, downloads: Long, likes: Long, description: String, tags: List<String>) = Surface(
    shape = RoundedCornerShape(22.dp),
    color = HubSurface,
    border = BorderStroke(1.dp, HubOutline.copy(alpha = 0.85f))
) {
    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionEyebrow(author, HubPrimary)
        Text(title, style = MaterialTheme.typography.titleLarge, color = HubTextPrimary, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Badge("${formatDownloads(downloads)} downloads", HubTextSecondary)
            Badge("$likes likes", HubTextSecondary)
        }
        Text(description, style = MaterialTheme.typography.bodyMedium, color = HubTextSecondary, maxLines = 3)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tags.take(4).forEach { tag ->
                Badge(tag, HubTextSecondary)
            }
        }
    }
}

@Composable private fun EmptyBrowseState(title: String, body: String) = Surface(
    shape = RoundedCornerShape(20.dp),
    color = HubSurface,
    border = BorderStroke(1.dp, HubOutline.copy(alpha = 0.85f))
) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = HubTextPrimary)
        Text(body, style = MaterialTheme.typography.bodySmall, color = HubTextSecondary)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun FeaturedVariantCard(title: String, recommendation: ModelRecommendation, downloadStatus: ModelDownloadStatus, isActiveModel: Boolean, isBusy: Boolean, activeDownloadModelId: String?, accent: Color, onPrimaryAction: () -> Unit) =
    run {
        val shape = RoundedCornerShape(20.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(
                    brush = featuredCardBrush(title),
                    shape = shape
                )
        ) {
            FeaturedCardTexture(title = title, accent = accent)
            Surface(
                shape = shape,
                color = Color.Transparent,
                border = BorderStroke(1.dp, accent.copy(alpha = 0.26f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                when (title) {
                                    "Most Recommended" -> "FLAGSHIP ARTIFACT"
                                    "Fastest" -> "LATENCY OPTIMIZED"
                                    else -> "REASONING ENGINE"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = accent
                            )
                            Text(
                                recommendation.model.name,
                                style = if (title == "Most Recommended") MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = HubTextPrimary
                            )
                        }
                        when (title) {
                            "Most Recommended" -> Icon(Icons.Default.Verified, contentDescription = null, tint = accent)
                            "Fastest" -> Icon(Icons.Default.Bolt, contentDescription = null, tint = accent)
                            else -> Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = accent)
                        }
                    }
                    Text(
                        featuredExplanation(title, recommendation),
                        style = MaterialTheme.typography.bodySmall,
                        color = HubTextSecondary
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Badge("Size: ${formatSize(recommendation.model.sizeGb)}", accent)
                        Badge("Required: ${formatRam(recommendation.estimatedRamMb)} RAM", accent)
                    }
                    when (downloadStatus.state) {
                        DownloadState.DOWNLOADING -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            LinearProgressIndicator(
                                progress = { downloadStatus.progress / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                "Downloading ${downloadStatus.progress}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = HubTextPrimary
                            )
                            OutlinedButton(
                                onClick = onPrimaryAction,
                                enabled = activeDownloadModelId == recommendation.model.id
                            ) {
                                Text("Cancel")
                            }
                        }
                        else -> Button(
                            onClick = onPrimaryAction,
                            enabled = !isBusy && (activeDownloadModelId == null || activeDownloadModelId == recommendation.model.id),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(buttonLabel(downloadStatus.state, isActiveModel))
                        }
                    }
                    if (activeDownloadModelId != null && activeDownloadModelId != recommendation.model.id && downloadStatus.state != DownloadState.DOWNLOADED) {
                        Text(
                            "Another download is in progress. Wait for it to finish or cancel it first.",
                            style = MaterialTheme.typography.bodySmall,
                            color = HubTextSecondary
                        )
                    }
                }
            }
        }
    }

private fun featuredCardBrush(title: String): Brush = when (title) {
    "Most Recommended" -> Brush.radialGradient(
        colors = listOf(HubPrimary.copy(alpha = 0.14f), Color(0xFF231F2D), HubSurface.copy(alpha = 0.98f)),
        center = Offset(900f, 40f),
        radius = 1100f
    )
    "Fastest" -> Brush.linearGradient(
        colors = listOf(Color(0xFF120D20), Color(0xFF1D1235), Color(0xFF17171D))
    )
    else -> Brush.linearGradient(
        colors = listOf(Color(0xFF20160F), Color(0xFF1B1821), Color(0xFF17171D))
    )
}

@Composable
private fun FeaturedCardTexture(title: String, accent: Color) {
    Canvas(
        modifier = Modifier.fillMaxSize()
    ) {
        when (title) {
            "Most Recommended" -> {
                val step = 14.dp.toPx()
                val stroke = 1.dp.toPx()
                var x = 0f
                while (x <= size.width) {
                    drawLine(
                        color = accent.copy(alpha = 0.09f),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = stroke
                    )
                    x += step
                }
                var y = 0f
                while (y <= size.height) {
                    drawLine(
                        color = accent.copy(alpha = 0.08f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = stroke
                    )
                    y += step
                }
                var diagonal = -size.height
                while (diagonal <= size.width) {
                    drawLine(
                        color = HubPrimary.copy(alpha = 0.05f),
                        start = Offset(diagonal, 0f),
                        end = Offset(diagonal + size.height, size.height),
                        strokeWidth = stroke
                    )
                    diagonal += 28.dp.toPx()
                }
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(size.width * 0.92f, size.height * 0.08f),
                        radius = size.minDimension * 0.65f
                    )
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF674BB5).copy(alpha = 0.14f), Color.Transparent),
                        center = Offset(size.width * 0.1f, size.height * 0.88f),
                        radius = size.minDimension * 0.70f
                    )
                )
                val dotGap = 24.dp.toPx()
                var dotX = dotGap / 2f
                while (dotX < size.width) {
                    var dotY = dotGap / 2f
                    while (dotY < size.height) {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.035f),
                            radius = 0.8.dp.toPx(),
                            center = Offset(dotX, dotY)
                        )
                        dotY += dotGap
                    }
                    dotX += dotGap
                }
            }
            "Fastest" -> {
                val stripeGap = 54.dp.toPx()
                val scanGap = 4.dp.toPx()
                val stroke = 1.dp.toPx()
                var x = 0f
                while (x <= size.width) {
                    drawLine(
                        color = accent.copy(alpha = 0.08f),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = stroke
                    )
                    x += stripeGap
                }
                var y = 0f
                while (y <= size.height) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.035f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = stroke
                    )
                    y += scanGap
                }
                var slash = -size.height
                while (slash <= size.width) {
                    drawLine(
                        color = accent.copy(alpha = 0.06f),
                        start = Offset(slash, 0f),
                        end = Offset(slash + size.height * 0.65f, size.height),
                        strokeWidth = stroke
                    )
                    slash += 34.dp.toPx()
                }
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            accent.copy(alpha = 0.14f),
                            HubPrimary.copy(alpha = 0.24f),
                            accent.copy(alpha = 0.10f),
                            Color.Transparent
                        ),
                        start = Offset(size.width * 0.15f, 0f),
                        end = Offset(size.width * 0.85f, size.height)
                    )
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.12f), Color.Transparent),
                        center = Offset(size.width * 0.08f, size.height * 0.12f),
                        radius = size.minDimension * 0.55f
                    )
                )
            }
            else -> {
                val cell = 30.dp.toPx()
                val stroke = Stroke(width = 1.dp.toPx())
                var x = -cell
                while (x <= size.width + cell) {
                    var y = -cell
                    while (y <= size.height + cell) {
                        val top = Offset(x + cell / 2f, y)
                        val right = Offset(x + cell, y + cell / 2f)
                        val bottom = Offset(x + cell / 2f, y + cell)
                        val left = Offset(x, y + cell / 2f)
                        drawLine(accent.copy(alpha = 0.09f), top, right, strokeWidth = stroke.width)
                        drawLine(accent.copy(alpha = 0.09f), right, bottom, strokeWidth = stroke.width)
                        drawLine(accent.copy(alpha = 0.09f), bottom, left, strokeWidth = stroke.width)
                        drawLine(accent.copy(alpha = 0.09f), left, top, strokeWidth = stroke.width)
                        y += cell
                    }
                    x += cell
                }
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            accent.copy(alpha = 0.05f),
                            Color.Transparent
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, size.height)
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun VariantCard(recommendation: ModelRecommendation, downloadStatus: ModelDownloadStatus, isActiveModel: Boolean, isBusy: Boolean, activeDownloadModelId: String?, onPrimaryAction: () -> Unit) = Surface(
    shape = RoundedCornerShape(20.dp),
    color = HubSurface,
    border = BorderStroke(1.dp, HubOutline.copy(alpha = 0.85f))
) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(recommendation.model.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = HubTextPrimary)
                Text(recommendation.model.fileName, style = MaterialTheme.typography.bodySmall, color = HubTextSecondary)
                Text(recommendation.whyRecommended, style = MaterialTheme.typography.bodySmall, color = HubTextSecondary, maxLines = 2)
            }
            Badge(recommendation.performance.label, toneFor(recommendation.performance))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Badge(recommendation.variantLabel, HubPrimary)
            if (isActiveModel) Badge("Active", HubTertiary)
        }
        MetaLine("Variant", "${formatSize(recommendation.model.sizeGb)} | ${recommendation.model.quantization}")
        MetaLine("Estimated runtime memory", formatRam(recommendation.estimatedRamMb))
        if (recommendation.performance == PerformanceLevel.RISKY) Text("Risky fit: this variant may be unstable on this device.", style = MaterialTheme.typography.bodySmall, color = HubTertiary)
        when (downloadStatus.state) {
            DownloadState.DOWNLOADING -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(progress = { downloadStatus.progress / 100f }, modifier = Modifier.fillMaxWidth())
                Text("Downloading ${downloadStatus.progress}%", style = MaterialTheme.typography.labelMedium, color = HubTextPrimary)
                OutlinedButton(
                    onClick = onPrimaryAction,
                    enabled = activeDownloadModelId == recommendation.model.id
                ) {
                    Text("Cancel")
                }
            }
            else -> Button(
                onClick = onPrimaryAction,
                enabled = !isBusy && (activeDownloadModelId == null || activeDownloadModelId == recommendation.model.id)
            ) { Text(buttonLabel(downloadStatus.state, isActiveModel)) }
        }
        if (activeDownloadModelId != null && activeDownloadModelId != recommendation.model.id && downloadStatus.state != DownloadState.DOWNLOADED) {
            Text(
                "Another download is in progress. Wait for it to finish or cancel it first.",
                style = MaterialTheme.typography.bodySmall,
                color = HubTextSecondary
            )
        }
    }
}

@Composable private fun ModelHubDialog(state: ModelHubDialogState, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Risky Fit") },
        text = { Text("This variant may be unstable on your device, but you can still try it.") },
        confirmButton = { Button(onClick = onConfirm) { Text("Continue") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable private fun Badge(label: String, color: Color) = Surface(
    color = color.copy(alpha = 0.12f),
    shape = RoundedCornerShape(999.dp),
    border = BorderStroke(1.dp, color.copy(alpha = 0.18f))
) {
    Text(
        label,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        color = color,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold
    )
}

private fun onVariantAction(
    recommendation: ModelRecommendation,
    downloadStatus: ModelDownloadStatus,
    onUse: () -> Unit,
    onDownload: () -> Unit,
    onDialog: (ModelHubDialogState) -> Unit,
    onCancel: () -> Unit,
) {
    when (downloadStatus.state) {
        DownloadState.DOWNLOADED -> onUse()
        DownloadState.DOWNLOADING -> onCancel()
        DownloadState.PAUSED, DownloadState.CANCELLED, DownloadState.FAILED, DownloadState.IDLE ->
            if (recommendation.performance == PerformanceLevel.RISKY) {
                onDialog(ModelHubDialogState.Risky(recommendation.model))
            } else {
                onDownload()
            }
    }
}

private fun featuredExplanation(title: String, recommendation: ModelRecommendation): String = when {
    title == "Most Recommended" -> "High-fidelity reasoning and instruction model optimized for this device profile."
    title == "Fastest" -> "Latency-first intelligence for instant feedback loops and mobile-native execution."
    title == "Higher Quality" ->
        if (recommendation.performance == PerformanceLevel.SLOW) {
            "State-of-the-art reasoning density for deeper logic, with a heavier runtime tradeoff."
        } else {
            "A heavier quality-focused stretch chosen for better answer quality."
        }
    else -> recommendation.whyRecommended
}

@Composable
private fun InlineFeaturedNote(message: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = HubSurface,
        border = BorderStroke(1.dp, HubOutline.copy(alpha = 0.85f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = HubTextSecondary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        )
    }
}

private data class VariantSection(
    val key: String,
    val title: String,
    val subtitle: String,
    val variants: List<ModelRecommendation>,
)

private fun buildVariantSections(variants: List<ModelRecommendation>): List<VariantSection> {
    val faster = variants.filter { it.performance == PerformanceLevel.FAST }
    val balanced = variants.filter { it.performance == PerformanceLevel.BALANCED }
    val heavier = variants.filter { it.performance == PerformanceLevel.SLOW }
    val risky = variants.filter { it.performance == PerformanceLevel.RISKY }
    return buildList {
        if (faster.isNotEmpty()) {
            add(
                VariantSection(
                    key = "faster",
                    title = "Faster Variants",
                    subtitle = "Lightest response paths with the least runtime pressure.",
                    variants = faster
                )
            )
        }
        if (balanced.isNotEmpty()) {
            add(
                VariantSection(
                    key = "balanced",
                    title = "Balanced Variants",
                    subtitle = "Better quality tradeoffs that should still run on this device.",
                    variants = balanced
                )
            )
        }
        if (heavier.isNotEmpty()) {
            add(
                VariantSection(
                    key = "heavier",
                    title = "Heavier Variants",
                    subtitle = "Stretch-up variants that may run slower, but can still be worth trying.",
                    variants = heavier
                )
            )
        }
        if (risky.isNotEmpty()) {
            add(
                VariantSection(
                    key = "risk",
                    title = "Try At Risk",
                    subtitle = "Heavier variants that may be unstable or very slow on this device.",
                    variants = risky
                )
            )
        }
    }
}

private data class DownloadListItem(
    val modelId: String,
    val model: ModelInfo?,
    val title: String,
    val subtitle: String,
    val status: ModelDownloadStatus,
    val isActive: Boolean,
)

@Composable
private fun ManualModelCard(
    path: String,
    isLoaded: Boolean,
) {
    val fileName = File(path).name
    val label = if (isLoaded) "Ready" else "Manual"
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.035f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.05f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Publish, contentDescription = null, tint = HubPrimary)
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Manual / Local GGUF", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = HubTextPrimary)
                    DownloadStateBadge(label, if (isLoaded) Color(0xFF6BE4A8) else HubTertiary)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DownloadMetaText(fileName)
                }
                Text(path, style = MaterialTheme.typography.bodySmall, color = HubTextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ActiveDownloadHero(entry: DownloadListItem, onPause: () -> Unit, onCancel: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.03f),
        border = BorderStroke(1.dp, HubPrimary.copy(alpha = 0.22f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = HubPrimaryStrong.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, HubPrimary.copy(alpha = 0.3f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = HubPrimary)
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            entry.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = HubTextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${entry.status.progress}% COMPLETE",
                                style = MaterialTheme.typography.labelSmall,
                                color = HubPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text("•", color = HubTextSecondary, style = MaterialTheme.typography.labelSmall)
                            Text(
                                entry.subtitle.uppercase(Locale.US),
                                style = MaterialTheme.typography.labelSmall,
                                color = HubTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPause) {
                        Icon(Icons.Default.Pause, contentDescription = "Pause download", tint = HubTextSecondary)
                    }
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel download", tint = HubTextSecondary)
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(999.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(entry.status.progress.coerceIn(0, 100) / 100f)
                        .background(
                            brush = Brush.horizontalGradient(listOf(HubPrimary, Color(0xFF00F2FE))),
                            shape = RoundedCornerShape(999.dp)
                        )
                        .padding(vertical = 1.5.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DownloadMetaText("DOWNLOADING ASSETS")
                DownloadMetaText(entry.status.errorMessage?.uppercase(Locale.US) ?: "IN PROGRESS")
            }
        }
    }
}

@Composable
private fun DownloadedModelCard(entry: DownloadListItem, onUse: () -> Unit, onDownload: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.035f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.05f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = downloadLibraryIcon(entry.title),
                        contentDescription = null,
                        tint = HubPrimary.copy(alpha = 0.8f)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(entry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = HubTextPrimary)
                    DownloadStateBadge(if (entry.isActive) "Ready" else downloadStateLabel(entry.status.state), if (entry.isActive) Color(0xFF6BE4A8) else downloadStateColor(entry.status.state))
                }
                DownloadMetaText(entry.subtitle)
                entry.status.localPath?.let {
                    DownloadMetaText(File(it).name)
                }
            }
            when (entry.status.state) {
                DownloadState.DOWNLOADED -> OutlinedButton(
                    onClick = onUse,
                    enabled = !entry.isActive,
                    border = BorderStroke(1.dp, HubPrimary.copy(alpha = 0.28f)),
                    shape = RoundedCornerShape(10.dp),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor = HubPrimary,
                        disabledContentColor = HubTextPrimary.copy(alpha = 0.92f),
                        disabledContainerColor = HubSurfaceRaised.copy(alpha = 0.55f)
                    )
                ) {
                    Text(if (entry.isActive) "Using" else "Use")
                }
                DownloadState.CANCELLED, DownloadState.FAILED, DownloadState.PAUSED -> OutlinedButton(
                    onClick = onDownload,
                    enabled = entry.model != null,
                    border = BorderStroke(1.dp, HubPrimary.copy(alpha = 0.28f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(if (entry.status.state == DownloadState.PAUSED) "Resume" else "Download")
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun DownloadStateBadge(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.20f))
    ) {
        Text(
            text = label.uppercase(Locale.US),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun DownloadMetaText(value: String) {
    Text(
        text = value,
        style = MaterialTheme.typography.labelSmall,
        color = HubTextSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

private fun downloadLibraryIcon(title: String): ImageVector = when {
    title.contains("image", ignoreCase = true) || title.contains("diffusion", ignoreCase = true) -> Icons.Default.Image
    title.contains("coder", ignoreCase = true) || title.contains("code", ignoreCase = true) -> Icons.Default.Code
    title.contains("chat", ignoreCase = true) || title.contains("nemo", ignoreCase = true) -> Icons.Default.Forum
    else -> Icons.Default.AutoAwesome
}
private fun toneFor(performance: PerformanceLevel): Color = when (performance) {
    PerformanceLevel.FAST -> Color(0xFF8FC8FF)
    PerformanceLevel.BALANCED -> Color(0xFF71D4BC)
    PerformanceLevel.SLOW -> Color(0xFFF2B56D)
    PerformanceLevel.RISKY -> Color(0xFFFF9F7A)
}

private fun downloadStateLabel(state: DownloadState): String = when (state) {
    DownloadState.IDLE -> "Idle"
    DownloadState.DOWNLOADING -> "Downloading"
    DownloadState.PAUSED -> "Paused"
    DownloadState.DOWNLOADED -> "Downloaded"
    DownloadState.CANCELLED -> "Cancelled"
    DownloadState.FAILED -> "Failed"
}

private fun downloadStateColor(state: DownloadState): Color = when (state) {
    DownloadState.IDLE -> Color(0xFF7B8192)
    DownloadState.DOWNLOADING -> Color(0xFF5B8DEF)
    DownloadState.PAUSED -> Color(0xFF8FC8FF)
    DownloadState.DOWNLOADED -> Color(0xFF6D5BD0)
    DownloadState.CANCELLED -> Color(0xFFB7791F)
    DownloadState.FAILED -> Color(0xFFCC5A71)
}

private fun formatRam(ramMb: Int): String = String.format(Locale.US, "%.1f GB", ramMb / 1024f)
private fun formatSize(sizeGb: Float): String = String.format(Locale.US, "%.1f GB", sizeGb)
private fun formatDownloads(downloads: Long): String = when {
    downloads >= 1_000_000 -> String.format(Locale.US, "%.1fM", downloads / 1_000_000f)
    downloads >= 1_000 -> String.format(Locale.US, "%.1fK", downloads / 1_000f)
    else -> downloads.toString()
}

private fun computeDeviceScore(deviceProfile: com.monicauditya.june.modelhub.DeviceProfile): Int {
    val usableRamGb = deviceProfile.usableRamMb / 1024f
    val availableRamGb = deviceProfile.availableRamMb / 1024f
    val totalRamGb = deviceProfile.totalRamMb / 1024f

    val ramScore = (((usableRamGb.coerceIn(2f, 16f) - 2f) / 14f) * 28f) + 18f
    val headroomRatio = (deviceProfile.usableRamMb.toFloat() / deviceProfile.availableRamMb.coerceAtLeast(1)).coerceIn(0.30f, 1.0f)
    val headroomScore = ((headroomRatio - 0.30f) / 0.70f) * 10f
    val cpuTierScore = when (deviceProfile.cpuTier) {
        com.monicauditya.june.modelhub.CpuTier.HIGH -> 30f
        com.monicauditya.june.modelhub.CpuTier.MID -> 22f
        com.monicauditya.june.modelhub.CpuTier.LOW -> 14f
    }
    val cpuCoreBonus = ((deviceProfile.cpuCores - 4).coerceIn(0, 8) * 1.5f)
    val abiScore = if (deviceProfile.abi.contains("arm64", ignoreCase = true)) 8f else 5f
    val capacityBonus = ((availableRamGb / totalRamGb.coerceAtLeast(1f)) * 4f).coerceIn(0f, 4f)
    val emulatorPenalty = if (deviceProfile.isEmulator) 6f else 0f

    return (ramScore + headroomScore + cpuTierScore + cpuCoreBonus + abiScore + capacityBonus - emulatorPenalty)
        .toInt()
        .coerceIn(20, 100)
}

private sealed interface ModelHubDialogState { val model: ModelInfo; data class Risky(override val model: ModelInfo) : ModelHubDialogState }
private val PerformanceLevel.label: String get() = when (this) { PerformanceLevel.FAST -> "Likely smooth"; PerformanceLevel.BALANCED -> "Balanced load"; PerformanceLevel.SLOW -> "Heavier run"; PerformanceLevel.RISKY -> "Try at risk" }
private val ModelRecommendation.variantLabel: String get() = when (performance) {
    PerformanceLevel.FAST -> "Faster"
    PerformanceLevel.BALANCED -> "Balanced"
    PerformanceLevel.SLOW -> "Heavier"
    PerformanceLevel.RISKY -> "Try at risk"
}
private fun buttonLabel(state: DownloadState, isActiveModel: Boolean): String = when {
    isActiveModel -> "Model Ready"
    state == DownloadState.DOWNLOADED -> "Use Model"
    state == DownloadState.PAUSED -> "Resume"
    state == DownloadState.FAILED || state == DownloadState.CANCELLED -> "Download"
    state == DownloadState.DOWNLOADING -> "Cancel"
    else -> "Download"
}


