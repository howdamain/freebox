package com.freebox.app.ui.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freebox.app.data.AlertsRepository
import com.freebox.app.data.LootItem
import com.freebox.app.data.ProfitAlert
import com.freebox.app.data.VaultRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VaultViewModel : ViewModel() {

    data class State(
        val watchlistLoading: Boolean = true,
        val alertsLoading: Boolean = true,
        val watchlist: List<LootItem> = emptyList(),
        val alerts: List<ProfitAlert> = emptyList(),
        val watchlistError: String? = null,
        val alertsError: String? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        loadWatchlist()
        loadAlerts()
    }

    private fun loadWatchlist() {
        viewModelScope.launch {
            _state.value = _state.value.copy(watchlistLoading = true, watchlistError = null)
            try {
                val items = VaultRepository.watchlist()
                _state.value = _state.value.copy(watchlistLoading = false, watchlist = items)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    watchlistLoading = false,
                    watchlistError = "Couldn't load watchlist. Tap retry."
                )
            }
        }
    }

    private fun loadAlerts() {
        viewModelScope.launch {
            _state.value = _state.value.copy(alertsLoading = true, alertsError = null)
            try {
                val alerts = AlertsRepository.list()
                _state.value = _state.value.copy(alertsLoading = false, alerts = alerts)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    alertsLoading = false,
                    alertsError = "Couldn't load alerts. Tap retry."
                )
            }
        }
    }

    fun deleteAlert(id: String) {
        viewModelScope.launch {
            try {
                AlertsRepository.delete(id)
                loadAlerts()
            } catch (e: Exception) {
                // silently ignore; list will be stale but not crash
            }
        }
    }
}
