package tech.syncvr.mdm_agent.repositories.auto_start.oculus.helpers

import java.util.*

class ExecuteCooldown(private val cooldownTime: Long) {

    private var lastFireTime: Long = 0

    fun attempt(runnable: () -> Unit) {
        val time = Date().time
        if (time - lastFireTime > cooldownTime) {
            runnable.invoke()
            lastFireTime = time
        }
    }

    fun restartCooldown() {
        lastFireTime = Date().time
    }
}
