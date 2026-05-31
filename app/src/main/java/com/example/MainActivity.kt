package com.example

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.provider.Settings
import android.util.Log
import android.media.MediaPlayer
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.data.WallpaperItem
import com.example.ui.theme.MyApplicationTheme
import java.io.File
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WallpaperAppScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperAppScreen(
    viewModel: WallpaperViewModel = viewModel()
) {
    val context = LocalContext.current
    val wallpapers by viewModel.wallpaperList.collectAsStateWithLifecycle()
    val activeWallpaper by viewModel.activeWallpaper.collectAsStateWithLifecycle()
    val editingItem by viewModel.editingItem.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()

    // Query permissions at runtime
    val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        android.Manifest.permission.READ_MEDIA_VIDEO
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.checkSelfPermission(android.Manifest.permission.READ_MEDIA_VIDEO) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val title = getFileName(context, uri) ?: "Видео обои"
            viewModel.startEditingNewVideo(uri, context, title)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.app_name),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        },
        floatingActionButton = {
            if (editingItem == null) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (hasPermission) {
                            videoPickerLauncher.launch("video/mp4")
                        } else {
                            permissionLauncher.launch(storagePermission)
                        }
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(text = stringResource(id = R.string.add_to_library)) },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.testTag("add_wallpaper_fab")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            AnimatedContent(
                targetState = editingItem,
                transitionSpec = {
                    slideInHorizontally { width -> width } + fadeIn() togetherWith
                            slideOutHorizontally { width -> -width } + fadeOut()
                },
                label = "ScreenStateTransition"
            ) { item ->
                if (item != null) {
                    // Show Editor panel
                    VideoEditorScreen(
                        item = item,
                        viewModel = viewModel,
                        onBackPressed = { viewModel.cancelEditing() }
                    )
                } else {
                    // Show Wallpaper list panel
                    WallpaperListScreen(
                        wallpapers = wallpapers,
                        activeWallpaper = activeWallpaper,
                        hasPermission = hasPermission,
                        onRequestPermission = { permissionLauncher.launch(storagePermission) },
                        onSelectVideo = { videoPickerLauncher.launch("video/mp4") },
                        onEdit = { viewModel.startEditingExisting(it, context) },
                        onDelete = { viewModel.deleteWallpaper(it) },
                        onSetActive = {
                            viewModel.setActive(it.id)
                            launchWallpaperSelection(context)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun WallpaperListScreen(
    wallpapers: List<WallpaperItem>,
    activeWallpaper: WallpaperItem?,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onSelectVideo: () -> Unit,
    onEdit: (WallpaperItem) -> Unit,
    onDelete: (WallpaperItem) -> Unit,
    onSetActive: (WallpaperItem) -> Unit
) {
    if (!hasPermission) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(id = R.string.permission_required),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(id = R.string.permission_desc),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onRequestPermission,
                modifier = Modifier.fillMaxWidth().testTag("permission_button")
            ) {
                Text("Предоставить доступ")
            }
        }
    } else if (wallpapers.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.VideoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(84.dp)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(id = R.string.empty_library_message),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onSelectVideo,
                modifier = Modifier.testTag("select_first_video_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Выбрать MP4")
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Highlights Section
            activeWallpaper?.let { active ->
                item {
                    Text(
                        text = "Активный профиль",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    ActiveWallpaperCard(active = active, onSetClick = { onSetActive(active) })
                }
            }

            // Library header
            item {
                Text(
                    text = stringResource(id = R.string.library_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            items(wallpapers, key = { it.id }) { item ->
                WallpaperItemRow(
                    item = item,
                    onEdit = { onEdit(item) },
                    onDelete = { onDelete(item) },
                    onSelect = { onSetActive(item) }
                )
            }
        }
    }
}

@Composable
fun ActiveWallpaperCard(
    active: WallpaperItem,
    onSetClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("active_wallpaper_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        ),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.DarkGray)
            ) {
                if (active.thumbnailUriStr != null) {
                    AsyncImage(
                        model = File(active.thumbnailUriStr),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.MovieFilter,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = active.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (active.isLooping) Icons.Default.Loop else Icons.Default.NavigateNext,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (active.isLooping) "Зациклен" else "Один раз",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (active.nightModeEnabled) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Icon(
                            imageVector = Icons.Default.Nightlight,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Автояркость",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onSetClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.Wallpaper, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Запуск", fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun WallpaperItemRow(
    item: WallpaperItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSelect: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.confirm_delete)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    }
                ) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .testTag("wallpaper_item_card_${item.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isActive) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.DarkGray)
            ) {
                if (item.thumbnailUriStr != null) {
                    AsyncImage(
                        model = File(item.thumbnailUriStr),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        tint = Color.LightGray,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                // Small play icon overlay to represent multimedia video
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.Center)
                )

                if (item.isActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                            .align(Alignment.BottomCenter)
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = "АКТИВЕН",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Loop badge
                    Icon(
                        imageVector = if (item.isLooping) Icons.Default.Repeat else Icons.Default.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (item.isLooping) "Циклично" else "Одиночный",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Night mode indicator
                    if (item.nightModeEnabled) {
                        VerticalDivider(Modifier.height(10.dp))
                        Icon(
                            imageVector = Icons.Default.DarkMode,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = Color.Gray
                        )
                        Text(
                            text = "Ночь",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Display Trim bounds details
                if (item.trimStartMs > 0L || item.trimEndMs > 0L) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Отрезок: ${formatTime(item.trimStartMs)} - ${formatTime(item.trimEndMs)}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Light
                    )
                }
            }

            // Commands column
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.testTag("edit_button_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.testTag("delete_button_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun VideoEditorScreen(
    item: WallpaperItem,
    viewModel: WallpaperViewModel,
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    val videoDurationMs by viewModel.videoDurationMs.collectAsStateWithLifecycle()

    // Seek states
    var startPercentage by remember(item.id) {
        val duration = if (videoDurationMs > 0) videoDurationMs.toFloat() else 10000f
        mutableStateOf(item.trimStartMs.toFloat() / duration)
    }
    var endPercentage by remember(item.id) {
        val duration = if (videoDurationMs > 0) videoDurationMs.toFloat() else 10000f
        val calculatedEnd = if (item.trimEndMs > 0) item.trimEndMs.toFloat() else duration
        mutableStateOf(calculatedEnd / duration)
    }

    // Cover extraction seeker index
    var coverTimeMs by remember { mutableStateOf(item.trimStartMs) }

    val durationMs = if (videoDurationMs > 0) videoDurationMs else 1L
    val startMsChecked = (startPercentage * durationMs).toLong().coerceIn(0L, durationMs)
    val endMsChecked = (endPercentage * durationMs).toLong().coerceIn(startMsChecked, durationMs)

    // Sync back to VM when calculations shift
    LaunchedEffect(startMsChecked, endMsChecked) {
        viewModel.updateEditingTrim(startMsChecked, endMsChecked)
        coverTimeMs = coverTimeMs.coerceIn(startMsChecked, endMsChecked)
    }

    var isLoopState by remember(item.id) { mutableStateOf(item.isLooping) }
    var keyNightState by remember(item.id) { mutableStateOf(item.nightModeEnabled) }
    var keyBrightnessFactor by remember(item.id) { mutableStateOf(item.nightBrightnessReduction) }

    LaunchedEffect(isLoopState, keyNightState, keyBrightnessFactor) {
        viewModel.updateEditingOptions(isLoopState, keyNightState, keyBrightnessFactor)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Upper Navigation row
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackPressed) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
            }
            Text(
                text = stringResource(R.string.edit_wallpaper),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = {
                    viewModel.saveEditingItem()
                },
                modifier = Modifier.testTag("save_wallpaper_button")
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.save))
            }
        }

        // Live player preview block
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                VideoPreviewPlayer(
                    videoUriStr = item.videoUriStr,
                    startMs = startMsChecked,
                    endMs = endMsChecked,
                    modifier = Modifier.fillMaxSize()
                )

                // Translucent dimming filter simulation to display night mode preview
                if (keyNightState) {
                    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                    val isNight = hour >= 22 || hour < 6
                    if (isNight) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = keyBrightnessFactor))
                        ) {
                            Text(
                                "Демонстрация ночной автояркости активна",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 10.sp,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Segment Trimming Sliders Section
        Text(
            text = stringResource(R.string.trim_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Старт: ${formatTime(startMsChecked)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            Text("Стоп: ${formatTime(endMsChecked)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
        }

        RangeSlider(
            value = startPercentage..endPercentage,
            onValueChange = { range ->
                startPercentage = range.start
                endPercentage = range.endInclusive
            },
            valueRange = 0f..1f,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .testTag("trim_range_slider")
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        // Looping setup
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(id = R.string.loop_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = stringResource(id = R.string.loop_desc),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = isLoopState,
                onCheckedChange = { isLoopState = it },
                modifier = Modifier.testTag("looping_switch")
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        // Auto night screen brightness
        val requiresWriteSettingsDialog = remember { mutableStateOf(false) }

        if (requiresWriteSettingsDialog.value) {
            AlertDialog(
                onDismissRequest = { requiresWriteSettingsDialog.value = false },
                title = { Text("Требуется разрешение изменения настроек") },
                text = { Text("Для управления системным уровнем яркости ночью требуется предоставить соответствующее разрешение в системном окне.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            requiresWriteSettingsDialog.value = false
                            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        }
                    ) {
                        Text("Перейти", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { requiresWriteSettingsDialog.value = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(id = R.string.night_mode_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = stringResource(id = R.string.night_mode_desc),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = keyNightState,
                onCheckedChange = { checked ->
                    if (checked && !Settings.System.canWrite(context)) {
                        requiresWriteSettingsDialog.value = true
                    } else {
                        keyNightState = checked
                    }
                },
                modifier = Modifier.testTag("night_brightness_switch")
            )
        }

        AnimatedVisibility(
            visible = keyNightState,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Степень затемнения:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text("${(keyBrightnessFactor * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }

                Slider(
                    value = keyBrightnessFactor,
                    onValueChange = { keyBrightnessFactor = it },
                    valueRange = 0.1f..0.8f,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Composable
fun VideoPreviewPlayer(
    videoUriStr: String,
    startMs: Long,
    endMs: Long,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var videoView: VideoView? = null

    val handler = remember { Handler(Looper.getMainLooper()) }
    val checkRunnable = remember(videoUriStr, startMs, endMs) {
        object : Runnable {
            override fun run() {
                val view = videoView ?: return
                try {
                    if (view.isPlaying) {
                        val pos = view.currentPosition
                        if (endMs > 0 && pos >= endMs) {
                            view.seekTo(startMs.toInt())
                            view.start()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("VideoPreviewPlayer", "Error checking playback position", e)
                }
                handler.postDelayed(this, 150)
            }
        }
    }

    DisposableEffect(videoUriStr, startMs, endMs) {
        handler.post(checkRunnable)
        onDispose {
            handler.removeCallbacks(checkRunnable)
            try {
                videoView?.stopPlayback()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            VideoView(ctx).apply {
                setOnErrorListener { _, _, _ ->
                    true // Handle error internally, do not pop crash dialog
                }
                setOnPreparedListener { mp ->
                    mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                    mp.setVolume(0f, 0f) // Wallpaper style (silenced preview)
                    mp.isLooping = true
                    try {
                        seekTo(startMs.toInt())
                        start()
                    } catch (e: Exception) {
                        Log.e("VideoPreviewPlayer", "Error preparing video view playback", e)
                    }
                }
                tag = videoUriStr
                if (videoUriStr.startsWith("content://")) {
                    setVideoURI(Uri.parse(videoUriStr))
                } else {
                    setVideoPath(videoUriStr)
                }
                videoView = this
            }
        },
        update = { view ->
            videoView = view
            val lastSetPath = view.tag as? String
            if (lastSetPath != videoUriStr) {
                view.tag = videoUriStr
                try {
                    view.stopPlayback()
                    if (videoUriStr.startsWith("content://")) {
                        view.setVideoURI(Uri.parse(videoUriStr))
                    } else {
                        view.setVideoPath(videoUriStr)
                    }
                } catch (e: Exception) {
                    Log.e("VideoPreviewPlayer", "Error updating VideoView URI", e)
                }
            }
        },
        modifier = modifier
    )
}

// Launches native system android live wallpaper picker dialog
fun launchWallpaperSelection(context: Context) {
    try {
        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
            putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(context, VideoWallpaperService::class.java)
            )
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        // Fallback picker intent for older or customized Android systems
        try {
            val intent = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (ex: Exception) {
            Log.e("MainActivity", "Failed to boot wallpaper picker intent", ex)
        }
    }
}

// Formats timestamp in ms into readable minutes:seconds
private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

// Queries filename safely from URI
private fun getFileName(context: Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/')
        if (cut != null && cut != -1) {
            result = result.substring(cut + 1)
        }
    }
    return result
}
