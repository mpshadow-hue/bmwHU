package com.bmw.mp

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.content.Context
import android.content.ContentUris
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.GpsStatus
import android.os.Looper
import java.time.LocalDate
import java.time.YearMonth
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class MainActivity : Activity() {
    private lateinit var mapView: MapView
    private var locationOverlay: MyLocationNewOverlay? = null
    private val locationRequest = 701
    private lateinit var dashboard: DashboardView
    private lateinit var locationManager: LocationManager
    private var lastFix: Location? = null
    private val stats by lazy { getSharedPreferences("driving_stats", Context.MODE_PRIVATE) }
    private var todayDistance = 0.0
    private var monthDistance = 0.0
    private var movingSeconds = 0L
    private var maxSpeed = 0f
    private var statsDay = ""
    private var statsMonth = ""
    private val gpsListener = LocationListener { location -> onGpsLocation(location) }
    private val gpsStatusListener = GpsStatus.Listener { updateSatelliteStatus() }
    private val musicTracks = mutableListOf<MusicTrack>()
    private var currentTrackIndex = -1
    private var mediaPlayer: MediaPlayer? = null
    private var shufflePlayback = false

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE

        Configuration.getInstance().userAgentValue = packageName
        mapView = MapView(this).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setBuiltInZoomControls(false)
            controller.setZoom(13.5)
            controller.setCenter(GeoPoint(43.8563, 18.4131))
            overlays.add(CopyrightOverlay(this@MainActivity))
        }
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        dashboard = DashboardView(this, onMapClick = {
            mapView.controller.setZoom(15.0)
            mapView.controller.animateTo(GeoPoint(43.8563, 18.4131))
            mapView.invalidate()
        }, onTrackSelected = { track -> playTrack(musicTracks.indexOfFirst { it.uri == track.uri }) }, onPrevious = { playPrevious() }, onPlayPause = { togglePlayback() }, onNext = { playNext() }, onShuffle = { shufflePlayback = !shufflePlayback }, onSort = { sortMusicAlphabetically() })
        val root = FrameLayout(this)
        root.addView(dashboard, FrameLayout.LayoutParams(-1, -1))
        root.addView(mapView, FrameLayout.LayoutParams(1, 1))
        loadDrivingStats()
        root.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->
            val w=r-l; val h=b-t
            val scale=minOf(w/1280f,h/720f)
            val ox=((w-1280f*scale)/2f).toInt()
            val oy=((h-720f*scale)/2f).toInt()
            val lp=mapView.layoutParams as FrameLayout.LayoutParams
            lp.width=(380f*scale).toInt()
            lp.height=(321f*scale).toInt()
            lp.leftMargin=ox+(22f*scale).toInt()
            lp.topMargin=oy+(217f*scale).toInt()
            mapView.layoutParams=lp
        }
        setContentView(root)
        ensurePermissions()
    }

    private fun ensurePermissions() {
        val required = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) required.add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) required.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) required.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        if (required.isNotEmpty()) requestPermissions(required.toTypedArray(), locationRequest) else {
            enableLiveLocation()
            loadMusicLibrary()
        }
    }

    private fun enableLiveLocation() {
        if (locationOverlay == null) {
            locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), mapView).also {
                it.enableMyLocation()
                it.enableFollowLocation()
                mapView.overlays.add(it)
            }
        }
        try {
            locationManager.addGpsStatusListener(gpsStatusListener)
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, gpsListener, Looper.getMainLooper())
        } catch (_: SecurityException) { }
    }

    override fun onRequestPermissionsResult(requestCode:Int, permissions:Array<out String>, grantResults:IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == locationRequest) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) enableLiveLocation()
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) loadMusicLibrary()
            else dashboard.updateMusicLibrary(emptyList(), -1, false)
        }
    }

    private fun loadMusicLibrary() {
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) return
        val loaded = mutableListOf<MusicTrack>()
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.DATE_ADDED)
        try {
            contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection,
                MediaStore.Audio.Media.IS_MUSIC + " != 0", null, MediaStore.Audio.Media.DATE_ADDED + " DESC")?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val title = cursor.getString(nameColumn) ?: "Audio"
                    val date = cursor.getLong(dateColumn)
                    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    val dateLabel = if (date > 0L) SimpleDateFormat("dd.MM.", Locale.getDefault()).format(Date(date * 1000L)) else ""
                    if (!title.endsWith(".mp4", true) && !title.endsWith(".mkv", true) && !title.endsWith(".3gp", true)) loaded.add(MusicTrack(uri, title, dateLabel, date))
                }
            }
        } catch (_: Exception) { }
        try {
            val videoProjection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.DURATION
            )
            contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                videoProjection,
                MediaStore.Video.Media.DURATION + " > 0",
                null,
                MediaStore.Video.Media.DATE_ADDED + " DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val title = cursor.getString(nameColumn) ?: "Video"
                    val date = cursor.getLong(dateColumn)
                    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    val dateLabel = if (date > 0L) SimpleDateFormat("dd.MM.", Locale.getDefault()).format(Date(date * 1000L)) else ""
                    loaded.add(MusicTrack(uri, title, dateLabel, date))
                }
            }
            loaded.sortByDescending { it.dateAdded }
        } catch (_: Exception) { }
        val currentUri = musicTracks.getOrNull(currentTrackIndex)?.uri
        musicTracks.clear(); musicTracks.addAll(loaded)
        currentTrackIndex = currentUri?.let { uri -> musicTracks.indexOfFirst { it.uri == uri } } ?: -1
        publishMusicLibrary()
    }

    private fun playTrack(index: Int) {
        if (index !in musicTracks.indices) return
        currentTrackIndex = index
        mediaPlayer?.release(); mediaPlayer = null
        val player = MediaPlayer(); mediaPlayer = player
        player.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
        player.setOnPreparedListener { prepared -> if (mediaPlayer === prepared) { prepared.start(); publishMusicLibrary() } else prepared.release() }
        player.setOnCompletionListener { playNext() }
        player.setOnErrorListener { failed, _, _ -> failed.release(); if (mediaPlayer === failed) mediaPlayer = null; publishMusicLibrary(); true }
        try { player.setDataSource(this, musicTracks[index].uri); player.prepareAsync() } catch (_: Exception) { player.release(); mediaPlayer = null }
        publishMusicLibrary()
    }

    private fun togglePlayback() {
        val player = mediaPlayer
        if (player == null) { if (musicTracks.isNotEmpty()) playTrack(if (currentTrackIndex >= 0) currentTrackIndex else 0); return }
        try { if (player.isPlaying) player.pause() else player.start() } catch (_: IllegalStateException) { }
        publishMusicLibrary()
    }

    private fun playNext() {
        if (musicTracks.isEmpty()) return
        val next = if (shufflePlayback && musicTracks.size > 1) {
            var candidate: Int
            do { candidate = Random.nextInt(musicTracks.size) } while (candidate == currentTrackIndex)
            candidate
        } else if (currentTrackIndex < 0) 0 else (currentTrackIndex + 1) % musicTracks.size
        playTrack(next)
    }

    private fun playPrevious() {
        try { if (mediaPlayer != null && mediaPlayer!!.currentPosition > 3000) { mediaPlayer!!.seekTo(0); return } } catch (_: IllegalStateException) { }
        if (musicTracks.isNotEmpty()) playTrack(if (currentTrackIndex <= 0) musicTracks.lastIndex else currentTrackIndex - 1)
    }

    private fun sortMusicAlphabetically() {
        val currentUri = musicTracks.getOrNull(currentTrackIndex)?.uri
        musicTracks.sortBy { it.title.lowercase(Locale.getDefault()) }
        currentTrackIndex = currentUri?.let { uri -> musicTracks.indexOfFirst { it.uri == uri } } ?: -1
        publishMusicLibrary()
    }

    private fun publishMusicLibrary() {
        val playing = try { mediaPlayer?.isPlaying == true } catch (_: IllegalStateException) { false }
        if (::dashboard.isInitialized) dashboard.updateMusicLibrary(musicTracks.toList(), currentTrackIndex, playing)
    }

    override fun onDestroy() { mediaPlayer?.release(); mediaPlayer = null; super.onDestroy() }

    private fun loadDrivingStats() {
        val day = LocalDate.now().toString()
        val month = YearMonth.now().toString()
        statsDay = stats.getString("day", "") ?: ""
        statsMonth = stats.getString("month", "") ?: ""
        if (statsDay != day) {
            todayDistance = 0.0
            maxSpeed = 0f
            movingSeconds = 0L
            statsDay = day
        } else {
            todayDistance = stats.getFloat("today_km", 0f).toDouble()
            maxSpeed = stats.getFloat("max_speed", 0f)
            movingSeconds = stats.getLong("moving_seconds", 0L)
        }
        monthDistance = if (statsMonth == month) stats.getFloat("month_km", 0f).toDouble() else 0.0
        statsMonth = month
        publishStats(0f)
    }

    private fun onGpsLocation(location: Location) {
        rollStatsPeriod()
        val speed = if (location.hasSpeed()) (location.speed * 3.6f).coerceAtLeast(0f) else 0f
        val previous = lastFix
        if (previous != null && location.time > previous.time) {
            val delta = previous.distanceTo(location).toDouble()
            val elapsed = ((location.time - previous.time) / 1000L).coerceIn(0L, 10L)
            if (delta in 2.0..500.0 && speed > 2f) {
                val km = delta / 1000.0
                todayDistance += km
                monthDistance += km
                movingSeconds += elapsed
            }
        }
        lastFix = location
        if (speed > maxSpeed) maxSpeed = speed
        saveDrivingStats()
        publishStats(speed)
    }

    private fun rollStatsPeriod() {
        val today = LocalDate.now().toString()
        val month = YearMonth.now().toString()
        if (today != statsDay) {
            todayDistance = 0.0
            maxSpeed = 0f
            movingSeconds = 0L
            statsDay = today
        }
        if (month != statsMonth) {
            monthDistance = 0.0
            statsMonth = month
        }
    }

    @Suppress("DEPRECATION")
    private fun updateSatelliteStatus() {
        try {
            val satellites = locationManager.getGpsStatus(null)?.satellites?.toList().orEmpty()
            val used = satellites.count { it.usedInFix() }
            if (::dashboard.isInitialized) dashboard.updateGpsSatellites(used, satellites.size)
        } catch (_: SecurityException) { }
    }

    private fun saveDrivingStats() {
        stats.edit().putString("day", statsDay).putString("month", statsMonth)
            .putFloat("today_km", todayDistance.toFloat()).putFloat("month_km", monthDistance.toFloat())
            .putFloat("max_speed", maxSpeed).putLong("moving_seconds", movingSeconds).apply()
    }

    private fun publishStats(speed: Float) {
        val average = if (movingSeconds > 0L) (todayDistance * 3600.0 / movingSeconds).toFloat() else 0f
        if (::dashboard.isInitialized) dashboard.updateDrivingStats(speed, todayDistance, monthDistance, average, maxSpeed)
    }

    override fun onPause() {
        if (::mapView.isInitialized) mapView.onPause()
        if (::locationManager.isInitialized) {
            try {
                locationManager.removeUpdates(gpsListener)
                locationManager.removeGpsStatusListener(gpsStatusListener)
            } catch (_: SecurityException) { }
        }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::mapView.isInitialized) mapView.onResume()
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            enableLiveLocation()
        }
    }
}
