package tech.syncvr.mdm_agent.repositories.firmware

import io.github.z4kn4fein.semver.Version

class MockFirmwareRepository : FirmwareRepository() {

    companion object {
        private const val TAG = "MockFirmwareRepository"
    }

    override fun getFirmwareVersion(): Version {
        return Version(0, 0, 0)
    }
}