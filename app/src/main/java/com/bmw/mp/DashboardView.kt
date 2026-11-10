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

class DashboardView(context: Context) : View(context) {
    init { setLayerType(View.LAYER_TYPE_SOFTWARE, null) }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val orange = Color.rgb(255, 137, 0)
    private val gold = Color.rgb(255, 190, 25)
    private val white = Color.WHITE
    private var notice = ""
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
        label(c, if (wifiOn) "◔" else "×", 493f, 105f, 38f, if (wifiOn) gold else Color.GRAY, true, Paint.Align.CENTER)
        panel(c, 756f, 64f, 62f, 58f)
        label(c, "ᛒ", 787f, 105f, 38f, if (bluetoothOn) gold else Color.GRAY, true, Paint.Align.CENTER)
        label(c, if (wifiOn) "on" else "off", 493f, 139f, 12f, white, false, Paint.Align.CENTER)
        label(c, if (bluetoothOn) "on" else "off", 787f, 139f, 12f, white, false, Paint.Align.CENTER)
        postInvalidateDelayed(2_000L)

        panel(c, 12f, 8f, 400f, 102f)
        metric(c, 25f, 17f, "tdy", "12.4", "km", "╱")
        metric(c, 215f, 17f, "mnth", "386", "km", "▦")
        panel(c, 12f, 118f, 400f, 82f)
        metric(c, 25f, 123f, "avg/tdy", "42.3", "km/h", "◴")
        metric(c, 215f, 123f, "max/tdy", "118", "km/h", "◴")

        panel(c, 12f, 210f, 400f, 335f); drawMap(c)
        panel(c, 25f, 557f, 160f, 38f); label(c, "● GPS 8/14", 40f, 583f, 18f, white, true)

        // Speedometer
        val cx = 640f; val cy = 260f
        p.style = Paint.Style.STROKE
        p.color = Color.rgb(255,65,0); p.strokeWidth = 22f
        p.setShadowLayer(22f,0f,0f,Color.rgb(255,55,0))
        c.drawArc(RectF(455f,72f,825f,442f),135f,270f,false,p)
        p.clearShadowLayer()
        p.color = orange; p.strokeWidth = 8f
        c.drawArc(RectF(455f,72f,825f,442f),135f,270f,false,p)
        p.color = gold; p.strokeWidth = 3f
        for (i in 0..40) {
            val a = Math.toRadians(135.0 + i*6.75)
            val r1 = 166f; val r2 = if (i%4==0) 187f else 178f
            c.drawLine(cx+cos(a).toFloat()*r1,cy+sin(a).toFloat()*r1,
                cx+cos(a).toFloat()*r2,cy+sin(a).toFloat()*r2,p)
        }
        p.style = Paint.Style.FILL
        listOf("0","20","40","60","80","100","120","140","160","180","200").forEachIndexed { i,n ->
            val a = Math.toRadians(135.0+i*27.0)
            label(c,n,cx+cos(a).toFloat()*145f,cy+sin(a).toFloat()*145f+8f,20f,white,false,Paint.Align.CENTER)
        }
        drawBmwRoundel(c,640f,260f,66f)
        label(c,"68",598f,392f,58f,orange,true,Paint.Align.RIGHT)
        label(c,"km/h",605f,392f,29f,white)
        label(c,"Evening Drive.mp3",640f,425f,21f,white,false,Paint.Align.CENTER)
        val bars = intArrayOf(20,28,35,43,31,24,40,52,35,47,57,43,30,48,38,25)
        bars.forEachIndexed { i,h -> p.color=if(i%3==0) gold else orange
            c.drawRect(460f+i*22f,500f-h,474f+i*22f,500f,p) }
        media(c,505f,548f,"◀|",false); media(c,640f,548f,"Ⅱ",true); media(c,775f,548f,"|▶",false)

        // car music
        panel(c,868f,8f,400f,537f)
        label(c,"↓",900f,48f,35f,gold,true,Paint.Align.CENTER)
        label(c,"car music",1080f,48f,26f,white,false,Paint.Align.CENTER)
        label(c,"2326",1225f,48f,18f,white,false,Paint.Align.RIGHT)
        p.color=Color.GREEN; c.drawCircle(1248f,22f,7f,p)
        val tracks=listOf("VID-20261110-WA0005.mp4","VID-20261110-WA0004.mp4","Evening Drive.mp3",
            "Retro Gaming.mp3","Autoput.mp3","Stari Hitovi.mp3","Ljetna Vožnja.mp3")
        tracks.forEachIndexed { i,t ->
            val y=88f+i*47f
            if(i==0){p.color=orange;p.style=Paint.Style.STROKE;p.strokeWidth=2f
                c.drawRoundRect(RectF(878f,y,1258f,y+43f),12f,12f,p);p.style=Paint.Style.FILL}
            label(c,if(i<2)"▶" else "♪",895f,y+29f,23f,gold,true)
            label(c,t,930f,y+28f,16f,white)
            p.color=Color.rgb(80,60,35); c.drawLine(882f,y+45f,1255f,y+45f,p)
        }
        panel(c,880f,465f,376f,66f)
        label(c,"⤨  Shuffle",910f,507f,21f,white)
        label(c,"☷  A-Z",1090f,507f,21f,white)

