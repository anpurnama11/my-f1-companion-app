package com.anpurnama.f1_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.anpurnama.f1_app.core.navigation.NavShell
import com.anpurnama.f1_app.ui.theme.F1appTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // edge-to-edge: keep the system from drawing a contrast scrim over the
        // gesture-pill / 3-button nav area — it would wash out the dark
        // surfaceContainer bottom bar. See edge-to-edge skill checklist.
        window.isNavigationBarContrastEnforced = false
        setContent {
            F1appTheme {
                NavShell()
            }
        }
    }
}
