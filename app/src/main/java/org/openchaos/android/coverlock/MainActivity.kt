package org.openchaos.android.coverlock

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.CompoundButton
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import org.openchaos.android.coverlock.service.CoverLockService
import org.openchaos.android.coverlock.receiver.LockAdmin


class MainActivity : FragmentActivity() {
    private val TAG = this.javaClass.simpleName

    private companion object {
        private const val REQUEST_ADMIN_ACCESS = 23
    }

    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponentName: ComponentName
    private lateinit var serviceIntent: Intent


    fun toggleAdmin(button: View) {
        Log.d(TAG, "toggleAdmin()")

        if ((button as CompoundButton).isChecked) {
            Log.d(TAG, "requesting device admin access")
            startActivityForResult(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponentName)
            }, REQUEST_ADMIN_ACCESS)
        } else {
            Log.d(TAG, "removing device admin access")
            devicePolicyManager.removeActiveAdmin(adminComponentName)
        }
    }

    fun toggleService(button: View) {
        Log.d(TAG, "toggleService()")

        if ((button as CompoundButton).isChecked) {
            Log.d(TAG, "starting service")
            startService(serviceIntent)
        } else {
            Log.d(TAG, "stopping service")
            stopService(serviceIntent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate()")
        super.onCreate(savedInstanceState)

        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponentName = ComponentName(applicationContext, LockAdmin::class.java)
        serviceIntent = Intent(applicationContext, CoverLockService::class.java)

        setContentView(R.layout.activity_main)
    }

    override fun onResume() {
        Log.d(TAG, "onResume()")
        super.onResume()

        devicePolicyManager.isAdminActive(adminComponentName).also { active ->
            Log.i(TAG, "device admin ${if (active) "en" else "dis"}abled")

            findViewById<CompoundButton>(R.id.btnAdminEnabled).apply {
                isChecked = active
                isEnabled = true
            }
        }

        findViewById<CompoundButton>(R.id.btnServiceEnabled).apply {
            isChecked = CoverLockService.serviceRunning
            isEnabled = true
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.d(TAG, "onActivityResult($requestCode, $resultCode")

        if (requestCode != REQUEST_ADMIN_ACCESS)
            return

        if (resultCode == RESULT_OK) {
            Toast.makeText(this, R.string.adminEnabled, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, R.string.adminError, Toast.LENGTH_SHORT).show()
        }

        return super.onActivityResult(requestCode, resultCode, data)
    }
}
