package tech.syncvr.mdm_agent.disk_space

import android.os.Environment
import android.os.StatFs

object DiskSpaceUtil {

    private const val KB = 1024

    fun getAvailableDiskSpace(): String {
        val externalStorageDirectory = Environment.getDataDirectory().absolutePath
        val statFs = StatFs(externalStorageDirectory)
        return statFs.availableBytes.formatSize()
    }

    private fun Long.formatSize(): String {
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = this.toDouble()
        var unitsIndex = 0
        while (value > KB && unitsIndex < units.size - 1) {
            value /= KB
            unitsIndex++
        }
        return String.format("%.2f %s", value, units[unitsIndex])
    }
}