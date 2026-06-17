package com.freebox.app.ui.pro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freebox.app.data.EntitlementRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class EntitlementState { UNKNOWN, ENTITLED, NOT_ENTITLED }

// Drives the hard-paywall gate. On launch it checks the user's entitlement;
// the trial CTA currently self-grants via the dev RPC (replaced by Play Billing
// verification before ship).
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
                EntitlementRepository.devGrant()
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
