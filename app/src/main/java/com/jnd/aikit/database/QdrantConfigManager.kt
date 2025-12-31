package com.jnd.aikit.database

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson

/**
 * Configuration manager for Qdrant database settings
 */
class QdrantConfigManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val PREFS_NAME = "qdrant_config"
        private const val KEY_CONFIG = "qdrant_config"
        private const val KEY_CONNECTION_STATUS = "connection_status"
        private const val KEY_LAST_CONNECTED = "last_connected"
    }

    /**
     * Save Qdrant configuration
     */
    fun saveConfig(config: QdrantDatabaseManager.Config) {
        val configJson = gson.toJson(config)
        prefs.edit()
            .putString(KEY_CONFIG, configJson)
            .apply()
    }

    /**
     * Load Qdrant configuration
     */
    fun loadConfig(): QdrantDatabaseManager.Config {
        val configJson = prefs.getString(KEY_CONFIG, null)
        return if (configJson != null) {
            try {
                gson.fromJson(configJson, QdrantDatabaseManager.Config::class.java)
            } catch (e: Exception) {
                getDefaultConfig()
            }
        } else {
            getDefaultConfig()
        }
    }

    /**
     * Get default configuration
     */
    fun getDefaultConfig(): QdrantDatabaseManager.Config {
        return QdrantDatabaseManager.Config(
            host = "localhost",
            port = 6334,
            timeoutSeconds = 30L,
            enableLogging = false,
            useTls = false
        )
    }

    /**
     * Save connection status
     */
    fun saveConnectionStatus(connected: Boolean, timestamp: Long = System.currentTimeMillis()) {
        prefs.edit()
            .putBoolean(KEY_CONNECTION_STATUS, connected)
            .putLong(KEY_LAST_CONNECTED, timestamp)
            .apply()
    }

    /**
     * Get last connection status
     */
    fun getConnectionStatus(): ConnectionStatus {
        val connected = prefs.getBoolean(KEY_CONNECTION_STATUS, false)
        val lastConnected = prefs.getLong(KEY_LAST_CONNECTED, 0L)
        return ConnectionStatus(connected, lastConnected)
    }

    /**
     * Clear all configuration
     */
    fun clearConfig() {
        prefs.edit().clear().apply()
    }

    /**
     * Connection status data class
     */
    data class ConnectionStatus(
        val connected: Boolean,
        val lastConnectedTimestamp: Long
    )
}
