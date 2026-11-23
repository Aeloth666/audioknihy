package cz.audioknihy.sleep

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface SleepMode {
    data class Minutes(val minutes: Int) : SleepMode
    data object EndOfChapter : SleepMode
}

/** Co má přehrávač udělat po jednom tiku časovače. */
data class TimerTick(val volume: Float, val shouldPause: Boolean, val remainingMs: Long?)

/**
 * Časovač vypnutí. Volá se [tick] zhruba každou sekundu.
 * Posledních [FADE_MS] ztlumí, pak řekne „pauza“. Zatřesení během ztlumení
 * nebo do [SHAKE_GRACE_MS] po vypnutí časovač prodlouží.
 */
class SleepTimer(private val clock: () -> Long) {

    private val _state = MutableStateFlow<SleepMode?>(null)
    val state: StateFlow<SleepMode?> = _state.asStateFlow()

    private var endsAt: Long? = null
    private var lastRemaining: Long? = null
    private var extraChapters = 0
    private var fading = false
    private var firedAt: Long? = null
    private var lastMode: SleepMode? = null

    fun start(mode: SleepMode) {
        _state.value = mode
        lastMode = mode
        endsAt = (mode as? SleepMode.Minutes)?.let { clock() + it.minutes * 60_000L }
        lastRemaining = null
        extraChapters = 0
        fading = false
        firedAt = null
    }

    /** Zbývající čas v režimu minut (pro zobrazení), jinak null. */
    fun remainingMs(): Long? = endsAt?.let { (it - clock()).coerceAtLeast(0) }

    fun cancel() {
        _state.value = null
        endsAt = null
        lastRemaining = null
        extraChapters = 0
        fading = false
        firedAt = null
        lastMode = null
    }

    /** [chapterRemainingRealMs] = zbývající čas kapitoly v reálném čase (mediální / rychlost). */
    fun tick(chapterRemainingRealMs: Long): TimerTick {
        val mode = _state.value ?: return NEUTRAL
        return when (mode) {
            is SleepMode.Minutes -> {
                val remaining = endsAt!! - clock()
                if (remaining <= 0) fire() else fadeTick(remaining)
            }
            SleepMode.EndOfChapter -> {
                val prev = lastRemaining
                lastRemaining = chapterRemainingRealMs
                val crossed = prev != null && prev <= BOUNDARY_MS && chapterRemainingRealMs > prev
                val ended = chapterRemainingRealMs <= 0 || crossed
                when {
                    ended && extraChapters > 0 -> {
                        extraChapters--
                        if (chapterRemainingRealMs <= 0) lastRemaining = null
                        TimerTick(1f, false, chapterRemainingRealMs)
                    }
                    ended -> fire()
                    extraChapters > 0 -> TimerTick(1f, false, chapterRemainingRealMs)
                    else -> fadeTick(chapterRemainingRealMs)
                }
            }
        }
    }

    private fun fadeTick(remaining: Long): TimerTick {
        fading = remaining <= FADE_MS
        val volume = if (fading) (remaining.toFloat() / FADE_MS).coerceIn(0f, 1f) else 1f
        return TimerTick(volume, false, remaining)
    }

    private fun fire(): TimerTick {
        firedAt = clock()
        _state.value = null
        endsAt = null
        lastRemaining = null
        fading = false
        return TimerTick(0f, true, 0)
    }

    /** Vrátí true, když zatřesení časovač prodloužilo (volající pak pokračuje v přehrávání). */
    fun onShake(): Boolean {
        val running = _state.value
        if (running != null) {
            val inFade = when (running) {
                is SleepMode.Minutes -> endsAt!! - clock() <= FADE_MS
                SleepMode.EndOfChapter -> fading
            }
            if (!inFade) return false
            fading = false
            when (running) {
                is SleepMode.Minutes -> endsAt = clock() + running.minutes * 60_000L
                SleepMode.EndOfChapter -> extraChapters++
            }
            return true
        }
        val fired = firedAt ?: return false
        val mode = lastMode ?: return false
        if (clock() - fired > SHAKE_GRACE_MS) return false
        start(mode)
        return true
    }

    companion object {
        const val FADE_MS = 10_000L
        const val SHAKE_GRACE_MS = 60_000L
        private const val BOUNDARY_MS = 1_500L
        private val NEUTRAL = TimerTick(1f, false, null)
    }
}
