package com.freebox.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freebox.app.data.ListingsRepository
import com.freebox.app.data.LootItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MapViewModel : ViewModel() {
    data class State(
        val loading: Boolean = true,
        val items: List<LootItem> = emptyList(),
        val error: String? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                _state.value = State(loading = false, items = ListingsRepository.fetchAvailable())
            } catch (e: Exception) {
                _state.value = State(loading = false, error = "Couldn't load map items.")
            }
        }
    }
}
