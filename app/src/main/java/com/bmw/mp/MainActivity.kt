package com.bmw.mp

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay

class MainActivity : Activity() {
    private lateinit var mapView: MapView

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
        root.addView(DashboardView(this), FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        root.addView(mapView, FrameLayout.LayoutParams(1, 1))
        root.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            val viewWidth = right - left
            val viewHeight = bottom - top
            val scale = minOf(viewWidth / 1280f, viewHeight / 720f)
            val offsetX = ((viewWidth - 1280f * scale) / 2f).toInt()
            val offsetY = ((viewHeight - 720f * scale) / 2f).toInt()
            val lp = mapView.layoutParams as FrameLayout.LayoutParams
            lp.width = (394f * scale).toInt()
            lp.height = (329f * scale).toInt()
            lp.leftMargin = offsetX + (15f * scale).toInt()
            lp.topMargin = offsetY + (213f * scale).toInt()
            mapView.layoutParams = lp
        }
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        if (::mapView.isInitialized) mapView.onResume()
    }

    override fun onPause() {
        if (::mapView.isInitialized) mapView.onPause()
        super.onPause()
    }
}
