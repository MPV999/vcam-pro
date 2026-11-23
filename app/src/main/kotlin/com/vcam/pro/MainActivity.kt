package com.vcam.pro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.vcam.pro.ui.VcamProApp
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val container = (application as VcamApplication).container
        // Read the stored language once, before the first frame is drawn.
        val language = runBlocking { container.settingsRepository.current().language }
        LocaleApplier.apply(this, language)

        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            VcamProApp(container)
        }
    }
}
