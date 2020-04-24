@file:Suppress("NOTHING_TO_INLINE")

package org.openchaos.android.coverlock.service

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.*
import android.telephony.TelephonyManager
import android.util.Log
import androidx.preference.PreferenceManager
import org.openchaos.android.coverlock.receiver.LockAdmin


class ProximityLocker(_context: Context, _handler: Handler = Handler(Looper.getMainLooper())) : SensorEventListener {
    private val TAG = this.javaClass.simpleName

    private val context = _context

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    private val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE)!! as DevicePolicyManager

    private val sensorHandler = _handler
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE)!! as SensorManager
    private val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)!!

    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager?
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager?
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?

    private var coverState: Boolean = false
    private var threshold: Float = Float.NEGATIVE_INFINITY
    private var wakeLock: PowerManager.WakeLock? = null


    fun start(): Boolean {
        Log.d(TAG, "start()")

        // TODO: decide on "best" values for samplingPeriod and maxReportLatency
        return sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL, sensorHandler)
    }

    fun stop() {
        Log.d(TAG, "stop()")

        sensorManager.unregisterListener(this, sensor)
        cancelAction()
    }

    private fun cancelAction() {
        Log.d(TAG, "cancelAction()")

        // cancel delayed locking/waking action, release wake lock if held
        sensorHandler.removeCallbacksAndMessages(null)
        if (wakeLock?.isHeld == true) wakeLock?.release()
    }

    private inline fun shouldLock(): Boolean = (
        devicePolicyManager.isAdminActive(ComponentName(context, LockAdmin::class.java)) &&
        powerManager?.isInteractive != false &&  // if true || null
        telephonyManager?.callState != TelephonyManager.CALL_STATE_OFFHOOK)

    private inline fun shouldWake(): Boolean = (
        powerManager?.isInteractive != true)

    private inline fun vibrate(milliseconds: Long) {
        if (prefs.getBoolean("Vibrate", false)) {
            vibrator?.vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        Log.d(TAG, "onSensorChanged()") // Log spam on virtual androids
        assert(event.sensor.type == Sensor.TYPE_PROXIMITY)

        val latency = (SystemClock.elapsedRealtimeNanos() - event.timestamp)/1000000f
        val rawValue = event.values[0]
        val newState = (rawValue < threshold)

        // nothing changed
        if (newState == coverState) {
            return
        }

        coverState = newState
        Log.d(TAG, (if (coverState) "" else "un") + "covered (value: $rawValue, latency: ${latency}ms)")

        cancelAction()

        val delayMillis = ((prefs.getString(if (coverState) "LockDelay" else "WakeDelay", null)?.toDoubleOrNull() ?: 0.0) * 1000).toLong()

        // keep CPU awake until handler finishes
        wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG)

        // -> covered. prepare to lock
        if (coverState && shouldLock() && prefs.getBoolean("ActionLock", false)) {
            wakeLock?.acquire(delayMillis + 500) // set wake lock timeout to delay + margin
            sensorHandler.postDelayed({
                if (shouldLock()) {
                    Log.i(TAG, "locking")
                    devicePolicyManager.lockNow()
                    vibrate(50)
                }
                wakeLock?.release()
            }, delayMillis)

            // -> uncovered. prepare to wake device
        } else if (!coverState && shouldWake() && prefs.getBoolean("ActionWake", false)) {
            wakeLock?.acquire(delayMillis + 500)
            sensorHandler.postDelayed({
                if (shouldWake()) {
                    Log.i(TAG, "awake!")
                    @Suppress("DEPRECATION") // only FULL_WAKE_LOCK works the way we woke
                    powerManager?.newWakeLock((PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP), TAG)?.acquire(0)
                    vibrate(100)
                }
                wakeLock?.release()
            }, delayMillis)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        Log.d(TAG, "onAccuracyChanged($accuracy)")
        assert(sensor.type == Sensor.TYPE_PROXIMITY)

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
