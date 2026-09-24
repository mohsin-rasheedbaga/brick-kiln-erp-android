package com.brickkiln.erp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.brickkiln.erp.ui.navigation.AppNavGraph
import com.brickkiln.erp.ui.theme.BrickKilnTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BrickKilnTheme {
                AppNavGraph()
            }
        }
    }
}
