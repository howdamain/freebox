package com.freebox.app.ui.pro

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freebox.app.data.Analytics
import com.freebox.app.data.BillingManager
import com.freebox.app.data.EntitlementRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class EntitlementState { UNKNOWN, ENTITLED, NOT_ENTITLED }

// Drives the hard-paywall gate. On launch it checks the user's entitlement and
// listens for Play Billing verification events. Entitlements are granted ONLY
// server-side (verify-purchase Edge Function); the client never self-grants —
// it re-reads the entitlements row after a verified purchase.
class EntitlementViewModel : ViewModel() {
    private val _state = MutableStateFlow(EntitlementState.UNKNOWN)
    val state: StateFlow<EntitlementState> = _state.asStateFlow()

    private val _working = MutableStateFlow(false)
    val working: StateFlow<Boolean> = _working.asStateFlow()

    init {
        refresh()
        // Re-check entitlement whenever Play Billing reports a verified purchase.
        viewModelScope.launch {
            BillingManager.events.collect { event ->
                if (event is BillingManager.BillingEvent.EntitlementChanged) refresh()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val entitled = runCatching { EntitlementRepository.isEntitled() }.getOrDefault(false)
            _state.value = if (entitled) EntitlementState.ENTITLED else EntitlementState.NOT_ENTITLED
        }
    }

    /** Launch the Play purchase flow for the selected plan ("Monthly"/"Yearly"). */
    fun subscribe(activity: Activity, plan: String) {
        val basePlanId =
            if (plan.equals("Yearly", ignoreCase = true)) BillingManager.BASE_PLAN_ANNUAL
            else BillingManager.BASE_PLAN_MONTHLY
        Analytics.track("subscribe_clicked", mapOf("plan" to plan))
        _working.value = true
        viewModelScope.launch {
            // launchBillingFlow returns once the Play dialog is shown; the actual
            // result arrives via BillingManager.events → refresh() flips the gate.
            runCatching { BillingManager.launchPurchase(activity, basePlanId) }
            _working.value = false
        }
    }
}
