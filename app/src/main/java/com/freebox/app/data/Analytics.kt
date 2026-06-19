package com.freebox.app.data

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Lightweight first-party analytics — logs user-interaction events to the
 * Supabase `analytics_events` table. No third-party SDK and no advertising ID:
 * these are "app activity" events already covered by the Data Safety declaration.
 *
 * Fire-and-forget: never blocks the UI, silently no-ops on failure or when the
 * user is signed out (RLS only allows inserting your own events).
 */
object Analytics {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun track(event: String, props: Map<String, String> = emptyMap()) {
        scope.launch {
            runCatching {
                val uid = supabase.auth.currentUserOrNull()?.id ?: return@launch
                val row = buildJsonObject {
                    put("user_id", uid)
                    put("event", event)
                    if (props.isNotEmpty()) {
                        put("properties", buildJsonObject { props.forEach { (k, v) -> put(k, v) } })
                    }
                }
                supabase.from("analytics_events").insert(row)
            }
        }
    }
}
