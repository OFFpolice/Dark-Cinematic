package com.example

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.data.WallpaperDatabase
import com.example.data.WallpaperItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class WallpaperViewModel(application: Application) : AndroidViewModel(application) {

    private val db = Room.databaseBuilder(
        application,
        WallpaperDatabase::class.java,
        "wallpaper_db"
    ).build()

    val wallpaperList: StateFlow<List<WallpaperItem>> = db.wallpaperDao.getAllWallpapers()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val activeWallpaper: StateFlow<WallpaperItem?> = db.wallpaperDao.getActiveWallpaperFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    // Editing State
    private val _editingItem = MutableStateFlow<WallpaperItem?>(null)
    val editingItem: StateFlow<WallpaperItem?> = _editingItem.asStateFlow()

    private val _videoDurationMs = MutableStateFlow(0L)
    val videoDurationMs: StateFlow<Long> = _videoDurationMs.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    fun startEditingNewVideo(uri: Uri, context: Context, title: String) {
        viewModelScope.launch {
            _isProcessing.value = true
            val uriString = uri.toString()
            val duration = getVideoDuration(context, uri)
            _videoDurationMs.value = duration

            // Save a default thumbnail of the first frame
            val localThumbnail = extractVideoFrameAt(context, uri.toString(), 0L)

            _editingItem.value = WallpaperItem(
                title = title,
                videoUriStr = uriString,
                trimStartMs = 0L,
                trimEndMs = duration,
                thumbnailUriStr = localThumbnail,
                isLooping = true
            )
            _isProcessing.value = false
        }
    }

    fun startEditingExisting(item: WallpaperItem, context: Context) {
        viewModelScope.launch {
            _isProcessing.value = true
            _editingItem.value = item
            val duration = getVideoDuration(context, Uri.parse(item.videoUriStr))
            _videoDurationMs.value = duration
            _isProcessing.value = false
        }
    }

    fun updateEditingTrim(startMs: Long, endMs: Long) {
        val current = _editingItem.value ?: return
        _editingItem.value = current.copy(
            trimStartMs = startMs,
            trimEndMs = endMs
        )
    }

    fun updateEditingOptions(isLooping: Boolean, nightModeEnabled: Boolean, brightnessReduction: Float) {
        val current = _editingItem.value ?: return
        _editingItem.value = current.copy(
            isLooping = isLooping,
            nightModeEnabled = nightModeEnabled,
            nightBrightnessReduction = brightnessReduction
        )
    }

    fun changeEditingCoverFrame(context: Context, timeMs: Long) {
        viewModelScope.launch {
            val current = _editingItem.value ?: return@launch
            _isProcessing.value = true
            val newThumbnail = extractVideoFrameAt(context, current.videoUriStr, timeMs)
            if (newThumbnail != null) {
                _editingItem.value = current.copy(thumbnailUriStr = newThumbnail)
            }
            _isProcessing.value = false
        }
    }

    fun saveEditingItem() {
        viewModelScope.launch {
            val item = _editingItem.value ?: return@launch
            _isProcessing.value = true
            
            // Insert or update DB
            db.wallpaperDao.insertWallpaper(item)
            
            _editingItem.value = null
            _isProcessing.value = false
        }
    }

    fun cancelEditing() {
        _editingItem.value = null
    }

    fun setActive(id: Int) {
        viewModelScope.launch {
            db.wallpaperDao.setActiveWallpaper(id)
        }
    }

    fun deleteWallpaper(item: WallpaperItem) {
        viewModelScope.launch {
            db.wallpaperDao.deleteWallpaper(item)
            // Clean up files if they are in app sandbox to keep device storage clean
            item.thumbnailUriStr?.let { path ->
                val file = File(path)
                if (file.exists() && file.parent == getApplication<Application>().filesDir.absolutePath) {
                    file.delete()
                }
            }
        }
    }

    private suspend fun getVideoDuration(context: Context, uri: Uri): Long {
        return withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                if (uri.toString().startsWith("content://")) {
                    retriever.setDataSource(context, uri)
                } else {
                    retriever.setDataSource(uri.toString())
                }
                val time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                time?.toLong() ?: 0L
            } catch (e: Exception) {
                Log.e("WallpaperViewModel", "Error getting video duration", e)
                0L
            } finally {
                try { retriever.release() } catch (ex: Exception) { }
            }
        }
    }

    private suspend fun extractVideoFrameAt(context: Context, videoUriStr: String, timeMs: Long): String? {
        return withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                val uri = Uri.parse(videoUriStr)
                if (videoUriStr.startsWith("content://")) {
                    retriever.setDataSource(context, uri)
                } else {
                    retriever.setDataSource(videoUriStr)
                }
                
                // Retriever requires microseconds
                val bitmap = retriever.getFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (bitmap != null) {
                    val fileName = "thumb_${System.currentTimeMillis()}_${timeMs}.jpg"
                    val file = File(context.filesDir, fileName)
                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                    }
                    file.absolutePath
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e("WallpaperViewModel", "Error extracting frame", e)
                null
            } finally {
                try { retriever.release() } catch (ex: Exception) { }
            }
        }
    }
}
