package org.openchaos.android.coverlock

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.CompoundButton
import android.widget.Switch
import android.widget.Toast
import org.openchaos.android.coverlock.service.CoverLockService
import org.openchaos.android.coverlock.service.LockAdmin


class MainActivity : Activity(), CompoundButton.OnCheckedChangeListener {
    private val TAG = this.javaClass.simpleName

    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponentName: ComponentName
    private lateinit var serviceIntent: Intent
    private lateinit var enable: Switch


    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate()")

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponentName = ComponentName(this, LockAdmin::class.java)
        serviceIntent = Intent(this, CoverLockService::class.java)

        enable = findViewById<Switch>(R.id.swEnable).also { it.setOnCheckedChangeListener(this) }
    }

    override fun onResume() {
        super.onResume()
        enable.isChecked = (devicePolicyManager.isAdminActive(adminComponentName))
    }

    override fun onCheckedChanged(buttonView: CompoundButton?, isChecked: Boolean) {
        if (isChecked) {
            startActivityForResult(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponentName)
            }, 23)
        } else {
            devicePolicyManager.removeActiveAdmin(adminComponentName);
            stopService(serviceIntent)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != 23)
                return

        if (resultCode == RESULT_OK) {
            Toast.makeText(this, "You have enabled the Admin Device features", Toast.LENGTH_SHORT).show();
            startService(Intent(this, CoverLockService::class.java))
        } else {
            Toast.makeText(this, "Problem to enable the Admin Device features", Toast.LENGTH_SHORT).show();
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
}
