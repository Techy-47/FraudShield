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

                val reasonsArray = JSONArray()
                item.reasons.forEach { reasonsArray.put(it) }
                put("reasons", reasonsArray)
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
                            linkCount = obj.optInt("linkCount")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}