package com.privacy.faraday

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.privacy.faraday.ui.debug.SignalTestScreen
import com.privacy.faraday.ui.theme.FaradayTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FaradayTheme {
                SignalTestScreen()
            }
        }
    }
}
