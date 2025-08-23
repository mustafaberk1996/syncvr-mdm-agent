package tech.syncvr.mdm_agent.utils

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.annotation.RequiresApi
import tech.syncvr.mdm_agent.activities.GrantAllFilesAccessActivity

object ExternalStoragePermissionManager {
    private val tag = "ExternalStoragePermissionManager"

    @RequiresApi(Build.VERSION_CODES.R)
    fun requestPermissionIfRequired(ctx: Context) {
        if (!Environment.isExternalStorageManager()) {
            Log.d(tag, "permission not granted. Starting activity to grant")
            val intent = Intent(ctx, GrantAllFilesAccessActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            ctx.startActivity(intent)
        } else {
            Log.d(tag, "permission already granted")
        }
    }
}
