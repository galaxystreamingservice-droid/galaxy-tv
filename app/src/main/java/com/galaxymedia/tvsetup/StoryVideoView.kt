package com.galaxymedia.tvsetup

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView

/**
 * One holiday stage: a short looping clip from assets/story, played by the stick's video chip.
 * The still picture shows first (and is all that shows when Screen effects are off).
 * The clip stops as soon as the home screen is hidden, so it never competes with the app being watched.
 */
class StoryVideoView(ctx: Context, private val stage: String, private val animate: Boolean) :
    FrameLayout(ctx), TextureView.SurfaceTextureListener {

    private val still = ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    private var tex: TextureView? = null
    private var surface: Surface? = null
    private var player: MediaPlayer? = null

    init {
        addView(still, FrameLayout.LayoutParams(-1, -1))
        try {
            context.applicationContext.assets.open("story/$stage.webp").use { still.setImageBitmap(BitmapFactory.decodeStream(it)) }
        } catch (e: Exception) { }
        if (animate) {
            tex = TextureView(ctx).also {
                it.surfaceTextureListener = this
                it.alpha = 0f
                addView(it, FrameLayout.LayoutParams(-1, -1))
            }
        }
    }

    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
        surface = Surface(st)
        start()
    }

    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}

    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
        stop()
        surface?.release()
        surface = null
        return true
    }

    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}

    private fun start() {
        val s = surface ?: return
        if (player != null || windowVisibility != View.VISIBLE) return
        try {
            val afd = context.applicationContext.assets.openFd("story/$stage.mp4")
            val mp = MediaPlayer()
            mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            mp.setSurface(s)
            mp.isLooping = true
            mp.setVolume(0f, 0f)
            mp.setOnPreparedListener {
                if (player === it) {
                    it.start()
                    tex?.animate()?.alpha(1f)?.setDuration(600)?.start()
                }
            }
            mp.setOnErrorListener { _, _, _ -> stop(); true }
            player = mp
            mp.prepareAsync()
        } catch (e: Exception) {
            stop()
        }
    }

    private fun stop() {
        val p = player
        player = null
        try { p?.release() } catch (e: Exception) { }
        tex?.animate()?.cancel()
        tex?.alpha = 0f
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == View.VISIBLE) start() else stop()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }
}
