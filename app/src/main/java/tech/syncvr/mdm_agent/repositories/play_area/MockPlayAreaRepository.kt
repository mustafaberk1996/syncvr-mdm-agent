package tech.syncvr.mdm_agent.repositories.play_area

import android.util.Log
import tech.syncvr.mdm_agent.mdm_common.Constants
import javax.inject.Inject

class MockPlayAreaRepository @Inject constructor() : IPlayAreaRepository {
    companion object {
        val TAG: String = this::class.java.declaringClass.simpleName
    }

    override fun onAppCreated() {
        Log.d(TAG, "onAppCreated playarea MOCK")
    }

    override fun getPlayAreaSetting(): String {
        return Constants.AUTO_PLAY_AREA_NONE
    }

    override fun setPlayAreaStanding() {

    }

    override fun setPlayAreaSitting() {

    }

    override fun clearPlayAreaConfiguration() {

    }
}