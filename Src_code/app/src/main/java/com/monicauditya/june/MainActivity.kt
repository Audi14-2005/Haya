package com.monicauditya.june

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.monicauditya.june.ui.ChatHistoryItem
import com.monicauditya.june.modelhub.DownloadState
import com.monicauditya.june.modelhub.ModelDownloadManager
import com.monicauditya.june.data.SharedPrefStore
import com.monicauditya.june.domain.ChatUseCase
import com.monicauditya.june.ui.ChatModelOption
import com.monicauditya.june.ui.ChatScreen
import com.monicauditya.june.ui.ChatViewModel
import com.monicauditya.june.ui.ModelHubScreen
import com.monicauditya.june.ui.ModelHubViewModel
import com.monicauditya.june.ui.PREF_ACTIVE_MODEL_ID
import com.monicauditya.june.ui.PREF_ACTIVE_MODEL_NAME
import com.monicauditya.june.ui.PREF_ACTIVE_MODEL_PATH
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel
import java.io.File
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch

private enum class AppDestination {
    HUB,
    CHAT,
    SETTINGS,
}

private fun cleanLocalModelDisplayName(raw: String): String {
    val normalized = raw
        .replace(Regex("""_[0-9a-f]{6,}$""", RegexOption.IGNORE_CASE), "")
        .replace('_', ' ')
        .replace('-', ' ')
        .replace(Regex("""\s+"""), " ")
        .trim()

    val lower = normalized.lowercase()
    val sizeMatch = Regex("""\b\d+(?:\.\d+)?b\b""", RegexOption.IGNORE_CASE)
        .find(lower)
        ?.value
        ?.uppercase()
    val quantMatch = Regex("""\bq\d+\b""", RegexOption.IGNORE_CASE)
        .find(lower)
        ?.value
        ?.uppercase()

    val ignored = setOf(
        "gguf", "chat", "instruct", "instruction", "it", "preview", "base",
        "km", "ks", "m", "s", "k", "mini", "model"
    )

    val rawParts = normalized.split(" ").filter { it.isNotBlank() }
    val nameParts = mutableListOf<String>()
    rawParts.forEach { token ->
        val lowerToken = token.lowercase()
        if (Regex("""\d+(?:\.\d+)?b""", RegexOption.IGNORE_CASE).matches(lowerToken)) return@forEach
        if (Regex("""q\d+.*""", RegexOption.IGNORE_CASE).matches(lowerToken)) return@forEach
        if (lowerToken in ignored) return@forEach
        if (nameParts.none { it.equals(token, ignoreCase = true) }) {
            nameParts += token.lowercase().replaceFirstChar {
                if (it.isLowerCase()) it.titlecase() else it.toString()
            }
        }
    }

    return buildString {
        append(nameParts.take(3).joinToString(" ").ifBlank { "Model" })
        if (!sizeMatch.isNullOrBlank()) append(" $sizeMatch")
        if (!quantMatch.isNullOrBlank()) append(" $quantMatch")
    }.replace(Regex("""\s+"""), " ").trim()
}

