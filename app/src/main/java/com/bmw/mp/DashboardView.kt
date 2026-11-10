package com.bmw.mp

import android.content.Context
import android.content.Intent
import android.bluetooth.BluetoothAdapter
import android.net.wifi.WifiManager
import android.provider.Settings
import android.app.AlertDialog
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.*

class DashboardView(context: Context, private val onMapClick: () -> Unit = {}, private val onTrackSelected: (MusicTrack) -> Unit = {}, private val onPrevious: () -> Unit = {}, private val onPlayPause: () -> Unit = {}, private val onNext: () -> Unit = {}, private val onShuffle: () -> Unit = {}, private val onSort: () -> Unit = {}, private val onReloadMusic: () -> Unit = {}) : View(context) {
    init { setLayerType(View.LAYER_TYPE_SOFTWARE, null) }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val orange = Color.rgb(255, 137, 0)
    private val gold = Color.rgb(255, 190, 25)
    private val white = Color.WHITE
    private var notice = ""
    private var musicTracks: List<MusicTrack> = emptyList()
    private var currentTrackIndex = -1
    private var musicIsPlaying = false
    private var shuffleEnabled = false
    private var playlistOffset = 0
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var listSwipeHandled = false
    private var gpsUsed = 0
    private var gpsVisible = 0
    private var liveSpeedKmh = 0f
    private var todayKm = 0.0
    private var monthKm = 0.0
    private var averageKmh = 0f
    private var maxKmh = 0f

    fun updateMusicLibrary(tracks: List<MusicTrack>, currentIndex: Int, isPlaying: Boolean) {
        musicTracks = tracks
        playlistOffset = playlistOffset.coerceIn(0, (musicTracks.size - 7).coerceAtLeast(0))
        currentTrackIndex = currentIndex
        musicIsPlaying = isPlaying
        postInvalidate()
    }

    fun updateGpsSatellites(used: Int, visible: Int) {
        gpsUsed = used.coerceAtLeast(0)
        gpsVisible = visible.coerceAtLeast(0)
        postInvalidate()
    }

