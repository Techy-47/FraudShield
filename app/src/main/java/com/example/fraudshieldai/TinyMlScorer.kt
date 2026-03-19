package com.example.fraudshieldai

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class TinyMlScorer(context: Context) {

    private val interpreter: Interpreter by lazy {
        Interpreter(loadModelFile(context))
    }

    private val preprocessor = TextPreprocessor(context)

    private fun loadModelFile(context: Context): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd("fraud_model.tflite")
        val inputStream = fileDescriptor.createInputStream()
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun predictScore(message: String): Float {
        val inputSequence = preprocessor.preprocess(message)

        val input = arrayOf(inputSequence)
        val output = Array(1) { FloatArray(1) }

        interpreter.run(input, output)

        return output[0][0].coerceIn(0f, 1f)
    }

    fun score(message: String): Int {
        return (predictScore(message) * 100).toInt().coerceIn(0, 100)
    }

    fun close() {
        interpreter.close()
    }
}