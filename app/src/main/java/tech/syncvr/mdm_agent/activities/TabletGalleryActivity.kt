package tech.syncvr.mdm_agent.activities

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import tech.syncvr.mdm_agent.R

@AndroidEntryPoint
class TabletGalleryActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "TabletGalleryActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tablet_gallery)

        Log.d(TAG, "STARTING TABLET GALLERY ACTIVITY!")
    }

    override fun onResume() {
        super.onResume()

        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (dpm.isLockTaskPermitted(packageName)) {
            Log.d(TAG, "TABLET GALLERY ACTIVITY SETTING LOCK TASK MODE!")
            startLockTask()
        }
    }

}