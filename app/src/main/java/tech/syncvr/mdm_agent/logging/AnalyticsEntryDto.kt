package tech.syncvr.mdm_agent.logging

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import tech.syncvr.mdm_agent.utils.DateSerializer
import java.util.Date

// This data class is serializable so we can send it to the backend.
// The current form of AnalyticsEntry and AnalyticsEntity are a bit unfortunate, in that
// they basically contain the entire entry as a json-serialized Map<String, Any>.

// Historically, when sending to the backend, this Json string was decoded using
// JSONObject and JSONArray, and we passed a complete RequestBody object to Retrofit,
// instead of passing a data class and relying on the Retrofit Json serializer to
// encode. To make this more standard approach possible, we have to introduce this new
// DTO.

@Serializable
data class AnalyticsEntryDto(
    val logName: String,
    val severity: String,
    @Serializable(with = DateSerializer::class)
    val timestamp: Date,
    val labels: HashMap<String, String>,
    val jsonPayload: HashMap<String, @Contextual Any>
)

@Serializable
data class AnalyticsEntriesDto(
    val logEntries: List<AnalyticsEntryDto>
)