class MainActivity : ComponentActivity() {
    private val sharedPrefStore: SharedPrefStore by inject()
    private val chatUseCase: ChatUseCase by inject()
    private val modelDownloadManager: ModelDownloadManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                1001
            )
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = AndroidColor.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        val savedModelPath = sharedPrefStore.get(PREF_ACTIVE_MODEL_PATH, "")
        val savedModelName = sharedPrefStore.get(PREF_ACTIVE_MODEL_NAME, "")
        val savedModelId = sharedPrefStore.get(PREF_ACTIVE_MODEL_ID, savedModelPath)
        val startupLocalModels = buildList {
            val savedFile = savedModelPath.takeIf { it.isNotBlank() }?.let(::File)
            if (savedFile != null && savedFile.exists()) {
                add(
                    ChatModelOption(
                        modelId = savedModelId.ifBlank { savedFile.absolutePath },
                        title = cleanLocalModelDisplayName(
                            savedModelName.ifBlank { savedFile.nameWithoutExtension }
                        ),
                        modelPath = savedFile.absolutePath
                    )
                )
                chatUseCase.setModelPath(savedFile.absolutePath)
            }

            modelDownloadManager.getModelsDirectory()
                .listFiles()
                ?.filter { it.isFile && it.extension.equals("gguf", ignoreCase = true) }
                ?.sortedByDescending { it.lastModified() }
                ?.forEach { file ->
                    if (savedFile?.absolutePath != file.absolutePath) {
                        add(
                            ChatModelOption(
                                modelId = file.absolutePath,
                                title = cleanLocalModelDisplayName(file.nameWithoutExtension),
                                modelPath = file.absolutePath
                            )
                        )
                    }
                }
        }
        val initialModelOption = startupLocalModels.firstOrNull()
        val hasStartupLocalModel = initialModelOption != null
        if (initialModelOption != null && savedModelPath.isBlank()) {
            chatUseCase.setModelPath(initialModelOption.modelPath)
        }
        if (initialModelOption != null && !chatUseCase.isModelLoaded()) {
            runBlocking {
                chatUseCase.loadModel(forceReload = false)
            }
        }

        setContent {
            MaterialTheme {
                val modelHubViewModel: ModelHubViewModel = koinViewModel()
                val modelHubState by modelHubViewModel.state.collectAsState()
                val chatViewModel: ChatViewModel = koinViewModel()
                val chatState by chatViewModel.state.collectAsState()
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val drawerScope = rememberCoroutineScope()
                var destination by rememberSaveable {
                    mutableStateOf(if (hasStartupLocalModel) AppDestination.CHAT else AppDestination.HUB)
                }
                var chatModelOptions by remember(startupLocalModels) {
                    mutableStateOf(startupLocalModels)
                }
                var activeChatModelId by rememberSaveable {
                    mutableStateOf(initialModelOption?.modelId)
                }

                BackHandler(enabled = drawerState.isOpen || destination != AppDestination.HUB) {
                    when {
                        drawerState.isOpen -> drawerScope.launch { drawerState.close() }
                        destination == AppDestination.CHAT -> destination = AppDestination.HUB
                        else -> destination = AppDestination.CHAT
                    }
                }

                LaunchedEffect(modelHubState.navigateToChatToken) {
                    if (modelHubState.navigateToChatToken != null) {
                        destination = AppDestination.CHAT
                        activeChatModelId = modelHubState.activeModelId
                        val knownModels = buildMap {
                            modelHubState.models.forEach { put(it.id, it.name) }
                            modelHubState.recommendations.forEach { put(it.model.id, it.model.name) }
                            modelHubState.browseDetails.variants.forEach { put(it.model.id, it.model.name) }
                        }
                        chatModelOptions = modelHubState.downloadStatuses.entries
                            .filter { it.value.state == DownloadState.DOWNLOADED }
                            .map { (modelId, status) ->
                                val title = knownModels[modelId]
                                    ?: modelId.substringBefore("::").ifBlank {
                                        status.localPath?.let { File(it).nameWithoutExtension } ?: "Model"
                                    }
                                ChatModelOption(
                                    modelId = modelId,
                                    title = cleanLocalModelDisplayName(title),
                                    modelPath = status.localPath ?: modelId
                                )
                            }
                            .sortedBy { it.title.lowercase() }
                        modelHubViewModel.consumeNavigateToChat()
                    }
                }

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    gesturesEnabled = true,
                    drawerContent = {
                        AppSidebar(
                            currentDestination = destination,
                            histories = chatState.histories,
                            onClose = { drawerScope.launch { drawerState.close() } },
                            onOpenModelSettings = {
                                destination = AppDestination.HUB
                                drawerScope.launch { drawerState.close() }
                            },
                            onOpenGeneralSettings = {
                                destination = AppDestination.SETTINGS
                                drawerScope.launch { drawerState.close() }
                            },
                            onNewChat = {
                                chatViewModel.createNewChat()
                                destination = AppDestination.CHAT
                                drawerScope.launch { drawerState.close() }
                            },
                            onDeleteHistory = { chatId ->
                                chatViewModel.deleteChat(chatId)
                            },
                            onOpenHistory = { chatId ->
                                chatViewModel.openChat(chatId)
                                destination = AppDestination.CHAT
                                drawerScope.launch { drawerState.close() }
                            }
                        )
                    }
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        when (destination) {
                            AppDestination.HUB -> {
                                ModelHubScreen(
                                    onMenuClick = { drawerScope.launch { drawerState.open() } },
                                    viewModel = modelHubViewModel
                                )
                            }
                            AppDestination.CHAT -> {
                                ChatScreen(
                                    state = chatState,
                                    onSendMessage = chatViewModel::sendMessage,
                                    onImportModel = { uri -> chatViewModel.importModel(this@MainActivity, uri) },
                                    onStopGeneration = chatViewModel::stopGeneration,
                                    modelOptions = chatModelOptions,
                                    activeModelId = activeChatModelId,
                                    modelSwitchInProgress = chatState.modelState == com.monicauditya.june.ui.ModelState.Loading,
                                    onMenuClick = { drawerScope.launch { drawerState.open() } },
                                    onSelectModel = { modelId ->
                                        val option = chatModelOptions.firstOrNull { it.modelId == modelId } ?: return@ChatScreen
                                        chatViewModel.switchModel(option.modelPath) { switched ->
                                            if (switched) {
                                                activeChatModelId = option.modelId
                                                sharedPrefStore.put(PREF_ACTIVE_MODEL_PATH, option.modelPath)
                                                sharedPrefStore.put(PREF_ACTIVE_MODEL_NAME, option.title)
                                                sharedPrefStore.put(PREF_ACTIVE_MODEL_ID, option.modelId)
                                            }
                                        }
                                    }
                                )
                            }
                            AppDestination.SETTINGS -> {
                                GeneralSettingsScreen(
                                    onMenuClick = { drawerScope.launch { drawerState.open() } }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppSidebar(
    currentDestination: AppDestination,
    histories: List<ChatHistoryItem>,
    onClose: () -> Unit,
    onOpenModelSettings: () -> Unit,
    onOpenGeneralSettings: () -> Unit,
    onNewChat: () -> Unit,
    onDeleteHistory: (Long) -> Unit,
    onOpenHistory: (Long) -> Unit,
) {
    ModalDrawerSheet(
        modifier = Modifier
            .width(332.dp)
            .fillMaxHeight()
            .background(Color.Transparent),
        drawerContainerColor = Color.Transparent,
        drawerContentColor = MaterialTheme.colorScheme.onSurface,
        windowInsets = WindowInsets.safeDrawing
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF121217).copy(alpha = 0.96f),
            shape = RoundedCornerShape(topEnd = 32.dp, bottomEnd = 32.dp),
            border = BorderStroke(1.dp, Color(0xFF2A292E).copy(alpha = 0.32f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFCEBDFF).copy(alpha = 0.08f),
                                Color(0xFF121217),
                                Color(0xFF0B0B0F)
                            )
                        )
                    )
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Haya",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF4F1F8)
                    )
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF1B1B1F).copy(alpha = 0.9f),
                        border = BorderStroke(1.dp, Color(0xFF2A292E).copy(alpha = 0.32f)),
                        modifier = Modifier.clickable(onClick = onClose)
                    ) {
                        Box(modifier = Modifier.padding(10.dp)) {
                            Text("×", color = Color(0xFFCBC3D7), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }

                SidebarAction(
                    title = "Model Settings",
                    icon = Icons.Default.Tune,
                    selected = currentDestination == AppDestination.HUB,
                    onClick = onOpenModelSettings
                )
                SidebarAction(
                    title = "General Settings",
                    icon = Icons.Default.Settings,
                    selected = currentDestination == AppDestination.SETTINGS,
                    onClick = onOpenGeneralSettings
                )
                SidebarAction(
                    title = "New Chat",
                    icon = Icons.Default.Add,
                    selected = false,
                    onClick = onNewChat
                )

                Text(
                    text = "History",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFFCBC3D7),
                    fontWeight = FontWeight.SemiBold
                )

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(histories, key = { it.id }) { history ->
                        SidebarHistoryRow(
                            item = history,
                            onDelete = { onDeleteHistory(history.id) },
                            onClick = { onOpenHistory(history.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SidebarAction(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (selected) Color(0xFF9B7FED).copy(alpha = 0.22f) else Color(0xFF1B1B1F).copy(alpha = 0.74f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0xFF2A292E).copy(alpha = if (selected) 0.42f else 0.22f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, tint = if (selected) Color(0xFFCEBDFF) else Color(0xFFCBC3D7))
            Text(
                text = title,
                color = Color(0xFFF4F1F8),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun SidebarHistoryRow(
    item: ChatHistoryItem,
    onDelete: () -> Unit,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (item.isActive) Color(0xFF262033).copy(alpha = 0.92f) else Color(0xFF17171D).copy(alpha = 0.86f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color(0xFF2A292E).copy(alpha = if (item.isActive) 0.36f else 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .background(
                        if (item.isActive) Color(0xFFCEBDFF).copy(alpha = 0.18f) else Color(0xFF232327),
                        CircleShape
                    )
                    .padding(8.dp)
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = if (item.isActive) Color(0xFFCEBDFF) else Color(0xFFCBC3D7),
                    modifier = Modifier.width(16.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = item.title,
                    color = Color(0xFFF4F1F8),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (item.isActive) "Current conversation" else "Open conversation",
                    color = Color(0xFF958EA0),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Surface(
                shape = CircleShape,
                color = Color(0xFF232327).copy(alpha = 0.9f),
                border = BorderStroke(1.dp, Color(0xFF2A292E).copy(alpha = 0.24f)),
                modifier = Modifier.clickable(onClick = onDelete)
            ) {
                Box(modifier = Modifier.padding(8.dp)) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Delete chat",
                        tint = Color(0xFFCBC3D7)
                    )
                }
            }
        }
    }
}

@Composable
private fun GeneralSettingsScreen(
    onMenuClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B0F))
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp, top = 6.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1F1F26).copy(alpha = 0.92f),
            border = BorderStroke(1.dp, Color(0xFF3A3447).copy(alpha = 0.72f))
        ) {
            Row(
                modifier = Modifier
                    .clickable(onClick = onMenuClick)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Menu, contentDescription = "Open navigation", tint = Color(0xFFF4F1F8))
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "General Settings",
                style = MaterialTheme.typography.headlineMedium,
                color = Color(0xFFF4F1F8),
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "This page is intentionally empty for now.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFCBC3D7)
            )
        }
    }
}
