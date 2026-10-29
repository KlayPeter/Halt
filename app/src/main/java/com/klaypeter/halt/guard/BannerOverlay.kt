package com.klaypeter.halt.guard

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.klaypeter.halt.util.formatClock

/**
 * 顶部悬浮倒计时胶囊，效果类似微信消息横幅。
 *
 * 用系统悬浮窗实现，所以盖得住全屏视频和任何 App。
 * 占位尽量小：一行文字 + 3dp 进度条，可以左右/上下划走。
 */
class BannerOverlay(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val density = context.resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).toInt()

    private var root: View? = null
    private var titleView: TextView? = null
    private var clockView: TextView? = null
    private var fillView: View? = null
    private var trackWidthPx = 0

    /** 用户手动划掉之后，本次使用期间就别再自动弹了。 */
    var dismissed: Boolean = false
        private set

    val isShowing: Boolean get() = root != null

    fun show(appLabel: String, onAction: () -> Unit) {
        if (!Settings.canDrawOverlays(context)) return
        hide()

        val cardWidth = (context.resources.displayMetrics.widthPixels * 0.94f).toInt()
        val innerPadding = dp(18)
        trackWidthPx = cardWidth - innerPadding * 2

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(innerPadding, dp(10), innerPadding, dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(Color.parseColor("#F21B1B21"))
            }
        }

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        titleView = TextView(context).apply {
            text = "$appLabel · 剩余"
            setTextColor(Color.parseColor("#B3FFFFFF"))
            textSize = 12f
        }
        clockView = TextView(context).apply {
            text = "0:00"
            setTextColor(Color.WHITE)
            textSize = 19f
            setPadding(dp(8), 0, 0, 0)
        }
        row.addView(titleView)
        row.addView(clockView)
        row.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))

        val close = TextView(context).apply {
            text = "✕"
            setTextColor(Color.parseColor("#8AFFFFFF"))
            textSize = 13f
            setPadding(dp(12), dp(4), dp(2), dp(4))
            setOnClickListener { dismissByUser() }
        }
        row.addView(close)
        card.addView(row)

        // 3dp 的进度条：底色轨道 + 按比例撑开的填充
        val track = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(2).toFloat()
                setColor(Color.parseColor("#33FFFFFF"))
            }
        }
        fillView = View(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(2).toFloat()
                setColor(Color.parseColor("#B3A0FF"))
            }
        }
        track.addView(fillView, FrameLayout.LayoutParams(0, FrameLayout.LayoutParams.MATCH_PARENT))
        card.addView(
            track,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(3)
            ).apply { topMargin = dp(8) }
        )

        // 划走：任意方向拖过 48dp 就收起
        val threshold = dp(48)
        var downX = 0f
        var downY = 0f
        card.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                }
                MotionEvent.ACTION_UP -> {
                    val dx = kotlin.math.abs(event.rawX - downX)
                    val dy = kotlin.math.abs(event.rawY - downY)
                    if (dx > threshold || dy > threshold) dismissByUser()
                }
            }
            false // 返回 false，让 ✕ 的点击照常工作
        }

        val params = WindowManager.LayoutParams(
            cardWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(44)
        }

        runCatching { windowManager.addView(card, params) }.onSuccess { root = card }

        card.setOnClickListener { onAction() }
    }

    /** 每秒调用一次，刷新倒计时和进度条。 */
    fun update(remainingMs: Long, fraction: Float) {
        val view = root ?: return
        if (!view.isAttachedToWindow) return
        clockView?.text = formatClock(remainingMs)
        fillView?.let { fill ->
            val width = (trackWidthPx * fraction.coerceIn(0f, 1f)).toInt()
            (fill.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
                if (lp.width != width) {
                    lp.width = width
                    fill.layoutParams = lp
                }
            }
        }
    }

    fun setHeader(text: String) {
        titleView?.text = text
    }

    /** 用户主动关掉：本次会话不再弹。 */
    fun dismissByUser() {
        dismissed = true
        hide()
    }

    /** 新的一次使用开始，重置「已划掉」状态。 */
    fun resetForSession() {
        dismissed = false
        hide()
    }

    fun hide() {
        windowManager.let { wm -> root?.let { runCatching { wm.removeView(it) } } }
        root = null
        titleView = null
        clockView = null
        fillView = null
    }
}
