package tech.syncvr.mdm_agent.firebase.retrofit

import kotlinx.serialization.Serializable

@Serializable
data class DeviceApiV2Response<T>(
    val response: T? = null,
    val errors: List<String>
)