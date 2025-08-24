package tech.syncvr.mdm_agent.policy.models

import kotlinx.serialization.Serializable

@Serializable
data class ConnectivityLabPolicy(
    val wifiDirect: WifiDirectPolicy = WifiDirectPolicy(),
    val dataChannel: DataChannelPolicy = DataChannelPolicy()
)

@Serializable
data class WifiDirectPolicy(
    val enabled: Boolean = true,
    val discoveryBackoffSeconds: List<Int> = listOf(1, 2, 4, 8, 16)
)

@Serializable
data class DataChannelPolicy(
    val heartbeatIntervalSec: Int = 5,
    val maxMisses: Int = 3
)