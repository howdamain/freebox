package com.freebox.app.data

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// App-facing alert model (DB-backed; id is the listings/alerts uuid).
data class ProfitAlert(
    val id: String,
    val keyword: String,
    val category: String,
    val minProfit: Int,
    val frequency: String
)

@Serializable
data class AlertDto(
    val id: String? = null,
    @SerialName("user_id") val userId: String? = null,
    val keyword: String,
    val category: String? = null,
    @SerialName("min_profit") val minProfit: Int = 0,
    val frequency: String = "Instant",
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
private data class AlertInsert(
    @SerialName("user_id") val userId: String,
    val keyword: String,
    val category: String?,
    @SerialName("min_profit") val minProfit: Int,
    val frequency: String
)

private fun AlertDto.toProfitAlert() = ProfitAlert(
    id = id ?: "",
    keyword = keyword,
    category = category ?: "",
    minProfit = minProfit,
    frequency = frequency
)

object AlertsRepository {

    suspend fun list(): List<ProfitAlert> {
        val uid = supabase.auth.currentUserOrNull()?.id ?: return emptyList()
        return supabase.from("alerts").select {
            filter { eq("user_id", uid) }
            order(column = "created_at", order = Order.DESCENDING)
        }.decodeList<AlertDto>().map { it.toProfitAlert() }
    }

    suspend fun count(): Int = list().size

    suspend fun byId(id: String): ProfitAlert? =
        supabase.from("alerts").select {
            filter { eq("id", id) }
        }.decodeList<AlertDto>().firstOrNull()?.toProfitAlert()

    suspend fun create(keyword: String, category: String?, minProfit: Int, frequency: String): ProfitAlert? {
        val uid = supabase.auth.currentUserOrNull()?.id ?: return null
        return supabase.from("alerts")
            .insert(AlertInsert(uid, keyword, category, minProfit, frequency)) { select() }
            .decodeSingleOrNull<AlertDto>()?.toProfitAlert()
    }

    suspend fun delete(id: String) {
        supabase.from("alerts").delete { filter { eq("id", id) } }
    }
}
