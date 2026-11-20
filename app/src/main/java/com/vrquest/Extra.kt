package com.vrquest

import android.content.ContentUris
import android.content.Context
import android.graphics.*
import android.media.MediaPlayer
import android.net.Uri
import android.provider.MediaStore
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.*
import kotlin.math.*
import kotlin.random.Random

// ---------- store: curated web apps and games, each opens in its own browser window ----------
class StoreApp(ctx: Context, host: VrHost) : VrApp(ctx, host) {
    override val id = "store"; override val title = L.t("Store", "Magazin"); override val icon = "apps"
    override val pxH = 760
    override fun build(): View {
        val items = listOf(
            Triple("▶  YouTube", L.t("Videos", "Filme"), "https://www.youtube.com/"),
            Triple("🎮  CrazyGames", L.t("Free browser games", "Jocuri gratis"), "https://www.crazygames.com/"),
            Triple("🕹  Poki", L.t("Free browser games", "Jocuri gratis"), "https://poki.com/"),
            Triple("♟  Lichess", L.t("Chess, free", "Șah gratis"), "https://lichess.org/"),
            Triple("🔢  2048", L.t("Puzzle", "Puzzle"), "https://play2048.co/"),
            Triple("🌌  Solar System", "NASA Eyes", "https://eyes.nasa.gov/apps/solar-system/"),
            Triple("🌍  Google Earth", L.t("Explore the planet", "Explorează planeta"), "https://earth.google.com/web/"),
            Triple("🏞  AirPano", L.t("360° panoramas", "Panorame 360°"), "https://www.airpano.com/"),
            Triple("📺  Twitch", "Live", "https://www.twitch.tv/"),
            Triple("📖  Wikipedia", L.t("Encyclopedia", "Enciclopedie"), "https://www.wikipedia.org/"),
            Triple("🎵  SoundCloud", L.t("Music", "Muzică"), "https://soundcloud.com/"),
            Triple("🗺  Maps", L.t("Maps", "Hărți"), "https://www.google.com/maps")
        )
        val grid = GridLayout(ctx).apply { columnCount = 3; setPadding(20, 20, 20, 20) }
        for ((name, sub, url) in items) {
            val c = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL; setPadding(28, 16, 28, 16); background = rounded(Theme.card, 26f); setOnClickListener { Sfx.click(); host.openUrl(url) } }
            c.addView(tv(ctx, name, 34f, Theme.text, true)); c.addView(tv(ctx, sub, 24f, Theme.sub))
            grid.addView(c, GridLayout.LayoutParams().apply { width = 395; height = 150; setMargins(8, 8, 8, 8) })
        }
        return ScrollView(ctx).apply { setBackgroundColor(Theme.bg); addView(grid) }
    }
}

// ---------- local video player ----------
class VideoApp(ctx: Context, host: VrHost) : VrApp(ctx, host) {
    override val id = "video"; override val title = L.t("Videos", "Video"); override val icon = "play"
    override val pxH = 720
    private val root = FrameLayout(ctx)
    private val items = ArrayList<Pair<Uri, String>>()
    private var mp: MediaPlayer? = null

