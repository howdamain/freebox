package com.freebox.app.data

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EntitlementDto(
    @SerialName("user_id") val userId: String,
    val status: String,
    @SerialName("expires_at") val expiresAt: String? = null
)

object EntitlementRepository {

    // Reads the caller's own entitlement (RLS allows select-own). The hard
    // paywall is enforced at the data layer too: listings RLS calls is_entitled().
    suspend fun isEntitled(): Boolean {
        val uid = supabase.auth.currentUserOrNull()?.id ?: return false
        val rows = supabase.from("entitlements").select {
            filter {
                eq("user_id", uid)
                eq("status", "active")
            }
        }.decodeList<EntitlementDto>()
        return rows.isNotEmpty()
    }
}
