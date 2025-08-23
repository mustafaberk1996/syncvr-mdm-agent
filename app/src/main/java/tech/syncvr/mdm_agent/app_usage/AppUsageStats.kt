package tech.syncvr.mdm_agent.app_usage

import kotlinx.serialization.Serializable

@Serializable
data class AppUsageStats(
    val startTime: Long,
    val endTime: Long,
    val usageTime: Long
)
