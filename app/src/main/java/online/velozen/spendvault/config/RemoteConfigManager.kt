package online.velozen.spendvault.config

import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import kotlinx.coroutines.tasks.await

class RemoteConfigManager {
    
    companion object {
        private const val TAG = "RemoteConfigManager"
        
        private const val KEY_CACHE_EXPIRATION_SECONDS = "remote_config_cache_expiration_seconds"
        private const val KEY_DEBUG_INFO_ENABLED = "debug_info_enabled"
        private const val KEY_AI_ENABLED = "ai_enabled"
        private const val KEY_AI_MODEL = "ai_model"
        private const val KEY_AI_MAX_TOKENS_PER_REQUEST = "ai_max_tokens_per_request"
        private const val KEY_AI_CONCURRENT_REQUESTS = "ai_concurrent_requests"
        private const val KEY_AI_MAX_ITEMS_PER_BATCH = "ai_max_items_per_batch"
        private const val KEY_FIREBASE_SYNC_THRESHOLD = "firebase_sync_threshold"
        private const val KEY_DEBUG_ANALYTICS_SYNC_PERIOD_HOURS = "debug_analytics_sync_period_hours"
        
        private const val DEFAULT_CACHE_EXPIRATION_SECONDS = 3600L
        private const val DEFAULT_DEBUG_INFO_ENABLED = false
        private const val DEFAULT_AI_ENABLED = true
        private const val DEFAULT_AI_MODEL = "deepseek/deepseek-chat-v3-0324:free"
        private const val DEFAULT_AI_MAX_TOKENS_PER_REQUEST = 4000L
        private const val DEFAULT_AI_CONCURRENT_REQUESTS = 6L
        private const val DEFAULT_AI_MAX_ITEMS_PER_BATCH = 30L
        private const val DEFAULT_FIREBASE_SYNC_THRESHOLD = 50L
        private const val DEFAULT_DEBUG_ANALYTICS_SYNC_PERIOD_HOURS = 5L
        
        @Volatile
        private var instance: RemoteConfigManager? = null
        
        fun getInstance(): RemoteConfigManager {
            return instance ?: synchronized(this) {
                instance ?: RemoteConfigManager().also { instance = it }
            }
        }
    }
    
    private val remoteConfig: FirebaseRemoteConfig by lazy {
        Firebase.remoteConfig
    }
    
    init {
        setupRemoteConfig()
    }
    
    private fun setupRemoteConfig() {
        val cacheExpiration = remoteConfig.getLong(KEY_CACHE_EXPIRATION_SECONDS)
            .takeIf { it > 0 } ?: DEFAULT_CACHE_EXPIRATION_SECONDS
        
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = cacheExpiration
        }
        remoteConfig.setConfigSettingsAsync(configSettings)
        
        val defaults = mapOf(
            KEY_CACHE_EXPIRATION_SECONDS to DEFAULT_CACHE_EXPIRATION_SECONDS,
            KEY_DEBUG_INFO_ENABLED to DEFAULT_DEBUG_INFO_ENABLED,
            KEY_AI_ENABLED to DEFAULT_AI_ENABLED,
            KEY_AI_MODEL to DEFAULT_AI_MODEL,
            KEY_AI_MAX_TOKENS_PER_REQUEST to DEFAULT_AI_MAX_TOKENS_PER_REQUEST,
            KEY_AI_CONCURRENT_REQUESTS to DEFAULT_AI_CONCURRENT_REQUESTS,
            KEY_AI_MAX_ITEMS_PER_BATCH to DEFAULT_AI_MAX_ITEMS_PER_BATCH,
            KEY_FIREBASE_SYNC_THRESHOLD to DEFAULT_FIREBASE_SYNC_THRESHOLD,
            KEY_DEBUG_ANALYTICS_SYNC_PERIOD_HOURS to DEFAULT_DEBUG_ANALYTICS_SYNC_PERIOD_HOURS
        )
        remoteConfig.setDefaultsAsync(defaults)
        
        Log.d(TAG, "Remote Config initialized with defaults, cache expiration: ${cacheExpiration}s")
    }
    
    suspend fun fetchAndActivate(): Boolean {
        return try {
            val activated = remoteConfig.fetchAndActivate().await()
            Log.d(TAG, "Remote config fetch and activated: $activated")
            activated
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch remote config", e)
            false
        }
    }
    
    fun isDebugInfoEnabled(): Boolean {
        return remoteConfig.getBoolean(KEY_DEBUG_INFO_ENABLED)
    }
    
    fun isAiEnabled(): Boolean {
        return remoteConfig.getBoolean(KEY_AI_ENABLED)
    }
    
    fun getAiModel(): String {
        return remoteConfig.getString(KEY_AI_MODEL)
    }
    
    fun getAiMaxTokensPerRequest(): Int {
        return remoteConfig.getLong(KEY_AI_MAX_TOKENS_PER_REQUEST).toInt()
    }
    
    fun getAiConcurrentRequests(): Int {
        return remoteConfig.getLong(KEY_AI_CONCURRENT_REQUESTS).toInt()
    }
    
    fun getAiMaxItemsPerBatch(): Int {
        return remoteConfig.getLong(KEY_AI_MAX_ITEMS_PER_BATCH).toInt()
    }
    
    fun getFirebaseSyncThreshold(): Int {
        return remoteConfig.getLong(KEY_FIREBASE_SYNC_THRESHOLD).toInt().coerceIn(10, 100)
    }
    
    fun getDebugAnalyticsSyncPeriodHours(): Long {
        return remoteConfig.getLong(KEY_DEBUG_ANALYTICS_SYNC_PERIOD_HOURS).takeIf { it > 0 } 
            ?: DEFAULT_DEBUG_ANALYTICS_SYNC_PERIOD_HOURS
    }
    
    fun getCacheExpirationSeconds(): Long {
        return remoteConfig.getLong(KEY_CACHE_EXPIRATION_SECONDS)
            .takeIf { it > 0 } ?: DEFAULT_CACHE_EXPIRATION_SECONDS
    }
    
    fun getAllConfigs(): Map<String, Any> {
        return mapOf(
            "cache_expiration_seconds" to getCacheExpirationSeconds(),
            "debug_info_enabled" to isDebugInfoEnabled(),
            "ai_enabled" to isAiEnabled(),
            "ai_model" to getAiModel(),
            "ai_max_tokens_per_request" to getAiMaxTokensPerRequest(),
            "ai_concurrent_requests" to getAiConcurrentRequests(),
            "ai_max_items_per_batch" to getAiMaxItemsPerBatch(),
            "debug_analytics_sync_period_hours" to getDebugAnalyticsSyncPeriodHours()
        )
    }
}
