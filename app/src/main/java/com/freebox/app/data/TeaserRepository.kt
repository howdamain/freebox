package com.freebox.app.data

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class TeaserSample(
    val title: String,
    @SerialName("est_resale_value") val value: Int? = null,
    val category: String? = null
)

@Serializable
data class AreaTeaser(
    val zip: String? = null,
    @SerialName("item_count") val itemCount: Int = 0,
    @SerialName("total_value") val totalValue: Int = 0,
    val sample: List<TeaserSample> = emptyList()
)

@Serializable
private data class ProfileZip(@SerialName("home_zip") val homeZip: String? = null)

object TeaserRepository {
    // Real per-area proof for the paywall: count + total est-resale + a few
    // LOCKED teaser items (titles/values, no location). Works for unentitled
    // users via the area_teaser SECURITY DEFINER RPC, so the paywall can show
    // genuine local numbers before the user pays.
    suspend fun myAreaTeaser(): AreaTeaser? {
        val uid = supabase.auth.currentUserOrNull()?.id ?: return null
        val zip = supabase.from("profiles").select { filter { eq("id", uid) } }
            .decodeList<ProfileZip>().firstOrNull()?.homeZip ?: return null
        return supabase.postgrest
            .rpc("area_teaser", buildJsonObject { put("p_zip", zip) })
            .decodeAs<AreaTeaser>()
    }
}
