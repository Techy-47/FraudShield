package com.example.fraudshieldai

import java.net.URI
import java.util.Locale

object LinkScanner {

    private val suspiciousTlds = listOf(
        ".xyz", ".top", ".click", ".loan", ".work", ".gq", ".tk", ".buzz", ".fit", ".rest", ".vip"
    )

    private val shorteners = listOf(
        "bit.ly", "tinyurl.com", "rb.gy", "t.co", "cutt.ly", "goo.su", "is.gd", "tiny.cc", "shorturl.at"
    )

    private val knownBadDomains = listOf(
        "secure-bank.xyz",
        "fake-bill.xyz",
        "goog.xyz",
        "pay-electricity-fast.xyz"
    )

    private val suspiciousBrandKeywords = listOf(
        "bank", "sbi", "hdfc", "icici", "axis", "kotak",
        "upi", "paytm", "phonepe", "gpay", "googlepay",
        "electricity", "bill", "kseb", "eb", "bescom",
        "refund", "kyc", "verify", "update", "suspend", "reward"
    )

    private val trustedDomains = listOf(
        "google.com",
        "paytm.com",
        "phonepe.com",
        "amazon.in",
        "amazon.com",
        "onlinesbi.sbi",
        "sbi.co.in",
        "hdfcbank.com",
        "icicibank.com",
        "axisbank.com",
        "kotak.com",
        "npci.org.in",
        "upi.com"
    )

    private val linkRegex = Regex(
        pattern = """((https?://|www\.)[^\s]+)|(\b[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}(/[^\s]*)?\b)""",
        option = RegexOption.IGNORE_CASE
    )

    fun analyzeLinks(message: String): LinkScanResult {
        val reasons = mutableListOf<String>()
        val maliciousLinks = mutableListOf<String>()
        val suspiciousLinks = mutableListOf<String>()
        val extractedLinks = extractLinks(message)

        var score = 0
        var sanitizedMessage = message

        extractedLinks.forEach { rawLink ->
            val normalizedLink = normalizeUrl(rawLink)
            val domain = extractDomain(normalizedLink)
            val domainLower = domain.lowercase(Locale.getDefault())

            var linkScore = 0
            val localReasons = mutableListOf<String>()

            if (domainLower.isNotBlank()) {
                if (isShortener(domainLower)) {
                    linkScore += 20
                    localReasons.add("Shortened URL detected")
                }

                if (hasSuspiciousTld(domainLower)) {
                    linkScore += 20
                    localReasons.add("Suspicious domain extension")
                }

                if (isKnownBadDomain(domainLower)) {
                    linkScore += 40
                    localReasons.add("Known malicious domain")
                }

                if (isIpAddress(domainLower)) {
                    linkScore += 30
                    localReasons.add("IP-based link detected")
                }

                if (hasSuspiciousBrandSpoof(domainLower)) {
                    linkScore += 25
                    localReasons.add("Brand/payment keyword in suspicious domain")
                }

                if (looksLikeTrustedBrandButNotTrusted(domainLower)) {
                    linkScore += 30
                    localReasons.add("Possible spoofed trusted brand domain")
                }
            }

            when {
                linkScore >= 50 -> {
                    maliciousLinks.add(rawLink)
                    reasons.add("Malicious link flagged: $rawLink (${localReasons.joinToString()})")
                    sanitizedMessage = sanitizedMessage.replace(rawLink, "[BLOCKED_LINK]")
                }

                linkScore >= 20 -> {
                    suspiciousLinks.add(rawLink)
                    reasons.add("Suspicious link flagged: $rawLink (${localReasons.joinToString()})")
                }
            }

            score += linkScore
        }

        if (extractedLinks.isNotEmpty()) {
            score += 10
            reasons.add("Message contains link(s)")
        }

        return LinkScanResult(
            links = extractedLinks,
            suspiciousLinks = suspiciousLinks,
            maliciousLinks = maliciousLinks,
            score = score.coerceAtMost(100),
            reasons = reasons.distinct(),
            sanitizedMessage = sanitizedMessage,
            hasBlockedLinks = maliciousLinks.isNotEmpty()
        )
    }

    private fun extractLinks(message: String): List<String> {
        return linkRegex.findAll(message)
            .map { it.value.trim().trimEnd('.', ',', ';', ')', ']') }
            .distinct()
            .toList()
    }

    private fun normalizeUrl(link: String): String {
        return if (link.startsWith("http://", true) || link.startsWith("https://", true)) {
            link
        } else {
            "https://$link"
        }
    }

    private fun extractDomain(link: String): String {
        return try {
            val host = URI(link).host ?: return ""
            host.removePrefix("www.")
        } catch (_: Exception) {
            ""
        }
    }

    private fun isShortener(domain: String): Boolean {
        return shorteners.any { domain == it || domain.endsWith(".$it") }
    }

    private fun hasSuspiciousTld(domain: String): Boolean {
        return suspiciousTlds.any { domain.endsWith(it) }
    }

    private fun isKnownBadDomain(domain: String): Boolean {
        return knownBadDomains.any { domain == it || domain.endsWith(".$it") }
    }

    private fun isIpAddress(domain: String): Boolean {
        val ipRegex = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
        return ipRegex.matches(domain)
    }

    private fun hasSuspiciousBrandSpoof(domain: String): Boolean {
        return suspiciousBrandKeywords.any { keyword ->
            domain.contains(keyword) && !trustedDomains.any { trusted ->
                domain == trusted || domain.endsWith(".$trusted")
            }
        }
    }

    private fun looksLikeTrustedBrandButNotTrusted(domain: String): Boolean {
        val trustedBrands = listOf(
            "google", "paytm", "phonepe", "amazon", "sbi", "hdfc", "icici", "axis", "kotak", "upi"
        )

        val mentionsBrand = trustedBrands.any { domain.contains(it) }
        val actuallyTrusted = trustedDomains.any { trusted ->
            domain == trusted || domain.endsWith(".$trusted")
        }

        return mentionsBrand && !actuallyTrusted
    }
}

data class LinkScanResult(
    val links: List<String>,
    val suspiciousLinks: List<String>,
    val maliciousLinks: List<String>,
    val score: Int,
    val reasons: List<String>,
    val sanitizedMessage: String,
    val hasBlockedLinks: Boolean
)