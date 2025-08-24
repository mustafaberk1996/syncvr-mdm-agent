package tech.syncvr.mdm_agent.policy

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import tech.syncvr.mdm_agent.policy.models.ConnectivityLabPolicy
import tech.syncvr.mdm_agent.utils.Logger
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PolicyRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: Logger
) {
    companion object {
        private const val TAG = "PolicyRepository"
        private const val PREFS_NAME = "mdm_policy_prefs"
        private const val KEY_LAST_POLICY = "last_policy"
        private const val KEY_LAST_HASH = "last_hash"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val _currentPolicy = MutableStateFlow(getDefaultPolicy())
    val currentPolicy: StateFlow<ConnectivityLabPolicy> = _currentPolicy.asStateFlow()

    init {
        // Load last valid policy from SharedPreferences on initialization
        loadLastValidPolicy()
    }

    private fun getDefaultPolicy(): ConnectivityLabPolicy {
        return ConnectivityLabPolicy()
    }

    private fun loadLastValidPolicy() {
        try {
            val lastPolicyJson = prefs.getString(KEY_LAST_POLICY, null)
            if (lastPolicyJson != null) {
                val policy = json.decodeFromString<ConnectivityLabPolicy>(lastPolicyJson)
                _currentPolicy.value = policy
                logger.d(TAG, "Loaded last valid policy from SharedPreferences")
            } else {
                logger.d(TAG, "No previous policy found, using default")
            }
        } catch (e: Exception) {
            logger.e(TAG, "Failed to load last valid policy: ${e.message}")
            _currentPolicy.value = getDefaultPolicy()
        }
    }

    fun refreshPolicy() {
        try {

            val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11 (API 30) ve sonrası
                Environment.isExternalStorageManager()
            } else {
                // Android 10 ve öncesi
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }

            if (!hasPermission) {
                //TODO: We may not need this
                logger.i(TAG, "Storage permission not granted. Cannot read policy file.")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:${context.packageName}")
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                }
                return
            }

            val policyFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "mdm_policy.json")
            if (!policyFile.exists()) {
                logger.d(TAG, "Policy file does not exist at ${policyFile.path}")
                return
            }

            val content = policyFile.readText()
            val contentHash = calculateHash(content)
            val lastHash = prefs.getString(KEY_LAST_HASH, null)

            // Check if content has changed
            if (contentHash == lastHash) {
                logger.d(TAG, "Policy file unchanged, skipping update")
                return
            }

            // Try to parse the new policy
            val newPolicy = json.decodeFromString<ConnectivityLabPolicy>(content)
            
            // If parsing succeeds, update current policy and persist
            _currentPolicy.value = newPolicy
            
            prefs.edit()
                .putString(KEY_LAST_POLICY, content)
                .putString(KEY_LAST_HASH, contentHash)
                .apply()

            logger.i(TAG, "Policy updated successfully. Hash: $contentHash")
            
        } catch (e: Exception) {
            logger.e(TAG, "Failed to parse policy file, keeping previous valid policy: ${e.message}")
            // Keep the current policy unchanged if parsing fails
        }
    }

    private fun calculateHash(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(content.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}