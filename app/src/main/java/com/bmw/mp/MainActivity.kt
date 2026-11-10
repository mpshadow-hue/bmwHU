package com.bmw.mp

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
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
        val root = FrameLayout(this)
        root.addView(DashboardView(this) {
            mapView.controller.setZoom(15.0)
            mapView.controller.animateTo(GeoPoint(43.8563, 18.4131))
            mapView.invalidate()
        }, FrameLayout.LayoutParams(-1, -1))
        root.addView(mapView, FrameLayout.LayoutParams(1, 1))
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

    override fun onPause() {
        if (::mapView.isInitialized) mapView.onPause()
        super.onPause()
    }
}
