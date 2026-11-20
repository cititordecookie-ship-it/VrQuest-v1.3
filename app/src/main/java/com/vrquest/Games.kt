package com.vrquest

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.*
import kotlin.random.Random

/** Base for Canvas games. The panel painter calls draw() every frame, so onDraw advances the simulation. */
abstract class GameView(ctx: Context) : View(ctx) {
    protected val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var last = 0L
    protected var ptrs = HashMap<Int, PointF>()
    protected var hover: PointF? = null
    protected var lastPts = HashMap<Int, PointF>()

    abstract fun step(dt: Float)
    abstract fun render(c: Canvas)
    open fun pointerMoved(id: Int, from: PointF?, to: PointF) {}
    open fun pointerDown(id: Int, x: Float, y: Float) {}
    open fun pointerUp(id: Int) {}

    override fun onDraw(c: Canvas) {
        val now = System.nanoTime(); val dt = if (last == 0L) 0.016f else min(0.05f, (now - last) / 1e9f); last = now
        step(dt); render(c)
    }
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val a = e.actionMasked
        for (i in 0 until e.pointerCount) {
            val id = e.getPointerId(i); val to = PointF(e.getX(i), e.getY(i))
            if (a == MotionEvent.ACTION_DOWN || (a == MotionEvent.ACTION_POINTER_DOWN && i == e.actionIndex)) { ptrs[id] = to; pointerDown(id, to.x, to.y) }
            else if (a == MotionEvent.ACTION_MOVE) { pointerMoved(id, ptrs[id], to); ptrs[id] = to }
        }
        if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) { for (k in ptrs.keys.toList()) pointerUp(k); ptrs.clear() }
        if (a == MotionEvent.ACTION_POINTER_UP) { val pid = e.getPointerId(e.actionIndex); pointerUp(pid); ptrs.remove(pid) }
        return true
    }
    override fun onHoverEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_HOVER_EXIT) { hover = null; return true }
        val to = PointF(e.x, e.y); pointerMoved(-1, hover, to); hover = to
        return true
    }
    protected fun text(c: Canvas, t: String, x: Float, y: Float, size: Float, color: Int = Color.WHITE, align: Paint.Align = Paint.Align.LEFT) {
        p.style = Paint.Style.FILL; p.color = color; p.textSize = size; p.textAlign = align; p.typeface = Typeface.DEFAULT_BOLD; c.drawText(t, x, y, p); p.textAlign = Paint.Align.LEFT
    }
}

abstract class GameApp(ctx: Context, host: VrHost) : VrApp(ctx, host) {
    override val icon get() = "games"
    override val singleton get() = true
}

// ================= PIANO =================
class PianoApp(ctx: Context, host: VrHost) : GameApp(ctx, host) {
    override val id = "piano"; override val title = L.t("Piano", "Pian"); override val pxH = 520
    override fun build(): View = PianoView(ctx)

    private class PianoView(ctx: Context) : GameView(ctx) {
        private val whiteSemis = intArrayOf(0, 2, 4, 5, 7, 9, 11)
        private val blackAfter = booleanArrayOf(true, true, false, true, true, true, false) // black key after white i%7
        private val nWhite = 14
        private val lit = HashMap<Int, Float>() // key idx -> glow
        private val down = HashMap<Int, Int>() // pointer -> key
        private val song = intArrayOf(0, 0, 4, 4, 5, 5, 4, 3, 3, 2, 2, 1, 1, 0) // twinkle (white key indexes)
        private var hintOn = false; private var songPos = 0
        private fun freq(semi: Int) = 261.63f * 2.0.pow(semi / 12.0).toFloat()
        private fun whiteSemi(i: Int) = (i / 7) * 12 + whiteSemis[i % 7]

