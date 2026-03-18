package com.example.fraudshieldai

import android.content.Context
import android.provider.ContactsContract

object ContactTrustHelper {

    fun isSavedContact(context: Context, phoneNumberOrName: String?): Boolean {
        if (phoneNumberOrName.isNullOrBlank()) return false

        val queryValue = phoneNumberOrName.trim()
        val normalizedTarget = normalizeNumber(queryValue)

        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null,
            null,
            null
        )

        cursor?.use {
            val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (it.moveToNext()) {
                val displayName = it.getString(nameIndex)?.trim().orEmpty()
                val storedNumber = it.getString(numberIndex)?.trim().orEmpty()
                val normalizedStored = normalizeNumber(storedNumber)

                if (displayName.equals(queryValue, ignoreCase = true)) {
                    return true
                }

                if (normalizedTarget.isNotBlank() &&
                    normalizedStored.isNotBlank() &&
                    (normalizedStored.endsWith(normalizedTarget) || normalizedTarget.endsWith(normalizedStored))
                ) {
                    return true
                }
            }
        }

        return false
    }

    private fun normalizeNumber(value: String): String {
        return value.filter { it.isDigit() }.takeLast(10)
    }
}