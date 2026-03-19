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
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class OverlayAlertService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: LinearLayout? = null

    private val handler = Handler(Looper.getMainLooper())
    private var autoHideRunnable: Runnable? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "⚠ Fraud Alert"
        val message = intent?.getStringExtra(EXTRA_MESSAGE) ?: "Suspicious content detected"
        val riskLevel = intent?.getStringExtra(EXTRA_RISK_LEVEL) ?: "HIGH"
        val blockedLink = intent?.getBooleanExtra(EXTRA_BLOCKED_LINK, false) ?: false

        removeOverlay()
        showCenteredPopup(title, message, riskLevel, blockedLink)

        return START_NOT_STICKY
    }

    private fun showCenteredPopup(
        title: String,
        message: String,
        riskLevel: String,
        blockedLink: Boolean
    ) {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val backgroundColor = when {
            blockedLink -> "#991B1B"
            riskLevel == "CRITICAL" -> "#7F1D1D"
            riskLevel == "HIGH" -> "#B91C1C"
            riskLevel == "MEDIUM" -> "#B45309"
            else -> "#1E3A8A"
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(18))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpF(24)
                setColor(Color.parseColor("#FFFFFF"))
                setStroke(dp(2), Color.parseColor(backgroundColor))
            }
            elevation = dpF(16)
        }

        val titleView = TextView(this).apply {
            text = title
            setTextColor(Color.parseColor(backgroundColor))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTypeface(typeface, Typeface.BOLD)
        }

        val subtitleView = TextView(this).apply {
            text = if (blockedLink) {
                "Protection applied locally"
            } else {
                "Latest scanned message needs attention"
            }
            setTextColor(Color.parseColor("#475569"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(6), 0, 0)
        }

        val messageView = TextView(this).apply {
            text = message
            setTextColor(Color.parseColor("#0F172A"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(0, dp(14), 0, 0)
        }

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(18), 0, 0)
        }

        val ignoreButton = Button(this).apply {
            text = "Ignore"
            isAllCaps = false
            setTextColor(Color.parseColor("#0F172A"))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpF(14)
                setColor(Color.parseColor("#E2E8F0"))
            }
            setOnClickListener {
                removeOverlay()
                stopSelf()
            }
        }

        val actionButton = Button(this).apply {
            text = "Action"
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpF(14)
                setColor(Color.parseColor(backgroundColor))
            }
            setOnClickListener {
                openAppToAnalysis()
                removeOverlay()
                stopSelf()
            }
        }

        buttonRow.addView(ignoreButton)
        buttonRow.addView(spaceView(dp(10)))
        buttonRow.addView(actionButton)

        container.addView(titleView)
        container.addView(subtitleView)
        container.addView(messageView)
        container.addView(buttonRow)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            width = (resources.displayMetrics.widthPixels * 0.88f).toInt()
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

        autoHideRunnable = Runnable {
            removeOverlay()
            stopSelf()
        }
        handler.postDelayed(autoHideRunnable!!, 10000)
    }

    private fun openAppToAnalysis() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra("open_analysis_tab", true)
        }
        startActivity(intent)
    }

    private fun removeOverlay() {
        autoHideRunnable?.let { handler.removeCallbacks(it) }
        autoHideRunnable = null

        overlayView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {
            }
            overlayView = null
        }
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

    private fun spaceView(width: Int): TextView {
        return TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(width, 1)
        }
    }

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_MESSAGE = "extra_message"
        private const val EXTRA_RISK_LEVEL = "extra_risk_level"
        private const val EXTRA_BLOCKED_LINK = "extra_blocked_link"

        fun showAlert(
            context: Context,
            title: String,
            message: String,
            riskLevel: String = "HIGH",
            blockedLink: Boolean = false
        ) {
            if (!Settings.canDrawOverlays(context)) return

            val intent = Intent(context, OverlayAlertService::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_MESSAGE, message)
                putExtra(EXTRA_RISK_LEVEL, riskLevel)
                putExtra(EXTRA_BLOCKED_LINK, blockedLink)
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