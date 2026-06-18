package com.freebox.app.ui.pro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freebox.app.data.EntitlementRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class EntitlementState { UNKNOWN, ENTITLED, NOT_ENTITLED }

// Drives the hard-paywall gate. On launch it checks the user's entitlement.
// The trial CTA must trigger Play Billing; entitlements are granted only
// server-side after purchase verification (no client-side self-grant).
class EntitlementViewModel : ViewModel() {
    private val _state = MutableStateFlow(EntitlementState.UNKNOWN)
    val state: StateFlow<EntitlementState> = _state.asStateFlow()

    private val _working = MutableStateFlow(false)
    val working: StateFlow<Boolean> = _working.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val entitled = runCatching { EntitlementRepository.isEntitled() }.getOrDefault(false)
            _state.value = if (entitled) EntitlementState.ENTITLED else EntitlementState.NOT_ENTITLED
        }
    }

    fun startTrial() {
        viewModelScope.launch {
            _working.value = true
            try {
                // Entitlements are granted only server-side after Play Billing
                // verification (service_role). Wire the purchase flow here; the
                // client never self-grants. Until then the CTA just re-checks state.
                val entitled = EntitlementRepository.isEntitled()
                _state.value = if (entitled) EntitlementState.ENTITLED else EntitlementState.NOT_ENTITLED
            } catch (_: Exception) {
                // stay on the paywall; user can retry
            } finally {
                _working.value = false
            }
        }
    }
}