        // Bottom menu: two custom app slots
        panel(c,26f,612f,1228f,78f)
        val xs=floatArrayOf(130f,375f,620f,865f,1110f)
        val names=listOf("Mapa","Radio",slotLabel(0),slotLabel(1),"Aplikacije")
        val icons=listOf("●","▣",if(names[2]=="+")"+" else "▣",if(names[3]=="+")"+" else "▣","▦")
        names.forEachIndexed { i,n ->
            label(c,icons[i],xs[i]-60f,660f,28f,if(n=="+")gold else orange,true,Paint.Align.CENTER)
            label(c,n,xs[i]+8f,660f,if(n.length>11)15f else 21f,white)
            if(i<4){p.color=orange;p.strokeWidth=1f;c.drawLine(xs[i]+122f,627f,xs[i]+122f,675f,p)}
        }
        if(notice.isNotEmpty()) label(c,notice,640f,605f,15f,gold,false,Paint.Align.CENTER)
        c.restore()
    }

    private fun metric(c:Canvas,x:Float,y:Float,title:String,value:String,unit:String,icon:String){
        label(c,title,x+92f,y+20f,17f,white,false,Paint.Align.CENTER)
        label(c,icon,x+8f,y+61f,27f,gold,true)
        label(c,value,x+55f,y+61f,30f,orange,true)
        p.textSize=30f;p.typeface=Typeface.DEFAULT_BOLD
        label(c,unit,x+60f+p.measureText(value),y+61f,18f,white)
        p.color=orange;p.strokeWidth=1f;c.drawLine(x+185f,y+3f,x+185f,y+69f,p)
    }
    private fun drawMap(c:Canvas){
        p.color=Color.rgb(8,28,24);c.drawRect(14f,212f,410f,543f,p)
        for(i in 0..9){p.color=if(i%2==0)Color.rgb(25,66,53) else Color.rgb(17,48,48)
            p.strokeWidth=1f;c.drawLine(18f,230f+i*31f,407f,220f+i*32f,p)
            c.drawLine(24f+i*39f,214f,20f+i*40f,542f,p)}
        p.color=Color.rgb(0,150,220);p.strokeWidth=3f
        val river=Path().apply{moveTo(18f,480f);cubicTo(120f,400f,160f,460f,220f,360f);cubicTo(270f,300f,330f,340f,410f,245f)}
        c.drawPath(river,p)
        p.color=gold;p.strokeWidth=7f
        val route=Path().apply{moveTo(205f,215f);cubicTo(230f,280f,245f,320f,230f,380f);cubicTo(220f,435f,245f,485f,235f,545f)}
        c.drawPath(route,p)
        p.color=Color.RED;c.drawCircle(230f,390f,21f,p);p.color=white;c.drawCircle(230f,390f,14f,p)
        label(c,"▲",230f,398f,24f,Color.BLUE,true,Paint.Align.CENTER)
        label(c,"Sarajevo",295f,355f,23f,white);label(c,"60",62f,495f,25f,white,true)
        label(c,"M18",350f,525f,22f,white,true);label(c,"▣",215f,310f,25f,white);label(c,"▣",245f,480f,25f,white)
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
        p.color=Color.WHITE;c.drawArc(inner,180f,90f,true,p)
        p.color=Color.rgb(0,160,220);c.drawArc(inner,270f,90f,true,p)
        p.color=Color.WHITE;c.drawArc(inner,0f,90f,true,p)
        p.color=Color.rgb(0,160,220);c.drawArc(inner,90f,90f,true,p)
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
    private fun handleShortcut(slot: Int) {
        val pkg = slotPackage(slot)
        if (pkg != null) {
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

    override fun onTouchEvent(e:MotionEvent):Boolean{
        if(e.action==MotionEvent.ACTION_UP){
            val s=min(width/1280f,height/720f)
            val x=(e.x-(width-1280f*s)/2f)/s;val y=(e.y-(height-720f*s)/2f)/s
            if (y in 55f..150f && x in 440f..535f) {
                try { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                catch (_: Exception) { notice = "Wi-Fi postavke nisu dostupne" }
                return true
            }
            if (y in 55f..150f && x in 735f..835f) {
                try { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                catch (_: Exception) { notice = "Bluetooth postavke nisu dostupne" }
                return true
            }
            if(y in 612f..700f){
                val i=((x-26f)/(1228f/5f)).toInt().coerceIn(0,4)
                when(i) {
                    2,3 -> handleShortcut(i-2)
                    0 -> notice="Mapa – povezujemo u sljedećem koraku"
                    1 -> notice="Radio – povezujemo u sljedećem koraku"
                    4 -> notice="Aplikacije – povezujemo u sljedećem koraku"
                }
                invalidate()
            }
        }
        return true
    }
}
