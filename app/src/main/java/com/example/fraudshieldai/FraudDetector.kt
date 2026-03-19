package com.example.fraudshieldai

import java.util.Locale
import kotlin.math.max
import kotlin.math.min

data class FraudResult(
    val score: Int,
    val riskLevel: String,
    val category: String,
    val reasons: List<String>,
    val safeSignals: List<String>,
    val mlScore: Float = 0f,
    val linkCount: Int = 0,
    val suspiciousLinks: List<String> = emptyList(),
    val maliciousLinks: List<String> = emptyList(),
    val sanitizedMessage: String = "",
    val hasBlockedLinks: Boolean = false
)

object FraudDetector {

    private val safeWords = listOf(
        "otp",
        "credited",
        "debited",
        "balance",
        "transaction",
        "withdrawal",
        "deposit",
        "statement",
        "account ending",
        "ref no",
        "reference no",
        "reference number",
        "txn id",
        "due date",
        "emi due",
        "payment received",
        "statement generated",
        "available balance",
        "mini statement",
        "neft",
        "rtgs",
        "imps",
        "upi",
        "upi id",
        "upi ref",
        "loan payment reminder"
    )

    private val suspiciousWords = listOf(
        "urgent",
        "immediately",
        "act now",
        "final notice",
        "blocked",
        "suspended",
        "penalty",
        "legal action",
        "verify now",
        "claim now",
        "winner",
        "reward",
        "free gift",
        "refund pending",
        "update kyc",
        "kyc pending",
        "account blocked",
        "account suspended",
        "click below",
        "limited time"
    )

