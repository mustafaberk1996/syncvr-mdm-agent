package tech.syncvr.mdm_agent.model

import java.util.*

data class AnalyticsEntry(
    val timeStamp: Date,
    val hashMap: HashMap<String, Any>,
)