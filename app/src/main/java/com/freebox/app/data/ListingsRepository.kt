package com.freebox.app.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class ListingDto(
    val id: String,
    val source: String,
    val title: String,
    val description: String? = null,
    val category: String? = null,
    val city: String? = null,
    @SerialName("est_resale_value") val estResaleValue: Int? = null,
    @SerialName("est_profit") val estProfit: Int? = null,
    val price: String = "Free",
    val condition: String? = null,
    @SerialName("finder_name") val finderName: String? = null,
    @SerialName("finder_note") val finderNote: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("posted_at") val postedAt: String? = null,
    val status: String = "available"
)

// Maps a DB row to the display model the screens already use. Distance stays a
// placeholder until GPS lands (Phase 7); the filter predicate treats 0.0 as "passes".
fun ListingDto.toLootItem(): LootItem = LootItem(
    id = id,
    title = title,
    description = description ?: "",
    category = (category ?: "misc").replaceFirstChar { it.uppercase() },
    location = city ?: "Nearby",
    timeAgo = relativeTime(postedAt),
    estProfit = "+$${estProfit ?: 0}",
    resaleValue = "$${estResaleValue ?: 0}",
    condition = condition ?: "—",
    distanceAway = "nearby",
    finderName = finderName ?: "A scavenger",
    finderNote = finderNote ?: "",
    sourceName = source,
    distanceMiles = 0.0,
    profitValue = estProfit ?: 0
)

// Dependency-free ISO-8601 relative time (java.time needs desugaring at minSdk 24).
// "XXX" timezone parsing is available from API 24; fractional seconds are stripped.
private fun relativeTime(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return try {
        val cleaned = iso.replace(Regex("\\.\\d+"), "")
        val parsed = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).parse(cleaned)
            ?: return ""
        val secs = (System.currentTimeMillis() - parsed.time) / 1000
        when {
            secs < 60 -> "just now"
            secs < 3600 -> "${secs / 60} min ago"
            secs < 86_400 -> "${secs / 3600} hr ago"
            else -> "${secs / 86_400} d ago"
        }
    } catch (e: Exception) {
        ""
    }
}

object ListingsRepository {

    suspend fun fetchAvailable(): List<LootItem> =
        supabase.from("listings").select {
            filter { eq("status", "available") }
            order(column = "posted_at", order = Order.DESCENDING)
        }.decodeList<ListingDto>().map { it.toLootItem() }

    suspend fun fetchById(id: String): LootItem? =
        supabase.from("listings").select {
            filter { eq("id", id) }
        }.decodeList<ListingDto>().firstOrNull()?.toLootItem()

    // Atomic claim via the SECURITY DEFINER RPC (checks entitlement + availability).
    suspend fun claim(id: String) {
        supabase.postgrest.rpc("claim_listing", buildJsonObject { put("p_listing_id", id) })
    }
}
