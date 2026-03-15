package com.example.fraudshieldai

object OtpFraudDetector {

    private val otpScamPatterns = listOf(
        "share otp",
        "send otp",
        "tell me otp",
        "verify otp",
        "confirm otp",
        "otp required",
        "otp needed",
        "do not share this otp with anyone except",
        "forward otp"
    )

    fun analyze(message: String): OtpScanResult {
        val lower = message.lowercase()
        val reasons = mutableListOf<String>()
        var score = 0

        otpScamPatterns.forEach { pattern ->
            if (lower.contains(pattern)) {
                score += 25
                reasons.add("OTP theft pattern detected: $pattern")
            }
        }

        if (Regex("""\botp\b""", RegexOption.IGNORE_CASE).containsMatchIn(message)) {
            score += 10
            reasons.add("OTP keyword found")
        }

        return OtpScanResult(
            score = score.coerceAtMost(100),
            reasons = reasons
        )
    }
}

data class OtpScanResult(
    val score: Int,
    val reasons: List<String>
)
