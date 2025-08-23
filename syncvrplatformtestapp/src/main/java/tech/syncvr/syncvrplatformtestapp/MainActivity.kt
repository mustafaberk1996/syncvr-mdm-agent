package tech.syncvr.syncvrplatformtestapp

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import kotlinx.coroutines.*
import tech.syncvr.logging_connector_v2.AnalyticsManager
import tech.syncvr.logging_connector_v2.LogLevel
import tech.syncvr.platform_sdk.SyncVRPlatformManager
import tech.syncvr.platform_sdk.parcelables.AppState
import tech.syncvr.platform_sdk.parcelables.Configuration

// I tend to test this directly on the command line with the following command
// adb shell am force-stop tech.syncvr.syncvrplatformtestapp && ./gradlew syncvrplatformtestapp:installDebug && adb shell monkey -p tech.syncvr.syncvrplatformtestapp 1 &&  sleep 1 && adb logcat -v color --pid=$(adb shell pidof tech.syncvr.syncvrplatformtestapp)

class MainActivity : AppCompatActivity() {
    companion object {
        val TAG: String = this::class.java.declaringClass.simpleName
    }

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Whoopsie", throwable)
    }

    private val coroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default + exceptionHandler)

    lateinit var sdk: SyncVRPlatformManager
    lateinit var analyticsManager: AnalyticsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.d(TAG, "TEST APP ON CREATE NEW SDK INSTANCE")
        // Creating the Manager starts service binding in the background
        sdk = SyncVRPlatformManager(this)
        doSdkThings()
        analyticsManager = AnalyticsManager(this)
        loggingConnectorV2Test()
    }

    private fun doSdkThings() {
        coroutineScope.launch {
            Log.d(TAG, "doSdkThings coroutine start")
            // Any call will ensure we're bound or wait if necessary
            val serialNo = sdk.getSerialNumber()
            Log.d(TAG, "serialno $serialNo")
            val customer = sdk.getCustomer()
            Log.d(TAG, "customer $customer")
            val department = sdk.getDepartment()
            Log.d(TAG, "department $department")
            try {
                val token = sdk.getAuthorizationToken()
                Log.d(TAG, "authorizationtoken $token")
            } catch (e: Exception) {
                Log.w(TAG, "Caught exception", e)
            }
            //val wifiSuccess = sdk.addWifiConfiguration("Honor 9", "dummypassword")
            //Log.d(TAG, "addWifiConfiguration $wifiSuccess")
            val wifiSuccess = sdk.addOpenWifiConfiguration("Honor 9")
            Log.d(TAG, "addWifiConfiguration $wifiSuccess")
            Log.d(TAG, "Direct call getConfiguration ${sdk.getConfiguration()}")
            sdk.configurationFlow.collect {
                Log.d(TAG, "Collected configuration $it")
                it?.let { it1 -> logDetailsAppState(it1) }
            }
        }
    }

    private fun logDetailsAppState(config:Configuration) {
        Log.d(TAG, "Apps in NotInstalled state: ${config.appList.count { it.state == AppState.NotInstalled }}")
        Log.d(TAG, "Apps in Downloading state: ${config.appList.count { it.state is AppState.Downloading }}")
        Log.d(TAG, "Apps in Installing state: ${config.appList.count { it.state == AppState.Installing }}")
        Log.d(TAG, "Apps in Installed state: ${config.appList.count { it.state == AppState.Installed }}")
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")
    }

    private fun loggingConnectorV2Test() {
        coroutineScope.launch {
            for(i in 1..20) {
                analyticsManager.sendAnalyticsEvent("test", "test1", "$i", LogLevel.INFO)
            }
            delay(10000)
            for(i in 100..120) {
                analyticsManager.sendAnalyticsEvent("test", "test2", "$i", LogLevel.INFO)
            }
        }
    }
}