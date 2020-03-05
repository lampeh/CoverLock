package org.openchaos.android.coverlock.service

import android.app.*
import android.app.admin.DevicePolicyManager
import android.content.*
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

    private val notificationId = 23

    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var sensorManager: SensorManager
    private lateinit var sensor: Sensor
    private lateinit var adminComponentName: ComponentName
    private lateinit var handler: Handler
    private lateinit var prefs: SharedPreferences
    private lateinit var notificationManager: NotificationManager
    private lateinit var notification: Notification.Builder

    private var telephonyManager: TelephonyManager? = null
    private var powerManager: PowerManager? = null
    private var vibrator: Vibrator? = null

    private var threshold: Float = Float.NEGATIVE_INFINITY
    private var coverState: Boolean = false
    private var sensorRunning: Boolean = false


    private fun startSensor() {
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL, sensor.minDelay)
        sensorRunning = true
        notificationManager.notify(notificationId, notification.setSubText(getString(R.string.srv_desc)).build())
    }

    private fun stopSensor() {
        sensorManager.unregisterListener(this, sensor)
        sensorRunning = false
        notificationManager.notify(notificationId, notification.setSubText(null).build())
    }

    private fun changeState(interactive: Boolean) {
        val shouldRun = prefs.getBoolean(if (interactive) "ActionLock" else "ActionWake", false)
        when {
            shouldRun && !sensorRunning -> startSensor()
            !shouldRun && sensorRunning -> stopSensor()
        }
    }

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "onReceive()")

            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> changeState(true)
                Intent.ACTION_SCREEN_OFF -> changeState(false)
                else -> Log.e(TAG, "unknown intent")
            }
        }
    }

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
            notificationManager = getSystemService(Context.NOTIFICATION_SERVICE)!! as NotificationManager
        } catch (e: Exception) {
            Log.e(TAG, "Error in required components", e)
            stopSelf() // TODO: test it
            return
        }

        // TODO: cleanup
        // TODO: set flags in ContentIntent?
        // TODO: getSystemService null check?
        notification = Notification.Builder(this,
            NotificationChannel(TAG, getString(R.string.srv_name), NotificationManager.IMPORTANCE_LOW).let {
                (getSystemService(NOTIFICATION_SERVICE) as NotificationManager?)?.createNotificationChannel(it)
                it.id
            })
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(PendingIntent.getActivity(applicationContext, 0,
                Intent(applicationContext, MainActivity::class.java), 0)
        )

        changeState(powerManager?.isInteractive != false)

        startForeground(notificationId, notification.build())

        // start/stop sensor on screen change
        // TODO: it's only useful if either actionLock/Wake is disabled
        registerReceiver(screenStateReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        })

        isRunning = true
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy()")

        unregisterReceiver(screenStateReceiver)
        stopSensor()
        handler.removeCallbacksAndMessages(null)
        notificationManager.cancelAll()
        stopForeground(false)

        isRunning = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        //Log.d(TAG, "onSensorChanged()") // Log spam on virtual androids
        assert(event.sensor.type == Sensor.TYPE_PROXIMITY)

        val rawValue = event.values[0]
        val newState = (rawValue <= threshold)

        // nothing changed
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
                    @Suppress("DEPRECATION") // only FULL_WAKE_LOCK works the way we woke
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
                return
            }
        }

        threshold = sensor.maximumRange / 2 // TODO: use LockDistance/WakeDistance
        Log.d(TAG, "maxRange = ${sensor.maximumRange}, threshold = $threshold")
    }
}
