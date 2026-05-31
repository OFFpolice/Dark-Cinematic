package com.example

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import androidx.room.Room
import com.example.data.WallpaperDatabase
import com.example.data.WallpaperItem
import kotlinx.coroutines.*
import java.io.File
import java.util.Calendar

class VideoWallpaperService : WallpaperService() {

    private lateinit var db: WallpaperDatabase

    override fun onCreate() {
        super.onCreate()
        db = Room.databaseBuilder(
            applicationContext,
            WallpaperDatabase::class.java,
            "wallpaper_db"
        ).build()
    }

    override fun onCreateEngine(): Engine {
        return VideoEngine()
    }

    inner class VideoEngine : Engine() {
        private var mediaPlayer: MediaPlayer? = null
        private var activeItem: WallpaperItem? = null
        private val mainHandler = Handler(Looper.getMainLooper())
        private var job: Job? = null
        private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

        // Periodically check trim boundaries
        private val trimLoopRunnable = object : Runnable {
            override fun run() {
                val player = mediaPlayer ?: return
                val item = activeItem ?: return
                
                if (player.isPlaying && item.trimEndMs > 0L) {
                    val currentPos = player.currentPosition.toLong()
                    if (currentPos >= item.trimEndMs) {
                        player.seekTo(item.trimStartMs.toInt())
                    }
                }
                mainHandler.postDelayed(this, 150)
            }
        }

        // Periodically check night brightness
        private var originalBrightness = -1
        private val nightModeRunnable = object : Runnable {
            override fun run() {
                applyNightModeIfEnabled()
                mainHandler.postDelayed(this, 15000) // check every 15s
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            
            // Listen to active wallpaper changes
            job = scope.launch {
                db.wallpaperDao.getActiveWallpaperFlow().collect { item ->
                    if (item != null) {
                        val isSameVideo = activeItem?.id == item.id && activeItem?.videoUriStr == item.videoUriStr
                        val trimChanged = activeItem?.trimStartMs != item.trimStartMs || activeItem?.trimEndMs != item.trimEndMs
                        
                        activeItem = item
                        
                        if (isVisible) {
                            if (!isSameVideo) {
                                setupMediaPlayer()
                            } else {
                                mediaPlayer?.isLooping = item.isLooping && (item.trimEndMs == 0L)
                                if (trimChanged) {
                                    mediaPlayer?.seekTo(item.trimStartMs.toInt())
                                }
                            }
                        }
                    } else {
                        activeItem = null
                        stopMediaPlayer()
                    }
                }
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            if (visible) {
                // Apply night brightness checks
                mainHandler.post(nightModeRunnable)
                
                if (mediaPlayer == null) {
                    setupMediaPlayer()
                } else {
                    try {
                        mediaPlayer?.start()
                        mainHandler.post(trimLoopRunnable)
                    } catch (e: Exception) {
                        Log.e("VideoWallpaper", "Error starting MediaPlayer", e)
                        setupMediaPlayer()
                    }
                }
            } else {
                mainHandler.removeCallbacks(trimLoopRunnable)
                mainHandler.removeCallbacks(nightModeRunnable)
                restoreOriginalBrightness()
                mediaPlayer?.pause()
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            if (isVisible) {
                setupMediaPlayer()
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            stopMediaPlayer()
        }

        override fun onDestroy() {
            super.onDestroy()
            job?.cancel()
            scope.cancel()
            mainHandler.removeCallbacksAndMessages(null)
            restoreOriginalBrightness()
            stopMediaPlayer()
        }

        private fun setupMediaPlayer() {
            stopMediaPlayer()
            val item = activeItem ?: return
            val file = File(item.videoUriStr)
            
            if (!item.videoUriStr.startsWith("content://") && !file.exists()) {
                Log.e("VideoWallpaper", "Video file does not exist: ${item.videoUriStr}")
                return
            }

            try {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(applicationContext, Uri.parse(item.videoUriStr))
                    setSurface(surfaceHolder.surface)
                    // Muted by default to fit clean wallpaper and power consumption expectations
                    setVolume(0f, 0f) 
                    isLooping = item.isLooping && (item.trimEndMs == 0L)
                    
                    setOnPreparedListener { mp ->
                        if (item.trimStartMs > 0L) {
                            mp.seekTo(item.trimStartMs.toInt())
                        }
                        if (isVisible) {
                            mp.start()
                            mainHandler.post(trimLoopRunnable)
                        }
                    }
                    
                    setOnErrorListener { _, what, extra ->
                        Log.e("VideoWallpaper", "MediaPlayer Error: what=$what, extra=$extra")
                        false
                    }
                    
                    prepareAsync()
                }
            } catch (e: Exception) {
                Log.e("VideoWallpaper", "Error setting up MediaPlayer", e)
            }
        }

        private fun stopMediaPlayer() {
            mainHandler.removeCallbacks(trimLoopRunnable)
            mediaPlayer?.apply {
                try {
                    if (isPlaying) stop()
                    release()
                } catch (e: Exception) {
                    // Ignore
                }
            }
            mediaPlayer = null
        }

        private fun applyNightModeIfEnabled() {
            val item = activeItem ?: return
            if (!item.nightModeEnabled) {
                restoreOriginalBrightness()
                return
            }

            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val isNight = hour >= 22 || hour < 6

            if (isNight) {
                try {
                    val cr = applicationContext.contentResolver
                    if (Settings.System.canWrite(applicationContext)) {
                        if (originalBrightness == -1) {
                            originalBrightness = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS)
                        }
                        val target = (originalBrightness * (1f - item.nightBrightnessReduction)).toInt().coerceIn(10, 255)
                        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, target)
                    }
                } catch (e: Exception) {
                    Log.e("VideoWallpaper", "Failed to adjust night brightness", e)
                }
            } else {
                restoreOriginalBrightness()
            }
        }

        private fun restoreOriginalBrightness() {
            if (originalBrightness != -1) {
                try {
                    val cr = applicationContext.contentResolver
                    if (Settings.System.canWrite(applicationContext)) {
                        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, originalBrightness)
                    }
                } catch (e: Exception) {
                    // Ignore
                } finally {
                    originalBrightness = -1
                }
            }
        }
    }
}
