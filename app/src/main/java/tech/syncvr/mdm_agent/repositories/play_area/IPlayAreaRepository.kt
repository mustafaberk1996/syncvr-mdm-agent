package tech.syncvr.mdm_agent.repositories.play_area

interface IPlayAreaRepository {

    companion object {
        private const val TAG = "AutoStartRepository"
    }

    fun onAppCreated()
    fun getPlayAreaSetting(): String
    fun setPlayAreaStanding()
    fun setPlayAreaSitting()
    fun clearPlayAreaConfiguration()
}