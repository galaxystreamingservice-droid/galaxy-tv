package com.galaxymedia.tvsetup

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

/**
 * TEMPORARY: plays every holiday stage back to back with a label, to review the scenes and to record for YouTube.
 * Right/Left skips, Back exits. Remove the Settings row when no longer needed.
 */
class SeasonPreviewActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var holder: FrameLayout
    private lateinit var label: TextView
    private lateinit var count: TextView
    private val stages = StoryStages.all()
    private var index = 0
    private val secondsEach = 9_000L

    private val advance = Runnable { show(index + 1) }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        holder = FrameLayout(this)
        root.addView(holder, FrameLayout.LayoutParams(-1, -1))
        label = TextView(this).apply {
            textSize = 22f; setTextColor(Color.parseColor("#1E1208")); setTypeface(null, Typeface.BOLD)
            setPadding(dp(22), dp(8), dp(22), dp(8))
            background = GradientDrawable().apply { cornerRadius = dp(22).toFloat(); setColor(Color.parseColor("#E8A03C")) }
        }
        root.addView(label, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(36) })
        count = TextView(this).apply { textSize = 14f; setTextColor(Color.parseColor("#9FB8C8")) }
        root.addView(count, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { topMargin = dp(20); rightMargin = dp(28) })
        setContentView(root)
        show(0)
    }

    private fun show(i: Int) {
        handler.removeCallbacks(advance)
        index = ((i % stages.size) + stages.size) % stages.size
        val st = stages[index]
        holder.removeAllViews()
        val old = StoryStages.oldScene(st.id)
        val v = if (old != null) SeasonView(this, old, "normal")
                else if (StoryStages.exists(this, st.id)) StoryVideoView(this, st.id, true)
                else SeasonView(this, if (st.id.startsWith("tg")) "thanksgiving" else "christmas", "normal")
        holder.addView(v, FrameLayout.LayoutParams(-1, -1))
        label.text = st.label
        count.text = "${index + 1} of ${stages.size}   ◀ ▶ skip   Back to exit"
        handler.postDelayed(advance, secondsEach)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> { show(index + 1); return true }
            KeyEvent.KEYCODE_DPAD_LEFT -> { show(index - 1); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(advance)
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(advance)
        handler.postDelayed(advance, secondsEach)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
