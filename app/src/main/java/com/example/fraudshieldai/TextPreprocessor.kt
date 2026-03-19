package com.example.fraudshieldai

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

class TextPreprocessor(context: Context) {

    companion object {
        const val MAX_LEN = 20
        const val PAD_ID = 0
        const val OOV_ID = 1
    }

    private val wordIndex: Map<String, Int> = loadVocab(context)

    private fun loadVocab(context: Context): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        val inputStream = context.assets.open("vocab.txt")
        val reader = BufferedReader(InputStreamReader(inputStream))

        reader.useLines { lines ->
            lines.forEachIndexed { index, token ->
                val cleanToken = token.trim()
                if (cleanToken.isNotEmpty()) {
                    map[cleanToken] = index
                }
            }
        }

        return map
    }

    fun preprocess(text: String): IntArray {
        val cleaned = cleanText(text)
        val tokens = if (cleaned.isBlank()) emptyList() else cleaned.split("\\s+".toRegex())

        val sequence = IntArray(MAX_LEN) { PAD_ID }

        for (i in 0 until minOf(tokens.size, MAX_LEN)) {
            sequence[i] = wordIndex[tokens[i]] ?: OOV_ID
        }

        return sequence
    }

    private fun cleanText(text: String): String {
        return text
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}