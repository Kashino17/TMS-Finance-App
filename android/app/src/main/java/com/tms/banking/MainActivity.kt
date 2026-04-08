package com.tms.banking

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.tms.banking.ui.navigation.TmsNavigation
import com.tms.banking.ui.theme.TMSBankingTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as TmsApp
        setContent {
            TMSBankingTheme {
                TmsNavigation(app = app)
            }
        }
    }
}
