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
        "https://" to 10
    )

    fun score(message: String): Int {
        val lower = message.lowercase()
        var score = 0

        weightedFeatures.forEach { (feature, weight) ->
            if (lower.contains(feature)) score += weight
        }

        return score.coerceAtMost(30)
    }
}
