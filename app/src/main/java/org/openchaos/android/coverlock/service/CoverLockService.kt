package org.openchaos.android.coverlock.service

import android.app.*
import org.openchaos.android.coverlock.R
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.IBinder
import android.os.PowerManager
import android.telephony.TelephonyManager
import android.util.Log


class CoverLockService : Service(), SensorEventListener {
    private val TAG = this.javaClass.simpleName

    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var sensorManager: SensorManager
    private lateinit var sensor: Sensor
    private lateinit var adminComponentName: ComponentName
    private lateinit var handler: Handler

    private var telephonyManager: TelephonyManager? = null
    private var powerManager: PowerManager? = null

    private var threshold: Float = 0f
    private var covered: Boolean = false

    private var id: Int = 0

    override fun onBind(intent: Intent): IBinder? {
        Log.e(TAG, "onBind() should not be called")
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStart($flags, $startId)")
        super.onStartCommand(intent, flags, startId)

        // TODO: cleanup
        id = startId
        startForeground(23, Notification.Builder(this, NotificationChannel("23", getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW).let {
                (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)?.createNotificationChannel(it)
                it.id })
            .setContentText(getString(R.string.srv_desc))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
        )

        // Optional components
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager?
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager?

        try {
            devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE)!! as DevicePolicyManager
            sensorManager = getSystemService(Context.SENSOR_SERVICE)!! as SensorManager
            sensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)!!
        } catch (e: Exception) {
            Log.e(TAG, "Error in required components", e)
            stopSelf()
            return START_NOT_STICKY
        }

        adminComponentName = ComponentName(this, LockAdmin::class.java)

        threshold = sensor.maximumRange / 2
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL, 1000000)

        handler = Handler()

        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy($id)")

        // TODO: is this check necessary?

        if (::sensorManager.isInitialized && ::sensor.isInitialized) {
            sensorManager.unregisterListener(this, sensor)
        }

        if (::handler.isInitialized) {
            handler.removeCallbacksAndMessages(null)
        }

        stopForeground(true)

        return super.onDestroy()
    }

    override fun onSensorChanged(event: SensorEvent) {
        Log.d(TAG, "onSensorChanged()")

        val oldState = covered
        covered = (event.values[0] <= threshold)

        if (covered != oldState) {
            Log.d(TAG, (if (covered) "" else "un") + "covered")
            if (devicePolicyManager.isAdminActive(adminComponentName)) {
                if (covered) {
                    handler.postDelayed({
                        if (telephonyManager?.callState != TelephonyManager.CALL_STATE_OFFHOOK) {
                            Log.d(TAG, "locking")
                            devicePolicyManager.lockNow()
                        }
                    }, 2000)
                } else {
                    handler.removeCallbacksAndMessages(null)
/*
                    powerManager?.newWakeLock((PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE), TAG)?.apply {
                        acquire()
                        release()
                    }
 */
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        Log.d(TAG, "onAccuracyChanged($accuracy)")

        threshold = sensor.maximumRange / 2
        Log.d(TAG, "maxRange = ${sensor.maximumRange}, threshold = $threshold")
    }
}
