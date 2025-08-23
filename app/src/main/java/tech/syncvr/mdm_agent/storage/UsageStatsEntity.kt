package tech.syncvr.mdm_agent.storage

import android.app.usage.UsageStats
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(indices = [Index(value = ["packageName", "startTime"], unique = true)])
data class UsageStatsEntity(
    @PrimaryKey(autoGenerate = true)
    var itemId: Long = 0L,
    @ColumnInfo(name = "packageName") val packageName: String,
    @ColumnInfo(name = "startTime") val startTime: Long,
    @ColumnInfo(name = "endTime") val endTime: Long,
    @ColumnInfo(name = "usageTime") val usageTime: Long,
    // If true, it still needs to be uploaded. If false, it has been uploaded already and we won't change it locally anymore.
    // This value will start as true, and will only be set to false once the entity has been successfully uploaded
    @ColumnInfo(name = "uploadRequired") val uploadRequired: Boolean
) {
    fun isEqual(other: UsageStats): Boolean {
        return packageName == other.packageName &&
                startTime == other.firstTimeStamp &&
                endTime == other.lastTimeStamp &&
                usageTime == other.totalTimeInForeground
    }

    fun isEqualStartTime(other: UsageStats): Boolean {
        return packageName == other.packageName &&
                startTime == other.firstTimeStamp
    }

    fun isEqualOverlap(other: UsageStats): Boolean {
        return packageName == other.packageName &&
                ((other.firstTimeStamp in (startTime + 1) until endTime) ||
                        (startTime in (other.firstTimeStamp + 1) until other.lastTimeStamp))
    }

    fun toLogString(): String{
        return "$itemId - $packageName - $startTime - $endTime - $usageTime - $uploadRequired"
    }
}
