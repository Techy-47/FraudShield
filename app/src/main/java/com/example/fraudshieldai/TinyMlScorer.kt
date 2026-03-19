package com.example.fraudshieldai

object TinyMlScorer {

    private val weightedFeatures = mapOf(
        "urgent" to 8,
        "immediately" to 8,
        "otp" to 10,
        "kyc" to 12,
        "bank" to 8,
        "electricity" to 8,
        "bill" to 6,
        "lottery" to 12,
        "winner" to 12,
        "click" to 8,
        "verify" to 8,
        "suspended" to 10,
        "account" to 6,
        "loan" to 8,
        ".xyz" to 14,
        "http://" to 10,
        "https://" to 10,
        "refund" to 10,
        "reward" to 10,
        "claim now" to 12,
        "update kyc" to 14,
        "blocked" to 10,
        "legal action" to 12,
        "call now" to 10,
        "share otp" to 18,
        "anydesk" to 20,
        "teamviewer" to 20,
        "remote access" to 20,
        "scan qr" to 12,
        "collect request" to 14,
        "new number" to 10,
        "send money" to 14
    )

    fun score(message: String): Int {
        val lower = message.lowercase()
        var score = 0

        weightedFeatures.forEach { (feature, weight) ->
            if (lower.contains(feature)) score += weight
        }

        if (Regex("""(https?://|www\.)""").containsMatchIn(lower)) {
            score += 6
        }

        if (Regex("""\b\d{1,3}(\.\d{1,3}){3}\b""").containsMatchIn(lower)) {
            score += 12
        }

        return score.coerceAtMost(100)
    }

    fun predictScore(message: String): Float {
        val rawScore = score(message)

        return when {
            rawScore >= 80 -> 0.95f
            rawScore >= 65 -> 0.85f
            rawScore >= 50 -> 0.72f
            rawScore >= 35 -> 0.58f
            rawScore >= 20 -> 0.38f
            else -> 0.12f
        }
    }
}