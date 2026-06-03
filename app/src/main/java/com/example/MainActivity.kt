package com.example

import android.app.AlertDialog
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.WallpaperItem
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import java.io.File
import java.util.Calendar
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: WallpaperViewModel

    private val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        android.Manifest.permission.READ_MEDIA_VIDEO
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            videoPickerLauncher.launch("video/mp4")
        } else {
            Toast.makeText(this, "Доступ к файлам видео отклонен", Toast.LENGTH_SHORT).show()
        }
    }

    private val videoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val title = getFileName(this, uri) ?: "Видео обои"
            viewModel.startEditingNewVideo(uri, this, title)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        viewModel = ViewModelProvider(this)[WallpaperViewModel::class.java]

        setContent {
            VideoWallpaperTheme {
                LiveWallpaperApp(
                    viewModel = viewModel,
                    onPickVideo = { triggerVideoSelection() },
                    onRequestWriteSettings = { showWriteSettingsPermissionDialog() },
                    onActivateLiveWallpaper = { launchWallpaperSelection(this) }
                )
            }
        }
    }

    fun triggerVideoSelection() {
        if (ContextCompat.checkSelfPermission(this, storagePermission) == PackageManager.PERMISSION_GRANTED) {
            videoPickerLauncher.launch("video/mp4")
        } else {
            requestPermissionLauncher.launch(storagePermission)
        }
    }

    private fun showWriteSettingsPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Требуется разрешение настроек")
            .setMessage("Для настройки автояркости ночью приложению требуется разрешение на запись системных настроек.")
            .setPositiveButton("Перейти") { _, _ ->
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

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

    private fun launchWallpaperSelection(context: Context) {
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
            try {
                val intent = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (ex: Exception) {
                Log.e("MainActivity", "Failed to boot live wallpaper choice dialog", ex)
            }
        }
    }
}

@Composable
fun LiveWallpaperApp(
    viewModel: WallpaperViewModel,
    onPickVideo: () -> Unit,
    onRequestWriteSettings: () -> Unit,
    onActivateLiveWallpaper: () -> Unit
) {
    val wallpaperList by viewModel.wallpaperList.collectAsStateWithLifecycle()
    val activeWallpaper by viewModel.activeWallpaper.collectAsStateWithLifecycle()
    val editingItem by viewModel.editingItem.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberBackground)
    ) {
        if (editingItem == null) {
            // MAIN LIBRARY SCREEN
            LibraryScreen(
                wallpaperList = wallpaperList,
                activeWallpaper = activeWallpaper,
                onPickVideo = onPickVideo,
                onEditItem = { item -> viewModel.startEditingExisting(item, viewModel.getApplication()) },
                onActivateLiveWallpaper = onActivateLiveWallpaper,
                onSetActive = { id -> viewModel.setActive(id) },
                onDeleteItem = { item -> viewModel.deleteWallpaper(item) }
            )
        } else {
            // EDITING PROFILE SCREEN
            EditorScreen(
                viewModel = viewModel,
                onRequestWriteSettings = onRequestWriteSettings
            )
        }

        // LOADING OVERLAY
        if (isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = NeonCyan,
                    modifier = Modifier.size(64.dp)
                )
            }
        }
    }
}

