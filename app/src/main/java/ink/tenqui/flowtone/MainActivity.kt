package ink.tenqui.flowtone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ink.tenqui.flowtone.ui.FlowtoneApp
import ink.tenqui.flowtone.ui.theme.FlowtoneTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FlowtoneTheme {
                FlowtoneApp()
            }
        }
    }
}
