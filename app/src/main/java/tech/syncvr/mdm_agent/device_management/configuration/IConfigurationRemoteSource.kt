package tech.syncvr.mdm_agent.device_management.configuration

import tech.syncvr.mdm_agent.device_management.configuration.models.Configuration

interface IConfigurationRemoteSource {
    suspend fun getConfiguration(): Configuration?
}