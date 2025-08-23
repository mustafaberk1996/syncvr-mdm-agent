package tech.syncvr.mdm_agent.device_management.services

import android.util.Log
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

class ShellCommandService {

    data class ShellCommandResult(
        val command: String,
        val success: Boolean,
        val stdOut: String,
        val errOut: String
    ) {
        override fun toString(): String {
            return "Command: $command - Success: $success - stdOut: $stdOut - errOut: $errOut"
        }
    }

    companion object {
        private const val TAG = "ShellCommandService"
    }

    fun runCommandPrintResult(cmdArray: Array<String>): ShellCommandResult {
        return runCommand(cmdArray).also {
            Log.d(TAG, it.toString())
        }
    }

    fun runCommand(cmdArray: Array<String>): ShellCommandResult {
        val command = cmdArray.joinToString(separator = " ")

        lateinit var p: Process

        try {
            p = Runtime.getRuntime().exec(cmdArray)
        } catch (securityException: SecurityException) {
            return ShellCommandResult(
                command,
                false,
                "",
                "Security Exception: ${securityException.message}"
            )
        } catch (ioException: IOException) {
            return ShellCommandResult(command, false, "", "IO Exception: ${ioException.message}")
        } catch (exception: Exception) {
            return ShellCommandResult(command, false, "", "Unknown Exception: ${exception.message}")
        }

        var stdOut = ""
        var stdOutLn = ""
        val inputStream = BufferedReader(InputStreamReader(p.inputStream))
        while (inputStream.readLine()?.also { stdOutLn = it } != null) {
            stdOut = stdOut.plus(stdOutLn)
        }
        inputStream.close()

        var errOut = ""
        var errOutLn = ""
        val errorStream = BufferedReader(InputStreamReader(p.errorStream))
        while (errorStream.readLine()?.also { errOutLn = it } != null) {
            errOut = errOut.plus(errOutLn)
        }
        errorStream.close()

        p.waitFor()
        return ShellCommandResult(
            command,
            p.exitValue() == 0,
            stdOut,
            errOut
        )
    }

}