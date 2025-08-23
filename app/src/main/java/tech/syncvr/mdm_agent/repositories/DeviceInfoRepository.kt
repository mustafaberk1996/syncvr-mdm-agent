package tech.syncvr.mdm_agent.repositories

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import tech.syncvr.mdm_agent.device_management.bluetooth_name.DeviceInfo
import tech.syncvr.mdm_agent.localcache.ILocalCacheSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceInfoRepository @Inject constructor(
    private val localCacheSource: ILocalCacheSource
) {
    suspend fun onDeviceInfoFetched(deviceInfo: DeviceInfo) {
        localCacheSource.setDeviceInfo(deviceInfo)
        _deviceInfoStateFlow.emit(deviceInfo)
    }

    private val _deviceInfoStateFlow = MutableStateFlow(localCacheSource.getDeviceInfo())

    val deviceInfoStateFlow = _deviceInfoStateFlow.asStateFlow()
}