package com.brickkiln.erp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.brickkiln.erp.ui.navigation.AppNavGraph
import com.brickkiln.erp.ui.theme.BrickKilnTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // enableEdgeToEdge is safe but requires activity-compose 1.8+.
        // Wrap in try-catch in case of older API level issues.
        try {
            enableEdgeToEdge()
        } catch (e: Exception) {
            // ignore — fall back to default theme
        }
        setContent {
            BrickKilnTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppNavGraph()
                }
            }
        }
    }
}