    fun updateDrivingStats(speedKmh: Float, today: Double, month: Double, average: Float, maximum: Float) {
        liveSpeedKmh = speedKmh.coerceAtLeast(0f)
        todayKm = today.coerceAtLeast(0.0)
        monthKm = month.coerceAtLeast(0.0)
        averageKmh = average.coerceAtLeast(0f)
        maxKmh = maximum.coerceAtLeast(0f)
        postInvalidate()
    }
    private val appPrefs = context.getSharedPreferences("bottom_shortcuts", Context.MODE_PRIVATE)
    private fun slotPackage(slot: Int) = appPrefs.getString("slot_$slot", null)
    private fun slotLabel(slot: Int): String {
        val pkg = slotPackage(slot) ?: return "+"
        return try {
            val app = context.packageManager.getApplicationInfo(pkg, 0)
            context.packageManager.getApplicationLabel(app).toString()
        } catch (_: Exception) { "+" }
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val s = min(width / 1280f, height / 720f)
        c.save()
        c.translate((width - 1280f*s)/2f, (height - 720f*s)/2f)
        c.scale(s, s)
        c.drawColor(Color.BLACK)

        label(c, SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()), 640f, 48f, 42f, white, true, Paint.Align.CENTER)
        postInvalidateDelayed(30_000L)
        val wifiOn = try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wm.isWifiEnabled
        } catch (_: Exception) { false }
        val bluetoothOn = try {
            BluetoothAdapter.getDefaultAdapter()?.isEnabled == true
        } catch (_: Exception) { false }
        panel(c, 462f, 64f, 62f, 58f)
        panel(c, 787f, 64f, 62f, 58f)
        drawBluetoothSymbol(c, 818f, 93f, if (bluetoothOn) gold else Color.GRAY)
        drawWifiSymbol(c, 493f, 93f, if (wifiOn) gold else Color.GRAY)
        postInvalidateDelayed(2_000L)

        panel(c, 12f, 8f, 400f, 102f)
        metric(c, 25f, 17f, "tdy", String.format(Locale.US, "%.1f", todayKm), "km", "route")
        metric(c, 215f, 17f, "mnth", String.format(Locale.US, "%.0f", monthKm), "km", "calendar")
        panel(c, 12f, 118f, 400f, 82f)
        metric(c, 25f, 123f, "avg/tdy", String.format(Locale.US, "%.1f", averageKmh), "km/h", "speed")
        metric(c, 215f, 123f, "max/tdy", String.format(Locale.US, "%.0f", maxKmh), "km/h", "speed")

        panel(c, 12f, 210f, 400f, 335f)
        panel(c, 25f, 549f, 160f, 38f); label(c, "● GPS $gpsUsed/$gpsVisible", 40f, 575f, 18f, white, true)

        // Speedometer
        val cx = 640f; val cy = 260f
        p.style = Paint.Style.STROKE
        p.color = Color.rgb(255,65,0); p.strokeWidth = 22f
        p.setShadowLayer(22f,0f,0f,Color.rgb(255,55,0))
        c.drawArc(RectF(444f,64f,836f,456f),135f,270f,false,p)
        p.clearShadowLayer()
        p.color = orange; p.strokeWidth = 8f
        c.drawArc(RectF(455f,72f,825f,442f),135f,270f,false,p)
        p.color = gold; p.strokeWidth = 3f
        for (i in 0..40) {
            val a = Math.toRadians(135.0 + i*6.75)
            val r1 = 175f; val r2 = if (i%4==0) 196f else 186f
            c.drawLine(cx+cos(a).toFloat()*r1,cy+sin(a).toFloat()*r1,
                cx+cos(a).toFloat()*r2,cy+sin(a).toFloat()*r2,p)
        }
        p.style = Paint.Style.FILL
        listOf("0","20","40","60","80","100","120","140","160","180","200").forEachIndexed { i,n ->
            val a = Math.toRadians(135.0+i*27.0)
            label(c,n,cx+cos(a).toFloat()*151f,cy+sin(a).toFloat()*151f+8f,20f,white,false,Paint.Align.CENTER)
        }
        val needleAngle = Math.toRadians(135.0 + (liveSpeedKmh.coerceIn(0f, 200f) / 200.0) * 270.0)
        p.style = Paint.Style.STROKE
        p.color = Color.rgb(255, 218, 90)
        p.strokeWidth = 5f
        p.setShadowLayer(12f, 0f, 0f, Color.rgb(255, 95, 0))
        c.drawLine(cx, cy, cx + cos(needleAngle).toFloat() * 183f, cy + sin(needleAngle).toFloat() * 183f, p)
        p.clearShadowLayer()
        p.style = Paint.Style.FILL
        c.drawCircle(cx, cy, 8f, p)
        drawBmwRoundel(c,640f,260f,66f)
        label(c,String.format(Locale.US, "%.0f", liveSpeedKmh),598f,392f,58f,orange,true,Paint.Align.RIGHT)
        label(c,"km/h",605f,392f,29f,white)
        label(c,musicTracks.getOrNull(currentTrackIndex)?.title?.take(27) ?: "Nema odabrane pjesme",640f,425f,21f,white,false,Paint.Align.CENTER)
        val bars = intArrayOf(20,28,35,43,31,24,40,52,35,47,57,43,30,48,38,25)
        bars.forEachIndexed { i,h -> p.color=if(i%3==0) gold else orange
            c.drawRect(460f+i*22f,478f-h,474f+i*22f,478f,p) }
        media(c,505f,537f,"|◀",false); media(c,640f,537f,if(musicIsPlaying) "Ⅱ" else "▶",true); media(c,775f,537f,"▶|",false)

        // car music
        panel(c,868f,8f,400f,558f)
        label(c,"↓",900f,48f,35f,gold,true,Paint.Align.CENTER)
        label(c,"car music",1080f,48f,26f,white,false,Paint.Align.CENTER)
        label(c,musicTracks.size.toString(),1225f,48f,18f,white,false,Paint.Align.RIGHT)
        p.color=Color.GREEN; c.drawCircle(1248f,22f,7f,p)
        if (musicTracks.isEmpty()) {
            label(c,"Nema pronađene muzike",1065f,235f,19f,Color.LTGRAY,false,Paint.Align.CENTER)
        } else {
            musicTracks.drop(playlistOffset).take(7).forEachIndexed { i, track ->
                val absoluteIndex = i + playlistOffset
                val y=85f+i*47f
                if(absoluteIndex==currentTrackIndex){
                    p.color=orange;p.style=Paint.Style.STROKE;p.strokeWidth=2f
                    c.drawRoundRect(RectF(878f,y,1258f,y+43f),12f,12f,p);p.style=Paint.Style.FILL
                }
                label(c,if(absoluteIndex==currentTrackIndex && musicIsPlaying)"▶" else "♫",895f,y+29f,23f,gold,true)
                label(c,track.title.take(29),930f,y+28f,16f,white)
                label(c,track.dateLabel,1245f,y+28f,16f,white,false,Paint.Align.RIGHT)
                p.color=Color.rgb(80,60,35); c.drawLine(882f,y+45f,1255f,y+45f,p)
            }
        }
        panel(c,880f,479f,376f,66f)
        label(c,if (shuffleEnabled) "⤨  Shuffle ON" else "⤨  Shuffle",910f,523f,21f,if (shuffleEnabled) gold else white)
        label(c,"☷  A-Z",1090f,523f,21f,white)

        // Bottom menu: two custom app slots
        panel(c,26f,606f,1228f,78f)
        val separators = floatArrayOf(312f,562f,755f,945f)
        separators.forEach { x -> p.color=orange;p.strokeWidth=1f;c.drawLine(x,621f,x,670f,p) }
        label(c,"●",150f,652f,28f,orange,true,Paint.Align.CENTER)
        label(c,"Mapa",188f,652f,21f,white)
        label(c,"▣",400f,652f,28f,orange,true,Paint.Align.CENTER)
        label(c,"Radio",438f,652f,21f,white)
        val shortcut0 = slotLabel(0)
        val shortcut1 = slotLabel(1)
        label(c,if(shortcut0=="+") "+" else "▣",655f,652f,30f,if(shortcut0=="+") gold else orange,true,Paint.Align.CENTER)
        if(shortcut0!="+") label(c,shortcut0.take(10),690f,652f,16f,white)
        label(c,if(shortcut1=="+") "+" else "▣",850f,652f,30f,if(shortcut1=="+") gold else orange,true,Paint.Align.CENTER)
        if(shortcut1!="+") label(c,shortcut1.take(10),885f,652f,16f,white)
        label(c,"▦",1010f,652f,28f,white,true,Paint.Align.CENTER)
        label(c,"Aplikacije",1068f,652f,21f,white)
        if(notice.isNotEmpty()) label(c,notice,640f,596f,15f,gold,false,Paint.Align.CENTER)
        c.restore()
    }

    private fun drawWifiSymbol(c: Canvas, cx: Float, cy: Float, color: Int) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = 5f
        p.strokeCap = Paint.Cap.ROUND
        p.color = color
        for (radius in listOf(26f, 18f, 10f)) {
            c.drawArc(RectF(cx-radius, cy-radius, cx+radius, cy+radius), 225f, 90f, false, p)
        }
        p.style = Paint.Style.FILL
        c.drawCircle(cx, cy+15f, 4f, p)
    }

    private fun drawBluetoothSymbol(c: Canvas, cx: Float, cy: Float, color: Int) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = 5f
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        p.color = color
        val path = Path().apply {
            moveTo(cx, cy-23f); lineTo(cx, cy+23f)
            moveTo(cx, cy-23f); lineTo(cx+16f, cy-8f); lineTo(cx-13f, cy+8f); lineTo(cx+16f, cy+23f); lineTo(cx, cy+8f)
        }
        c.drawPath(path, p)
        p.style = Paint.Style.FILL
    }

    private fun drawMetricIcon(c: Canvas, cx: Float, cy: Float, type: String) {
        p.style = Paint.Style.STROKE
        p.color = gold
        p.strokeWidth = 2.5f
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        when (type) {
            "route" -> {
                val path = Path().apply {
                    moveTo(cx-12f,cy+12f)
                    cubicTo(cx-18f,cy-2f,cx-1f,cy-17f,cx+4f,cy-7f)
                    cubicTo(cx+8f,cy+1f,cx-3f,cy+2f,cx+8f,cy+13f)
                }
                c.drawPath(path,p)
                p.style = Paint.Style.FILL
                c.drawCircle(cx-12f,cy+12f,3f,p)
                c.drawCircle(cx+8f,cy+13f,3f,p)
            }
            "calendar" -> {
                c.drawRoundRect(RectF(cx-14f,cy-12f,cx+14f,cy+13f),3f,3f,p)
                c.drawLine(cx-14f,cy-4f,cx+14f,cy-4f,p)
                c.drawLine(cx-7f,cy-16f,cx-7f,cy-8f,p)
                c.drawLine(cx+7f,cy-16f,cx+7f,cy-8f,p)
                p.style = Paint.Style.FILL
                c.drawCircle(cx-6f,cy+3f,2f,p)
                c.drawCircle(cx+3f,cy+3f,2f,p)
            }
            else -> {
                c.drawArc(RectF(cx-15f,cy-15f,cx+15f,cy+15f),190f,160f,false,p)
                c.drawLine(cx,cy,cx+9f,cy-7f,p)
                p.style = Paint.Style.FILL
                c.drawCircle(cx,cy,3f,p)
            }
        }
        p.style = Paint.Style.FILL
    }

    private fun metric(c:Canvas,x:Float,y:Float,title:String,value:String,unit:String,icon:String){
        label(c,title,x+92f,y+20f,17f,white,false,Paint.Align.CENTER)
        drawMetricIcon(c,x+20f,y+47f,icon)
        label(c,value,x+55f,y+61f,30f,orange,true)
        p.textSize=30f;p.typeface=Typeface.DEFAULT_BOLD
        label(c,unit,x+60f+p.measureText(value),y+61f,18f,white)
        p.color=orange;p.strokeWidth=1f;c.drawLine(x+185f,y+3f,x+185f,y+69f,p)
    }
    private fun drawMap(c:Canvas){
        val save=c.save()
        c.clipRect(14f,212f,410f,543f)
        p.style=Paint.Style.FILL
        val bg=Paint(Paint.ANTI_ALIAS_FLAG).apply{shader=LinearGradient(14f,212f,410f,543f,Color.rgb(4,27,23),Color.rgb(5,12,20),Shader.TileMode.CLAMP)}
        c.drawRect(14f,212f,410f,543f,bg)
        // Dense city blocks and green areas
        val blocks=arrayOf(
            floatArrayOf(20f,220f,65f,265f),floatArrayOf(75f,220f,126f,257f),
            floatArrayOf(140f,218f,184f,270f),floatArrayOf(250f,220f,300f,265f),
            floatArrayOf(320f,220f,400f,275f),floatArrayOf(25f,285f,82f,335f),
            floatArrayOf(95f,280f,145f,335f),floatArrayOf(285f,285f,345f,340f),
            floatArrayOf(350f,290f,405f,345f),floatArrayOf(22f,430f,75f,475f),
            floatArrayOf(90f,440f,145f,495f),floatArrayOf(290f,435f,350f,485f),
            floatArrayOf(355f,430f,405f,490f),floatArrayOf(25f,500f,95f,540f),
            floatArrayOf(315f,500f,400f,540f)
        )
        blocks.forEachIndexed{i,v->
            p.color=when(i%4){0->Color.rgb(12,65,43);1->Color.rgb(15,48,43);2->Color.rgb(20,57,38);else->Color.rgb(10,43,42)}
            c.drawRoundRect(RectF(v[0],v[1],v[2],v[3]),4f,4f,p)
        }
        // Fine street grid
        p.color=Color.rgb(46,79,75);p.strokeWidth=1f
        for(i in 0..13){val y=218f+i*25f;c.drawLine(14f,y,410f,y+if(i%2==0)8f else -5f,p)}
        for(i in 0..15){val x=18f+i*26f;c.drawLine(x,212f,x+if(i%3==0)18f else -4f,545f,p)}
        // Main arterial roads
        p.color=Color.rgb(68,93,94);p.strokeWidth=5f
        val roads=listOf(
            Path().apply{moveTo(10f,300f);cubicTo(90f,325f,120f,350f,210f,360f);cubicTo(290f,375f,340f,420f,420f,430f)},
            Path().apply{moveTo(25f,420f);cubicTo(110f,390f,160f,405f,230f,455f);cubicTo(300f,500f,350f,500f,420f,510f)},
            Path().apply{moveTo(75f,210f);cubicTo(100f,280f,120f,350f,95f,545f)}
        )
        roads.forEach{c.drawPath(it,p)}
        // Bright blue side roads
        p.color=Color.rgb(0,153,235);p.strokeWidth=4f
        val blue=Path().apply{moveTo(14f,460f);cubicTo(95f,420f,145f,405f,210f,355f);cubicTo(280f,300f,335f,315f,410f,245f)}
        c.drawPath(blue,p)
        val blue2=Path().apply{moveTo(45f,215f);cubicTo(85f,280f,75f,350f,125f,410f);cubicTo(165f,455f,180f,495f,205f,545f)}
        c.drawPath(blue2,p)
        // Main yellow navigation road and orange glow
        val route=Path().apply{moveTo(165f,210f);cubicTo(195f,265f,215f,305f,228f,360f);cubicTo(242f,415f,250f,470f,240f,545f)}
        p.color=Color.rgb(255,95,0);p.strokeWidth=14f;p.setShadowLayer(12f,0f,0f,Color.rgb(255,65,0));c.drawPath(route,p);p.clearShadowLayer()
        p.color=Color.rgb(255,196,22);p.strokeWidth=6f;c.drawPath(route,p)
        // Current location marker
        p.color=Color.rgb(255,30,30);c.drawCircle(230f,390f,22f,p)
        p.color=Color.WHITE;c.drawCircle(230f,390f,15f,p)
        p.color=Color.rgb(0,115,240)
        val arrow=Path().apply{moveTo(230f,378f);lineTo(239f,397f);lineTo(230f,393f);lineTo(221f,397f);close()}
        c.drawPath(arrow,p)
        // Camera markers and road labels
        p.color=Color.RED;c.drawCircle(215f,315f,19f,p);c.drawCircle(285f,480f,19f,p)
        p.color=Color.WHITE;c.drawCircle(215f,315f,13f,p);c.drawCircle(285f,480f,13f,p)
        label(c,"▣",215f,322f,18f,Color.BLACK,true,Paint.Align.CENTER)
        label(c,"▣",285f,487f,18f,Color.BLACK,true,Paint.Align.CENTER)
        p.color=Color.RED;c.drawCircle(62f,490f,32f,p);p.color=Color.WHITE;c.drawCircle(62f,490f,25f,p)
        label(c,"60",62f,500f,26f,Color.BLACK,true,Paint.Align.CENTER)
        label(c,"Sarajevo",292f,365f,24f,white,true)
        p.color=Color.rgb(0,105,220);c.drawRoundRect(RectF(326f,510f,390f,540f),5f,5f,p)
        label(c,"M18",358f,532f,20f,white,true,Paint.Align.CENTER)
        c.restoreToCount(save)
    }
    private fun panel(c:Canvas,x:Float,y:Float,w:Float,h:Float){
        val rect=RectF(x,y,x+w,y+h)
        p.style=Paint.Style.FILL;p.color=Color.rgb(12,7,3)
        p.setShadowLayer(12f,0f,0f,Color.rgb(255,75,0))
        c.drawRoundRect(rect,18f,18f,p);p.clearShadowLayer()
        p.color=Color.rgb(255,105,0);p.style=Paint.Style.STROKE;p.strokeWidth=3f
        p.setShadowLayer(10f,0f,0f,Color.rgb(255,70,0))
        c.drawRoundRect(rect,18f,18f,p);p.clearShadowLayer()
        p.color=Color.rgb(255,205,70);p.strokeWidth=1f
        c.drawRoundRect(RectF(x+2f,y+2f,x+w-2f,y+h-2f),16f,16f,p)
        p.style=Paint.Style.FILL
    }
    private fun drawBmwRoundel(c:Canvas,cx:Float,cy:Float,r:Float){
        // Black outer ring with warm backlight
        p.style=Paint.Style.FILL;p.color=Color.BLACK
        p.setShadowLayer(18f,0f,0f,Color.rgb(255,65,0))
        c.drawCircle(cx,cy,r,p);p.clearShadowLayer()
        p.color=Color.WHITE;p.style=Paint.Style.STROKE;p.strokeWidth=2.5f
        c.drawCircle(cx,cy,r-2f,p)
        // Inner BMW roundel: four alternating blue/white quadrants
        val inner=RectF(cx-42f,cy-42f,cx+42f,cy+42f)
        p.style=Paint.Style.FILL
        p.color=Color.rgb(0,160,220);c.drawArc(inner,180f,90f,true,p)
        p.color=Color.WHITE;c.drawArc(inner,270f,90f,true,p)
        p.color=Color.rgb(0,160,220);c.drawArc(inner,0f,90f,true,p)
        p.color=Color.WHITE;c.drawArc(inner,90f,90f,true,p)
        p.color=Color.WHITE;p.style=Paint.Style.STROKE;p.strokeWidth=2f
        c.drawCircle(cx,cy,42f,p)
        // BMW lettering follows the upper arc of the black ring
        p.style=Paint.Style.FILL;p.color=Color.WHITE
        p.textSize=13f;p.typeface=Typeface.create("sans-serif",Typeface.BOLD)
        p.textAlign=Paint.Align.CENTER
        val arc=Path().apply{addArc(RectF(cx-54f,cy-54f,cx+54f,cy+54f),205f,130f)}
        c.drawTextOnPath("B M W",arc,0f,0f,p)
        p.style=Paint.Style.STROKE;p.strokeWidth=2f;p.color=Color.WHITE
        c.drawCircle(cx,cy,r-2f,p)
        p.style=Paint.Style.FILL
    }
    private fun media(c:Canvas,x:Float,y:Float,t:String,big:Boolean){
        p.color=Color.rgb(255,70,0);p.style=Paint.Style.STROKE;p.strokeWidth=12f
        p.setShadowLayer(16f,0f,0f,Color.rgb(255,55,0))
        c.drawCircle(x,y,if(big)48f else 36f,p);p.clearShadowLayer()
        p.color=orange;p.strokeWidth=2.5f;c.drawCircle(x,y,if(big)48f else 36f,p)
        p.style=Paint.Style.FILL;label(c,t,x,y+11f,if(big)34f else 24f,white,true,Paint.Align.CENTER)
    }
    private fun label(c:Canvas,t:String,x:Float,y:Float,size:Float,color:Int,bold:Boolean=false,align:Paint.Align=Paint.Align.LEFT){
        p.color=color;p.textSize=size;p.textAlign=align
        p.typeface=if(bold)Typeface.create("sans-serif",Typeface.BOLD) else Typeface.create("sans-serif",Typeface.NORMAL)
        c.drawText(t,x,y,p)
    }

    private fun openRadio() {
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val candidates = context.packageManager.queryIntentActivities(query, 0)
        val radios = candidates.filter {
            val name = it.loadLabel(context.packageManager).toString().lowercase(Locale.getDefault())
            name.contains("radio") || name.contains("fm ")
        }.sortedBy { it.loadLabel(context.packageManager).toString().lowercase(Locale.getDefault()) }

        if (radios.isNotEmpty()) {
            val labels = radios.map { it.loadLabel(context.packageManager).toString() }.toTypedArray()
            if (radios.size == 1) {
                val launch = context.packageManager.getLaunchIntentForPackage(radios[0].activityInfo.packageName)
                if (launch != null) context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } else {
                AlertDialog.Builder(context).setTitle("Odaberi radio aplikaciju").setItems(labels) { _, which ->
                    val launch = context.packageManager.getLaunchIntentForPackage(radios[which].activityInfo.packageName)
                    if (launch != null) context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }.setNegativeButton("Odustani", null).show()
            }
            return
        }

        val musicIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC)
        if (context.packageManager.queryIntentActivities(musicIntent, 0).isNotEmpty()) {
            try { context.startActivity(Intent.createChooser(musicIntent, "Otvori muzički plejer").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            catch (_: Exception) { notice = "Nije pronađena radio aplikacija"; invalidate() }
        } else {
            notice = "Nije pronađena radio aplikacija"
            invalidate()
        }
    }

    private fun openAllApps() {
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = context.packageManager.queryIntentActivities(query, 0).sortedBy { it.loadLabel(context.packageManager).toString().lowercase() }
        if (apps.isEmpty()) { notice = "Nema pronađenih aplikacija"; invalidate(); return }
        val labels = apps.map { it.loadLabel(context.packageManager).toString() }.toTypedArray()
        AlertDialog.Builder(context).setTitle("Aplikacije").setItems(labels) { _, which ->
            try { val launch = context.packageManager.getLaunchIntentForPackage(apps[which].activityInfo.packageName); launch?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); if (launch != null) context.startActivity(launch) }
            catch (_: Exception) { notice = "Aplikacija se ne može pokrenuti"; invalidate() }
        }.setNegativeButton("Zatvori", null).show()
    }

    private fun handleShortcut(slot: Int, forceChoose: Boolean = false) {
        val pkg = slotPackage(slot)
        if (!forceChoose && pkg != null) {
            val launch = context.packageManager.getLaunchIntentForPackage(pkg)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launch)
                return
            }
        }
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = context.packageManager.queryIntentActivities(query, 0)
            .sortedBy { it.loadLabel(context.packageManager).toString().lowercase() }
        if (apps.isEmpty()) {
            notice = "Nema pronađenih aplikacija"
            invalidate()
            return
        }
        val labels = apps.map { it.loadLabel(context.packageManager).toString() }.toTypedArray()
        AlertDialog.Builder(context)
            .setTitle("Odaberi aplikaciju")
            .setItems(labels) { _, which ->
                val chosen = apps[which].activityInfo.packageName
                appPrefs.edit().putString("slot_$slot", chosen).apply()
                notice = "Dodano: " + labels[which]
                invalidate()
            }
            .setNegativeButton("Odustani", null)
            .show()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val scale = min(width / 1280f, height / 720f)
        val x = (e.x - (width - 1280f * scale) / 2f) / scale
        val y = (e.y - (height - 720f * scale) / 2f) / scale
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = x
                touchDownY = y
                listSwipeHandled = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (touchDownX in 875f..1260f && touchDownY in 80f..430f && x in 875f..1260f && abs(y - touchDownY) > 28f) {
                    val step = if (y < touchDownY) 1 else -1
                    playlistOffset = (playlistOffset + step).coerceIn(0, (musicTracks.size - 7).coerceAtLeast(0))
                    touchDownY = y
                    listSwipeHandled = true
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (listSwipeHandled) return true
                if (y in 8f..70f && x in 875f..935f) { onReloadMusic(); return true }
                if (y in 55f..150f && x in 440f..535f) {
                    try { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    catch (_: Exception) { notice = "Wi-Fi postavke nisu dostupne"; invalidate() }
                    return true
                }
                if (y in 55f..150f && x in 765f..875f) {
                    try { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    catch (_: Exception) { notice = "Bluetooth postavke nisu dostupne"; invalidate() }
                    return true
                }
                if (y in 500f..595f) {
                    when { x in 455f..570f -> onPrevious(); x in 585f..695f -> onPlayPause(); x in 710f..835f -> onNext() }
                    return true
                }
                if (y in 475f..545f && x in 875f..1260f) {
                    if (x < 1065f) { shuffleEnabled = !shuffleEnabled; onShuffle(); invalidate() } else onSort()
                    return true
                }
                if (y in 80f..430f && x in 875f..1260f) {
                    val index = playlistOffset + ((y - 85f) / 47f).toInt()
                    musicTracks.getOrNull(index)?.let { onTrackSelected(it) }
                    return true
                }
                if (y in 612f..700f) {
                    val index = ((x - 26f) / (1228f / 5f)).toInt().coerceIn(0, 4)
                    when (index) {
                        2, 3 -> handleShortcut(index - 2, e.eventTime - e.downTime >= 600L)
                        0 -> { onMapClick(); notice = "Mapa – prikaz Sarajeva" }
                        1 -> openRadio()
                        4 -> openAllApps()
                    }
                    invalidate()
                }
                return true
            }
        }
        return true
    }
}
