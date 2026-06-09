package com.example

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.LruCache
import android.util.Size
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.databinding.ItemVideoBinding
import kotlinx.coroutines.*
import java.io.File
import java.util.Locale

class VideoAdapter(
    private var videos: List<VideoItem>,
    private val onItemClick: (VideoItem) -> Unit
) : RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {

    private val thumbnailCache = LruCache<String, Bitmap>(50)
    private val adapterScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun updateData(newVideos: List<VideoItem>) {
        videos = newVideos
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val binding = ItemVideoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VideoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val video = videos[position]
        holder.bind(video)
    }

    override fun getItemCount(): Int = videos.size

    inner class VideoViewHolder(private val binding: ItemVideoBinding) : RecyclerView.ViewHolder(binding.root) {
        private var loadJob: Job? = null

        fun bind(video: VideoItem) {
            // Display title safely
            val displayName = video.name.ifEmpty { "Video" }
            binding.tvTitle.text = displayName
            binding.tvDuration.text = formatDuration(video.duration)
            
            // Clean up previous image or set blank placeholder color as we load thumbnail asynchronously
            binding.ivThumbnail.setImageBitmap(null)
            loadJob?.cancel()

            binding.root.setOnClickListener {
                onItemClick(video)
            }

            val cacheKey = video.path
            val cachedBitmap = thumbnailCache.get(cacheKey)
            if (cachedBitmap != null) {
                binding.ivThumbnail.setImageBitmap(cachedBitmap)
            } else {
                loadJob = adapterScope.launch {
                    val bitmap = withContext(Dispatchers.IO) {
                        try {
                            val context = binding.root.context
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                try {
                                    context.contentResolver.loadThumbnail(video.contentUri, Size(300, 400), null)
                                } catch (e: Exception) {
                                    extractFrame(video.path)
                                }
                            } else {
                                extractFrame(video.path)
                            }
                        } catch (e: Exception) {
                            null
                        }
                    }
                    if (bitmap != null) {
                        thumbnailCache.put(cacheKey, bitmap)
                        binding.ivThumbnail.setImageBitmap(bitmap)
                    }
                }
            }
        }

        private fun extractFrame(path: String): Bitmap? {
            val retriever = MediaMetadataRetriever()
            return try {
                retriever.setDataSource(path)
                retriever.getFrameAtTime(1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } catch (e: Exception) {
                null
            } finally {
                try { retriever.release() } catch (ex: Exception) {}
            }
        }
    }

    fun onDestroy() {
        adapterScope.cancel()
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}