        private fun keyAt(x: Float, y: Float): Int { // returns semitone-coded id: white = i, black = 100+i
            val kw = width / nWhite.toFloat(); val top = 90f
            if (y < top) { if (x in 20f..320f && y in 12f..80f) return 999; return -1 }
            val bh = (height - top) * 0.6f
            if (y < top + bh) for (i in 0 until nWhite - 1) if (blackAfter[i % 7]) { val cx = (i + 1) * kw; if (abs(x - cx) < kw * 0.3f) return 100 + i }
            return min(nWhite - 1, (x / kw).toInt())
        }
        override fun pointerDown(id: Int, x: Float, y: Float) { press(id, x, y) }
        private fun press(id: Int, x: Float, y: Float) {
            val k = keyAt(x, y); if (k < 0) return
            if (k == 999) { hintOn = !hintOn; songPos = 0; return }
            down[id] = k
            val semi = if (k >= 100) whiteSemi(k - 100) + 1 else whiteSemi(k)
            Sfx.note(freq(semi)); lit[k] = 1f
            if (hintOn && k < 100 && k == song[songPos % song.size]) songPos++
        }
        override fun pointerMoved(id: Int, from: PointF?, to: PointF) {
            if (id < 0) return
            val k = keyAt(to.x, to.y); if (k != down[id] && k >= 0 && k != 999) press(id, to.x, to.y)
        }
        override fun step(dt: Float) { for (k in lit.keys.toList()) { val v = lit[k]!! - dt * 2.2f; if (v <= 0f) lit.remove(k) else lit[k] = v }; if (ptrs.isEmpty()) down.clear() }
        override fun render(c: Canvas) {
            c.drawColor(0xFF10131A.toInt())
            p.style = Paint.Style.FILL; p.color = if (hintOn) Theme.accent else 0xFF2A2E3A.toInt(); c.drawRoundRect(20f, 12f, 320f, 80f, 30f, 30f, p)
            text(c, if (hintOn) L.t("Hints: ON", "Indicii: ON") else L.t("Song hints", "Indicii melodie"), 170f, 58f, 30f, if (hintOn) Color.BLACK else Color.WHITE, Paint.Align.CENTER)
            text(c, L.t("Use both hands: pinch or poke the keys", "Folosește ambele mâini: ciupește sau apasă tastele"), width - 20f, 58f, 28f, 0xFF9AA3B5.toInt(), Paint.Align.RIGHT)
            val kw = width / nWhite.toFloat(); val top = 90f; val bh = (height - top) * 0.6f
            val next = if (hintOn) song[songPos % song.size] else -1
            for (i in 0 until nWhite) {
                val g = lit[i] ?: 0f
                p.style = Paint.Style.FILL; p.color = Color.rgb(250 - (g * 120).toInt(), 250 - (g * 40).toInt(), 250); if (i == next) p.color = 0xFFA5F0B0.toInt()
                c.drawRoundRect(i * kw + 2, top, (i + 1) * kw - 2, height - 4f, 14f, 14f, p)
            }
            for (i in 0 until nWhite - 1) if (blackAfter[i % 7]) {
                val g = lit[100 + i] ?: 0f; val cx = (i + 1) * kw
                p.color = Color.rgb(20 + (g * 40).toInt(), 20 + (g * 150).toInt(), 24 + (g * 200).toInt())
                c.drawRoundRect(cx - kw * 0.3f, top, cx + kw * 0.3f, top + bh, 10f, 10f, p)
            }
        }
    }
}

// ================= PAINTING VR =================
class PaintApp(ctx: Context, host: VrHost) : GameApp(ctx, host) {
    override val id = "paint"; override val title = "Painting VR"; override val pxH = 760; override val widthM get() = 2.2f
    override fun build(): View = PaintView(ctx, host)

    private class PaintView(ctx: Context, val host: VrHost) : GameView(ctx) {
        private val colors = intArrayOf(0xFFFFFFFF.toInt(), 0xFF111111.toInt(), 0xFFFF3B30.toInt(), 0xFFFF9500.toInt(), 0xFFFFD60A.toInt(), 0xFF34C759.toInt(),
            0xFF00C7BE.toInt(), 0xFF30B0FF.toInt(), 0xFF0A4DFF.toInt(), 0xFF8E44FF.toInt(), 0xFFFF4FD8.toInt(), 0xFF8B5A2B.toInt(), 0xFF9AA3B5.toInt(), 0xFFA5F0B0.toInt(), 0xFFFFB3C7.toInt())
        private val tools = arrayOf("Brush", "Spray", "Marker", "Eraser")
        private val sizes = floatArrayOf(6f, 16f, 36f)
        private val sizeNames = arrayOf("S", "M", "L")
        private var tool = 0; private var colorIdx = 7; private var rainbow = false; private var sizeIdx = 1
        private var bmp: Bitmap? = null; private var cv: Canvas? = null
        private val undo = ArrayList<Bitmap>()
        private val last = HashMap<Int, PointF>()
        private val cursor = HashMap<Int, PointF>()
        private var hue = 0f
        private val barW = 150f
        private val topH = 96f
        private val bg = 0xFF1B1E27.toInt()
        private val paper = Color.WHITE

