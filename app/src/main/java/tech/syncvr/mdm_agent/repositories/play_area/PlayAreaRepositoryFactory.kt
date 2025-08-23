package tech.syncvr.mdm_agent.repositories.play_area

import android.os.Build
import tech.syncvr.mdm_agent.MDMAgentModule.Companion.MODEL_PICO_4_A
import tech.syncvr.mdm_agent.MDMAgentModule.Companion.MODEL_PICO_4_B
import tech.syncvr.mdm_agent.MDMAgentModule.Companion.MODEL_PICO_4_C
import tech.syncvr.mdm_agent.MDMAgentModule.Companion.MODEL_PICO_4_ULTRA_A
import tech.syncvr.mdm_agent.MDMAgentModule.Companion.MODEL_PICO_4_ULTRA_B
import tech.syncvr.mdm_agent.MDMAgentModule.Companion.MODEL_PICO_4_ULTRA_C
import tech.syncvr.mdm_agent.MDMAgentModule.Companion.MODEL_PICO_4_ULTRA_D
import tech.syncvr.mdm_agent.MDMAgentModule.Companion.MODEL_PICO_NEO_2
import tech.syncvr.mdm_agent.MDMAgentModule.Companion.MODEL_PICO_NEO_3
import tech.syncvr.mdm_agent.repositories.play_area.pico.PicoConfigPlayAreaRepository
import javax.inject.Inject
import javax.inject.Provider

class PlayAreaRepositoryFactory @Inject constructor(
    private val picoProvider: Provider<PicoConfigPlayAreaRepository>,
    private val mockProvider: Provider<MockPlayAreaRepository>,
) {
    fun get(): IPlayAreaRepository {
        return when (Build.MODEL) {
            MODEL_PICO_NEO_3,
            MODEL_PICO_NEO_2,
            MODEL_PICO_4_B,
            MODEL_PICO_4_C,
            MODEL_PICO_4_A,
            MODEL_PICO_4_ULTRA_A,
            MODEL_PICO_4_ULTRA_B,
            MODEL_PICO_4_ULTRA_C,
            MODEL_PICO_4_ULTRA_D -> {
                picoProvider.get()
            }

            else -> {
                mockProvider.get()
            }
        }
    }
}
