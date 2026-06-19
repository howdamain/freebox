package com.freebox.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.freebox.app.data.BillingManager
import com.freebox.app.ui.theme.FreeboxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Connect to Play Billing early so entitlement restores on launch.
        BillingManager.start(applicationContext)
        // Edge-to-edge is enforced at targetSdk 36 — opt in explicitly so older
        // versions match and status-bar icons stay dark on our light surfaces.
        enableEdgeToEdge()
        setContent {
            FreeboxTheme {
                FreeboxApp()
            }
        }
    }
}
