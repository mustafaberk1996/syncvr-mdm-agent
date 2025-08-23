package tech.syncvr.mdm_agent.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.google.api.Usage

@Dao
interface UsageStatsDao {
    @Query("SELECT * FROM UsageStatsEntity ORDER BY startTime DESC")
    suspend fun getAll(): List<UsageStatsEntity>

    @Query("SELECT *FROM UsageStatsEntity WHERE startTime > :minStartTime")
    suspend fun getFromStartTime(minStartTime: Long): List<UsageStatsEntity>

    @Query("SELECT * FROM UsageStatsEntity WHERE uploadRequired AND startTime < :maxStartTime")
    suspend fun getUploadRequired(maxStartTime: Long): List<UsageStatsEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg usageStatsEntities: UsageStatsEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(usageStatsEntities: List<UsageStatsEntity>)

    @Update
    suspend fun updateUsageStats(usageStatsEntity: UsageStatsEntity)

    @Update
    suspend fun updateUsageStats(usageStatsEntities: List<UsageStatsEntity>)

    @Query("DELETE FROM UsageStatsEntity")
    suspend fun deleteAll()
}