package com.example.fraudshieldai

object LinkScanner {

    private val suspiciousTlds = listOf(
        ".xyz", ".top", ".click", ".loan", ".work", ".gq", ".tk", ".buzz"
    )

    private val shorteners = listOf(
        "bit.ly", "tinyurl.com", "rb.gy", "t.co", "cutt.ly", "goo.su", "is.gd"
    )

    private val knownBadDomains = listOf(
        "secure-bank.xyz",
        "fake-bill.xyz",
        "goog.xyz",
        "pay-electricity-fast.xyz"
    )

    fun analyzeLinks(message: String): LinkScanResult {
        val lower = message.lowercase()
        val reasons = mutableListOf<String>()
        var score = 0

        val extractedLinks = Regex("""(https?://\S+|www\.\S+|\b[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}\S*)""")
            .findAll(message)
            .map { it.value.trim() }
            .toList()

        extractedLinks.forEach { link ->
            val linkLower = link.lowercase()

            if (shorteners.any { linkLower.contains(it) }) {
                score += 20
                reasons.add("Shortened URL detected: $link")
            }

            if (suspiciousTlds.any { linkLower.contains(it) }) {
                score += 20
                reasons.add("Suspicious domain extension detected in: $link")
            }

            if (knownBadDomains.any { linkLower.contains(it) }) {
                score += 35
                reasons.add("Known malicious domain detected: $link")
            }
        }

        if (extractedLinks.isNotEmpty()) {
            score += 10
            reasons.add("Message contains link(s)")
        }

        return LinkScanResult(
            links = extractedLinks,
            score = score.coerceAtMost(100),
            reasons = reasons
        )
    }
}

data class LinkScanResult(
    val links: List<String>,
    val score: Int,
    val reasons: List<String>
)
