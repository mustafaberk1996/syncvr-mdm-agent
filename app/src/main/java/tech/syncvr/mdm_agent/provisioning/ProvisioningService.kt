package tech.syncvr.mdm_agent.provisioning

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import fi.iki.elonen.NanoHTTPD
import java.net.BindException
import javax.inject.Inject

@AndroidEntryPoint
class ProvisioningService : Service() {
    companion object {
        const val TAG = "ProvisioningService"
        const val ACTION_START_PROVISIONING = "tech.syncvr.intent.START_PROVISIONING"
        const val ACTION_STOP_PROVISIONING = "tech.syncvr.intent.STOP_PROVISIONING"
        const val localport = 9789
    }

    private val nanoServer = ProvisioningServer()

    @Inject
    lateinit var provisioningServerLogic: ProvisioningServerLogic

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (val action = intent?.action) {
            ACTION_START_PROVISIONING -> {
                processStartProvisioningAction()
            }
            ACTION_STOP_PROVISIONING -> {
                processStopProvisioningAction()
            }
            else -> {
                Log.d(TAG, "Unknown action $action")
            }
        }
        return START_STICKY
    }

    private fun processStartProvisioningAction() {
        Log.i(TAG, "Start provisioning server")
        try {
            provisioningServerLogic.setProvisioning(true)
            if (!nanoServer.isAlive) {
                nanoServer.start()
            }
        } catch (e: BindException) {
            Log.w(TAG, "Provisioning server seems to be already started")
        }
    }

    private fun processStopProvisioningAction() {
        Log.i(TAG, "Stop provisioning server")
        nanoServer.stop()
        provisioningServerLogic.setProvisioning(false)
    }

    inner class ProvisioningServer : NanoHTTPD("localhost", localport) {
        override fun serve(session: IHTTPSession?): Response {
            val uri = session?.uri
            isRequestAuthenticated(session)
            Log.d(TAG, "URI $uri")
            return when (uri) {
                "/" -> this@ProvisioningService.provisioningServerLogic.pongResponse()
                "/mdmstate" -> this@ProvisioningService.provisioningServerLogic.mdmStateResponse()
                "/postdevicestatus" -> this@ProvisioningService.provisioningServerLogic.postDeviceStatusResponse()
                "/deviceconfiguration" -> this@ProvisioningService.provisioningServerLogic.deviceConfigurationReponse()
                "/setconfigwifis" -> this@ProvisioningService.provisioningServerLogic.setWifis()
                else -> {
                    Log.d(TAG, "Unmatched uri $uri")
                    return super.serve(session)
                }
            }
        }

        private fun isRequestAuthenticated(session: IHTTPSession?) {
            val authHeader = session?.headers?.get("Authorization")
            // hook jwt verif here ?
            val authenticated = true
            if (!authenticated) {
                throw IllegalAccessException("Request authentication failed")
            }
        }
    }
}