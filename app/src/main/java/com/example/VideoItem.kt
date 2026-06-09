package com.example

import android.net.Uri

data class VideoItem(
    val id: Long,
    val name: String,
    val contentUri: Uri,
    val path: String,
    val duration: Long
)
