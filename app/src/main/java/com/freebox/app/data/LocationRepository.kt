package com.freebox.app.data

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object LocationRepository {
    // Demand-driven: schedules scraping for the user's ZIP (activates its 3 sources
    // in crawl_targets) and records the ZIP on their profile. Called once, from
    // onboarding — so the fleet only ever crawls areas real users have entered.
    suspend fun activateZip(zip: String) {
        supabase.postgrest.rpc("activate_zip", buildJsonObject { put("p_zip", zip) })
    }
}
