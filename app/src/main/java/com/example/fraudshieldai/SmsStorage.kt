package com.example.fraudshieldai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object SmsStorage {

    private const val PREF_NAME = "fraudshield_storage"
    private const val KEY_HISTORY = "history"

    fun saveHistory(context: Context, history: List<SmsHistoryItem>) {
        val jsonArray = JSONArray()

        history.forEach { item ->
            val obj = JSONObject().apply {
                put("sender", item.sender)
                put("message", item.message)
                put("fraudScore", item.fraudScore)
                put("riskLevel", item.riskLevel)
                put("category", item.category)
                put("scannedAt", item.scannedAt)
                put("mlScore", item.mlScore)
                put("linkCount", item.linkCount)
                put("hasBlockedLinks", item.hasBlockedLinks)
                put("isSanitized", item.isSanitized)

                val reasonsArray = JSONArray()
                item.reasons.forEach { reasonsArray.put(it) }
                put("reasons", reasonsArray)

                val suspiciousLinksArray = JSONArray()
                item.suspiciousLinks.forEach { suspiciousLinksArray.put(it) }
                put("suspiciousLinks", suspiciousLinksArray)

                val maliciousLinksArray = JSONArray()
                item.maliciousLinks.forEach { maliciousLinksArray.put(it) }
                put("maliciousLinks", maliciousLinksArray)
            }
            jsonArray.put(obj)
        }

        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_HISTORY, jsonArray.toString())
            .apply()
    }

    fun loadHistory(context: Context): List<SmsHistoryItem> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()

        return try {
            val jsonArray = JSONArray(raw)
            buildList {
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)

                    val reasonsJson = obj.optJSONArray("reasons") ?: JSONArray()
                    val reasons = mutableListOf<String>()
                    for (j in 0 until reasonsJson.length()) {
                        reasons.add(reasonsJson.optString(j))
                    }

                    val suspiciousLinksJson = obj.optJSONArray("suspiciousLinks") ?: JSONArray()
                    val suspiciousLinks = mutableListOf<String>()
                    for (j in 0 until suspiciousLinksJson.length()) {
                        suspiciousLinks.add(suspiciousLinksJson.optString(j))
                    }

                    val maliciousLinksJson = obj.optJSONArray("maliciousLinks") ?: JSONArray()
                    val maliciousLinks = mutableListOf<String>()
                    for (j in 0 until maliciousLinksJson.length()) {
                        maliciousLinks.add(maliciousLinksJson.optString(j))
                    }

                    add(
                        SmsHistoryItem(
                            sender = obj.optString("sender"),
                            message = obj.optString("message"),
                            fraudScore = obj.optInt("fraudScore"),
                            riskLevel = obj.optString("riskLevel"),
                            category = obj.optString("category"),
                            reasons = reasons,
                            scannedAt = obj.optString("scannedAt"),
                            mlScore = obj.optInt("mlScore"),
                            linkCount = obj.optInt("linkCount"),
                            hasBlockedLinks = obj.optBoolean("hasBlockedLinks", false),
                            suspiciousLinks = suspiciousLinks,
                            maliciousLinks = maliciousLinks,
                            isSanitized = obj.optBoolean("isSanitized", false)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}