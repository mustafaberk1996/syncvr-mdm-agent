package tech.syncvr.mdm_agent.receivers

import android.app.admin.DevicePolicyManager
import android.app.admin.FreezePeriod
import android.app.admin.SystemUpdatePolicy
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_BOOT_COMPLETED
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import tech.syncvr.mdm_agent.app_usage.app_sessions.UsageStatsEventsRepository
import tech.syncvr.mdm_agent.logging.AnalyticsLogger
import tech.syncvr.mdm_agent.utils.ExternalStoragePermissionManager
import java.time.MonthDay
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG: String = "BootCompletedReceiver"
    }

    @Inject
    lateinit var usageStatsEventsRepository: UsageStatsEventsRepository

    @Inject
    lateinit var analyticsLogger: AnalyticsLogger

    override fun onReceive(context: Context, p1: Intent?) {
        if (p1?.action == ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "ACTION_BOOT_COMPLETED")
            usageStatsEventsRepository.onBootCompleted()

            try {
                reapplySystemUpdatePolicy(context)
            } catch (ex: Exception) {
                Log.d(TAG, "Exception at reapplySystemUpdatePolicy: ${ex.message} ")
                analyticsLogger.logErrorMsg(
                    AnalyticsLogger.Companion.LogEventType.MDM_EVENT,
                    "Exception at reapplySystemUpdatePolicy: ${ex.message}"
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Log.d(TAG, "Starting handler for asking for permission if required")
                Handler(Looper.getMainLooper()).postDelayed({
                    Log.d(TAG, "asking for permission if required")
                    ExternalStoragePermissionManager.requestPermissionIfRequired(context)
                }, 15_000)
            } else {
                Log.d(TAG, "Lower then BUild.version.R")
            }

        } else {
            return
        }
    }

    private fun reapplySystemUpdatePolicy(ctx: Context) {
        val dpm = ctx.getSystemService(DevicePolicyManager::class.java) ?: run {
            Log.e(TAG, "DPM unavailable")
            analyticsLogger.logErrorMsg(
                AnalyticsLogger.Companion.LogEventType.MDM_EVENT,
                "DPM unavailable for reapplySystemUpdatePolicy()"
            )
            return
        }
        val admin = ComponentName(ctx, DeviceOwnerReceiver::class.java)

        if (!dpm.isDeviceOwnerApp(ctx.packageName) || !dpm.isAdminActive(admin)) {
            Log.e(TAG, "Not device owner or admin inactive – cannot set update policy")
            analyticsLogger.logErrorMsg(
                AnalyticsLogger.Companion.LogEventType.MDM_EVENT,
                "Not device owner or admin inactive – cannot set update policy"
            )
            return
        }

        // 1) always postponing up to 30 days
        val policy = SystemUpdatePolicy.createPostponeInstallPolicy()
        val mf = Build.MANUFACTURER.lowercase(Locale.US)

        // 2) choosing a freeze window per device type
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val freezeList = mutableListOf<FreezePeriod>()
            when {
                mf.contains("samsung") -> {
                    // Samsung tablets: June 1 → August 29  (90 days)
                    freezeList += FreezePeriod(
                        MonthDay.of(6, 1),
                        MonthDay.of(8, 28)
                    )
                }

                mf.contains("lenovo") -> {
                    // Lenovo tablets: July 1 → September 28  (90 days)
                    freezeList += FreezePeriod(
                        MonthDay.of(7, 1),
                        MonthDay.of(9, 27)
                    )
                }

                mf.contains("pico") -> {
                    // Pico headsets: no fixed freeze—rely on reboot refresh only
                }

                else -> {
                    // Default: January 1 → March 31  (90 days)
                    freezeList += FreezePeriod(
                        MonthDay.of(1, 1),
                        MonthDay.of(3, 30)
                    )
                }
            }
            if (freezeList.isNotEmpty()) {
                policy.freezePeriods = freezeList
            }
        }

        // 3) applying update policy
        dpm.setSystemUpdatePolicy(admin, policy)
        Log.i(TAG, "Applied SystemUpdatePolicy → $policy")
        analyticsLogger.logMsg(
            AnalyticsLogger.Companion.LogEventType.MDM_EVENT,
            "Applied SystemUpdatePolicy → $policy for $mf"
        )
    }
}
