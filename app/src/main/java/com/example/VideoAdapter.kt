package com.example

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.databinding.ItemVideoBinding
import kotlinx.coroutines.*
import java.io.File
import java.util.Locale

class VideoAdapter(
    private val onItemClick: (VideoItem) -> Unit
) : ListAdapter<VideoItem, RecyclerView.ViewHolder>(VideoDiffCallback()) {

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_ITEM = 1
    }

    // Dynamic cache sized to 1/8th of available runtime memory in bytes
    private val thumbnailCache: LruCache<String, Bitmap> by lazy {
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSize = (maxMemory / 8).coerceAtLeast(1024 * 4) // At least 4MB
        object : LruCache<String, Bitmap>(cacheSize) {
            override fun sizeOf(key: String, bitmap: Bitmap): Int {
                return bitmap.byteCount / 1024
            }
        }
    }

    private val adapterScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    class VideoDiffCallback : DiffUtil.ItemCallback<VideoItem>() {
        override fun areItemsTheSame(oldItem: VideoItem, newItem: VideoItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: VideoItem, newItem: VideoItem): Boolean {
            return oldItem.name == newItem.name && 
                   oldItem.path == newItem.path && 
                   oldItem.duration == newItem.duration
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) TYPE_HEADER else TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            val view = inflater.inflate(R.layout.item_gallery_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val binding = ItemVideoBinding.inflate(inflater, parent, false)
            VideoViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is HeaderViewHolder) {
            holder.bind(currentList.size)
        } else if (holder is VideoViewHolder) {
            val video = getItem(position - 1)
            holder.bind(video)
        }
    }

    override fun getItemCount(): Int {
        val size = currentList.size
        return if (size == 0) 0 else size + 1
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is VideoViewHolder) {
            holder.recycle()
        }
    }

    inner class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvCount: android.widget.TextView = itemView.findViewById(R.id.tv_count_header)
        fun bind(count: Int) {
            tvCount.text = tvCount.context.getString(R.string.videos_found_count, count)
        }
    }

    inner class VideoViewHolder(private val binding: ItemVideoBinding) : RecyclerView.ViewHolder(binding.root) {
        private var loadJob: Job? = null

        fun bind(video: VideoItem) {
            val displayName = video.name.ifEmpty { "Video" }
            binding.tvTitle.text = displayName
            binding.tvDuration.text = formatDuration(video.duration)

            binding.root.setOnClickListener {
                onItemClick(video)
            }

            val cacheKey = video.path
            val cachedBitmap = thumbnailCache.get(cacheKey)
            if (cachedBitmap != null) {
                binding.ivThumbnail.setImageBitmap(cachedBitmap)
            } else {
                binding.ivThumbnail.setImageDrawable(null)
                loadJob?.cancel()
                loadJob = adapterScope.launch {
                    val bitmap = withContext(Dispatchers.IO) {
                        loadOptimizedThumbnail(video)
                    }
                    if (isActive && bitmap != null) {
                        thumbnailCache.put(cacheKey, bitmap)
                        binding.ivThumbnail.setImageBitmap(bitmap)
                    }
                }
            }
        }

        fun recycle() {
            loadJob?.cancel()
            loadJob = null
            binding.ivThumbnail.setImageDrawable(null)
        }

        private fun loadOptimizedThumbnail(video: VideoItem): Bitmap? {
            return try {
                val context = binding.root.context
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        context.contentResolver.loadThumbnail(video.contentUri, Size(240, 426), null)
                    } catch (e: Exception) {
                        extractFrameFromPath(video.path)
                    }
                } else {
                    extractFrameFromPath(video.path)
                }
            } catch (e: Exception) {
                null
            }
        }

        private fun extractFrameFromPath(path: String): Bitmap? {
            val retriever = MediaMetadataRetriever()
            return try {
                retriever.setDataSource(path)
                retriever.getFrameAtTime(500000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } catch (e: Exception) {
                null
            } finally {
                try { retriever.release() } catch (ex: Exception) {}
            }
        }
    }

    fun onDestroy() {
        adapterScope.cancel()
        thumbnailCache.evictAll()
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}
