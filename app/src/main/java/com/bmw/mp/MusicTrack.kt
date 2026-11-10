package com.bmw.mp

import android.net.Uri

data class MusicTrack(
    val uri: Uri,
    val title: String,
    val dateLabel: String,
    val dateAdded: Long = 0L
)
