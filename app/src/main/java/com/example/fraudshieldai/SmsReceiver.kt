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

            val signature = "$sender|$fullMessage"
            val now = System.currentTimeMillis()

            if (signature == lastSmsSignature && now - lastSmsTimestamp < 2000) return
            lastSmsSignature = signature
            lastSmsTimestamp = now

            val result = FraudDetector.analyzeMessage(
                sender = sender,
                message = fullMessage,
                sourceApp = "SMS",
                isSavedContact = false
            )

            val currentTime = SimpleDateFormat(
                "dd MMM yyyy, hh:mm a",
                Locale.getDefault()
            ).format(Date())

            SmsAnalysisState.updateState(
                context = context,
                sender = sender,
                message = fullMessage,
                fraudScore = result.score,
                riskLevel = result.riskLevel,
                category = result.category,
                reasons = listOf("Source: SMS") + result.reasons,
                scannedAt = currentTime,
                mlScore = (result.mlScore * 100).toInt(),
                linkCount = result.linkCount
            )

            if (result.riskLevel == "HIGH" || result.riskLevel == "MEDIUM") {
                OverlayAlertService.showAlert(
                    context = context,
                    title = "⚠ Fraud SMS Detected",
                    message = "${result.category} • ${result.riskLevel}"
                )
            }

            Log.d("SmsReceiver", "Sender: $sender")
            Log.d("SmsReceiver", "Message: $fullMessage")
            Log.d("SmsReceiver", "Fraud Score: ${result.score}")
            Log.d("SmsReceiver", "Risk Level: ${result.riskLevel}")
            Log.d("SmsReceiver", "Category: ${result.category}")

        } catch (e: Exception) {
            Log.e("SmsReceiver", "Error reading SMS: ${e.message}", e)
        }
    }
}