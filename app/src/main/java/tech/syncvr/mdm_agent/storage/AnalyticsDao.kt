package tech.syncvr.mdm_agent.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AnalyticsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(articleEntity: AnalyticsEntity): Long

    @Query("SELECT * FROM AnalyticsEntity ORDER BY timeStamp ASC LIMIT 100")
    suspend fun getOldest100Entries(): List<AnalyticsEntity>

    @Query("DELETE FROM AnalyticsEntity WHERE timeStamp <= :cutoffTimestamp")
    suspend fun deleteEntriesUpTo(cutoffTimestamp: Long): Int
}