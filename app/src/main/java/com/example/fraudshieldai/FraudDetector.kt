package com.example.fraudshieldai

object FraudDetector {

    fun analyzeMessage(message: String): FraudResult {
        val lowerMessage = message.lowercase()

        val suspiciousKeywords = listOf(
            "urgent",
            "immediately",
            "pay now",
            "click here",
            "verify account",
            "account blocked",
            "suspended",
            "electricity disconnected",
            "electricity will be disconnected",
            "power cut",
            "water disconnected",
            "gas disconnected",
            "bank alert",
            "update kyc",
            "lottery",
            "prize",
            "win cash",
            "loan approved",
            "call this number",
            "contact officer"
        )

        var ruleScore = 0
        val reasons = mutableListOf<String>()

        suspiciousKeywords.forEach { keyword ->
            if (lowerMessage.contains(keyword)) {
                ruleScore += 12
                reasons.add("Suspicious phrase detected: $keyword")
            }
        }

        val phonePattern = Regex("""\b\d{10}\b""")
        if (phonePattern.containsMatchIn(message)) {
            ruleScore += 8
            reasons.add("Phone number detected in suspicious context")
        }

        val linkResult = LinkScanner.analyzeLinks(message)
        val otpResult = OtpFraudDetector.analyze(message)
        val mlScore = TinyMlScorer.score(message)

        reasons.addAll(linkResult.reasons)
        reasons.addAll(otpResult.reasons)

        val totalScore = (
                ruleScore +
                        linkResult.score +
                        otpResult.score +
                        (mlScore / 2)
                ).coerceAtMost(100)

        val riskLevel = when {
            totalScore >= 70 -> "HIGH"
            totalScore >= 40 -> "MEDIUM"
            totalScore >= 15 -> "LOW"
            else -> "SAFE"
        }

        val category = when {
            lowerMessage.contains("otp") -> "OTP Scam"
            lowerMessage.contains("lottery") || lowerMessage.contains("prize") || lowerMessage.contains("win cash") ->
                "Lottery Scam"
            lowerMessage.contains("electricity") || lowerMessage.contains("water") || lowerMessage.contains("gas") ->
                "Utility Scam"
            lowerMessage.contains("bank") || lowerMessage.contains("kyc") || lowerMessage.contains("account") ->
                "Banking Scam"
            lowerMessage.contains("loan") ->
                "Loan Scam"
            linkResult.links.isNotEmpty() ->
                "Phishing Scam"
            totalScore > 0 ->
                "Suspicious Message"
            else ->
                "Safe Message"
        }

        return FraudResult(
            score = totalScore,
            riskLevel = riskLevel,
            category = category,
            reasons = reasons.distinct(),
            mlScore = mlScore,
            linkCount = linkResult.links.size
        )
    }
}

data class FraudResult(
    val score: Int,
    val riskLevel: String,
    val category: String,
    val reasons: List<String>,
    val mlScore: Int,
    val linkCount: Int
)