    fun analyzeMessage(
        sender: String?,
        message: String,
        sourceApp: String = "SMS",
        isSavedContact: Boolean = false,
        mlScoreInput: Float = 0f
    ): FraudResult {

        val text = message.lowercase(Locale.getDefault())
        val senderText = sender.orEmpty().trim()

        var score = 0
        val reasons = mutableListOf<String>()
        val safeSignals = mutableListOf<String>()

        // -----------------------
        // Trust / slight negative scoring
        // -----------------------
        if (isSavedContact) {
            score -= 18
            safeSignals.add("Sender exists in contacts")
        }

        parseDltSuffix(senderText)?.let { suffix ->
            when (suffix) {
                'G' -> {
                    score -= 16
                    safeSignals.add("Government sender category")
                }
                'T' -> {
                    score -= 12
                    safeSignals.add("Transactional sender category")
                }
                'S' -> {
                    score -= 5
                    safeSignals.add("Service sender category")
                }
                'P' -> {
                    score += 10
                    reasons.add("Promotional sender category")
                }
            }
        }

        // -----------------------
        // Safe words
        // -----------------------
        var safeReduction = 0
        for (word in safeWords) {
            if (text.contains(word)) {
                safeReduction += 2
            }
        }
        safeReduction = min(safeReduction, 6)
        if (safeReduction > 0) {
            score -= safeReduction
            safeSignals.add("Contains transactional / safe-context words")
        }

        // -----------------------
        // Suspicious words
        // -----------------------
        var suspiciousAdd = 0
        for (word in suspiciousWords) {
            if (text.contains(word)) {
                suspiciousAdd += 6
            }
        }
        suspiciousAdd = min(suspiciousAdd, 24)
        if (suspiciousAdd > 0) {
            score += suspiciousAdd
            reasons.add("Message contains urgency or pressure language")
        }

        // -----------------------
        // Tone / content
        // -----------------------
        val hasUrgency = containsAny(
            text,
            listOf("urgent", "immediately", "act now", "today", "within 24 hours", "final notice")
        )
        if (hasUrgency) {
            score += 8
            reasons.add("Urgent tone detected")
        }

        val hasThreat = containsAny(
            text,
            listOf(
                "blocked",
                "suspended",
                "penalty",
                "legal action",
                "deactivated",
                "restricted",
                "account suspension",
                "account blocked",
                "account suspended",
                "disconnected",
                "electricity disconnected",
                "service suspended"
            )
        )
        if (hasThreat) {
            score += 18
            reasons.add("Threat / fear language detected")
        }

        val kycScamPattern =
            text.contains("kyc") && containsAny(
                text,
                listOf("suspended", "blocked", "expired", "pending", "verify", "update")
            )

        if (kycScamPattern) {
            score += 20
            reasons.add("KYC scam pattern detected")
        }

        val utilityScamPattern = containsAny(
            text,
            listOf(
                "electricity bill",
                "power disconnection",
                "water bill",
                "gas bill",
                "service disconnected",
                "bill overdue",
                "meter disconnected",
                "utility payment"
            )
        )
        if (utilityScamPattern) {
            score += 18
            reasons.add("Utility bill scam pattern detected")
        }

        val hasReward = containsAny(
            text,
            listOf("winner", "reward", "cashback", "free gift", "prize", "congratulations")
        )
        if (hasReward) {
            score += 15
            reasons.add("Reward / prize bait detected")
        }

        val asksClick = containsAny(
            text,
            listOf("click", "tap here", "open link", "visit now", "verify now", "claim now")
        )
        if (asksClick) {
            score += 12
            reasons.add("Asks user to click or open link")
        }

        val asksCall = containsAny(
            text,
            listOf("call now", "contact immediately", "call customer care", "call helpline")
        )
        if (asksCall) {
            score += 18
            reasons.add("Asks user to call immediately")
        }

        if (asksCall && hasThreat) {
            score += 25
            reasons.add("Call-based scam pattern (threat + call)")
        }

        val asksReply = containsAny(
            text,
            listOf("reply yes", "reply now", "send details", "share details")
        )
        if (asksReply) {
            score += 8
            reasons.add("Asks for direct reply / details")
        }

        val asksDownload = containsAny(
            text,
            listOf("download app", "install apk", "security update apk", "install now")
        )
        if (asksDownload) {
            score += 30
            reasons.add("App / APK install request detected")
        }

        val asksSensitiveInfo = containsAny(
            text,
            listOf("share otp", "share your otp", "send otp", "share pin", "share cvv", "share password")
        )
        if (asksSensitiveInfo) {
            score += 45
            reasons.add("Sensitive information request detected")
        }

        val asksRemoteAccess = containsAny(
            text,
            listOf("anydesk", "teamviewer", "screen share", "remote access")
        )
        if (asksRemoteAccess) {
            score += 50
            reasons.add("Remote access request detected")
        }

        val refundScam = containsAny(
            text,
            listOf("refund approved", "income tax refund", "refund processed", "tax refund")
        )
        if (refundScam) {
            score += 30
            reasons.add("Refund scam pattern detected")
        }

        if (refundScam && hasUrgency) {
            score += 10
            reasons.add("Refund scam with urgency")
        }

        if (kycScamPattern && hasUrgency) {
            score += 15
            reasons.add("High-risk KYC urgency pattern")
        }

        // -----------------------
        // Link scanner integration
        // -----------------------
        val linkScan = LinkScanner.analyzeLinks(message)
        val hasLink = linkScan.links.isNotEmpty()
        val linkCount = linkScan.links.size

        if (hasLink) {
            score += 5
            reasons.add("Message contains link(s)")
        } else {
            safeSignals.add("No external link detected")

            if (hasThreat || asksCall || kycScamPattern || utilityScamPattern) {
                score += 10
                reasons.add("Suspicious message without link (common scam pattern)")
            }
        }

        score += linkScan.score
        reasons.addAll(linkScan.reasons)

        if (linkScan.hasBlockedLinks) {
            score += 20
            reasons.add("Malicious link blocked for user safety")
        }

        if (linkScan.maliciousLinks.isNotEmpty() && (claimsOfficialBrand(text) || utilityScamPattern || refundScam || kycScamPattern)) {
            score += 15
            reasons.add("Malicious link combined with impersonation-style content")
        }

        // -----------------------
        // Brand / sender mismatch
        // -----------------------
        val claimsOfficial = claimsOfficialBrand(text)
        val senderLooksOfficial = senderLooksOfficial(senderText)

        if (claimsOfficial && senderText.isNotBlank() && !senderLooksOfficial) {
            score += 22
            reasons.add("Claims official identity but sender looks unrelated")
        }

        if (claimsOfficial && hasLink && linkScan.maliciousLinks.isNotEmpty()) {
            score += 20
            reasons.add("Official impersonation with malicious link")
        }

        if (sourceApp.contains("WhatsApp", ignoreCase = true) && !isSavedContact && claimsOfficial) {
            score += 18
            reasons.add("Unknown WhatsApp sender claiming authority")
        }

        // -----------------------
        // WhatsApp / social scam patterns
        // -----------------------
        if (sourceApp.contains("WhatsApp", ignoreCase = true) && !isSavedContact) {
            score += 10
            reasons.add("Unknown WhatsApp sender")
        }

        val asksMoneyUrgently = containsAny(
            text,
            listOf(
                "send money",
                "bhej de",
                "bhejdo",
                "upi karo",
                "pay urgently",
                "transfer urgently",
                "need money urgently",
                "paise bhej",
                "5000 bhej",
                "jaldi bhej",
                "urgent paise",
                "gpay kar",
                "phonepe kar"
            )
        )
        if (asksMoneyUrgently) {
            score += 35
            reasons.add("Urgent money request detected")
        }

        val changedNumberPattern = containsAny(
            text,
            listOf(
                "changed my number",
                "new number",
                "old number lost",
                "save this number",
                "mera naya number",
                "old sim lost"
            )
        )
        if (changedNumberPattern) {
            score += 22
            reasons.add("Changed number scam pattern detected")
        }

        if (changedNumberPattern && asksMoneyUrgently) {
            score += 35
            reasons.add("High-risk impersonation (number change + money request)")
        }

        if (isSavedContact && asksMoneyUrgently) {
            score += 25
            reasons.add("Saved contact unexpectedly asking urgent money")
        }

        val qrOrCollectPattern = containsAny(
            text,
            listOf("scan qr", "collect request", "payment collect", "approve request")
        )
        if (qrOrCollectPattern) {
            score += 20
            reasons.add("QR / payment collect scam pattern")
        }

        // -----------------------
        // OTP safe pattern
        // -----------------------
        val containsOtp = text.contains("otp")
        val saysDoNotShare = containsAny(
            text,
            listOf("do not share", "never share", "don't share")
        )

        if (containsOtp && saysDoNotShare && !hasLink && !asksReply && !asksCall && !asksSensitiveInfo) {
            score -= 10
            safeSignals.add("Standard OTP safety format")
        }

        // -----------------------
        // Combo rules
        // -----------------------
        if (hasThreat && hasUrgency && (asksClick || asksCall || asksDownload)) {
            score += 25
            reasons.add("Threat + urgency + action combo")
        }

        if (hasReward && (asksClick || asksReply || hasLink)) {
            score += 15
            reasons.add("Reward + claim combo")
        }

        if (utilityScamPattern && (hasUrgency || hasThreat) && hasLink) {
            score += 20
            reasons.add("Utility scam with urgency/threat/link combo")
        }

        // -----------------------
        // Formatting
        // -----------------------
        if (looksLikeExcessiveCaps(message)) {
            score += 6
            reasons.add("Excessive caps pattern")
        }

        if (hasTooManyPunctuation(message)) {
            score += 6
            reasons.add("Aggressive punctuation pattern")
        }

// -----------------------
// Optional ML contribution
// -----------------------
        if (mlScoreInput >= 0.55f) {
            score += 20
            reasons.add("ML model marked message as highly suspicious")
        } else if (mlScoreInput >= 0.45f) {
            score += 12
            reasons.add("ML model marked message as suspicious")
        } else if (mlScoreInput >= 0.35f) {
            score += 6
            reasons.add("ML model found mild scam-like language")
        } else if (mlScoreInput <= 0.18f) {
            score -= 6
            safeSignals.add("ML model indicates low scam probability")
        }

        if (mlScoreInput >= 0.35f && (
                    hasThreat ||
                            kycScamPattern ||
                            utilityScamPattern ||
                            asksSensitiveInfo ||
                            asksRemoteAccess ||
                            asksMoneyUrgently ||
                            linkScan.maliciousLinks.isNotEmpty()
                    )
        ) {
            score += 8
            reasons.add("ML suspicion supports rule-based fraud indicators")
        }

        if (mlScoreInput >= 0.40f && score >= 40) {
            score += 10
            reasons.add("ML reinforces strong scam indicators")
        }

        if (mlScoreInput <= 0.30f && safeSignals.isNotEmpty()) {
            score -= 5
        }

        // -----------------------
        // Final score clamp
        // -----------------------
        score = max(0, min(score, 100))

        val riskLevel = when {
            score <= 20 -> "LOW"
            score <= 45 -> "MEDIUM"
            score <= 70 -> "HIGH"
            else -> "CRITICAL"
        }

        val category = when {
            asksSensitiveInfo || asksRemoteAccess || asksDownload -> "Credential Theft"
            kycScamPattern -> "KYC Scam"
            linkScan.maliciousLinks.isNotEmpty() -> "Malicious Link"
            utilityScamPattern -> "Utility Bill Scam"
            hasReward -> "Prize / Reward Scam"
            asksMoneyUrgently || changedNumberPattern -> "Money Request Scam"
            refundScam -> "Refund Scam"
            asksCall && hasThreat -> "Call Scam"
            hasThreat && claimsOfficial -> "Impersonation Scam"
            hasLink -> "Suspicious Link"
            else -> "General Analysis"
        }

        return FraudResult(
            score = score,
            riskLevel = riskLevel,
            category = category,
            reasons = reasons.distinct(),
            safeSignals = safeSignals.distinct(),
            mlScore = mlScoreInput,
            linkCount = linkCount,
            suspiciousLinks = linkScan.suspiciousLinks,
            maliciousLinks = linkScan.maliciousLinks,
            sanitizedMessage = if (linkScan.sanitizedMessage.isNotBlank()) linkScan.sanitizedMessage else message,
            hasBlockedLinks = linkScan.hasBlockedLinks
        )
    }

