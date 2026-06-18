package com.freebox.app.data

import io.github.jan.supabase.functions.functions

object AccountRepository {
    // Deletes the signed-in user's account via the delete-account Edge Function
    // (service-role deleteUser → cascades profile/vault/alerts/claims/entitlement).
    // The SDK attaches the current session JWT automatically.
    suspend fun deleteAccount() {
        supabase.functions.invoke("delete-account")
    }
}
