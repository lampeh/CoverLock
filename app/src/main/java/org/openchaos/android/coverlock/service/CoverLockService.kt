package org.openchaos.android.coverlock.service

import android.app.*
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.*
import android.telephony.TelephonyManager
import android.util.Log
import androidx.preference.PreferenceManager
import org.openchaos.android.coverlock.MainActivity
import org.openchaos.android.coverlock.R
import org.openchaos.android.coverlock.receiver.LockAdmin


// TODO: maybe cache preference values, reload service on change

class CoverLockService : Service(), SensorEventListener {
    private val TAG = this.javaClass.simpleName

    companion object Status { var isRunning: Boolean = false }

    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var sensorManager: SensorManager
    private lateinit var sensor: Sensor
    private lateinit var adminComponentName: ComponentName
    private lateinit var handler: Handler
    private lateinit var prefs: SharedPreferences

    private var telephonyManager: TelephonyManager? = null
    private var powerManager: PowerManager? = null
    private var vibrator: Vibrator? = null

    private var threshold: Float = Float.NEGATIVE_INFINITY
    private var coverState: Boolean = false


    override fun onBind(intent: Intent): IBinder? {
        Log.e(TAG, "onBind() should not be called")
        return null
    }

    override fun onCreate() {
        Log.d(TAG, "onCreate()")

        // empty DeviceAdminReceiver
        adminComponentName = ComponentName(this, LockAdmin::class.java)

        // optional components
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager?
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager?
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?

        // required components
        try {
            devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE)!! as DevicePolicyManager
            sensorManager = getSystemService(Context.SENSOR_SERVICE)!! as SensorManager
            sensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)!!
            handler = Handler()
            prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "Error in required components", e)
            stopSelf() // TODO: test it
            return
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand()")

        if (!isRunning) {
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL, sensor.maxDelay)

            // TODO: cleanup
            // TODO: set flags in ContentIntent?
            // TODO: getSystemService null check?
            startForeground(
                23,
                Notification.Builder(
                        applicationContext,
                        NotificationChannel(TAG, getString(R.string.srv_name), NotificationManager.IMPORTANCE_LOW).let {
                            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager?)?.createNotificationChannel(it)
                            it.id
                        })
                    .setSubText(getString(R.string.srv_desc))
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentIntent(
                        PendingIntent.getActivity(
                            applicationContext,
                            0,
                            Intent(applicationContext, MainActivity::class.java),
                            0
                        )
                    )
                    .build()
            )

            isRunning = true
        }

        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy()")

        // TODO: is this check necessary?

        if (::sensorManager.isInitialized && ::sensor.isInitialized) {
            sensorManager.unregisterListener(this, sensor)
        }

        if (::handler.isInitialized) {
            handler.removeCallbacksAndMessages(null)
        }

        stopForeground(true)

        isRunning = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        //Log.d(TAG, "onSensorChanged()") // Log spam on virtual androids
        assert(event.sensor.type == Sensor.TYPE_PROXIMITY)

        val rawValue = event.values[0]
        val newState = (rawValue <= threshold)

        if (newState == coverState) {
            return
        }

        coverState = newState
        Log.d(TAG, (if (coverState) "" else "un") + "covered ($rawValue)")

        // cancel delayed action
        handler.removeCallbacksAndMessages(null)

        // TODO: ignore isInteractive?
        // TODO: whitelist apps and modes
        fun shouldLock(): Boolean = (
            devicePolicyManager.isAdminActive(adminComponentName) &&
            powerManager?.isInteractive != false &&  // if true || null
            telephonyManager?.callState != TelephonyManager.CALL_STATE_OFFHOOK)

        fun shouldWake(): Boolean = (
            powerManager?.isInteractive != true)

        fun vibrate(milliseconds: Long) {
            if (prefs.getBoolean("Vibrate", false)) {
                vibrator?.vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }

        if (coverState && prefs.getBoolean("ActionLock", false)) {
            handler.postDelayed({
                if (shouldLock()) {
                    Log.i(TAG, "locking")
                    devicePolicyManager.lockNow()
                    vibrate(50)
                }
            }, 2000) // TODO: use LockDelay
        } else if (!coverState && prefs.getBoolean("ActionWake", false)) {
            handler.postDelayed({
                if (shouldWake()) {
                    Log.i(TAG, "awake!")
                    @Suppress("DEPRECATION")
                    powerManager?.newWakeLock(
                        (PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE),
                        TAG
                    )?.acquire(0)
                    vibrate(100)
                }
            }, 300) // TODO: use WakeDelay
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
            }
            else -> {
                threshold = sensor.maximumRange / 2 // TODO: use LockDistance/WakeDistance
                Log.d(TAG, "maxRange = ${sensor.maximumRange}, threshold = $threshold")
            }
        }
    }
}
