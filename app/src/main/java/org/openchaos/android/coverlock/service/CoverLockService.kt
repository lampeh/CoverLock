@file:Suppress("NOTHING_TO_INLINE")

package org.openchaos.android.coverlock.service

import android.app.*
import android.app.admin.DevicePolicyManager
import android.content.*
import android.content.res.Configuration
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.os.*
import android.telephony.TelephonyManager
import android.util.Log
import androidx.preference.PreferenceManager
import org.openchaos.android.coverlock.MainActivity
import org.openchaos.android.coverlock.R
import org.openchaos.android.coverlock.receiver.LockAdmin


// TODO: maybe cache preference values, reload service on change
// TODO: disentangle & extract sensor listener class. maybe state change listener, too


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
    private lateinit var adminComponentName: ComponentName

    private lateinit var sensorManager: SensorManager
    private lateinit var sensorHandler: Handler
    private lateinit var sensor: Sensor

    private lateinit var notificationManager: NotificationManager
    private lateinit var notification: Notification.Builder
    private lateinit var prefs: SharedPreferences

    private var telephonyManager: TelephonyManager? = null
    private var powerManager: PowerManager? = null
    private var vibrator: Vibrator? = null

    private var threshold: Float = Float.NEGATIVE_INFINITY
    private var wakeLock: PowerManager.WakeLock? = null

    private val sensorLock = mutableSetOf<String>()


    private fun startSensor() {
        Log.d(TAG, "startSensor()")

        if (sensorLock.isNotEmpty()) {
            Log.d(TAG, "sensor locked out: ${sensorLock.toString()}")
            return
        }

        // TODO: decide on "best" values for samplingPeriod and maxReportLatency
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL, sensorHandler)
        sensorRunning = true
        notificationManager.notify(notificationId, notification.setSubText(getString(R.string.srv_desc)).build())
    }

    private fun stopSensor() {
        Log.d(TAG, "stopSensor()")

        sensorManager.unregisterListener(this, sensor)
        cancelAction()
        sensorRunning = false
        notificationManager.notify(notificationId, notification.setSubText(null).build())
    }

    private fun changeState(interactive: Boolean?) {
        Log.d(TAG, "changeState($interactive)")

        val shouldRun = prefs.getBoolean(if (interactive != false) "ActionLock" else "ActionWake", false)
        when {
            shouldRun && !sensorRunning -> startSensor()
            !shouldRun && sensorRunning -> stopSensor()
        }
    }

    private fun addLock(lock: String) {
        Log.d(TAG, "addLock($lock)")

        sensorLock.add(lock)
        stopSensor()
    }

    private fun removeLock(lock: String) {
        Log.d(TAG, "removeLock($lock)")

        sensorLock.remove(lock)
        if (sensorLock.isEmpty()) {
            changeState(powerManager?.isInteractive)
        } else {
            Log.d(TAG, "locks remaining: ${sensorLock.toString()}")
        }
    }

    private fun clearLocks() {
        Log.d(TAG, "clearLocks()")

        sensorLock.clear()
        changeState(powerManager?.isInteractive)
    }

    private fun changeLock(lock: String, locked: Boolean) {
        when (locked) {
            true -> addLock(lock)
            false -> removeLock(lock)
        }
    }

    private val stateChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "onReceive(${intent?.action})")

            // TODO: changing preferences does not reset sensor locks
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> changeState(true)
                Intent.ACTION_SCREEN_OFF -> changeState(false)
                Intent.ACTION_CONFIGURATION_CHANGED -> {
                    if (prefs.getBoolean("PauseLandscape", false)) {
                        changeLock("landscape",
                            (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE))
                    }
                }
                AudioManager.ACTION_HEADSET_PLUG -> {
                    if (prefs.getBoolean("PauseHeadset", false)) {
                        changeLock("headset-${intent.getStringExtra("name") ?: "default"}",
                            (intent.getIntExtra("state", 0) == 1))
                    }
                }
                else -> Log.w(TAG, "unhandled intent")
            }
        }
    }


    // region service

    override fun onBind(intent: Intent): IBinder? {
        Log.e(TAG, "onBind() should not be called")
        return null
    }

    override fun onCreate() {
        Log.d(TAG, "onCreate()")
        assert(!serviceRunning)

        // device admin auth token
        adminComponentName = ComponentName(applicationContext, LockAdmin::class.java)

        // optional components
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager?
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager?
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?

        /// TODO: more cleanup required
        // required components
        try {
            devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE)!! as DevicePolicyManager
            sensorManager = getSystemService(Context.SENSOR_SERVICE)!! as SensorManager
            sensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)!!
            sensorHandler = Handler(mainLooper) // TODO: run sensor in its own thread?
            prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            notificationManager = getSystemService(Context.NOTIFICATION_SERVICE)!! as NotificationManager
        } catch (e: Exception) {
            Log.e(TAG, "Error in required components", e)
            stopSelf() // TODO: test it
            return
        }

        // prepare proto-notification
        notification = Notification.Builder(this,
            NotificationChannel(TAG, getString(R.string.srv_name), NotificationManager.IMPORTANCE_LOW).let { channel ->
                notificationManager.createNotificationChannel(channel)
                channel.id
            })
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(PendingIntent.getActivity(applicationContext, 0,
                Intent(applicationContext, MainActivity::class.java), 0)
            )

        // foreground services can be started at boot and receive sensor updates during sleep
        startForeground(notificationId, notification.build())

        // assume initial screen state
        changeState(powerManager?.isInteractive)

        // TODO: register only the actions required by preferences, update on change
        registerReceiver(stateChangeReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_CONFIGURATION_CHANGED)
            addAction(AudioManager.ACTION_HEADSET_PLUG)
        })

        serviceRunning = true
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy()")

        if (!serviceRunning) {
            return
        }

        unregisterReceiver(stateChangeReceiver)
        stopSensor()
        notificationManager.cancelAll()
        stopForeground(false)

        serviceRunning = false
    }

    // endregion


    // region sensor event handler

    private fun cancelAction() {
        Log.d(TAG, "cancelAction()")

        // cancel delayed locking/waking action, release wake lock if held
        sensorHandler.removeCallbacksAndMessages(null)
        if (wakeLock?.isHeld == true) wakeLock?.release()
    }

    private inline fun shouldLock(): Boolean = (
        devicePolicyManager.isAdminActive(adminComponentName) &&
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

    // endregion
}