    private fun parseDltSuffix(sender: String): Char? {
        val upper = sender.uppercase(Locale.getDefault())
        return when {
            upper.endsWith("-G") -> 'G'
            upper.endsWith("-T") -> 'T'
            upper.endsWith("-S") -> 'S'
            upper.endsWith("-P") -> 'P'
            else -> null
        }
    }

    private fun containsAny(text: String, phrases: List<String>): Boolean {
        return phrases.any { text.contains(it) }
    }

    private fun claimsOfficialBrand(text: String): Boolean {
        val keywords = listOf(
            "sbi", "hdfc", "icici", "axis bank", "pnb", "bank",
            "aadhaar", "uidai", "income tax", "epfo", "digilocker",
            "government", "govt", "npci", "electricity", "water board", "gas agency"
        )
        return keywords.any { text.contains(it) }
    }

    private fun senderLooksOfficial(sender: String): Boolean {
        if (sender.isBlank()) return false
        val upper = sender.uppercase(Locale.getDefault())

        if (upper.contains("-G") || upper.contains("-T") || upper.contains("-S") || upper.contains("-P")) {
            return true
        }

        val onlyAlphaNum = upper.all { it.isLetterOrDigit() || it == '-' || it == ' ' }
        return sender.length in 5..15 && onlyAlphaNum && sender.any { it.isLetter() }
    }

    private fun looksLikeExcessiveCaps(message: String): Boolean {
        val letters = message.filter { it.isLetter() }
        if (letters.length < 8) return false
        val upperCount = letters.count { it.isUpperCase() }
        return upperCount >= letters.length * 0.7
    }

    private fun hasTooManyPunctuation(message: String): Boolean {
        return message.contains("!!!") || message.contains("???") || message.count { it == '!' } >= 4
    }
}