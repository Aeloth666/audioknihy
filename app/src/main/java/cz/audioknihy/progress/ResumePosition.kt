package cz.audioknihy.progress

/** Po pauze delší než [pauseThresholdMs] se vrátí o [rewindMs], aby posluchač chytil souvislost. */
fun resumePositionMs(
    savedMs: Long,
    lastPlayedAt: Long,
    now: Long,
    rewindMs: Long,
    pauseThresholdMs: Long = 180_000L,
): Long = if (now - lastPlayedAt > pauseThresholdMs) (savedMs - rewindMs).coerceAtLeast(0) else savedMs
