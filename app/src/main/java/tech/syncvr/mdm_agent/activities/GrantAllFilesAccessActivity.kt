package tech.syncvr.mdm_agent.activities

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.annotation.RequiresApi
import tech.syncvr.mdm_agent.R

class GrantAllFilesAccessActivity : Activity() {
    private val REQ_ALL_FILES = 42
    private val tag = "GrantAllFilesAccessActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                )

        setContentView(R.layout.activity_grant_all_files)

        val dpm = getSystemService(DevicePolicyManager::class.java)
        if (dpm.isLockTaskPermitted(packageName)) {
            startLockTask()
        }
        Log.d(tag, "Activity asking user permission is created")
    }

    // 4) Prevent Back button
    override fun onBackPressed() {
        // swallow it
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onResume() {
        super.onResume()
        initView()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun initView() {

        val btnSettings = findViewById<Button>(R.id.btnSettings)
        val textTitle = findViewById<TextView>(R.id.tvTitle)
        val textDescription = findViewById<TextView>(R.id.tvDescription)

        if (Environment.isExternalStorageManager()) {
            textTitle.setText(R.string.permission_title_pos)
            textDescription.setText(R.string.permission_description_pos)
            btnSettings.isEnabled = false
        } else {
            textTitle.setText(R.string.permission_title_neg)
            textDescription.setText(R.string.permission_description_neg)
            btnSettings.isEnabled = true
        }

        btnSettings.setOnClickListener {
            openAppSettings()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun openAppSettings() {
        val uri = Uri.fromParts("package", packageName, null)
        val settingsIntent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = uri
        }
        startActivityForResult(settingsIntent, REQ_ALL_FILES)


    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val granted = Environment.isExternalStorageManager()

        if (granted) {
            stopLockTask()
            finish()
            Log.d(tag, "Returning back from settings with result granted = $granted")
        }
    }
}
