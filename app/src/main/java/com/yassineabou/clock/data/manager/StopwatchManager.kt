package com.yassineabou.clock.data.manager

import androidx.compose.runtime.mutableStateListOf
import com.yassineabou.clock.data.model.StopwatchState
import com.yassineabou.clock.data.workManager.worker.STOPWATCH_TAG
import com.yassineabou.clock.data.workManager.worker.StopwatchWorker
import com.yassineabou.clock.util.GlobalProperties.TIME_FORMAT
import com.zhuinden.flowcombinetuplekt.combineTuple
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.Timer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.fixedRateTimer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Singleton
class StopwatchManager @Inject constructor(
    private val workRequestManager: WorkRequestManager,
) {

    var lapTimes = mutableStateListOf<String>()
        private set

    private val secondFlow = MutableStateFlow("00")
    private val minuteFlow = MutableStateFlow("00")
    private val hourFlow = MutableStateFlow("00")
    private val isPlayingFlow = MutableStateFlow(false)
    private val isResetFlow = MutableStateFlow(true)

    val stopwatchState = combineTuple(
        secondFlow,
        minuteFlow,
        hourFlow,
        isPlayingFlow,
        isResetFlow,
    ).map { (second, minute, hour, isPlaying, isReset) ->
        StopwatchState(
            second = second,
            minute = minute,
            hour = hour,
            isPlaying = isPlaying,
            isReset = isReset,
        )
    }

    private var duration: Duration = Duration.ZERO
    private var timer: Timer? = null

    fun start() {
        timer = fixedRateTimer(initialDelay = 1000L, period = 1000L) {
            duration = duration.plus(1.seconds)
            updateStopwatchState()
        }
        isPlayingFlow.value = true
        if (isResetFlow.value) {
            workRequestManager.enqueueWorker<StopwatchWorker>(STOPWATCH_TAG)
            isResetFlow.value = false
        }
    }

    private fun updateStopwatchState() {
        duration.toComponents { hours, minutes, seconds, _ ->
            secondFlow.value = seconds.pad()
            minuteFlow.value = minutes.pad()
            hourFlow.value = hours.toInt().pad()
        }
    }

    fun lap() {
        val time = duration.toComponents { hours, minutes, seconds, _ ->
            String.format(TIME_FORMAT, hours, minutes, seconds)
        }
        lapTimes.add(time)
    }

    fun clear() {
        lapTimes.clear()
    }

    private fun Int.pad(): String {
        return this.toString().padStart(2, '0')
    }

    fun stop() {
        timer?.cancel()
        isPlayingFlow.value = false
    }

    fun reset() {
        isResetFlow.value = true
        workRequestManager.cancelWorker(STOPWATCH_TAG)
        stop()
        duration = Duration.ZERO
        updateStopwatchState()
    }
}
