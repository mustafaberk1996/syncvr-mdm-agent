package tech.syncvr.mdm_agent

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import tech.syncvr.mdm_agent.activities.MainActivity

class MDMAgentForegroundService : Service() {

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "MDMAgentForegroundService"
        private const val TAG = "MDMAgentForegroundService"
    }

    override fun onCreate() {
        startForeground(1, getNotification())
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    /**
     * Helper function for creating a notification icon
     */
    private fun getNotification(): Notification {
        createNotificationChannel()
        val notificationIntent = Intent(this, MainActivity::class.java)

        val flags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // maybe you can set these flags for all versions of Android, but I don't want to take the risk now. The mutability flag is mandatory for from Android 31
                Log.d(TAG, "pendingIntent creation for Android S+")
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else 0
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, flags)
        Log.d(TAG, "pendingIntent created succesfully")
        return Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("SyncVR MDM Agent")
            .setContentText("MDM Agent Service")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setTicker("active")
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_ID,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}