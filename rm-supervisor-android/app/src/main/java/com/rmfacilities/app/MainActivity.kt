package com.rmfacilities.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import com.rmfacilities.app.ui.navigation.AppNavGraph
import com.rmfacilities.app.ui.theme.RMFacilitiesTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RMFacilitiesTheme {
                Surface {
                    AppNavGraph(app = application as RMFacilitiesApp)
                }
            }
        }
    }
}
