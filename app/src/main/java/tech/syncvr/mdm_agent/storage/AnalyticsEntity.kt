package tech.syncvr.mdm_agent.storage

import androidx.annotation.NonNull
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.*

@Entity
data class AnalyticsEntity(
    @PrimaryKey(autoGenerate = true)
    var itemId: Long = 0L,
    @NonNull @ColumnInfo(name = "timeStamp") val timeStamp: Date,
    @NonNull @ColumnInfo(name = "analyticsHashmap") val analyticsHashmap: String,
)
