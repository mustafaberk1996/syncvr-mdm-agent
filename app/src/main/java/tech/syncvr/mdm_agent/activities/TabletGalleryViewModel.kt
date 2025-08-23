package tech.syncvr.mdm_agent.activities

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.lifecycle.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import tech.syncvr.mdm_agent.MDMAgentApplication
import tech.syncvr.mdm_agent.R
import tech.syncvr.mdm_agent.activities.items.TabletGalleryApp
import tech.syncvr.mdm_agent.activities.items.TabletGalleryLink
import tech.syncvr.mdm_agent.device_identity.DeviceIdentityRepository
import tech.syncvr.mdm_agent.device_management.configuration.ConfigurationRepository
import tech.syncvr.mdm_agent.device_management.configuration.models.Configuration
import tech.syncvr.mdm_agent.repositories.DeviceInfoRepository
import tech.syncvr.mdm_agent.repositories.auto_start.tablet.TabletAutoStartManager.Companion.BROWSER_PACKAGE
import java.util.*
import javax.inject.Inject

@SuppressLint("MissingPermission")
@HiltViewModel
class TabletGalleryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val configurationRepository: ConfigurationRepository,
    private val deviceIdentityRepository: DeviceIdentityRepository,
    private val deviceInfoRepository: DeviceInfoRepository
) : ViewModel() {

    companion object {
        private const val TAG = "TabletGalleryViewModel"
        private const val REFRESH_GALLERY_APPS_INTERVAL = 10 * 1000L

        private val GALLERY_LINKS = mapOf<String, List<TabletGalleryLink>>(
            "en" to listOf(
                TabletGalleryLink(
                    "Hygiene Protocol",
                    "https://docs.google.com/document/d/1J0Xd20xAWgY-gGkXaM72fXp_zNQXR10C/view?usp=sharing"
                ),
                TabletGalleryLink(
                    "Pico G3 Manual",
                    "https://drive.google.com/file/d/1FJY8RhGgRKBww_JrOcXb29SYN8fzK1i-/view?usp=sharing"
                ),
                TabletGalleryLink(
                    "Pico 4 Manual",
                    "https://drive.google.com/file/d/1JAuiDh-aHsq7HKCtacFEMuh2UjrzsGbl/view?usp=sharing"
                ),
                TabletGalleryLink(
                    "Relax & Distract Plus Manual",
                    "https://drive.google.com/file/d/1A4UrU7UegVwpCFq5jFXnxXsn-SVUCDev/view?usp=sharing"
                ),
                TabletGalleryLink(
                    "Communication Protocol",
                    "https://drive.google.com/file/d/1rQG4OaDTjqt4GeJGti81GB5DvVhXcG6B/view?usp=sharing"
                ),
                TabletGalleryLink(
                    "Information video Virtual Reality",
                    "https://youtu.be/vwXP2qQmFJY"
                ),
            ),
            "nl" to listOf(
                TabletGalleryLink(
                    "Hygiëne protocol",
                    "https://docs.google.com/document/d/1MsdakgaKtyvGKu4ZC80INQsx74xwi97N/view?usp=sharing"
                ),
                TabletGalleryLink(
                    "Pico G3 Handleiding",
                    "https://drive.google.com/file/d/1ViYUO-mpcttCj2Y_S2Olxsdp_jUgOAIc/view?usp=sharing"
                ),
                TabletGalleryLink(
                    "Pico 4 Handleiding",
                    "https://drive.google.com/file/d/1AWX9Fr9zSvTGHVEdSCCgvttO7AcFODXM/view?usp=sharing"
                ),
                TabletGalleryLink(
                    "Relax & Distract Plus handleiding",
                    "https://drive.google.com/file/d/1AyyFRTz-J_-x1kHSpoIiHk6x5zA0MzPd/view?usp=sharing"
                ),
                TabletGalleryLink(
                    "Communicatie protocol",
                    "https://drive.google.com/file/d/12M7T3Qatg0zo4348wdpOjcVZXuhRYbuh/view?usp=sharing"
                ),
                TabletGalleryLink(
                    "Informatievideo Virtual Reality",
                    "https://youtu.be/67vURpv77tw"
                ),
            ),
            "de" to listOf(
                TabletGalleryLink(
                    "Hygieneprotokoll",
                    "https://docs.google.com/document/d/13OO_MRCw93RCgtvyzHzu80L95L0z6WHM/view?usp=sharing"
                ),
                TabletGalleryLink(
                    "Pico G3 Handbuch",
                    "https://drive.google.com/file/d/1eS2JMedwFTTgI780wIMuRvU3W4nbgsdH/view?usp=sharing"
                ),
                TabletGalleryLink(
                    "Pico 4 Handbuch",
                    "https://drive.google.com/file/d/14kvFvN6kwnzy3ULMPqNo4psvwqtHgSqW/view?usp=sharing"
                ),
                TabletGalleryLink(
                    "Relax & Distract Plus Handbuch",
                    "https://drive.google.com/file/d/1kt5B_4a1VtD7jyVpQDaRWR0-TcIRtJsN/view?usp=sharing"
                ),
                TabletGalleryLink(
                    "Kommunikationsprotokoll",
                    "https://drive.google.com/file/d/10uMu9JXP6elTqXgqiIxsYRwAayG7tRub/view?usp=sharing"
                ),
                TabletGalleryLink(
                    "Informationsvideo Virtual Reality",
                    "https://youtu.be/p3RLLYqZEZA"
                ),

            ),

            )
    }

    private val _galleryApps = MutableLiveData<List<TabletGalleryApp>>()
    val galleryApps: LiveData<List<TabletGalleryApp>> = _galleryApps

    private val _galleryLinks = MutableLiveData<List<TabletGalleryLink>>()
    val galleryLinks: LiveData<List<TabletGalleryLink>> = _galleryLinks

    private val _deviceName = MutableLiveData<String>()
    val deviceName: LiveData<String> = _deviceName

    private val onAppInstalled = configurationRepository.appInstalledTrigger.asSharedFlow()
        .mapNotNull { configurationRepository.configuration.value }

    init {
        viewModelScope.launch {
            merge(
                configurationRepository.configuration,
                onAppInstalled
            ).collect { configuration ->
                refreshGalleryApps(
                    configuration
                )
            }
        }
        viewModelScope.launch {
            while (true) {
                refreshGalleryLinks()
                delay(REFRESH_GALLERY_APPS_INTERVAL)
            }
        }
        viewModelScope.launch {
            deviceInfoRepository.deviceInfoStateFlow.collect {
                _deviceName.value = it.humanReadableName
            }
        }
    }

    private fun refreshGalleryLinks() {
        _galleryLinks.value = GALLERY_LINKS[Locale.getDefault().language] ?: GALLERY_LINKS["en"]
    }

    private fun refreshGalleryApps(configuration: Configuration?) {

        var galleryAppList = configuration?.managed?.mapNotNull { app ->
            galleryAppFromPackageName(app.appPackageName)
        } ?: listOf()

        val settingsApp = galleryAppFromPackageName("com.android.settings")
        if (settingsApp != null) {
            galleryAppList = galleryAppList + settingsApp
        }

        val chromeApp = galleryAppFromPackageName(BROWSER_PACKAGE)
        if (chromeApp != null) {
            galleryAppList = galleryAppList + chromeApp
        }

        _galleryApps.value = galleryAppList
    }

    private fun galleryAppFromPackageName(packageName: String): TabletGalleryApp {
        val packageManager = (context as MDMAgentApplication).packageManager

        val appInfo = try {
            packageManager.getApplicationInfo(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            return TabletGalleryApp(
                packageName, packageName,
                context.getDrawable(R.drawable.installation_symbol)!!
            )
        }

        return TabletGalleryApp(
            appInfo.packageName,
            packageManager.getApplicationLabel(appInfo) as String,
            packageManager.getApplicationIcon(appInfo)
        )
    }

    fun onTabletGalleryAppClicked(packageName: String) {
        val packageManager = (context as MDMAgentApplication).packageManager
        try {
            packageManager.getApplicationInfo(packageName, 0)
            context.startActivity(
                packageManager.getLaunchIntentForPackage(packageName)
            )
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "App $packageName not ready for launch yet")
        }
    }

}
