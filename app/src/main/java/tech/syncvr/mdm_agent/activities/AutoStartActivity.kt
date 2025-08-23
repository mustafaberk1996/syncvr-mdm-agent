package tech.syncvr.mdm_agent.activities

import android.content.Context
import android.content.Intent


private fun Context.getMainActivity(packageName: String): String {
    val intent = Intent(Intent.ACTION_MAIN).also {
        it.setPackage(packageName)
    }
    val resolveInfo = packageManager.resolveActivity(intent, 0)
        ?: return "com.unity3d.player.UnityPlayerActivity"

    return resolveInfo.activityInfo.name
}