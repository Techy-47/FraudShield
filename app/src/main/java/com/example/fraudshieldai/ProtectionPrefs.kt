package com.example.fraudshieldai

import android.content.Context

object ProtectionPrefs {
    private const val PREF_NAME = "fraudshield_prefs"
    private const val KEY_PROTECTION_ENABLED = "protection_enabled"

    fun isProtectionEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_PROTECTION_ENABLED, true)
    }

    fun setProtectionEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_PROTECTION_ENABLED, enabled).apply()
    }
}