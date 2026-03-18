package com.example.fraudshieldai

import java.util.Locale
import kotlin.math.max
import kotlin.math.min

enum class FraudLabel {
    SAFE,
    MOSTLY_SAFE,
    SUSPICIOUS,
    FRAUD
}

data class FraudAnalysisResult(
    val score: Int,
    val label: FraudLabel,
    val reasons: List<String>,
    val safeSignals: List<String>
)

object FraudScoringEngine {

    private val safeWords = setOf(
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

    private val officialDomains = listOf(
        "gov.in",
        "uidai.gov.in",
        "npci.org.in",
        "sbi.co.in",
        "onlinesbi.sbi",
        "hdfcbank.com",
        "icicibank.com",
        "axisbank.com",
        "kotak.com",
        "pnbindia.in",
        "bankofbaroda.in"
    )

    private val urlRegex = Regex(
        pattern = """(?i)\b((?:https?://|www\.)[^\s]+)""",
        option = RegexOption.IGNORE_CASE
    )

    fun analyze(
        sender: String?,
        message: String,
        isSavedContact: Boolean = false,
        hasRecentConversation: Boolean = false,
        isFrequentlyContacted: Boolean = false,
        sourceApp: String = "SMS" // SMS, WhatsApp, Gmail, RCS
    ): FraudAnalysisResult {

        val text = message.lowercase(Locale.getDefault())
        val normalizedSender = sender?.trim().orEmpty()

        var score = 0
        val reasons = mutableListOf<String>()
        val safeSignals = mutableListOf<String>()

        // ---------------------------
        // 1) TRUST / SAFE REDUCTIONS
        // ---------------------------
        if (isSavedContact) {
            score -= 20
            safeSignals.add("Saved contact")
        }

        if (hasRecentConversation) {
            score -= 8
            safeSignals.add("Recent conversation exists")
        }

        if (isFrequentlyContacted) {
            score -= 7
            safeSignals.add("Frequently contacted sender")
        }

        parseDltSuffix(normalizedSender)?.let { suffix ->
            when (suffix) {
                'G' -> {
                    score -= 18
                    safeSignals.add("Government-category sender")
                }
                'T' -> {
                    score -= 14
                    safeSignals.add("Transactional-category sender")
                }
                'S' -> {
                    score -= 6
                    safeSignals.add("Service-category sender")
                }
                'P' -> {
                    score += 12
                    reasons.add("Promotional-category sender")
                }
            }
        }

        // ---------------------------
        // 2) SAFE WORDS
        // ---------------------------
        var safeReduction = 0
        for (word in safeWords) {
            if (text.contains(word)) {
                safeReduction += 2
            }
        }
        safeReduction = min(safeReduction, 10)
        if (safeReduction > 0) {
            score -= safeReduction
            safeSignals.add("Transactional / financial safe-context words")
        }

        // ---------------------------
        // 3) SUSPICIOUS WORDS
        // ---------------------------
        var suspiciousAdd = 0
        for (word in suspiciousWords) {
            if (text.contains(word)) {
                suspiciousAdd += 6
            }
        }
        suspiciousAdd = min(suspiciousAdd, 24)
        if (suspiciousAdd > 0) {
            score += suspiciousAdd
            reasons.add("Suspicious keywords detected")
        }

        // ---------------------------
        // 4) BASIC TONE / PATTERN
        // ---------------------------
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
            listOf("blocked", "suspended", "penalty", "legal action", "deactivated", "restricted")
        )
        if (hasThreat) {
            score += 12
            reasons.add("Threat / fear language detected")
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
            reasons.add("Asks user to open / click a link")
        }

        val asksCall = containsAny(
            text,
            listOf("call now", "contact immediately", "call customer care", "call helpline")
        )
        if (asksCall) {
            score += 10
            reasons.add("Asks user to call immediately")
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
            reasons.add("Asks to install an app / APK")
        }

        val asksSensitiveInfo = containsAny(
            text,
            listOf("share otp", "share your otp", "send otp", "share pin", "share cvv", "share password")
        )
        if (asksSensitiveInfo) {
            score += 45
            reasons.add("Requests sensitive information")
        }

        val asksRemoteAccess = containsAny(
            text,
            listOf("anydesk", "teamviewer", "screen share", "remote access")
        )
        if (asksRemoteAccess) {
            score += 50
            reasons.add("Requests remote access")
        }

        // ---------------------------
        // 5) LINK ANALYSIS
        // ---------------------------
        val urls = urlRegex.findAll(text).map { it.value }.toList()
        val hasLink = urls.isNotEmpty()

        if (hasLink) {
            score += 10
            reasons.add("Contains external link")
        } else {
            safeSignals.add("No external link detected")
        }

        var officialDomainMatched = false
        urls.forEach { url ->
            val cleaned = url.lowercase(Locale.getDefault())

            if (isShortLink(cleaned)) {
                score += 18
                reasons.add("Shortened / masked link detected")
            }

            if (isRawIpLink(cleaned)) {
                score += 25
                reasons.add("Raw IP address link detected")
            }

            if (hasWeirdHyphenDomain(cleaned)) {
                score += 12
                reasons.add("Suspicious domain format detected")
            }

            if (matchesOfficialDomain(cleaned)) {
                officialDomainMatched = true
            } else if (claimsOfficialBrand(text) && !matchesOfficialDomain(cleaned)) {
                score += 24
                reasons.add("Brand/domain mismatch detected")
            }
        }

        if (officialDomainMatched) {
            score -= 10
            safeSignals.add("Official domain matched")
        }

        // ---------------------------
        // 6) SENDER MISMATCH / IMPERSONATION
        // ---------------------------
        val claimsOfficial = claimsOfficialBrand(text)
        val senderLooksOfficial = senderLooksOfficial(normalizedSender)

        if (claimsOfficial && normalizedSender.isNotBlank() && !senderLooksOfficial) {
            score += 22
            reasons.add("Claims official identity but sender looks unrelated")
        }

        if (sourceApp.equals("WhatsApp", ignoreCase = true) && !isSavedContact && claimsOfficial) {
            score += 18
            reasons.add("Unknown WhatsApp sender claiming authority")
        }

        // ---------------------------
        // 7) WHATSAPP / PERSONAL SCAM PATTERNS
        // ---------------------------
        if (sourceApp.equals("WhatsApp", ignoreCase = true) && !isSavedContact) {
            score += 10
            reasons.add("Unknown WhatsApp sender")
        }

        val asksMoneyUrgently = containsAny(
            text,
            listOf(
                "send money",
                "bhej de",
                "upi karo",
                "pay urgently",
                "transfer urgently",
                "5000 urgently",
                "need money urgently"
            )
        )

        if (asksMoneyUrgently) {
            score += 25
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
            reasons.add("Changed-number scam pattern detected")
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
            reasons.add("Payment collect / QR payment pattern")
        }

        // ---------------------------
        // 8) OTP SAFETY SPECIAL CASE
        // ---------------------------
        val containsOtp = text.contains("otp")
        val saysDoNotShare = containsAny(
            text,
            listOf("do not share", "never share", "don't share")
        )

        if (containsOtp && saysDoNotShare && !hasLink && !asksReply && !asksCall && !asksSensitiveInfo) {
            score -= 10
            safeSignals.add("Standard OTP safety message pattern")
        }

        // ---------------------------
        // 9) COMBO RULES
        // ---------------------------
        if (hasThreat && hasUrgency && (asksClick || asksCall || asksDownload)) {
            score += 15
            reasons.add("Threat + urgency + action combo")
        }

        if (hasReward && (asksClick || asksReply || hasLink)) {
            score += 15
            reasons.add("Reward + claim action combo")
        }

        if (claimsOfficial && hasLink && !officialDomainMatched) {
            score += 20
            reasons.add("Official impersonation + suspicious link combo")
        }

        // ---------------------------
        // 10) FORMATTING HEURISTICS
        // ---------------------------
        if (looksLikeExcessiveCaps(message)) {
            score += 6
            reasons.add("Excessive caps / shouting pattern")
        }

        if (hasTooManyPunctuation(message)) {
            score += 6
            reasons.add("Aggressive punctuation pattern")
        }

        // ---------------------------
        // 11) CLAMP SCORE
        // ---------------------------
        score = max(0, min(score, 100))

        // ---------------------------
        // 12) LABEL
        // ---------------------------
        val label = when {
            score <= 20 -> FraudLabel.SAFE
            score <= 40 -> FraudLabel.MOSTLY_SAFE
            score <= 60 -> FraudLabel.SUSPICIOUS
            else -> FraudLabel.FRAUD
        }

        return FraudAnalysisResult(
            score = score,
            label = label,
            reasons = reasons.distinct(),
            safeSignals = safeSignals.distinct()
        )
    }

