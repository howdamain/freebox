package com.freebox.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class SearchFilters(
    val quickSelects: Set<String> = emptySet(),
    val minProfit: Int = 50,
    val sources: Set<String> = setOf("Facebook Marketplace", "Craigslist", "OfferUp")
)

object FiltersStore {
    var filters by mutableStateOf(SearchFilters())
}
