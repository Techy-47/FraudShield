package com.example.fraudshieldai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.telephony.SmsMessage
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private var lastSmsSignature: String = ""
        private var lastSmsTimestamp: Long = 0L
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.provider.Telephony.SMS_RECEIVED") return
        if (!ProtectionPrefs.isProtectionEnabled(context)) return

        try {
            val bundle: Bundle = intent.extras ?: return
            val pdus = bundle["pdus"] as? Array<*> ?: return
            val format = bundle.getString("format")

            val messages = pdus.mapNotNull { pdu ->
                try {
                    if (format != null) {
                        SmsMessage.createFromPdu(pdu as ByteArray, format)
                    } else {
                        @Suppress("DEPRECATION")
                        SmsMessage.createFromPdu(pdu as ByteArray)
                    }
                } catch (_: Exception) {
                    null
                }
            }

            if (messages.isEmpty()) return

            val sender = messages.firstOrNull()?.displayOriginatingAddress ?: "Unknown"
            val fullMessage = buildString {
                messages.forEach { sms ->
                    append(sms.displayMessageBody ?: "")
                }
            }.trim()

            if (fullMessage.isBlank()) return

            val normalizedContent = fullMessage
                .lowercase()
                .replace(Regex("\\s+"), " ")
                .trim()

            val signature = "$sender|$normalizedContent"
            val now = System.currentTimeMillis()

            if (signature == lastSmsSignature && now - lastSmsTimestamp < 2000) return
            lastSmsSignature = signature
            lastSmsTimestamp = now

            val mlScore = try {
                val scorer = TinyMlScorer(context)
                scorer.predictScore(fullMessage)
            } catch (e: Exception) {
                Log.w("SmsReceiver", "ML scorer failed: ${e.message}")
                0f
            }

            val result = FraudDetector.analyzeMessage(
                sender = sender,
                message = fullMessage,
                sourceApp = "SMS",
                isSavedContact = false,
                mlScoreInput = mlScore
            )

            val currentTime = SimpleDateFormat(
                "dd MMM yyyy, hh:mm a",
                Locale.getDefault()
            ).format(Date())

            val finalReasons = mutableListOf<String>()
            finalReasons.add("Source: SMS")
            if (result.hasBlockedLinks) {
                finalReasons.add("Protection: Malicious link blocked and sanitized")
            }
            finalReasons.addAll(result.reasons)
            finalReasons.addAll(result.safeSignals.map { "Safe signal: $it" })

            val displayMessage = if (result.hasBlockedLinks) {
                result.sanitizedMessage
            } else {
                fullMessage
            }

            SmsAnalysisState.updateState(
                context = context,
                sender = sender,
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
                    result.riskLevel == "CRITICAL" -> "🚨 Critical Fraud SMS"
                    result.riskLevel == "HIGH" -> "⚠ Fraud SMS Detected"
                    else -> "⚠ Suspicious SMS"
                }

                val alertMessage = when {
                    result.hasBlockedLinks ->
                        "${result.category} • Unsafe link neutralized"

                    else ->
                        "${result.category} • ${result.riskLevel} • Score ${result.score}"
                }

                OverlayAlertService.showAlert(
                    context = context,
                    title = alertTitle,
                    message = alertMessage,
                    riskLevel = result.riskLevel,
                    blockedLink = result.hasBlockedLinks
                )
            }

            Log.d("SmsReceiver", "Sender: $sender")
            Log.d("SmsReceiver", "Message: $fullMessage")
            Log.d("SmsReceiver", "Sanitized: ${result.sanitizedMessage}")
            Log.d("SmsReceiver", "Fraud Score: ${result.score}")
            Log.d("SmsReceiver", "Risk Level: ${result.riskLevel}")
            Log.d("SmsReceiver", "Category: ${result.category}")
            Log.d("SmsReceiver", "Blocked Links: ${result.hasBlockedLinks}")
            Log.d("SmsReceiver", "Malicious Links: ${result.maliciousLinks}")
            Log.d("SmsReceiver", "Suspicious Links: ${result.suspiciousLinks}")

        } catch (e: Exception) {
            Log.e("SmsReceiver", "Error reading SMS: ${e.message}", e)
        }
    }

    private fun shouldShowOverlay(riskLevel: String, hasBlockedLinks: Boolean): Boolean {
        if (hasBlockedLinks) return true
        return riskLevel == "MEDIUM" || riskLevel == "HIGH" || riskLevel == "CRITICAL"
    }
}