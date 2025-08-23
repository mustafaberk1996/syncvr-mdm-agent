package tech.syncvr.mdm_agent

import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import tech.syncvr.mdm_agent.app_usage.UsageStatsRemoteUploader
import tech.syncvr.mdm_agent.app_usage.app_sessions.UsageStatsEventsRepository
import tech.syncvr.mdm_agent.app_usage.UsageStatsRepository
import tech.syncvr.mdm_agent.device_identity.DeviceIdentityRepository
import tech.syncvr.mdm_agent.device_management.configuration.AdbConfigurationRemoteSource
import tech.syncvr.mdm_agent.device_management.configuration.ConfigurationRemoteSource
import tech.syncvr.mdm_agent.device_management.configuration.ConfigurationRepository
import tech.syncvr.mdm_agent.device_management.configuration.IConfigurationRemoteSource
import tech.syncvr.mdm_agent.device_management.device_status.DeviceStatusRemoteSource
import tech.syncvr.mdm_agent.device_management.device_status.IDeviceStatusRemoteSource
import tech.syncvr.mdm_agent.device_management.system_settings.*
import tech.syncvr.mdm_agent.device_management.system_settings.pico.PicoSystemSettingsService
import tech.syncvr.mdm_agent.device_management.system_settings.pico.ToBServiceSystemSettingsService
import tech.syncvr.mdm_agent.device_management.system_settings.tablet.TabletSystemSettingsService
import tech.syncvr.mdm_agent.firebase.DeviceApiService
import tech.syncvr.mdm_agent.firebase.FirebaseAuthHandler
import tech.syncvr.mdm_agent.firebase.IAuthenticationService
import tech.syncvr.mdm_agent.firebase.IDeviceApiService
import tech.syncvr.mdm_agent.localcache.ILocalCacheSource
import tech.syncvr.mdm_agent.localcache.SharedPrefsLocalCacheSource
import tech.syncvr.mdm_agent.logging.AnalyticsLogger
import tech.syncvr.mdm_agent.repositories.auto_start.AutoStartManager
import tech.syncvr.mdm_agent.repositories.auto_start.MockAutoStartManager
import tech.syncvr.mdm_agent.repositories.auto_start.oculus.OculusAutoStartManager
import tech.syncvr.mdm_agent.repositories.auto_start.pico.PicoConfigAutoStartManager
import tech.syncvr.mdm_agent.repositories.auto_start.pico.PicoPropertyAutoStartManager
import tech.syncvr.mdm_agent.repositories.auto_start.tablet.TabletAutoStartManager
import tech.syncvr.mdm_agent.repositories.firmware.FirmwareRepository
import tech.syncvr.mdm_agent.repositories.firmware.MockFirmwareRepository
import tech.syncvr.mdm_agent.repositories.firmware.PicoFirmwareRepository
import tech.syncvr.mdm_agent.repositories.play_area.IPlayAreaRepository
import tech.syncvr.mdm_agent.repositories.play_area.PlayAreaRepositoryFactory
import tech.syncvr.mdm_agent.storage.AppDatabase
import tech.syncvr.mdm_agent.utils.DefaultLogger
import tech.syncvr.mdm_agent.utils.Logger
import tech.syncvr.mdm_agent.utils.Pui5Logger
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MDMAgentModule {

    @Binds
    abstract fun bindDeviceApiService(deviceApiService: DeviceApiService): IDeviceApiService

    @Binds
    abstract fun bindLocalCacheSource(localCacheSource: SharedPrefsLocalCacheSource): ILocalCacheSource

    @Binds
    abstract fun bindDeviceStatusRemoteSource(deviceStatusRemoteSource: DeviceStatusRemoteSource): IDeviceStatusRemoteSource

    companion object {
        // Use lowercase for manufacturers. We've seen occurrences of "Pico" and "PICO"
        const val MANUFACTURER_PICO = "pico"
        internal const val MANUFACTURER_SAMSUNG = "samsung"
        internal const val MANUFACTURER_GOOGLE = "google"
        internal const val MANUFACTURER_OCULUS = "oculus"

        const val MODEL_PICO_G2_4K = "Pico G2 4K"
        const val MODEL_PICO_NEO_2 = "Pico Neo 2"
        const val MODEL_PICO_NEO_3 = "Pico Neo 3"

        // TODO: condense to one Pico 4 model (or two if we differentiate between Enterprise and Consumer editions)
        const val MODEL_PICO_4_A = "A8150"
        const val MODEL_PICO_4_B = "A8E50"
        const val MODEL_PICO_4_C = "A8110"

        const val MODEL_PICO_4_ULTRA_A = "A9210"
        const val MODEL_PICO_4_ULTRA_B = "A94Y0"
        const val MODEL_PICO_4_ULTRA_C = "A94U0"
        const val MODEL_PICO_4_ULTRA_D = "A9410"

        const val MODEL_PICO_G3_A = "A7Q10"
        const val MODEL_PICO_G3_B = "A7Q50"
        const val MODEL_PICO_G3_C = "A7R50"

        @Singleton
        @Provides
        fun provideConfigurationRemoteSource(
            @ApplicationContext appContext: Context,
            deviceApiService: DeviceApiService
        ): IConfigurationRemoteSource {
            return if (BuildConfig.ADB_CONFIG_REMOTE_SOURCE) {
                AdbConfigurationRemoteSource(appContext)
            } else {
                ConfigurationRemoteSource(deviceApiService)
            }
        }

        @Singleton
        @Provides
        fun provideUsageStatsRemoteUploader(
            deviceApiService: DeviceApiService
        ): UsageStatsRemoteUploader {
            return UsageStatsRemoteUploader(
                deviceApiService
            )
        }

        @Singleton
        @Provides
        fun provideAuthenticationService(
            @ApplicationContext appContext: Context,
            analyticsLogger: AnalyticsLogger
        ): IAuthenticationService {
            return when (BuildConfig.BUILD_TYPE) {
                "mock" -> object : IAuthenticationService {
                    override fun isSignedIn(): Boolean {
                        return true
                    }

                    override suspend fun getIdToken(refresh: Boolean): String? {
                        return "1029384756"
                    }

                    override suspend fun signInRoutine() {
//                        TODO("Not yet implemented")
                    }

                    override suspend fun loginAndGetIdToken(): String? {
                        return "1029384756"
                    }

                }

                else -> FirebaseAuthHandler(
                    appContext.getString(R.string.syncvr_app_key),
                    DeviceIdentityRepository(),
                    analyticsLogger
                )
            }
        }

        @Singleton
        @Provides
        fun provideAutoStartRepository(
            @ApplicationContext context: Context,
            configurationRepository: ConfigurationRepository,
            logger: Logger,
            analyticsLogger: AnalyticsLogger
        ): AutoStartManager {
            return when (Build.MANUFACTURER.lowercase()) {
                MANUFACTURER_PICO -> {
                    if (Build.MODEL == MODEL_PICO_G2_4K) {
                        analyticsLogger.logMsg(
                            AnalyticsLogger.Companion.LogEventType.MDM_EVENT,
                            "Using PropertyAutoStart!"
                        )
                        PicoPropertyAutoStartManager(context, analyticsLogger)
                    } else {
                        analyticsLogger.logMsg(
                            AnalyticsLogger.Companion.LogEventType.MDM_EVENT,
                            "Using ConfigAutoStart!"
                        )
                        PicoConfigAutoStartManager(context, logger, analyticsLogger)
                    }
                }

                MANUFACTURER_SAMSUNG -> {
                    TabletAutoStartManager(context)
                }

                MANUFACTURER_GOOGLE -> {
                    TabletAutoStartManager(context)
                }

                MANUFACTURER_OCULUS -> {
                    OculusAutoStartManager(context, analyticsLogger)
                }

                else -> {
                    MockAutoStartManager(context)
                }
            }
        }

        @Singleton
        @Provides
        fun provideSystemSettingsService(
            @ApplicationContext context: Context,
            analyticsLogger: AnalyticsLogger,
        ): ISystemSettingsService {
            return when (Build.MANUFACTURER.lowercase()) {
                MANUFACTURER_PICO -> {
                    when (Build.MODEL) {

                        MODEL_PICO_NEO_3, MODEL_PICO_4_A, MODEL_PICO_4_B, MODEL_PICO_4_C, MODEL_PICO_4_ULTRA_A, MODEL_PICO_4_ULTRA_B, MODEL_PICO_4_ULTRA_C, MODEL_PICO_4_ULTRA_D -> {
                            ToBServiceSystemSettingsService(context, analyticsLogger, false)
                        }

                        MODEL_PICO_G3_A, MODEL_PICO_G3_B, MODEL_PICO_G3_C -> {
                            ToBServiceSystemSettingsService(context, analyticsLogger, true)
                        }

                        else -> {
                            PicoSystemSettingsService(context, analyticsLogger)
                        }
                    }
                }

                MANUFACTURER_SAMSUNG -> {
                    TabletSystemSettingsService(context)
                }

                else -> {
                    MockSystemSettingsService()
                }
            }
        }

        @Singleton
        @Provides
        fun provideLogger(): Logger {
            return when (Build.MODEL) {
                MODEL_PICO_4_A, MODEL_PICO_4_B, MODEL_PICO_4_C, MODEL_PICO_G3_A, MODEL_PICO_G3_B, MODEL_PICO_G3_C, MODEL_PICO_4_ULTRA_A, MODEL_PICO_4_ULTRA_B, MODEL_PICO_4_ULTRA_C, MODEL_PICO_4_ULTRA_D -> {
                    Pui5Logger()
                }

                else -> {
                    DefaultLogger()
                }
            }
        }


        @Singleton
        @Provides
        fun provideUsageStatsRepository(
            @ApplicationContext context: Context,
            appDatabase: AppDatabase,
            analyticsLogger: AnalyticsLogger,
            usageStatsRemoteUploader: UsageStatsRemoteUploader,
            localCacheSource: SharedPrefsLocalCacheSource
        ): UsageStatsRepository {
            return UsageStatsRepository(
                context.getSystemService(UsageStatsManager::class.java),
                appDatabase,
                analyticsLogger,
                usageStatsRemoteUploader,
                localCacheSource
            )
        }

        @Singleton
        @Provides
        fun provideUsageStatsEventsRepository(
            @ApplicationContext context: Context,
            analyticsLogger: AnalyticsLogger,
            localCacheSource: SharedPrefsLocalCacheSource,
            autoStartManager: AutoStartManager,
        ): UsageStatsEventsRepository {
            return UsageStatsEventsRepository(
                context.getSystemService(UsageStatsManager::class.java),
                analyticsLogger,
                localCacheSource,
                autoStartManager,
                context.packageManager
            )
        }

        @Singleton
        @Provides
        fun provideFirmwareRepository(
            @ApplicationContext context: Context,
            analyticsLogger: AnalyticsLogger
        ): FirmwareRepository {
            return if (Build.MANUFACTURER.lowercase() == MANUFACTURER_PICO) {
                PicoFirmwareRepository(context, analyticsLogger)
            } else {
                MockFirmwareRepository()
            }
        }


        @Singleton
        @Provides
        fun providePlayAreaRepository(factory: PlayAreaRepositoryFactory): IPlayAreaRepository {
            return factory.get()
        }

    }
}
