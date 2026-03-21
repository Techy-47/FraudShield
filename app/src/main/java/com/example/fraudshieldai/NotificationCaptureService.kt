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
        "com.whatsapp.w4b" to "WhatsApp Business",
        "com.google.android.gm" to "Gmail"
    )

    companion object {
        private var lastSignature: String = ""
        private var lastTimestamp: Long = 0L
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d("NotificationCapture", "Listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d("NotificationCapture", "Listener disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        Log.d("NotificationCapture", "onNotificationPosted triggered")

        if (sbn == null) {
            Log.d("NotificationCapture", "Skipped: sbn is null")
            return
        }

        val packageName = sbn.packageName ?: ""
        Log.d("NotificationCapture", "Incoming package: $packageName")

        try {
            if (packageName == this.packageName) {
                Log.d("NotificationCapture", "Skipped: own app notification")
                return
            }

            if (!supportedPackages.containsKey(packageName)) {
                Log.d("NotificationCapture", "Skipped: unsupported package $packageName")
                return
            }

            // Temporary bypass removed from ProtectionPrefs for reliable testing
            // Re-enable later if needed:
            // if (!ProtectionPrefs.isProtectionEnabled(this)) return

            // Temporary bypass of ignore logic for reliable testing
            // Re-enable later if needed:
            // if (shouldIgnoreNotification(sbn)) return

            val notification = sbn.notification
            if (notification == null) {
                Log.d("NotificationCapture", "Skipped: notification is null")
                return
            }

            val extras = notification.extras
            if (extras == null) {
                Log.d("NotificationCapture", "Skipped: extras are null")
                return
            }

            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
            val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
            val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim().orEmpty()

            val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
                ?.mapNotNull { it?.toString()?.trim() }
                ?.filter { it.isNotBlank() }
                .orEmpty()

            Log.d("NotificationCapture", "title=$title")
            Log.d("NotificationCapture", "text=$text")
            Log.d("NotificationCapture", "bigText=$bigText")
            Log.d("NotificationCapture", "subText=$subText")
            Log.d("NotificationCapture", "textLines=$textLines")

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

            if (visibleContent.isBlank()) {
                Log.d("NotificationCapture", "Skipped: visible content blank")
                return
            }

            if (visibleContent.lowercase().contains("sensitive notification content hidden")) {
                Log.d("NotificationCapture", "Skipped: sensitive content hidden")
                return
            }

            if (isBackgroundNoise(visibleContent)) {
                Log.d("NotificationCapture", "Skipped: background noise")
                return
            }

            val normalizedContent = visibleContent
                .lowercase()
                .replace(Regex("\\s+"), " ")
                .trim()

            val signature = "$packageName|$sender|$normalizedContent"
            val now = System.currentTimeMillis()

            if (signature == lastSignature && now - lastTimestamp < 4000) {
                Log.d("NotificationCapture", "Skipped: duplicate notification")
                return
            }

            lastSignature = signature
            lastTimestamp = now

            val isSaved = try {
                ContactTrustHelper.isSavedContact(this, senderTitle)
            } catch (e: Exception) {
                Log.w("NotificationCapture", "Contact check failed: ${e.message}")
                false
            }

            val mlScore = try {
                val scorer = TinyMlScorer(this)
                scorer.predictScore(visibleContent)
            } catch (e: Exception) {
                Log.w("NotificationCapture", "ML scorer failed: ${e.message}")
                0f
            }

            val result = FraudDetector.analyzeMessage(
                sender = senderTitle,
                message = visibleContent,
                sourceApp = sourceName,
                isSavedContact = isSaved,
                mlScoreInput = mlScore
            )

            val currentTime = SimpleDateFormat(
                "dd MMM yyyy, hh:mm a",
                Locale.getDefault()
            ).format(Date())

            val finalReasons = mutableListOf<String>()
            finalReasons.add("Source: $sourceName notification")
            if (result.hasBlockedLinks) {
                finalReasons.add("Protection: Malicious link blocked and sanitized")
            }
            finalReasons.addAll(result.reasons)
            finalReasons.addAll(result.safeSignals.map { "Safe signal: $it" })

            val displayMessage = if (result.hasBlockedLinks) {
                result.sanitizedMessage
            } else {
                visibleContent
            }

            SmsAnalysisState.updateState(
                context = this,
                sender = if (senderTitle.isNotBlank()) "$sourceName: $senderTitle" else sourceName,
                message = displayMessage,
                fraudScore = result.score,
                riskLevel = result.riskLevel,
                category = result.category,
                reasons = finalReasons.distinct(),
                scannedAt = currentTime,
                mlScore = (result.mlScore * 100).toInt(),
                linkCount = result.linkCount,
                hasBlockedLinks = result.hasBlockedLinks,
                suspiciousLinks = result.suspiciousLinks,
                maliciousLinks = result.maliciousLinks,
                isSanitized = result.hasBlockedLinks
            )

            if (shouldShowOverlay(result.riskLevel, result.hasBlockedLinks)) {
                val alertTitle = when {
                    result.hasBlockedLinks -> "🚫 Malicious Link Blocked"
                    result.riskLevel == "CRITICAL" -> "🚨 Critical Fraud Alert"
                    result.riskLevel == "HIGH" -> "⚠ Fraud Alert"
                    else -> "⚠ Suspicious Message"
                }

                val alertMessage = when {
                    result.hasBlockedLinks ->
                        "$sourceName • ${result.category} • Unsafe link neutralized"

                    result.riskLevel == "CRITICAL" ->
                        "$sourceName • ${result.category} • Score ${result.score}"

                    else ->
                        "$sourceName • ${result.category} • Score ${result.score}"
                }

                OverlayAlertService.showAlert(
                    context = this,
                    title = alertTitle,
                    message = alertMessage,
                    riskLevel = result.riskLevel,
                    blockedLink = result.hasBlockedLinks
                )
            }

            Log.d("NotificationCapture", "Package: $packageName")
            Log.d("NotificationCapture", "Sender: $sender")
            Log.d("NotificationCapture", "Visible content: $visibleContent")
            Log.d("NotificationCapture", "Sanitized content: ${result.sanitizedMessage}")
            Log.d("NotificationCapture", "Fraud Score: ${result.score}")
            Log.d("NotificationCapture", "Risk Level: ${result.riskLevel}")
            Log.d("NotificationCapture", "Category: ${result.category}")
            Log.d("NotificationCapture", "Blocked Links: ${result.hasBlockedLinks}")
            Log.d("NotificationCapture", "Malicious Links: ${result.maliciousLinks}")
            Log.d("NotificationCapture", "Suspicious Links: ${result.suspiciousLinks}")
            Log.d("NotificationCapture", "Reasons: ${result.reasons}")
            Log.d("NotificationCapture", "Safe Signals: ${result.safeSignals}")

        } catch (e: Exception) {
            Log.e("NotificationCapture", "Error reading notification: ${e.message}", e)
        }
    }

    private fun shouldShowOverlay(riskLevel: String, hasBlockedLinks: Boolean): Boolean {
        if (hasBlockedLinks) return true
        return riskLevel == "MEDIUM" || riskLevel == "HIGH" || riskLevel == "CRITICAL"
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
            "sensitive notification content hidden"
        )

        return blockedPhrases.any { lower.contains(it) }
    }
}