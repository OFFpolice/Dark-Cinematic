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
import androidx.compose.ui.draw.clipToBounds
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
import com.example.ui.theme.*
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
            Column {
                CenterAlignedTopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterFrames,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Live-Wallpaper",
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp,
                                color = Color.White,
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = CyberBackground
                    )
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, NeonCyan, NeonPurple, Color.Transparent)
                            )
                        )
                )
            }
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
                    icon = { Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black) },
                    text = { Text(text = stringResource(id = R.string.add_to_library), fontWeight = FontWeight.Bold, color = Color.Black) },
                    containerColor = NeonCyan,
                    modifier = Modifier
                        .testTag("add_wallpaper_fab")
                        .border(
                            BorderStroke(
                                width = 1.5.dp,
                                brush = Brush.linearGradient(listOf(Color.White, NeonCyan))
                            ),
                            shape = FloatingActionButtonDefaults.extendedFabShape
                        )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            CyberBackground,
                            Color(0xFF0F1422)
                        )
                    )
                )
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
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(NeonPurple.copy(alpha = 0.15f), CircleShape)
                    .border(2.dp, NeonPurple, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = NeonPurple,
                    modifier = Modifier.size(48.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(id = R.string.permission_required),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(id = R.string.permission_desc),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = CyberMuted
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("permission_button")
            ) {
                Text("Предоставить доступ", fontWeight = FontWeight.Bold, color = Color.White)
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
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(NeonCyan.copy(alpha = 0.1f), CircleShape)
                    .border(1.5.dp, Brush.sweepGradient(listOf(NeonCyan, NeonPurple, NeonCyan)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.VideoLibrary,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(56.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Библиотека пуста",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(id = R.string.empty_library_message),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = CyberMuted
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onSelectVideo,
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                modifier = Modifier
                    .testTag("select_first_video_button")
                    .height(50.dp)
                    .border(BorderStroke(1.dp, Color.White), RoundedCornerShape(100))
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Добавить первое видео", fontWeight = FontWeight.Bold, color = Color.Black)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Highlights Section
            activeWallpaper?.let { active ->
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "АКТИВНЫЙ ПРОФИЛЬ",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan,
                            letterSpacing = 1.sp
                        )
                    }
                    ActiveWallpaperCard(active = active, onSetClick = { onSetActive(active) })
                }
            }

            // Library header
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Wallpaper, contentDescription = null, tint = NeonPurple, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(id = R.string.library_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 0.5.sp
                    )
                }
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
            containerColor = CyberCard.copy(alpha = 0.75f)
        ),
        border = BorderStroke(1.5.dp, Brush.linearGradient(listOf(NeonCyan, NeonPurple)))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.DarkGray)
                    .border(1.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
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

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = active.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (active.isLooping) Icons.Default.Loop else Icons.Default.NavigateNext,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = NeonPurple
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (active.isLooping) "Зациклен" else "Один раз",
                        style = MaterialTheme.typography.bodySmall,
                        color = CyberMuted
                    )
                    if (active.nightModeEnabled) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Icon(
                            imageVector = Icons.Default.Nightlight,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = NeonCyan
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Автояркость",
                            style = MaterialTheme.typography.bodySmall,
                            color = CyberMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onSetClick,
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            ) {
                Icon(Icons.Default.Wallpaper, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Запуск", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Black)
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
            title = { Text(stringResource(R.string.confirm_delete), color = Color.White) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    }
                ) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel), color = CyberMuted)
                }
            },
            containerColor = CyberCard,
            titleContentColor = Color.White
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .testTag("wallpaper_item_card_${item.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isActive) CyberActiveBg else CyberCard.copy(alpha = 0.65f)
        ),
        border = BorderStroke(
            width = if (item.isActive) 1.5.dp else 1.dp,
            color = if (item.isActive) NeonCyan else CyberBorder
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.DarkGray)
                    .border(1.dp, if (item.isActive) NeonCyan.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(10.dp))
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

                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        .padding(2.dp)
                )

                if (item.isActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(NeonCyan)
                            .align(Alignment.BottomCenter)
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = "АКТИВЕН",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (item.isLooping) Icons.Default.Repeat else Icons.Default.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = NeonPurple
                    )
                    Text(
                        text = if (item.isLooping) "Циклично" else "Одиночный",
                        fontSize = 11.sp,
                        color = CyberMuted
                    )

                    if (item.nightModeEnabled) {
                        VerticalDivider(
                            modifier = Modifier.height(10.dp),
                            color = CyberBorder
                        )
                        Icon(
                            imageVector = Icons.Default.DarkMode,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = NeonCyan
                        )
                        Text(
                            text = "Ночь",
                            fontSize = 11.sp,
                            color = CyberMuted
                        )
                    }
                }

                if (item.trimStartMs > 0L || item.trimEndMs > 0L) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Отрезок: ${formatTime(item.trimStartMs)} - ${formatTime(item.trimEndMs)}",
                        fontSize = 11.sp,
                        color = NeonMint,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

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
                        tint = NeonCyan
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
            .background(Color.Transparent)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Upper Navigation row
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackPressed) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Назад", tint = Color.White)
            }
            Text(
                text = stringResource(R.string.edit_wallpaper),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(start = 8.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = {
                    viewModel.saveEditingItem()
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .testTag("save_wallpaper_button")
                    .border(BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)), RoundedCornerShape(12.dp))
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.save), fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        // Live player preview block
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, CyberBorder),
            colors = CardDefaults.cardColors(containerColor = CyberCard)
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
                                color = NeonCyan,
                                fontSize = 10.sp,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                                    .border(0.5.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Segment Trimming Sliders Section
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.Start)
        ) {
            Icon(Icons.Default.ContentCut, contentDescription = null, tint = NeonPurple, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.trim_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Старт: ${formatTime(startMsChecked)}", fontSize = 12.sp, color = NeonCyan, fontWeight = FontWeight.Medium)
            Text("Стоп: ${formatTime(endMsChecked)}", fontSize = 12.sp, color = NeonPurple, fontWeight = FontWeight.Medium)
        }

        RangeSlider(
            value = startPercentage..endPercentage,
            onValueChange = { range ->
                startPercentage = range.start
                endPercentage = range.endInclusive
            },
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                activeTrackColor = NeonCyan,
                inactiveTrackColor = CyberBorder,
                activeTickColor = NeonCyan,
                inactiveTickColor = CyberBorder,
                thumbColor = NeonCyan
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .testTag("trim_range_slider")
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = CyberBorder)

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
                    fontSize = 14.sp,
                    color = Color.White
                )
                Text(
                    text = stringResource(id = R.string.loop_desc),
                    fontSize = 11.sp,
                    color = CyberMuted
                )
            }
            Switch(
                checked = isLoopState,
                onCheckedChange = { isLoopState = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = NeonCyan,
                    checkedTrackColor = NeonCyan.copy(alpha = 0.5f),
                    uncheckedThumbColor = CyberMuted,
                    uncheckedTrackColor = CyberBorder
                ),
                modifier = Modifier.testTag("looping_switch")
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = CyberBorder)

        // Auto night screen brightness
        val requiresWriteSettingsDialog = remember { mutableStateOf(false) }

        if (requiresWriteSettingsDialog.value) {
            AlertDialog(
                onDismissRequest = { requiresWriteSettingsDialog.value = false },
                title = { Text("Требуется разрешение изменения настроек", color = Color.White) },
                text = { Text("Для управления системным уровнем яркости ночью требуется предоставить соответствующее разрешение в системном окне.", color = CyberMuted) },
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
                        Text("Перейти", fontWeight = FontWeight.Bold, color = NeonCyan)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { requiresWriteSettingsDialog.value = false }) {
                        Text(stringResource(R.string.cancel), color = CyberMuted)
                    }
                },
                containerColor = CyberCard,
                titleContentColor = Color.White
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
                    fontSize = 14.sp,
                    color = Color.White
                )
                Text(
                    text = stringResource(id = R.string.night_mode_desc),
                    fontSize = 11.sp,
                    color = CyberMuted
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
                colors = SwitchDefaults.colors(
                    checkedThumbColor = NeonCyan,
                    checkedTrackColor = NeonCyan.copy(alpha = 0.5f),
                    uncheckedThumbColor = CyberMuted,
                    uncheckedTrackColor = CyberBorder
                ),
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
                        CyberBorder.copy(alpha = 0.5f),
                        RoundedCornerShape(12.dp)
                    )
                    .border(1.dp, CyberBorder, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Степень затемнения:", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White)
                    Text("${(keyBrightnessFactor * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                }

                Slider(
                    value = keyBrightnessFactor,
                    onValueChange = { keyBrightnessFactor = it },
                    valueRange = 0.1f..0.8f,
                    colors = SliderDefaults.colors(
                        thumbColor = NeonCyan,
                        activeTrackColor = NeonCyan,
                        inactiveTrackColor = CyberBorder
                    ),
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
    var videoView: AspectRatioVideoView? = null

    val handler = remember { Handler(Looper.getMainLooper()) }
    val checkRunnable = remember(videoUriStr, startMs, endMs) {
        object : Runnable {
            override fun run() {
                val view = videoView ?: return
                try {
                    val pos = view.currentPosition
                    if (endMs > 0 && pos >= endMs) {
                        view.seekTo(startMs.toInt())
                        if (!view.isPlaying) {
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

    Box(
        modifier = modifier.clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                AspectRatioVideoView(ctx).apply {
                    setOnErrorListener { _, _, _ ->
                        true // Handle error internally, do not pop crash dialog
                    }
                    setOnPreparedListener { mp ->
                        mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT)
                        mp.setVolume(0f, 0f) // Wallpaper style (silenced preview)
                        
                        setVideoSize(mp.videoWidth, mp.videoHeight)
                        
                        val duration = mp.duration.toLong()
                        val isFullyUntrimmed = startMs <= 100L && (endMs == 0L || endMs >= duration - 100L)
                        mp.isLooping = isFullyUntrimmed

                        try {
                            seekTo(startMs.toInt())
                            start()
                        } catch (e: Exception) {
                            Log.e("VideoPreviewPlayer", "Error preparing video view playback", e)
                        }
                    }
                    setOnCompletionListener { mp ->
                        try {
                            seekTo(startMs.toInt())
                            start()
                        } catch (e: Exception) {
                            Log.e("VideoPreviewPlayer", "Error looping in onCompletion", e)
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
                    view.setVideoSize(0, 0)
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
            modifier = Modifier.fillMaxSize()
        )
    }
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
