package com.example

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.LruCache
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.databinding.ItemVideoBinding
import kotlinx.coroutines.*
import java.io.File
import java.util.Locale

class VideoAdapter(
    private var videos: List<VideoItem>,
    private val onItemClick: (VideoItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_ITEM = 1
    }

    private val thumbnailCache = LruCache<String, Bitmap>(50)
    private val adapterScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun updateData(newVideos: List<VideoItem>) {
        videos = newVideos
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) TYPE_HEADER else TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_gallery_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val binding = ItemVideoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            VideoViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is HeaderViewHolder) {
            holder.bind()
        } else if (holder is VideoViewHolder) {
            val video = videos[position - 1]
            holder.bind(video)
        }
    }

    override fun getItemCount(): Int = if (videos.isEmpty()) 0 else videos.size + 1

    inner class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvCount: android.widget.TextView = itemView.findViewById(R.id.tv_count_header)
        fun bind() {
            tvCount.text = tvCount.context.getString(R.string.videos_found_count, videos.size)
        }
    }

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