@Composable
fun LibraryScreen(
    wallpaperList: List<WallpaperItem>,
    activeWallpaper: WallpaperItem?,
    onPickVideo: () -> Unit,
    onEditItem: (WallpaperItem) -> Unit,
    onActivateLiveWallpaper: () -> Unit,
    onSetActive: (Int) -> Unit,
    onDeleteItem: (WallpaperItem) -> Unit
) {
    Scaffold(
        containerColor = CyberBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            // Glowing Custom Bottom Center FAB button for choosing videos
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = onPickVideo,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(28.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
                    modifier = Modifier
                        .height(56.dp)
                        .testTag("add_video_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Video Icon",
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ДОБАВИТЬ ВИДЕО",
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        fontSize = 14.sp
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Top Header Theme bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Live Wallpaper",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                // Cyber Gradient bottom highlight line
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(2.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, NeonCyan, NeonPurple, Color.Transparent)
                            )
                        )
                )
            }

            if (wallpaperList.isEmpty() && activeWallpaper == null) {
                // EMPTY STATE
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.VideoLibrary,
                        contentDescription = "Empty Book Icon",
                        tint = NeonCyan,
                        modifier = Modifier
                            .size(72.dp)
                            .padding(bottom = 16.dp)
                    )
                    Text(
                        text = "Библиотека пуста",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Добавьте ваше первое видео в формате MP4!",
                        color = CyberMuted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onPickVideo,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonCyan,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text(
                            text = "Выбрать видео",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                // SCROLLABLE LIST OF WALPAPERS
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Active Profile Card
                    if (activeWallpaper != null) {
                        item {
                            ActiveWallpaperCard(
                                item = activeWallpaper,
                                onActivateLiveWallpaper = onActivateLiveWallpaper,
                                onEditItem = onEditItem
                            )
                        }
                    }

                    // Divider title
                    if (wallpaperList.isNotEmpty()) {
                        item {
                            Text(
                                text = "Библиотека обоев",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        items(wallpaperList) { item ->
                            LibraryItemRow(
                                item = item,
                                onSetActive = { onSetActive(item.id) },
                                onEditItem = { onEditItem(item) },
                                onDeleteItem = { onDeleteItem(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveWallpaperCard(
    item: WallpaperItem,
    onActivateLiveWallpaper: () -> Unit,
    onEditItem: (WallpaperItem) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CyberCard),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.5.dp,
                brush = Brush.horizontalGradient(listOf(NeonCyan, NeonPurple)),
                shape = RoundedCornerShape(16.dp)
            )
            .testTag("active_wallpaper_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Star active",
                    tint = NeonCyan,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "АКТИВНЫЙ ПРОФИЛЬ",
                    color = NeonCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Rounded corner thumbnail preview
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(CyberBorder)
                ) {
                    if (item.thumbnailUriStr != null && File(item.thumbnailUriStr).exists()) {
                        AsyncImage(
                            model = File(item.thumbnailUriStr),
                            contentDescription = "Active thumbnail",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = "Cam",
                                tint = CyberMuted,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = item.title,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Диапазон: ${formatTime(item.trimStartMs)} - ${formatTime(item.trimEndMs)}",
                        color = CyberMuted,
                        fontSize = 11.sp
                    )
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        ProfileBadge(text = if (item.isLooping) "Цикл" else "Разово", color = NeonCyan)
                        if (item.nightModeEnabled) {
                            ProfileBadge(text = "Затемнение: ${(item.nightBrightnessReduction * 100).toInt()}%", color = NeonPurple)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onActivateLiveWallpaper,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonPurple,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    modifier = Modifier
                        .weight(1.2f)
                        .height(36.dp)
                ) {
                    Text(
                        text = "Активировать в ОС",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = { onEditItem(item) },
                    border = BorderStroke(1.dp, CyberBorder),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = NeonCyan
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier
                        .weight(0.8f)
                        .height(36.dp)
                ) {
                    Text(
                        text = "Изменить",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun LibraryItemRow(
    item: WallpaperItem,
    onSetActive: () -> Unit,
    onEditItem: () -> Unit,
    onDeleteItem: () -> Unit
) {
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Удалить обои") },
            text = { Text("Вы уверены, что хотите окончательно удалить эти живые обои из библиотеки?") },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Отмена", color = CyberMuted)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirmDialog = false
                    onDeleteItem()
                }) {
                    Text("Удалить", color = NeonPink, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CyberCard),
        border = BorderStroke(1.dp, CyberBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("library_item_row_${item.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(CyberBorder)
            ) {
                if (item.thumbnailUriStr != null && File(item.thumbnailUriStr).exists()) {
                    AsyncImage(
                        model = File(item.thumbnailUriStr),
                        contentDescription = "Library thumbnail",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = "Video Cam",
                            tint = CyberMuted,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.title,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    
                    IconButton(
                        onClick = { showDeleteConfirmDialog = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Item",
                            tint = NeonPink,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Text(
                    text = "Отрезок: ${formatTime(item.trimStartMs)} - ${formatTime(item.trimEndMs)}",
                    color = CyberMuted,
                    fontSize = 11.sp
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ProfileBadge(text = if (item.isLooping) "Цикл" else "Раз", color = NeonCyan)
                        if (item.nightModeEnabled) {
                            ProfileBadge(text = "Ночь", color = NeonPurple)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = onEditItem,
                            modifier = Modifier
                                .size(28.dp)
                                .background(CyberBorder, RoundedCornerShape(6.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Profile",
                                tint = NeonCyan,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        Button(
                            onClick = onSetActive,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonCyan,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text(
                                text = "Выбрать",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun EditorScreen(
    viewModel: WallpaperViewModel,
    onRequestWriteSettings: () -> Unit
) {
    val context = LocalContext.current
    val editingItem by viewModel.editingItem.collectAsStateWithLifecycle()
    val videoDurationMs by viewModel.videoDurationMs.collectAsStateWithLifecycle()

    val currentItem = editingItem ?: return
    val totalDuration = if (videoDurationMs > 0L) videoDurationMs else 10000L

    // Safe boundaries
    val startProgress = (currentItem.trimStartMs.toFloat() / totalDuration).coerceIn(0f, 1f)
    val endProgress = (currentItem.trimEndMs.toFloat() / totalDuration).coerceIn(0f, 1f)

    // Layout Scrolling
    val scrollState = rememberScrollState()

    // Interactive playback seek ref
    var systemVideoView: AspectRatioVideoView? by remember { mutableStateOf(null) }

    // Synchronize play boundaries playback loop
    LaunchedEffect(currentItem.trimStartMs, currentItem.trimEndMs) {
        while (true) {
            delay(150)
            val view = systemVideoView ?: continue
            try {
                if (view.isPlaying) {
                    val pos = view.currentPosition.toLong()
                    if (currentItem.trimEndMs > 0L && pos >= currentItem.trimEndMs) {
                        view.seekTo(currentItem.trimStartMs.toInt())
                    }
                }
            } catch (e: Exception) {
                Log.e("EditorScreen", "Playback looping error check", e)
            }
        }
    }

    // Checking system local night hour
    val isNightModeThemeActive = remember(currentItem.nightModeEnabled) {
        if (currentItem.nightModeEnabled) {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            hour >= 22 || hour < 6
        } else false
    }

    Scaffold(
        containerColor = CyberBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            // Elegant Editor Screen Top bar header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.cancelEditing() },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel Editing",
                            tint = NeonPink
                        )
                    }

                    Text(
                        text = if (currentItem.id == 0) "Добавление обоев" else "Настройка профиля",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    IconButton(
                        onClick = { viewModel.saveEditingItem() },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Save Wallpaper Settings",
                            tint = NeonCyan
                        )
                    }
                }
                
                // Cyber line divider
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .height(1.dp)
                        .background(CyberBorder)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(bottom = 32.dp)
        ) {
            // PLAYER PREVIEW COMPONENT
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, CyberBorder, RoundedCornerShape(12.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                // Live Video Rendering
                AndroidView(
                    factory = { ctx ->
                        AspectRatioVideoView(ctx).apply {
                            setOnErrorListener { _, _, _ -> true }
                            setOnPreparedListener { mp ->
                                mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT)
                                mp.setVolume(0f, 0f) // Wallpaper styled, silent
                                setVideoSize(mp.videoWidth, mp.videoHeight)
                                mp.isLooping = currentItem.trimStartMs <= 100L && (currentItem.trimEndMs == 0L || currentItem.trimEndMs >= mp.duration - 100)
                                seekTo(currentItem.trimStartMs.toInt())
                                start()
                            }
                            setOnCompletionListener { mp ->
                                try {
                                    seekTo(currentItem.trimStartMs.toInt())
                                    start()
                                } catch (ex: Exception) {}
                            }
                            if (currentItem.videoUriStr.startsWith("content://")) {
                                setVideoURI(Uri.parse(currentItem.videoUriStr))
                            } else {
                                setVideoPath(currentItem.videoUriStr)
                            }
                        }.also {
                            systemVideoView = it
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Night mode dim simulation filter over player view
                if (isNightModeThemeActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = currentItem.nightBrightnessReduction))
                    )
                    
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(NeonPurple.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "НОЧНОЙ РЕЖИМ",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // SLIDERS & CONTROLS BOX CONTAINER
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Trimming controls Card
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberCard),
                    border = BorderStroke(1.dp, CyberBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Обрезка видео",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Выберите диапазон воспроизведения",
                            color = CyberMuted,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // Trim Start
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Начало", color = Color.White, fontSize = 12.sp)
                            Text(
                                text = formatTime(currentItem.trimStartMs),
                                color = NeonCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = startProgress,
                            onValueChange = { newVal ->
                                val calculatedStart = (newVal * totalDuration).toLong().coerceAtMost(currentItem.trimEndMs - 500L)
                                viewModel.updateEditingTrim(calculatedStart.coerceAtLeast(0L), currentItem.trimEndMs)
                            },
                            onValueChangeFinished = {
                                systemVideoView?.seekTo(currentItem.trimStartMs.toInt())
                            },
                            colors = SliderDefaults.colors(
                                activeTrackColor = NeonCyan,
                                thumbColor = NeonCyan,
                                inactiveTrackColor = CyberBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Trim End
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Конец", color = Color.White, fontSize = 12.sp)
                            Text(
                                text = formatTime(currentItem.trimEndMs),
                                color = NeonCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = endProgress,
                            onValueChange = { newVal ->
                                val calculatedEnd = (newVal * totalDuration).toLong().coerceAtLeast(currentItem.trimStartMs + 500L)
                                viewModel.updateEditingTrim(currentItem.trimStartMs, calculatedEnd.coerceAtMost(totalDuration))
                            },
                            onValueChangeFinished = {
                                systemVideoView?.seekTo(currentItem.trimStartMs.toInt())
                            },
                            colors = SliderDefaults.colors(
                                activeTrackColor = NeonCyan,
                                thumbColor = NeonCyan,
                                inactiveTrackColor = CyberBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Options card (Loop & Night mode)
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberCard),
                    border = BorderStroke(1.dp, CyberBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        // Looping options row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Зациклить воспроизведение",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Повторять воспроизведение видео по кругу",
                                    color = CyberMuted,
                                    fontSize = 11.sp
                                )
                            }
                            Switch(
                                checked = currentItem.isLooping,
                                onCheckedChange = { isChecked ->
                                    viewModel.updateEditingOptions(
                                        isChecked,
                                        currentItem.nightModeEnabled,
                                        currentItem.nightBrightnessReduction
                                    )
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = NeonCyan,
                                    checkedTrackColor = NeonCyan.copy(alpha = 0.3f),
                                    uncheckedBorderColor = CyberBorder
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(CyberBorder))
                        Spacer(modifier = Modifier.height(16.dp))

                        // Night dims options row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Автояркость в ночное время",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Снижает яркость экрана ночью (22:00 - 06:00)",
                                    color = CyberMuted,
                                    fontSize = 11.sp
                                )
                            }
                            Switch(
                                checked = currentItem.nightModeEnabled,
                                onCheckedChange = { isChecked ->
                                    if (isChecked && !Settings.System.canWrite(context)) {
                                        onRequestWriteSettings()
                                    } else {
                                        viewModel.updateEditingOptions(
                                            currentItem.isLooping,
                                            isChecked,
                                            currentItem.nightBrightnessReduction
                                        )
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = NeonCyan,
                                    checkedTrackColor = NeonCyan.copy(alpha = 0.3f),
                                    uncheckedBorderColor = CyberBorder
                                )
                            )
                        }

                        // Sliding factor controls layout
                        AnimatedVisibility(
                            visible = currentItem.nightModeEnabled,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp)
                                    .background(CyberBackground.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .border(1.dp, CyberBorder, RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = "Степень затемнения:", color = Color.White, fontSize = 12.sp)
                                    Text(
                                        text = "${(currentItem.nightBrightnessReduction * 100).toInt()}%",
                                        color = NeonCyan,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Slider(
                                    value = currentItem.nightBrightnessReduction,
                                    valueRange = 0.1f..0.8f,
                                    onValueChange = { newVal ->
                                        viewModel.updateEditingOptions(currentItem.isLooping, true, newVal)
                                    },
                                    colors = SliderDefaults.colors(
                                        activeTrackColor = NeonCyan,
                                        thumbColor = NeonCyan,
                                        inactiveTrackColor = CyberBorder
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                // Choose Cover Frame selector card
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberCard),
                    border = BorderStroke(1.dp, CyberBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Выбор обложки (кадра)",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Выберите кадр видео для обложки в вашей библиотеке",
                            color = CyberMuted,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        val trimSpan = (currentItem.trimEndMs - currentItem.trimStartMs).coerceAtLeast(1L)
                        // Assume frame is set to start initially
                        val currentRelTime = (currentItem.trimStartMs).coerceAtLeast(0L)
                        val relativeProgress = ((currentRelTime - currentItem.trimStartMs).toFloat() / trimSpan).coerceIn(0f, 1f)

                        Slider(
                            value = relativeProgress,
                            onValueChange = { valPct ->
                                val selectMs = currentItem.trimStartMs + (valPct * trimSpan).toLong()
                                systemVideoView?.seekTo(selectMs.toInt())
                                viewModel.changeEditingCoverFrame(context, selectMs)
                            },
                            colors = SliderDefaults.colors(
                                activeTrackColor = NeonMint,
                                thumbColor = NeonMint,
                                inactiveTrackColor = CyberBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Action Buttons Save/Cancel Column
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.cancelEditing() },
                        border = BorderStroke(1.dp, NeonPink.copy(alpha = 0.8f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = NeonPink
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text(
                            text = "Отмена",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = { viewModel.saveEditingItem() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonPurple,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1.2f)
                            .height(48.dp)
                    ) {
                        Text(
                            text = "Сохранить",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// FORMAT TIME UTIL
private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}
