package com.example.fraudshieldai

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class SmsHistoryItem(
    val sender: String,
    val message: String,
    val fraudScore: Int,
    val riskLevel: String,
    val category: String,
    val reasons: List<String>,
    val scannedAt: String,
    val mlScore: Int,
    val linkCount: Int
)

data class SmsAnalysisUiState(
    val sender: String = "No SMS yet",
    val message: String = "Waiting for incoming SMS...",
    val fraudScore: Int = 0,
    val riskLevel: String = "SAFE",
    val category: String = "Safe Message",
    val reasons: List<String> = emptyList(),
    val scannedAt: String = "--",
    val history: List<SmsHistoryItem> = emptyList(),
    val mlScore: Int = 0,
    val linkCount: Int = 0
)

object SmsAnalysisState {
    private val _uiState = MutableStateFlow(SmsAnalysisUiState())
    val uiState: StateFlow<SmsAnalysisUiState> = _uiState

    fun initialize(context: Context) {
        val history = SmsStorage.loadHistory(context)
        if (history.isNotEmpty()) {
            val latest = history.first()
            _uiState.value = SmsAnalysisUiState(
                sender = latest.sender,
                message = latest.message,
                fraudScore = latest.fraudScore,
                riskLevel = latest.riskLevel,
                category = latest.category,
                reasons = latest.reasons,
                scannedAt = latest.scannedAt,
                history = history,
                mlScore = latest.mlScore,
                linkCount = latest.linkCount
            )
        }
    }

    fun updateState(
        context: Context,
        sender: String,
        message: String,
        fraudScore: Int,
        riskLevel: String,
        category: String,
        reasons: List<String>,
        scannedAt: String,
        mlScore: Int,
        linkCount: Int
    ) {
        val newItem = SmsHistoryItem(
            sender = sender,
            message = message,
            fraudScore = fraudScore,
            riskLevel = riskLevel,
            category = category,
            reasons = reasons,
            scannedAt = scannedAt,
            mlScore = mlScore,
            linkCount = linkCount
        )

        val currentHistory = _uiState.value.history

        val updatedHistory = if (
            currentHistory.isNotEmpty() &&
            currentHistory.first().sender == newItem.sender &&
            currentHistory.first().message == newItem.message &&
            currentHistory.first().fraudScore == newItem.fraudScore
        ) {
            currentHistory
        } else {
            (listOf(newItem) + currentHistory).take(20)
        }

        _uiState.value = SmsAnalysisUiState(
            sender = sender,
            message = message,
            fraudScore = fraudScore,
            riskLevel = riskLevel,
            category = category,
            reasons = reasons,
            scannedAt = scannedAt,
            history = updatedHistory,
            mlScore = mlScore,
            linkCount = linkCount
        )

        SmsStorage.saveHistory(context, updatedHistory)
    }
}