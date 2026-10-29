package com.klaypeter.halt.guard

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 额度用完时盖在最上层的一整屏提示。
 * 盖住内容 → 用户点不进去；几秒后自动消失，避免一直挡着屏幕。
 */
class OverlayController(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var view: View? = null

    private val hideTask = Runnable { remove() }

    fun show(
        title: String,
        subtitle: String,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
        durationMs: Long = 4_000
    ) {
        if (!Settings.canDrawOverlays(context)) return
        remove()

        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val container = FrameLayout(context).apply {
            setBackgroundColor(Color.parseColor("#F2101014"))
        }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), 0, dp(28), 0)
        }
        column.addView(TextView(context).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 24f
            gravity = Gravity.CENTER
        })
        column.addView(TextView(context).apply {
            text = subtitle
            setTextColor(Color.parseColor("#B0FFFFFF"))
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, 0)
        })

        if (actionLabel != null && onAction != null) {
            column.addView(TextView(context).apply {
                text = actionLabel
                setTextColor(Color.parseColor("#141118"))
                textSize = 16f
                gravity = Gravity.CENTER
                setPadding(dp(28), dp(12), dp(28), dp(12))
                background = GradientDrawable().apply {
                    cornerRadius = dp(24).toFloat()
                    setColor(Color.parseColor("#B3A0FF"))
                }
                setOnClickListener {
                    remove()
                    onAction()
                }
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(26); gravity = Gravity.CENTER_HORIZONTAL })
        }

        container.addView(
            column,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        runCatching { windowManager.addView(container, params) }
            .onSuccess {
                view = container
                handler.removeCallbacks(hideTask)
                handler.postDelayed(hideTask, durationMs)
            }
    }

    fun remove() {
        handler.removeCallbacks(hideTask)
        view?.let { runCatching { windowManager.removeView(it) } }
        view = null
    }
}
