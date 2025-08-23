package tech.syncvr.logging_connector_v2

import kotlinx.serialization.Serializable

@Serializable
data class AnalyticsEventData(
    val name: String,
    val value: String? = null
)

