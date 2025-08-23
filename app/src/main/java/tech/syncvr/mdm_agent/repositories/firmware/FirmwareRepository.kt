package tech.syncvr.mdm_agent.repositories.firmware

import io.github.z4kn4fein.semver.Version

abstract class FirmwareRepository {

    abstract fun getFirmwareVersion(): Version

}