package com.bmw.mp

import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.MapTileIndex

class EsriWorldImageryTileSource : XYTileSource(
    "Esri World Imagery",
    0,
    19,
    256,
    ".jpg",
    arrayOf("https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"),
    "Sources: Esri, Maxar, Earthstar Geographics, and the GIS User Community"
) {
    override fun getTileURLString(index: Long): String {
        return "https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/" +
            MapTileIndex.getZoom(index) + "/" +
            MapTileIndex.getY(index) + "/" +
            MapTileIndex.getX(index)
    }
}