        private fun ensure() {
            val w = width - barW.toInt(); val h = height - topH.toInt()
            if (w <= 0 || h <= 0) return
            if (bmp == null || bmp!!.width != w || bmp!!.height != h) {
                val nb = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); val nc = Canvas(nb); nc.drawColor(paper)
                bmp?.let { nc.drawBitmap(it, 0f, 0f, null) }
                bmp = nb; cv = nc
            }
        }
        private fun curColor(): Int = if (rainbow) Color.HSVToColor(floatArrayOf(hue, 0.9f, 1f)) else colors[colorIdx]
        private fun pushUndo() { bmp?.let { undo.add(it.copy(Bitmap.Config.ARGB_8888, false)); if (undo.size > 12) undo.removeAt(0).recycle() } }

        // layout hit tests: returns true if the event hit UI
        private fun ui(x: Float, y: Float): Boolean {
            if (y < topH) {
                for (i in tools.indices) if (x in (16f + i * 150f)..(156f + i * 150f) && y in 12f..84f) { tool = i; Sfx.click(); return true }
                for (i in sizes.indices) if (x in (640f + i * 80f)..(710f + i * 80f) && y in 12f..84f) { sizeIdx = i; Sfx.click(); return true }
                if (x in 900f..1020f && y in 12f..84f) { undo.lastOrNull()?.let { u -> cv?.drawBitmap(u, 0f, 0f, null); undo.removeAt(undo.size - 1).recycle() }; Sfx.click(); return true }
                if (x in 1030f..1150f && y in 12f..84f) { pushUndo(); cv?.drawColor(paper); Sfx.click(); return true }
                if (x in 1160f..1280f && y in 12f..84f) { save(); return true }
                return true
            }
            if (x < barW) {
                val rows = colors.size + 1; val ch = (height - topH) / rows
                val i = ((y - topH) / ch).toInt().coerceIn(0, rows - 1)
                if (i == colors.size) rainbow = true else { rainbow = false; colorIdx = i }
                Sfx.click(); return true
            }
            return false
        }
        private fun save() {
            val b = bmp ?: return
            Thread {
                try {
                    val v = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "VrQuest_paint_${System.currentTimeMillis()}.png")
                        put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                        if (android.os.Build.VERSION.SDK_INT >= 29) put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/VrQuest")
                    }
                    val u = context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v)
                    if (u != null) { context.contentResolver.openOutputStream(u)?.use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }; host.toast("Saved to Pictures/VrQuest") }
                    else host.toast("Could not save")
                } catch (e: Exception) { host.toast("Save failed") }
            }.start()
        }

        private fun stroke(a: PointF, b: PointF) {
            val c = cv ?: return
            val ax = a.x - barW; val ay = a.y - topH; val bx = b.x - barW; val by = b.y - topH
            val sz = sizes[sizeIdx]
            when (tool) {
                1 -> { // spray
                    p.style = Paint.Style.FILL; p.color = curColor(); p.alpha = 150
                    val n = (sz * 1.2f).toInt() + 6; val rad = sz * 1.8f
                    for (i in 0 until n) {
                        val ang = Random.nextFloat() * 6.2832f; val r = rad * sqrt(Random.nextFloat())
                        c.drawCircle(bx + cos(ang) * r, by + sin(ang) * r, 1.6f, p)
                    }
                    p.alpha = 255
                }
                else -> {
                    p.style = Paint.Style.STROKE; p.strokeCap = if (tool == 2) Paint.Cap.SQUARE else Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
                    p.strokeWidth = when (tool) { 2 -> sz * 1.6f; 3 -> sz * 2.4f; else -> sz }
                    p.color = if (tool == 3) paper else curColor()
                    p.alpha = if (tool == 2) 140 else 255
                    c.drawLine(ax, ay, bx, by, p); p.alpha = 255
                }
            }
            if (rainbow) hue = (hue + 3f) % 360f
        }

        override fun pointerDown(id: Int, x: Float, y: Float) {
            ensure()
            if (ui(x, y)) { last.remove(id); return }
            pushUndo()
            val pt = PointF(x, y); last[id] = pt; stroke(pt, pt)
        }
        override fun pointerMoved(id: Int, from: PointF?, to: PointF) {
            cursor[id] = PointF(to.x, to.y)
            val l = last[id] ?: return
            if (id >= 0 && ptrs.containsKey(id)) { stroke(l, to); last[id] = PointF(to.x, to.y) }
        }
        override fun pointerUp(id: Int) { last.remove(id) }
        override fun onHoverEvent(e: MotionEvent): Boolean {
            if (e.actionMasked == MotionEvent.ACTION_HOVER_EXIT) cursor.remove(-1) else cursor[-1] = PointF(e.x, e.y)
            return super.onHoverEvent(e)
        }
        override fun step(dt: Float) { ensure(); if (ptrs.isEmpty()) last.clear() }

        override fun render(c: Canvas) {
            c.drawColor(bg)
            bmp?.let { c.drawBitmap(it, barW, topH, null) }
            // top bar
            p.style = Paint.Style.FILL; p.color = 0xFF232733.toInt(); c.drawRect(0f, 0f, width.toFloat(), topH, p)
            for (i in tools.indices) {
                p.color = if (i == tool) Theme.accent else 0xFF2E3342.toInt()
                c.drawRoundRect(16f + i * 150f, 12f, 156f + i * 150f, 84f, 24f, 24f, p)
                text(c, tools[i], 86f + i * 150f, 58f, 28f, if (i == tool) Color.BLACK else Color.WHITE, Paint.Align.CENTER)
            }
            text(c, "Size", 560f, 58f, 26f, 0xFF9AA3B5.toInt(), Paint.Align.CENTER)
            for (i in sizes.indices) {
                p.color = if (i == sizeIdx) Theme.accent else 0xFF2E3342.toInt()
                c.drawRoundRect(640f + i * 80f, 12f, 710f + i * 80f, 84f, 24f, 24f, p)
                text(c, sizeNames[i], 675f + i * 80f, 58f, 28f, if (i == sizeIdx) Color.BLACK else Color.WHITE, Paint.Align.CENTER)
            }
            val btn = arrayOf("Undo", "Clear", "Save")
            for (i in btn.indices) {
                p.color = 0xFF2E3342.toInt(); c.drawRoundRect(900f + i * 130f, 12f, 1020f + i * 130f, 84f, 24f, 24f, p)
                text(c, btn[i], 960f + i * 130f, 58f, 28f, Color.WHITE, Paint.Align.CENTER)
            }
            // color bar
            p.color = 0xFF232733.toInt(); c.drawRect(0f, topH, barW, height.toFloat(), p)
            val rows = colors.size + 1; val ch = (height - topH) / rows
            for (i in 0 until rows) {
                val t = topH + i * ch
                if (i == colors.size) {
                    val sh = SweepGradient(barW / 2, t + ch / 2, intArrayOf(Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED), null)
                    p.shader = sh; c.drawRoundRect(14f, t + 4f, barW - 14f, t + ch - 4f, 14f, 14f, p); p.shader = null
                } else { p.color = colors[i]; c.drawRoundRect(14f, t + 4f, barW - 14f, t + ch - 4f, 14f, 14f, p) }
                val sel = if (i == colors.size) rainbow else (!rainbow && i == colorIdx)
                if (sel) { p.style = Paint.Style.STROKE; p.strokeWidth = 6f; p.color = Theme.accent; c.drawRoundRect(10f, t + 2f, barW - 10f, t + ch - 2f, 16f, 16f, p); p.style = Paint.Style.FILL }
            }
            // brush preview cursors
            for ((_, pt) in cursor) {
                if (pt.x < barW || pt.y < topH) continue
                p.style = Paint.Style.STROKE; p.strokeWidth = 3f; p.color = Color.argb(220, 0, 0, 0)
                val r = if (tool == 1) sizes[sizeIdx] * 1.8f else (if (tool == 3) sizes[sizeIdx] * 1.2f else sizes[sizeIdx] / 2f) + 1f
                c.drawCircle(pt.x, pt.y, r, p); p.color = Color.WHITE; p.strokeWidth = 1.5f; c.drawCircle(pt.x, pt.y, r + 2f, p)
                p.style = Paint.Style.FILL
            }
        }
    }
}
