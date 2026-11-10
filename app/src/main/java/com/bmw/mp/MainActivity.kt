package com.bmw.mp

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
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
        dashboard = DashboardView(this) {
            mapView.controller.setZoom(15.0)
            mapView.controller.animateTo(GeoPoint(43.8563, 18.4131))
            mapView.invalidate()
        }
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
        ensureLocationPermission()
    }

    private fun ensureLocationPermission() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            enableLiveLocation()
        } else {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), locationRequest)
        }
    }

    private fun enableLiveLocation() {
        if (locationOverlay != null) return
        locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), mapView).also {
            it.enableMyLocation()
            it.enableFollowLocation()
            mapView.overlays.add(it)
        }
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, gpsListener, Looper.getMainLooper())
        } catch (_: SecurityException) { }
    }

    override fun onRequestPermissionsResult(requestCode:Int, permissions:Array<out String>, grantResults:IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode==locationRequest && grantResults.isNotEmpty() &&
            grantResults[0]==PackageManager.PERMISSION_GRANTED) enableLiveLocation()
    }

    override fun onResume() {
        super.onResume()
        if (::mapView.isInitialized) mapView.onResume()
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) enableLiveLocation()
    }

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
            try { locationManager.removeUpdates(gpsListener) } catch (_: SecurityException) { }
        }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::mapView.isInitialized) mapView.onResume()
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            enableLiveLocation()
            try { locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, gpsListener, Looper.getMainLooper()) } catch (_: SecurityException) { }
        }
    }
}
