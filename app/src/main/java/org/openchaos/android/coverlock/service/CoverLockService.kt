package org.openchaos.android.coverlock.service

import android.app.*
import android.content.*
import android.content.res.Configuration
import android.media.AudioManager
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.preference.PreferenceManager
import org.openchaos.android.coverlock.MainActivity
import org.openchaos.android.coverlock.R


// TODO: maybe cache preference values, reload service on change
// TODO: disentangle & extract sensor listener class. maybe state change listener, too


class CoverLockService : Service() {
    private val TAG = this.javaClass.simpleName

    companion object {
        var serviceRunning: Boolean = false
            private set

        var sensorRunning: Boolean = false
            private set

        private const val notificationId = 23
    }

    private lateinit var notificationManager: NotificationManager
    private lateinit var notification: Notification.Builder

    private lateinit var prefs: SharedPreferences

    private var powerManager: PowerManager? = null

    private val sensorLock = mutableSetOf<String>()

    private lateinit var proximityLocker: ProximityLocker


    private fun startSensor() {
        Log.d(TAG, "startSensor()")

        if (sensorRunning) {
            return
        }

        if (sensorLock.isNotEmpty()) {
            Log.d(TAG, "sensor locked out: ${sensorLock.toString()}")
            return
        }

        sensorRunning = proximityLocker.start()
        notificationManager.notify(notificationId, notification.setSubText(getString(R.string.srv_desc)).build())
    }

    private fun stopSensor() {
        Log.d(TAG, "stopSensor()")

        if (!sensorRunning) {
            return
        }

        proximityLocker.stop()
        sensorRunning = false
        notificationManager.notify(notificationId, notification.setSubText(null).build()) // TODO: use icon to display sensor state
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

        // optional components
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager?

        /// TODO: more cleanup required
        // required components
        try {
            prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            notificationManager = getSystemService(Context.NOTIFICATION_SERVICE)!! as NotificationManager
            proximityLocker = ProximityLocker(applicationContext)
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

        // lock sensor if service started in landscape orientation
        if ((prefs.getBoolean("PauseLandscape", false)) && (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)) {
            changeLock("landscape", true)
        }

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
        notificationManager.cancel(notificationId)
        stopForeground(false)

        serviceRunning = false
    }

    // endregion
}
