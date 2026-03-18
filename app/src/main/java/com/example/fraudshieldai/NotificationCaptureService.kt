package com.example.fraudshieldai

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationCaptureService : NotificationListenerService() {

    private val supportedPackages = mapOf(
        "com.google.android.apps.messaging" to "Google Messages / RCS",
        "com.whatsapp" to "WhatsApp",
        "com.google.android.gm" to "Gmail"
    )

    companion object {
        private var lastSignature: String = ""
        private var lastTimestamp: Long = 0L
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        try {
            val packageName = sbn.packageName ?: return
            if (packageName == this.packageName) return
            if (!supportedPackages.containsKey(packageName)) return
            if (shouldIgnoreNotification(sbn)) return

            val notification = sbn.notification ?: return
            val extras = notification.extras ?: return

            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
            val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
            val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim().orEmpty()

            val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
                ?.mapNotNull { it?.toString()?.trim() }
                ?.filter { it.isNotBlank() }
                .orEmpty()

            val sourceName = supportedPackages[packageName] ?: packageName
            val senderTitle = cleanSender(title)
            val sender = if (senderTitle.isNotBlank()) senderTitle else sourceName

            val messageBody = extractBestMessageBody(
                text = text,
                bigText = bigText,
                subText = subText,
                lines = textLines
            )

            val visibleContent = if (messageBody.isNotBlank()) {
                messageBody
            } else {
                buildVisibleContent(
                    title = title,
                    subText = subText,
                    text = text,
                    bigText = bigText,
                    lines = textLines
                )
            }
            if (visibleContent.isBlank()) return

            if (visibleContent.lowercase().contains("sensitive notification content hidden")) {
                return
            }

            if (visibleContent.isBlank()) return
            if (isBackgroundNoise(visibleContent)) return

            val normalizedContent = visibleContent
                .lowercase()
                .replace(Regex("\\s+"), " ")
                .trim()

            val signature = "$packageName|$sender|$normalizedContent"
            val now = System.currentTimeMillis()

            if (signature == lastSignature && now - lastTimestamp < 4000) return
            lastSignature = signature
            lastTimestamp = now

            val isSaved = ContactTrustHelper.isSavedContact(this, senderTitle)

            val result = FraudDetector.analyzeMessage(
                sender = senderTitle,
                message = visibleContent,
                sourceApp = sourceName,
                isSavedContact = isSaved
            )

            val currentTime = SimpleDateFormat(
                "dd MMM yyyy, hh:mm a",
                Locale.getDefault()
            ).format(Date())

            val finalReasons = mutableListOf<String>()
            finalReasons.add("Source: $sourceName notification")
            finalReasons.addAll(result.reasons)
            finalReasons.addAll(result.safeSignals.map { "Safe signal: $it" })

            SmsAnalysisState.updateState(
                context = this,
                sender = if (senderTitle.isNotBlank()) "$sourceName: $senderTitle" else sourceName,
                message = visibleContent,
                fraudScore = result.score,
                riskLevel = result.riskLevel,
                category = result.category,
                reasons = finalReasons.distinct(),
                scannedAt = currentTime,
                mlScore = (result.mlScore * 100).toInt(),
                linkCount = result.linkCount
            )

            if (result.riskLevel == "HIGH" || result.riskLevel == "MEDIUM") {
                OverlayAlertService.showAlert(
                    context = this,
                    title = if (result.riskLevel == "HIGH") "⚠ Fraud Alert" else "⚠ Suspicious Message",
                    message = "$sourceName • ${result.category} • Score ${result.score}"
                )
            }

            Log.d("NotificationCapture", "Package: $packageName")
            Log.d("NotificationCapture", "Sender: $sender")
            Log.d("NotificationCapture", "Visible content: $visibleContent")
            Log.d("NotificationCapture", "Fraud Score: ${result.score}")
            Log.d("NotificationCapture", "Risk Level: ${result.riskLevel}")
            Log.d("NotificationCapture", "Category: ${result.category}")
            Log.d("NotificationCapture", "Reasons: ${result.reasons}")
            Log.d("NotificationCapture", "Safe Signals: ${result.safeSignals}")

        } catch (e: Exception) {
            Log.e("NotificationCapture", "Error reading notification: ${e.message}", e)
        }
    }

    private fun shouldIgnoreNotification(sbn: StatusBarNotification): Boolean {
        val notification = sbn.notification ?: return true

        if (sbn.isOngoing) return true

        val flags = notification.flags
        if ((flags and Notification.FLAG_ONGOING_EVENT) != 0) return true
        if ((flags and Notification.FLAG_FOREGROUND_SERVICE) != 0) return true

        val category = notification.category.orEmpty()
        if (category == Notification.CATEGORY_SERVICE ||
            category == Notification.CATEGORY_PROGRESS ||
            category == Notification.CATEGORY_STATUS
        ) {
            return true
        }

        return false
    }

    private fun extractBestMessageBody(
        text: String,
        bigText: String,
        subText: String,
        lines: List<String>
    ): String {
        return when {
            bigText.isNotBlank() -> bigText
            lines.isNotEmpty() -> lines.joinToString(" ").trim()
            text.isNotBlank() -> text
            subText.isNotBlank() -> subText
            else -> ""
        }
    }

    private fun buildVisibleContent(
        title: String,
        subText: String,
        text: String,
        bigText: String,
        lines: List<String>
    ): String {
        return buildString {
            if (title.isNotBlank()) append(title)
            if (subText.isNotBlank() && !contains(subText)) {
                if (isNotEmpty()) append(" ")
                append(subText)
            }
            if (text.isNotBlank() && !contains(text)) {
                if (isNotEmpty()) append(" ")
                append(text)
            }
            if (bigText.isNotBlank() && !contains(bigText)) {
                if (isNotEmpty()) append(" ")
                append(bigText)
            }
            if (lines.isNotEmpty()) {
                val joined = lines.joinToString(" ").trim()
                if (joined.isNotBlank() && !contains(joined)) {
                    if (isNotEmpty()) append(" ")
                    append(joined)
                }
            }
        }.trim()
    }

    private fun cleanSender(rawSender: String): String {
        return rawSender
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun isBackgroundNoise(content: String): Boolean {
        val lower = content.lowercase()

        val blockedPhrases = listOf(
            "doing work in the background",
            "running in the background",
            "syncing messages",
            "chat features connected",
            "chat features are ready",
            "tap to turn off usb",
            "usb debugging on",
            "charging this device via usb",
            "android system",
            "sensitive notification content hidden",
        )

        return blockedPhrases.any { lower.contains(it) }
    }
}