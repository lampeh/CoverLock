package org.openchaos.android.coverlock.service

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.SystemClock
import android.util.Log


class ProximitySensor(_context: Context, _handler: Handler, _initialState: Boolean = false) : SensorEventListener {
    private val TAG = this.javaClass.simpleName

    private val context = _context

    private val sensorHandler = _handler
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY) as Sensor

    private var sensorState: Boolean = _initialState
    private var threshold: Float = Float.NEGATIVE_INFINITY


    fun start(): Boolean {
        Log.d(TAG, "start()")

        // TODO: decide on "best" values for samplingPeriod and maxReportLatency
        return sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL, sensorHandler)
    }

    fun stop() {
        Log.d(TAG, "stop()")

        sensorManager.unregisterListener(this, sensor)
    }

    override fun onSensorChanged(event: SensorEvent) {
        Log.d(TAG, "onSensorChanged()") // Log spam on virtual androids
        assert(event.sensor.type == Sensor.TYPE_PROXIMITY)

        val latency = (SystemClock.elapsedRealtimeNanos() - event.timestamp)/1000000f
        val rawValue = event.values[0]
        val newState = (rawValue < threshold)

        // nothing changed
        if (newState == sensorState) {
            return
        }

        sensorState = newState
        Log.d(TAG, (if (sensorState) "" else "un") + "covered (value: $rawValue, latency: ${latency}ms)")

        // TODO: resolve CoverLockService dependency while respecting handler namespace
        // TODO: check return code?
        sensorHandler.sendEmptyMessage(if (sensorState) CoverLockService.SENSOR_CLOSED else CoverLockService.SENSOR_OPEN)
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        Log.d(TAG, "onAccuracyChanged($accuracy)")
        assert(sensor.type == Sensor.TYPE_PROXIMITY)

        // TODO: notify service via handler?

        when (accuracy) {
            SensorManager.SENSOR_STATUS_NO_CONTACT,
            SensorManager.SENSOR_STATUS_UNRELIABLE -> {
                threshold = Float.NEGATIVE_INFINITY
                Log.i(TAG, "sensor offline or unreliable")
                return
            }
        }

        threshold = sensor.maximumRange / 2 // TODO: use LockDistance/WakeDistance
        Log.d(TAG, "maxRange = ${sensor.maximumRange}, threshold = $threshold")
    }
}
