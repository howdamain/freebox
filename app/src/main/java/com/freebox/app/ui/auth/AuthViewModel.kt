package com.freebox.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freebox.app.data.supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val info: String? = null
)

// Wraps Supabase email auth. The app-wide session is observed via
// supabase.auth.sessionStatus in FreeboxApp, which gates navigation;
// this VM only drives the sign-in / sign-up form.
class AuthViewModel : ViewModel() {
    private val _ui = MutableStateFlow(AuthUiState())
    val ui: StateFlow<AuthUiState> = _ui.asStateFlow()

    fun signIn(email: String, password: String) {
        if (!validate(email, password)) return
        _ui.value = AuthUiState(loading = true)
        viewModelScope.launch {
            try {
                supabase.auth.signInWith(Email) {
                    this.email = email.trim()
                    this.password = password
                }
                _ui.value = AuthUiState()
            } catch (e: Exception) {
                _ui.value = AuthUiState(error = e.message ?: "Couldn't sign in. Check your email and password.")
            }
        }
    }

    fun signUp(email: String, password: String) {
        if (!validate(email, password)) return
        _ui.value = AuthUiState(loading = true)
        viewModelScope.launch {
            try {
                supabase.auth.signUpWith(Email) {
                    this.email = email.trim()
                    this.password = password
                }
                // If the project requires email confirmation, no session is set yet.
                _ui.value = if (supabase.auth.currentSessionOrNull() == null) {
                    AuthUiState(info = "Almost there — check your email to confirm, then sign in.")
                } else {
                    AuthUiState()
                }
            } catch (e: Exception) {
                _ui.value = AuthUiState(error = e.message ?: "Couldn't create your account.")
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            try { supabase.auth.signOut() } catch (_: Exception) {}
        }
    }

    fun clearMessage() {
        _ui.value = _ui.value.copy(error = null, info = null)
    }

    private fun validate(email: String, password: String): Boolean {
        if (email.isBlank() || !email.contains("@")) {
            _ui.value = AuthUiState(error = "Enter a valid email address.")
            return false
        }
        if (password.length < 6) {
            _ui.value = AuthUiState(error = "Password must be at least 6 characters.")
            return false
        }
        return true
    }
}
