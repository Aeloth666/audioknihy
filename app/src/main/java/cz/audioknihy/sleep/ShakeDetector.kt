package cz.audioknihy.sleep

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/** Zatřesení telefonem (zrychlení přes 2,7 g). Zapíná se jen při běžícím časovači. */
class ShakeDetector(context: Context, private val onShake: () -> Unit) : SensorEventListener {
    private val sensors = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var lastShake = 0L
    private var running = false

    fun start() {
        if (running) return
        val acc = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        running = sensors.registerListener(this, acc, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        if (!running) return
        sensors.unregisterListener(this)
        running = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        val (x, y, z) = Triple(event.values[0], event.values[1], event.values[2])
        val g = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
        val now = System.currentTimeMillis()
        if (g > THRESHOLD_G && now - lastShake > DEBOUNCE_MS) {
            lastShake = now
            onShake()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    companion object {
        const val THRESHOLD_G = 2.7f
        const val DEBOUNCE_MS = 1_000L
    }
}
