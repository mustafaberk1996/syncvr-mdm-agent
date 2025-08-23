package tech.syncvr.mdm_agent.utils

import android.util.Log

// PUI5 does not log debug-messages, so we convert them into info messages
class Pui5Logger : Logger {
    override fun e(tag: String, message: String, throwable: Throwable?) {
        if (throwable == null) {
            Log.e(tag, message)
        } else {
            Log.e(tag, message, throwable)
        }
    }

    override fun d(tag: String, message: String) {
        Log.i(tag, message)
    }

    override fun i(tag: String, message: String) {
        Log.i(tag, message)
    }
}