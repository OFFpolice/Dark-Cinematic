package com.example

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.data.WallpaperDatabase
import com.example.data.WallpaperItem
import com.example.databinding.DialogPreviewBinding
import com.example.databinding.FragmentGalleryBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class GalleryFragment : Fragment() {

    private var _binding: FragmentGalleryBinding? = null
    private val binding get() = _binding!!

    private var videoAdapter: VideoAdapter? = null
    private var skeletonAnimator: ObjectAnimator? = null
    private lateinit var db: WallpaperDatabase

    private val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        android.Manifest.permission.READ_MEDIA_VIDEO
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            checkPermissionsAndLoadVideos()
        } else {
            showPermissionLayout()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = WallpaperDatabase.getInstance(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGalleryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        startSkeletonAnimation()

        binding.btnGrantPermission.setOnClickListener {
            requestPermissionLauncher.launch(storagePermission)
        }

        checkPermissionsAndLoadVideos()
    }

    private fun setupRecyclerView() {
        videoAdapter = VideoAdapter { videoItem ->
            showWallpaperPreviewDialog(videoItem)
        }

        val layoutManager = GridLayoutManager(requireContext(), 2)
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (videoAdapter?.getItemViewType(position) == VideoAdapter.TYPE_HEADER) {
                    2
                } else {
                    1
                }
            }
        }

        binding.recyclerGallery.apply {
            this.layoutManager = layoutManager
            this.adapter = videoAdapter
            setHasFixedSize(true)
            setItemViewCacheSize(12)
            recycledViewPool.setMaxRecycledViews(VideoAdapter.TYPE_ITEM, 20)
        }

        binding.recyclerGallery.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val firstVisibleItem = layoutManager.findFirstVisibleItemPosition()
                if (firstVisibleItem > 1) {
                    binding.fabScrollToTop.show()
                } else {
                    binding.fabScrollToTop.hide()
                }
            }
        })

        binding.fabScrollToTop.setOnClickListener {
            binding.recyclerGallery.smoothScrollToPosition(0)
        }
    }

    private fun startSkeletonAnimation() {
        skeletonAnimator = ObjectAnimator.ofFloat(binding.layoutSkeleton, View.ALPHA, 0.4f, 1.0f).apply {
            duration = 750
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    private fun stopSkeletonAnimation() {
        skeletonAnimator?.cancel()
        skeletonAnimator = null
    }

    private fun checkPermissionsAndLoadVideos() {
        val permissionCheck = ContextCompat.checkSelfPermission(requireContext(), storagePermission)
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            binding.layoutPermission.visibility = View.GONE
            loadVideosAsync()
        } else {
            showPermissionLayout()
        }
    }

    private fun showPermissionLayout() {
        stopSkeletonAnimation()
        binding.layoutSkeleton.visibility = View.GONE
        binding.layoutEmpty.visibility = View.GONE
        binding.layoutGalleryContent.visibility = View.GONE
        binding.layoutPermission.visibility = View.VISIBLE
    }

    private fun loadVideosAsync() {
        binding.layoutSkeleton.visibility = View.VISIBLE
        binding.layoutEmpty.visibility = View.GONE
        binding.layoutGalleryContent.visibility = View.GONE
        startSkeletonAnimation()

        lifecycleScope.launch {
            val verticalVideosList = withContext(Dispatchers.IO) {
                queryVerticalVideos(requireContext().applicationContext)
            }

            stopSkeletonAnimation()
            binding.layoutSkeleton.visibility = View.GONE

            if (verticalVideosList.isEmpty()) {
                binding.layoutEmpty.visibility = View.VISIBLE
                binding.layoutGalleryContent.visibility = View.GONE
                videoAdapter?.submitList(emptyList())
            } else {
                binding.layoutEmpty.visibility = View.GONE
                binding.layoutGalleryContent.visibility = View.VISIBLE
                videoAdapter?.submitList(verticalVideosList)
            }
        }
    }

    private fun queryVerticalVideos(context: Context): List<VideoItem> {
        val list = ArrayList<VideoItem>(32)
        val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT
        )

        try {
            context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val durCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                val widthCol = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Video_$id"
                    val path = cursor.getString(pathCol) ?: ""
                    val duration = if (durCol != -1) cursor.getLong(durCol) else 0L

                    var width = if (widthCol != -1) cursor.getInt(widthCol) else 0
                    var height = if (heightCol != -1) cursor.getInt(heightCol) else 0

                    val videoUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                    if (path.isNotEmpty() && File(path).exists()) {
                        var isVertical = false
                        if (width > 0 && height > 0) {
                            isVertical = height >= width
                        } else {
                            val retriever = MediaMetadataRetriever()
                            try {
                                retriever.setDataSource(context, videoUri)
                                val wStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                                val hStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                                val rotStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)

                                val w = wStr?.toIntOrNull() ?: 0
                                val h = hStr?.toIntOrNull() ?: 0
                                val rot = rotStr?.toIntOrNull() ?: 0

                                isVertical = if (rot == 90 || rot == 270) {
                                    w >= h
                                } else {
                                    h >= w
                                }
                            } catch (e: Exception) {
                                // Fallback default to allow user preview
                                isVertical = true
                            } finally {
                                try { retriever.release() } catch (ignored: Exception) {}
                            }
                        }

                        if (isVertical) {
                            list.add(VideoItem(id, name, videoUri, path, duration))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("Gallery", "Error querying Mediastore", e)
        }
        return list
    }

    private fun showWallpaperPreviewDialog(video: VideoItem) {
        val builder = AlertDialog.Builder(requireContext())
        val dialogBinding = DialogPreviewBinding.inflate(layoutInflater)
        builder.setView(dialogBinding.root)

        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogBinding.dialogVideoView.apply {
            setOnPreparedListener { mp ->
                mp.isLooping = true
                mp.setVolume(0f, 0f)
                setVideoSize(mp.videoWidth, mp.videoHeight)
                start()
            }
            setOnErrorListener { _, _, _ -> true }
            setVideoPath(video.path)
        }

        dialogBinding.btnDialogCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnDialogSet.setOnClickListener {
            dialogBinding.dialogVideoView.stopPlayback()
            dialog.dismiss()

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    db.wallpaperDao.clearAllActive()
                    val wallpaper = WallpaperItem(
                        title = video.name,
                        videoUriStr = video.path,
                        trimStartMs = 0L,
                        trimEndMs = video.duration,
                        isLooping = true,
                        thumbnailUriStr = null,
                        nightModeEnabled = false,
                        isActive = true
                    )
                    db.wallpaperDao.insertWallpaper(wallpaper)

                    withContext(Dispatchers.Main) {
                        launchLiveWallpaperSettings()
                    }
                } catch (e: Exception) {
                    Log.e("Gallery", "Failed to insert active item into Database", e)
                }
            }
        }

        dialog.setOnDismissListener {
            try {
                dialogBinding.dialogVideoView.stopPlayback()
            } catch (ignored: Exception) {}
        }

        dialog.show()
    }

    private fun launchLiveWallpaperSettings() {
        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
            putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(requireContext(), VideoWallpaperService::class.java)
            )
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            try {
                val fallbackIntent = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)
                startActivity(fallbackIntent)
            } catch (ex: Exception) {
                Log.e("Gallery", "Live Wallpaper chooser is unavailable", ex)
            }
        }
    }

    override fun onDestroyView() {
        stopSkeletonAnimation()
        super.onDestroyView()
        videoAdapter?.onDestroy()
        _binding = null
    }
}
