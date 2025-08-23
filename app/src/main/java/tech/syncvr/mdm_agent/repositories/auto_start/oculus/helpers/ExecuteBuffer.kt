package tech.syncvr.mdm_agent.repositories.auto_start.oculus.helpers

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tech.syncvr.mdm_agent.MDMAgentApplication

class ExecuteBuffer(private val bufferTime: Long) {

    private var job: Job? = null

    fun attempt(runnable: () -> Unit) {
        job?.cancel()
        job = MDMAgentApplication.coroutineScope.launch {
            delay(bufferTime)
            runnable.invoke()
        }
    }

}