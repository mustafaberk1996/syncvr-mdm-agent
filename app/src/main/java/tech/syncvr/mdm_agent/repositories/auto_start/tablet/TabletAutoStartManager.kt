package tech.syncvr.mdm_agent.repositories.auto_start.tablet

import android.app.ActivityOptions
import android.app.WallpaperManager
import android.app.WallpaperManager.FLAG_LOCK
import android.app.WallpaperManager.FLAG_SYSTEM
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import tech.syncvr.mdm_agent.R
import tech.syncvr.mdm_agent.activities.TabletGalleryActivity
import tech.syncvr.mdm_agent.receivers.DeviceOwnerReceiver
import tech.syncvr.mdm_agent.repositories.auto_start.AutoStartManager

class TabletAutoStartManager(context: Context) : AutoStartManager(context) {

    companion object {
        private const val TAG = "TabletAutoStartRepository"
        const val BROWSER_PACKAGE = "com.android.chrome"
    }

    init {
        WallpaperManager.getInstance(context)
            .setResource(+R.drawable.background, FLAG_SYSTEM or FLAG_LOCK)
    }

    override fun getAutoStartPackage(): String? {
        val intent = Intent(Intent.ACTION_MAIN).also {
            it.addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo =
            context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                ?: return null

        return if (resolveInfo.activityInfo.packageName == context.packageName) {
            context.packageName
        } else {
            null
        }
    }

    override fun setAutoStart(
        autoStartPackageName: String,
        allowedPackages: List<String>
    ): Boolean {

        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, DeviceOwnerReceiver::class.java)

        // always set the LockTask permitted packages, they may have changed!
        dpm.setLockTaskPackages(
            adminComponent,
            allowedPackages.toMutableList().also {
                it.add(context.packageName)
                it.add("com.android.settings")
                it.add(BROWSER_PACKAGE)
                it.add("com.android.systemui")
                it.add("com.google.android.captiveportallogin")
                it.add("com.steinwurf.adbjoinwifi")
                it.add("com.android.wifi.dialog")
                // system update on Samsung's A7 rely on that
                it.add("com.wssyncmldm")
            }.toTypedArray()
        )

        // for tablets we don't care which app is in autostart, we just enable kiosk mode
        if (getAutoStartPackage() == context.packageName) {
            return false
        }

        // set LockTask permitted features
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            dpm.setLockTaskFeatures(
                adminComponent,
                DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS or
                        DevicePolicyManager.LOCK_TASK_FEATURE_HOME or
//                        DevicePolicyManager.LOCK_TASK_FEATURE_OVERVIEW or
                        DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO
            )
        }

        // enable the TabletGalleryActivity
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, TabletGalleryActivity::class.java),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )

        // set the TabletGalleryActivity as Home
        IntentFilter(Intent.ACTION_MAIN).also {
            it.addCategory(Intent.CATEGORY_HOME)
            it.addCategory(Intent.CATEGORY_DEFAULT)

            dpm.addPersistentPreferredActivity(
                adminComponent,
                it,
                ComponentName(context, TabletGalleryActivity::class.java)
            )
        }

        // start the TabletGalleryActivity
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val options = ActivityOptions.makeBasic().also {
                it.setLockTaskEnabled(true)
            }
            val intent = Intent(context, TabletGalleryActivity::class.java).also {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            context.startActivity(intent, options.toBundle())
        }

        return true
    }

    override fun clearAutoStartPackage(): Boolean {

        // clear TabletGalleryActivity as preferred HOME activity
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        dpm.clearPackagePersistentPreferredActivities(
            ComponentName(context, DeviceOwnerReceiver::class.java),
            context.packageName
        )

        // unset the LockTaskMode permitted packages
        dpm.setLockTaskPackages(
            ComponentName(context, DeviceOwnerReceiver::class.java),
            arrayOf()
        )

        // disable TabletGalleryActivity component, to force Android to return back to actual home screen
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, TabletGalleryActivity::class.java),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )

        return true
    }

    override fun requiresReboot(): Boolean {
        return false
    }

    private fun logHomeApps() {
        val intent = Intent(Intent.ACTION_MAIN).also {
            it.addCategory(Intent.CATEGORY_HOME)
//            it.addCategory(Intent.CATEGORY_DEFAULT)
        }

        val resolveInfoList =
            context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        resolveInfoList.forEach {
            Log.d(TAG, "HOME APP ResolveInfo: $it")
        }

        val resolveInfo =
            context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        Log.d(TAG, "Best Resolve Info: $resolveInfo")

        Log.d(TAG, "------")
    }

    private fun getSystemApps(): List<String> {
        return context.packageManager.getInstalledPackages(PackageManager.GET_META_DATA)
            .filter {
                (it.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) == ApplicationInfo.FLAG_SYSTEM
            }.map {
                it.packageName
            }.onEach {
                Log.d(TAG, "ALLOWING SYSTEM APP: $it")
            }
    }

    /**
     * Some sources of info:
     * https://developer.android.com/reference/kotlin/android/app/admin/DevicePolicyManager#setlocktaskfeatures
     * https://developer.android.com/work/dpc/dedicated-devices/lock-task-mode
     * https://developer.android.com/work/dpc/dedicated-devices/cookbook#be_the_home_app
     *
     * Strategy:
     * - Implement a Home Screen activity that displays buttons for all managed and external apps in the configuration
     * - If Auto-Start is enabled, enable Lock Task mode
     *
     */
}