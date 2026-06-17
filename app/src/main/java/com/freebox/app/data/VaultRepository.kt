package com.freebox.app.data

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class VaultItemRow(@SerialName("listing_id") val listingId: String)

@Serializable
private data class VaultInsert(
    @SerialName("user_id") val userId: String,
    @SerialName("listing_id") val listingId: String
)

object VaultRepository {

    private suspend fun watchlistIds(): List<String> {
        val uid = supabase.auth.currentUserOrNull()?.id ?: return emptyList()
        return supabase.from("vault_items").select {
            filter { eq("user_id", uid) }
        }.decodeList<VaultItemRow>().map { it.listingId }
    }

    suspend fun watchlist(): List<LootItem> {
        val ids = watchlistIds()
        if (ids.isEmpty()) return emptyList()
        return supabase.from("listings").select {
            filter { isIn("id", ids) }
        }.decodeList<ListingDto>().map { it.toLootItem() }
    }

    suspend fun isSaved(listingId: String): Boolean = watchlistIds().contains(listingId)

    suspend fun add(listingId: String) {
        val uid = supabase.auth.currentUserOrNull()?.id ?: return
        supabase.from("vault_items").insert(VaultInsert(uid, listingId))
    }

    suspend fun remove(listingId: String) {
        val uid = supabase.auth.currentUserOrNull()?.id ?: return
        supabase.from("vault_items").delete {
            filter {
                eq("user_id", uid)
                eq("listing_id", listingId)
            }
        }
    }
}
