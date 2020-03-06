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

    companion object {
        var serviceRunning: Boolean = false
            private set

        var sensorRunning: Boolean = false
            private set

        var coverState: Boolean = false
            private set

        private const val notificationId = 23
    }

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


    private fun startSensor() {
        Log.d(TAG, "startSensor()")
        sensorManager.registerListener(this, sensor, sensor.maxDelay, sensor.maxDelay)
        sensorRunning = true
        notificationManager.notify(notificationId, notification.setSubText(getString(R.string.srv_desc)).build())
    }

    private fun stopSensor() {
        Log.d(TAG, "stopSensor()")
        sensorManager.unregisterListener(this, sensor)
        sensorRunning = false
        notificationManager.notify(notificationId, notification.setSubText(null).build())
    }

    private fun changeState(interactive: Boolean) {
        Log.d(TAG, "changeState($interactive)")
        val shouldRun = prefs.getBoolean(if (interactive) "ActionLock" else "ActionWake", false)
        when {
            shouldRun && !sensorRunning -> startSensor()
            !shouldRun && sensorRunning -> stopSensor()
        }
    }

    private val stateChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "onReceive(${intent?.action})")

            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> changeState(true)
                Intent.ACTION_SCREEN_OFF -> changeState(false)
                else -> Log.w(TAG, "unhandled intent")
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
            NotificationChannel(TAG, getString(R.string.srv_name), NotificationManager.IMPORTANCE_LOW).let { channel ->
                (getSystemService(NOTIFICATION_SERVICE) as NotificationManager?)?.createNotificationChannel(channel)
                channel.id
            })
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(PendingIntent.getActivity(applicationContext, 0,
                Intent(applicationContext, MainActivity::class.java), 0)
        )

        changeState(powerManager?.isInteractive != false)

        startForeground(notificationId, notification.build())

        // start/stop sensor on screen change
        // TODO: it's only useful if either actionLock/Wake is disabled
        registerReceiver(stateChangeReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        })

        serviceRunning = true
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy()")

        unregisterReceiver(stateChangeReceiver)
        stopSensor()
        handler.removeCallbacksAndMessages(null)
        notificationManager.cancelAll()
        stopForeground(false)

        serviceRunning = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        //Log.d(TAG, "onSensorChanged()") // Log spam on virtual androids
        assert(event.sensor.type == Sensor.TYPE_PROXIMITY)

        val latency = (SystemClock.elapsedRealtimeNanos() - event.timestamp)/1000000f
        val rawValue = event.values[0]
        val newState = (rawValue <= threshold)

        // nothing changed
        if (newState == coverState) {
            return
        }

        coverState = newState
        Log.d(TAG, (if (coverState) "" else "un") + "covered (value: $rawValue, latency: ${latency}ms)")

        // cancel delayed action
        // TODO: release debug wake lock
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
            // TODO: handler runs late. aquire wake lock first?
            var wakeLock: PowerManager.WakeLock? = null
            if (prefs.getBoolean("DebugWake", false)) {
                wakeLock = powerManager?.newWakeLock(PowerManager.FULL_WAKE_LOCK, TAG)?.apply { acquire(1000) }
                Log.d(TAG, "wake lock acquired")
            }
            vibrate(50)
            handler.postDelayed({
                if (shouldWake()) {
                    Log.i(TAG, "awake!")
                    wakeLock?.release()
                    @Suppress("DEPRECATION") // only FULL_WAKE_LOCK works the way we woke
                    powerManager?.newWakeLock(
                        (PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP),
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