    private fun containsAny(text: String, phrases: List<String>): Boolean {
        return phrases.any { text.contains(it) }
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

    private fun claimsOfficialBrand(text: String): Boolean {
        val keywords = listOf(
            "sbi", "hdfc", "icici", "axis bank", "pnb", "bank",
            "aadhaar", "uidai", "income tax", "epfo", "digilocker",
            "government", "govt", "npci"
        )
        return keywords.any { text.contains(it) }
    }

    private fun senderLooksOfficial(sender: String): Boolean {
        if (sender.isBlank()) return false
        val upper = sender.uppercase(Locale.getDefault())

        if (upper.contains("-G") || upper.contains("-T") || upper.contains("-S") || upper.contains("-P")) {
            return true
        }

        // Example short code / alpha sender ID
        val onlyAlphaNum = upper.all { it.isLetterOrDigit() || it == '-' }
        return sender.length in 5..12 && onlyAlphaNum && sender.any { it.isLetter() }
    }

    private fun isShortLink(url: String): Boolean {
        val shorteners = listOf(
            "bit.ly", "tinyurl.com", "rb.gy", "cutt.ly", "goo.gl", "t.co"
        )
        return shorteners.any { url.contains(it) }
    }

    private fun isRawIpLink(url: String): Boolean {
        val ipRegex = Regex("""(?i)\b(?:https?://)?(?:\d{1,3}\.){3}\d{1,3}\b""")
        return ipRegex.containsMatchIn(url)
    }

    private fun hasWeirdHyphenDomain(url: String): Boolean {
        val cleaned = url.removePrefix("https://").removePrefix("http://").removePrefix("www.")
        val domain = cleaned.substringBefore("/")
        return domain.count { it == '-' } >= 2
    }

    private fun matchesOfficialDomain(url: String): Boolean {
        return officialDomains.any { official -> url.contains(official) }
    }

    private fun looksLikeExcessiveCaps(message: String): Boolean {
        val letters = message.filter { it.isLetter() }
        if (letters.length < 8) return false
        val uppercaseCount = letters.count { it.isUpperCase() }
        return uppercaseCount >= letters.length * 0.7
    }

    private fun hasTooManyPunctuation(message: String): Boolean {
        return message.contains("!!!") || message.contains("???") || message.count { it == '!' } >= 4
    }
}