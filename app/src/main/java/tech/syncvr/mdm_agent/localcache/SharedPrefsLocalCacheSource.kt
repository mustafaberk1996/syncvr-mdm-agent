package tech.syncvr.mdm_agent.localcache

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tech.syncvr.mdm_agent.R
import tech.syncvr.mdm_agent.app_usage.app_sessions.AppUsageSessionCalculator.*
import tech.syncvr.mdm_agent.device_management.bluetooth_name.DeviceInfo
import tech.syncvr.mdm_agent.device_management.configuration.models.Configuration
import tech.syncvr.mdm_agent.device_management.configuration.models.ManagedAppPackage
import tech.syncvr.mdm_agent.device_management.configuration.models.WifiPoint
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class SharedPrefsLocalCacheSource @Inject constructor(@ApplicationContext private val appContext: Context) :
    ILocalCacheSource {

    companion object {
        private const val TAG = "LocalCacheSource"
        private const val DEFAULT_APPS_KEY = "default_apps"
        private const val WIFIS_KEY = "wifis"
        private const val MANAGED_APPS_KEY = "managed_apps"
        private const val AUTO_START_KEY = "auto_start"
        private const val AUTO_PLAY_AREA_KEY = "play_area"
        private const val DEVICE_INFO_KEY = "device_info"

        // App Usage Sessions
        private const val LAST_SYSTEM_EVENT_QUERY_TIME_KEY = "last_system_event_query_time"
        private const val ACTIVE_APP_SESSION_KEY = "active_app_session_key"
        private const val VISIBLE_APPS_KEY = "visible_apps"

        //App Usage stats
        private const val LAST_USAGE_STATS_QUERY_TIME_KEY = "last_usage_stats_query_time"

        private val keyGenParameterSpec = MasterKeys.AES256_GCM_SPEC
        private val mainKeyAlias = MasterKeys.getOrCreate(keyGenParameterSpec)

    }

    private val securePreferences = EncryptedSharedPreferences.create(
        "secure_prefs",
        mainKeyAlias,
        appContext,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private fun storeStringValue(key: String, value: String?) {
        val sharedPref = appContext.getSharedPreferences(
            appContext.getString(R.string.sharedprefs_filename),
            Context.MODE_PRIVATE
        )

        with(sharedPref.edit()) {
            this.putString(key, value)
            commit()
        }
    }

    private fun storeSecureStringValue(key: String, value: String) {
        with(securePreferences.edit()) {
            putString(key, value)
            commit()
        }
    }

    private fun getSecureStringValue(key: String): String? {
        // Reading data from Preferences
        return securePreferences.getString(key, null)
    }

    private fun getStringValue(key: String): String? {
        val sharedPref = appContext.getSharedPreferences(
            appContext.getString(R.string.sharedprefs_filename),
            Context.MODE_PRIVATE
        )

        return sharedPref.getString(key, null)
    }

    private fun getLongValue(key: String): Long {
        val sharedPref = appContext.getSharedPreferences(
            appContext.getString(R.string.sharedprefs_filename),
            Context.MODE_PRIVATE
        )

        return sharedPref.getLong(key, 0)
    }

    private fun storeLongValue(key: String, value: Long) {
        val sharedPref = appContext.getSharedPreferences(
            appContext.getString(R.string.sharedprefs_filename),
            Context.MODE_PRIVATE
        )

        with(sharedPref.edit()) {
            this.putLong(key, value)
            commit()
        }
    }

    private fun clearValue(key: String) {
        val sharedPref = appContext.getSharedPreferences(
            appContext.getString(R.string.sharedprefs_filename),
            Context.MODE_PRIVATE
        )

        with(sharedPref.edit()) {
            this.remove(key)
            commit()
        }
    }

    override fun storePlatformApps(platformApps: List<ManagedAppPackage>) {
        val platformAppsJson = Json.encodeToString(platformApps)
        storeStringValue(DEFAULT_APPS_KEY, platformAppsJson)
    }

    override fun storeConfiguration(configuration: Configuration) {
        val managedAppsJson = Json.encodeToString(configuration.managed)
        val wifis = Json.encodeToString(configuration.wifis)
        storeStringValue(MANAGED_APPS_KEY, managedAppsJson)
        storeStringValue(AUTO_START_KEY, configuration.autoStart)
        storeSecureStringValue(WIFIS_KEY, wifis)
    }

    override fun getPlatformApps(): List<ManagedAppPackage>? {
        val platformAppsJson = getStringValue(DEFAULT_APPS_KEY) ?: return null
        val platformApps = try {
            Json.decodeFromString<List<ManagedAppPackage>>(platformAppsJson)
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Couldn't JSON deserialize previously cached default apps: $platformAppsJson",
                e
            )
            null
        } ?: return null

        return platformApps
    }

    override fun getConfiguration(): Configuration? {
        val managedApps = getStringValue(MANAGED_APPS_KEY) ?: return null
        val managedAppsJson = try {
            Json.decodeFromString<List<ManagedAppPackage>>(managedApps)
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't JSON Deserialize previously cached configuration: $managedApps", e)
            null
        } ?: return null
        val wifisJson = getSecureStringValue(WIFIS_KEY)
        val wifis: List<WifiPoint> = wifisJson?.let {
            try {
                Json.decodeFromString(wifisJson)
            } catch (_: Throwable) {
                emptyList()
            }
        } ?: emptyList()

        val autoStart = getStringValue(AUTO_START_KEY)

        return Configuration(
            managed = managedAppsJson,
            autoStart = autoStart,
            wifis = wifis
        )
    }

    override fun getPlayAreaConfig(): String? {
        return getStringValue(AUTO_PLAY_AREA_KEY)
    }

    override fun setPlayAreaConfig(mode: String) {
        storeStringValue(AUTO_PLAY_AREA_KEY, mode)
    }

    override fun setDeviceInfo(deviceInfo: DeviceInfo) {
        val deviceInfoJson = Gson().toJson(deviceInfo)
        storeStringValue(DEVICE_INFO_KEY, deviceInfoJson)
    }

    override fun getDeviceInfo(): DeviceInfo {
        val deviceInfoJson = getStringValue(DEVICE_INFO_KEY)
        return if (deviceInfoJson != null)
            Gson().fromJson(deviceInfoJson, DeviceInfo::class.java)
        else DeviceInfo()
    }

    override fun getActiveAppSession(): ActiveAppUsageSession? {
        val activeAppSessionString = getStringValue(ACTIVE_APP_SESSION_KEY)
        return if (activeAppSessionString != null) {
            Json.decodeFromString<ActiveAppUsageSession>(activeAppSessionString)
        } else {
            null
        }
    }

    override fun setActiveAppSession(session: ActiveAppUsageSession) {
        val activeAppSessionString = Json.encodeToString(session)
        storeStringValue(ACTIVE_APP_SESSION_KEY, activeAppSessionString)
    }

    override fun clearActiveAppSession() {
        clearValue(ACTIVE_APP_SESSION_KEY)
    }

    override fun getLastSystemEventQueryTime(): Long {
        return getLongValue(LAST_SYSTEM_EVENT_QUERY_TIME_KEY)
    }

    override fun setLastSystemEventQueryTime(lastQueryTime: Long) {
        storeLongValue(LAST_SYSTEM_EVENT_QUERY_TIME_KEY, lastQueryTime)
    }

    override fun getVisibleApps(): List<VisibleApp> {
        val visibleAppsString = getStringValue(VISIBLE_APPS_KEY)
        return if (visibleAppsString != null) {
            Json.decodeFromString(visibleAppsString)
        } else {
            listOf()
        }
    }

    override fun setVisibleApps(visibleApps: List<VisibleApp>) {
        val visibleAppsString =  Json.encodeToString(visibleApps)
        storeStringValue(VISIBLE_APPS_KEY, visibleAppsString)
    }

    override fun getLastUsageStatsQueryTime(): Long {
        return getLongValue(LAST_USAGE_STATS_QUERY_TIME_KEY)
    }

    override fun setLastUsageStatsQueryTime(lastQueryTime: Long) {
        storeLongValue(LAST_USAGE_STATS_QUERY_TIME_KEY, lastQueryTime)
    }

}