    override fun build(): View {
        root.setBackgroundColor(Theme.bg)
        val msg = tv(ctx, L.t("Loading…", "Se încarcă…"), 34f, Theme.sub).apply { gravity = Gravity.CENTER }
        root.addView(msg, FrameLayout.LayoutParams(-1, -1))
        root.addView(button(ctx, "Browse files…", 28f, Theme.accent, Color.BLACK) { host.pickFile(arrayOf("video/*")) { u -> if (u != null) play(u) } }, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply { setMargins(0, 0, 24, 24) })
        host.requestMedia { ok ->
            if (!ok) { msg.text = L.t("Allow media access in Android settings, then reopen.", "Permite accesul la media din setări, apoi redeschide."); return@requestMedia }
            Thread {
                try {
                    val base = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    ctx.contentResolver.query(base, arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME), null, null, "date_added DESC")?.use { c ->
                        var n = 0; while (c.moveToNext() && n < 300) { items.add(ContentUris.withAppendedId(base, c.getLong(0)) to (c.getString(1) ?: "video")); n++ }
                    }
                } catch (_: Exception) {}
                ui { if (items.isEmpty()) msg.text = L.t("No videos found.", "Nu s-au găsit video-uri.") else showList() }
            }.start()
        }
        return root
    }

    private fun showList() {
        release(); root.removeAllViews()
        root.setBackgroundColor(Theme.bg)
        val lv = ListView(ctx)
        lv.adapter = object : BaseAdapter() {
            override fun getCount() = items.size
            override fun getItem(i: Int) = items[i]
            override fun getItemId(i: Int) = i.toLong()
            override fun getView(i: Int, cv: View?, p: ViewGroup?): View = (cv as? TextView ?: tv(ctx, "", 32f).apply { setPadding(30, 26, 30, 26) }).also { it.text = "▶  " + items[i].second }
        }
        lv.setOnItemClickListener { _, _, i, _ -> play(items[i].first) }
        root.addView(lv, FrameLayout.LayoutParams(-1, -1))
        root.addView(button(ctx, "Browse files…", 28f, Theme.accent, Color.BLACK) { host.pickFile(arrayOf("video/*")) { u -> if (u != null) play(u) } }, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply { setMargins(0, 0, 24, 24) })
    }

    private fun play(u: Uri) {
        release(); root.removeAllViews()
        root.setBackgroundColor(Color.BLACK)
        val tx = TextureView(ctx)
        val holder = FrameLayout(ctx)
        holder.addView(tx, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        root.addView(holder, FrameLayout.LayoutParams(-1, -1))
        val ctrl = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(0xAA000000.toInt()); setPadding(16, 6, 16, 6) }
        val seek = SeekBar(ctx); seek.max = 1000
        var user = false
        val pp = button(ctx, "⏸", 34f) { }
        pp.setOnClickListener { mp?.let { if (it.isPlaying) { it.pause(); pp.text = "▶" } else { it.start(); pp.text = "⏸" } } }
        ctrl.addView(button(ctx, "◀") { showList() }, LinearLayout.LayoutParams(-2, -2).apply { setMargins(6, 4, 6, 4) })
        ctrl.addView(button(ctx, "-10") { mp?.let { it.seekTo(max(0, it.currentPosition - 10000)) } }, LinearLayout.LayoutParams(-2, -2).apply { setMargins(6, 4, 6, 4) })
        ctrl.addView(pp, LinearLayout.LayoutParams(-2, -2).apply { setMargins(6, 4, 6, 4) })
        ctrl.addView(button(ctx, "+10") { mp?.let { it.seekTo(min(it.duration, it.currentPosition + 10000)) } }, LinearLayout.LayoutParams(-2, -2).apply { setMargins(6, 4, 6, 4) })
        ctrl.addView(seek, LinearLayout.LayoutParams(0, 60, 1f))
        root.addView(ctrl, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) { if (fromUser) mp?.let { it.seekTo((it.duration * p / 1000L).toInt()) } }
            override fun onStartTrackingTouch(s: SeekBar) { user = true }
            override fun onStopTrackingTouch(s: SeekBar) { user = false }
        })
        tx.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: android.graphics.SurfaceTexture, w: Int, h: Int) {
                try {
                    val m = MediaPlayer(); mp = m
                    m.setDataSource(ctx, u); m.setSurface(Surface(st)); m.setVolume(Prefs.volume, Prefs.volume)
                    m.setOnVideoSizeChangedListener { _, vw, vh ->
                        if (vw > 0 && vh > 0) {
                            val ar = vw.toFloat() / vh; val W = root.width.toFloat(); val H = root.height.toFloat()
                            val lw = if (W / H > ar) (H * ar).toInt() else W.toInt(); val lh = if (W / H > ar) H.toInt() else (W / ar).toInt()
                            tx.layoutParams = FrameLayout.LayoutParams(lw, lh, Gravity.CENTER)
                        }
                    }
                    m.setOnCompletionListener { pp.text = "▶" }
                    m.setOnPreparedListener { it.start() }
                    m.prepareAsync()
                    seek.post(object : Runnable { override fun run() { mp?.let { if (!user && it.duration > 0) seek.progress = (it.currentPosition * 1000L / it.duration).toInt() }; if (mp != null) seek.postDelayed(this, 500) } })
                } catch (e: Exception) { host.toast("Video: ${e.message}") }
            }
            override fun onSurfaceTextureSizeChanged(st: android.graphics.SurfaceTexture, w: Int, h: Int) {}
            override fun onSurfaceTextureDestroyed(st: android.graphics.SurfaceTexture): Boolean = true
            override fun onSurfaceTextureUpdated(st: android.graphics.SurfaceTexture) {}
        }
    }
    private fun release() { try { mp?.release() } catch (_: Exception) {}; mp = null }
    override fun onClose() = release()
}
