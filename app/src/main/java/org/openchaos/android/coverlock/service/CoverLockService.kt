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
import android.util.Log
import org.openchaos.android.coverlock.MainActivity


const val TAG = "CoverLockService"

class CoverLockService : Service(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private lateinit var sensor: Sensor
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var compName: ComponentName
    private lateinit var handler: Handler

    private var maxRange: Float = 0f
    private var threshold: Float = 0f
    private var locked: Boolean = false
    private var id: Int = 0

    override fun onBind(intent: Intent): IBinder? {
        Log.e(TAG, "onBind() should not be called")
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStart($flags, $startId)")
        super.onStartCommand(intent, flags, startId)

        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        startForeground(23, Notification.Builder(this, NotificationChannel("23", getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW).let {
                nm?.createNotificationChannel(it)
                it.id })
            .setContentText(getString(R.string.srv_desc))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
        )

        id = startId

        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        compName = ComponentName(this, LockAdmin::class.java)

        sensorManager = getSystemService(Context.SENSOR_SERVICE)!! as SensorManager
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)!!
        maxRange = sensor.maximumRange
        threshold = maxRange / 2

        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL, 1000000)

        handler = Handler()

        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy($id)")

        if (::sensorManager.isInitialized) {
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

        val oldState = locked
        locked = (event.values[0] <= threshold)

        if (locked != oldState) {
            Log.d(TAG, (if (locked) "" else "un") + "locked")
            if (devicePolicyManager.isAdminActive(compName)) {
                if (locked) {
                    handler.postDelayed({ devicePolicyManager.lockNow() }, 2000)
                } else {
                    handler.removeCallbacksAndMessages(null)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        Log.d(TAG, "onAccuracyChanged($accuracy)")

        maxRange = sensor.maximumRange
        threshold = maxRange / 2
        Log.d(TAG, "maxRange = $maxRange, threshold = $threshold")
    }
}
