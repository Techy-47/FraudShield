package com.example.fraudshieldai

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

class OverlayAlertService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: LinearLayout? = null

    private val handler = Handler(Looper.getMainLooper())
    private var showRunnable: Runnable? = null
    private var hideRunnable: Runnable? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "⚠ Fraud Alert"
        val message = intent?.getStringExtra(EXTRA_MESSAGE) ?: "Suspicious content detected"

        cancelPendingTasks()
        removeOverlay()

        showRunnable = Runnable {
            try {
                showOverlay(title, message)
            } catch (e: Exception) {
                Log.e("OverlayAlertService", "Failed to show overlay", e)
                stopSelf()
            }
        }

        handler.postDelayed(showRunnable!!, 3000) // 3 second delay
        return START_NOT_STICKY
    }

    private fun showOverlay(title: String, message: String) {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        if (overlayView != null) {
            removeOverlay()
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val topInset = getStatusBarHeight()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(18))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.parseColor("#D92D20"))
                cornerRadii = floatArrayOf(
                    0f, 0f,
                    0f, 0f,
                    dpF(22), dpF(22),
                    dpF(22), dpF(22)
                )
            }
            elevation = dpF(10)
        }

        val titleView = TextView(this).apply {
            text = title
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(typeface, Typeface.BOLD)
        }

        val messageView = TextView(this).apply {
            text = message
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, dp(6), 0, 0)
        }

        container.addView(titleView)
        container.addView(messageView)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            x = 0
            y = topInset + dp(70)
        }

        overlayView = container
        try {
            windowManager?.addView(overlayView, params)
        } catch (e: Exception) {
            Log.e("OverlayAlertService", "Failed to add overlay view", e)
            removeOverlay()
            stopSelf()
            return
        }

        hideRunnable = Runnable {
            removeOverlay()
            stopSelf()
        }

        handler.postDelayed(hideRunnable!!, 6500) // visible for 6.5 sec
    }

    private fun getStatusBarHeight(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            val insets = wm.currentWindowMetrics.windowInsets
            insets.getInsets(WindowInsets.Type.statusBars()).top
        } else {
            @Suppress("DEPRECATION")
            resources.getIdentifier("status_bar_height", "dimen", "android")
                .takeIf { it > 0 }
                ?.let { resources.getDimensionPixelSize(it) } ?: dp(24)
        }
    }

    private fun removeOverlay() {
        overlayView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {
            }
            overlayView = null
        }
    }

    private fun cancelPendingTasks() {
        showRunnable?.let { handler.removeCallbacks(it) }
        hideRunnable?.let { handler.removeCallbacks(it) }
        showRunnable = null
        hideRunnable = null
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    private fun dpF(value: Int): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        )
    }

    override fun onDestroy() {
        cancelPendingTasks()
        removeOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_MESSAGE = "extra_message"

        fun showAlert(context: Context, title: String, message: String) {
            if (!Settings.canDrawOverlays(context)) return

            val intent = Intent(context, OverlayAlertService::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_MESSAGE, message)
            }

            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.e("OverlayAlertService", "Unable to start overlay service", e)
            }
        }

        fun openOverlayPermission(context: Context) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}