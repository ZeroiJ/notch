package com.notch.island.core

import com.notch.island.model.TimerData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manages live countdown timer sessions and dispatches updates to the Dynamic Island.
 */
object TimerManager {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var timerJob: Job? = null

    private val _timerState = MutableStateFlow<TimerData?>(null)
    val timerState: StateFlow<TimerData?> = _timerState.asStateFlow()

    fun startTimer(durationMinutes: Int = 5) {
        val totalMs = durationMinutes * 60 * 1000L
        _timerState.value = TimerData(
            totalDurationMs = totalMs,
            remainingMs = totalMs,
            isRunning = true,
            isPaused = false
        )

        startLoop()
        IslandEventManager.onTimerUpdated(_timerState.value)
    }

    fun pauseTimer() {
        val current = _timerState.value ?: return
        timerJob?.cancel()
        _timerState.value = current.copy(isRunning = false, isPaused = true)
        IslandEventManager.onTimerUpdated(_timerState.value)
    }

    fun resumeTimer() {
        val current = _timerState.value ?: return
        _timerState.value = current.copy(isRunning = true, isPaused = false)
        startLoop()
        IslandEventManager.onTimerUpdated(_timerState.value)
    }

    fun addOneMinute() {
        val current = _timerState.value ?: return
        val updatedRemaining = current.remainingMs + 60_000L
        val updatedTotal = current.totalDurationMs + 60_000L
        _timerState.value = current.copy(
            totalDurationMs = updatedTotal,
            remainingMs = updatedRemaining
        )
        IslandEventManager.onTimerUpdated(_timerState.value)
    }

    fun resetTimer() {
        timerJob?.cancel()
        _timerState.value = null
        IslandEventManager.onTimerUpdated(null)
    }

    private fun startLoop() {
        timerJob?.cancel()
        timerJob = scope.launch {
            val stepMs = 200L
            while (true) {
                delay(stepMs)
                val current = _timerState.value ?: break
                if (!current.isRunning) break

                val newRemaining = current.remainingMs - stepMs
                if (newRemaining <= 0) {
                    _timerState.value = current.copy(remainingMs = 0, isRunning = false)
                    IslandEventManager.postPeek(
                        title = "Timer Complete",
                        subtitle = "Time is up!",
                        trailingText = "00:00",
                        durationMs = 5000L
                    )
                    resetTimer()
                    break
                } else {
                    _timerState.value = current.copy(remainingMs = newRemaining)
                    IslandEventManager.onTimerUpdated(_timerState.value)
                }
            }
        }
    }
}
