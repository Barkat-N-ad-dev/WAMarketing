package com.wamarketing.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SessionManager(context: Context? = null) {

    private val prefs: SharedPreferences? = context?.getSharedPreferences("wamarketing_session", Context.MODE_PRIVATE)

    private val _isLoggedIn = MutableStateFlow(
        prefs?.getBoolean(KEY_IS_LOGGED_IN, true) ?: true
    )
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _hasSeenOnboarding = MutableStateFlow(
        prefs?.getBoolean(KEY_HAS_SEEN_ONBOARDING, false) ?: false
    )
    val hasSeenOnboarding: StateFlow<Boolean> = _hasSeenOnboarding.asStateFlow()

    private val _userEmail = MutableStateFlow(
        prefs?.getString(KEY_USER_EMAIL, "admin@wamarketing.app") ?: "admin@wamarketing.app"
    )
    val userEmail: StateFlow<String> = _userEmail.asStateFlow()

    private val _userName = MutableStateFlow(
        prefs?.getString(KEY_USER_NAME, "Business Owner") ?: "Business Owner"
    )
    val userName: StateFlow<String> = _userName.asStateFlow()

    fun completeOnboarding() {
        _hasSeenOnboarding.value = true
        prefs?.edit()?.putBoolean(KEY_HAS_SEEN_ONBOARDING, true)?.apply()
    }

    fun login(email: String, name: String, token: String) {
        _isLoggedIn.value = true
        _userEmail.value = email
        _userName.value = name
        prefs?.edit()?.apply {
            putBoolean(KEY_IS_LOGGED_IN, true)
            putString(KEY_USER_EMAIL, email)
            putString(KEY_USER_NAME, name)
            putString(KEY_AUTH_TOKEN, token)
            apply()
        }
    }

    /**
     * Preserves user session during WhatsApp number verification and OAuth connections.
     * Guaranteed to never destroy active login state or credentials.
     */
    fun maintainSession() {
        _isLoggedIn.value = true
        prefs?.edit()?.putBoolean(KEY_IS_LOGGED_IN, true)?.apply()
    }

    fun logout() {
        _isLoggedIn.value = false
        prefs?.edit()?.apply {
            putBoolean(KEY_IS_LOGGED_IN, false)
            remove(KEY_AUTH_TOKEN)
            apply()
        }
    }

    companion object {
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_HAS_SEEN_ONBOARDING = "has_seen_onboarding"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_AUTH_TOKEN = "auth_token"

        @Volatile
        private var instance: SessionManager? = null

        fun getInstance(context: Context? = null): SessionManager {
            return instance ?: synchronized(this) {
                instance ?: SessionManager(context?.applicationContext).also { instance = it }
            }
        }
    }
}
