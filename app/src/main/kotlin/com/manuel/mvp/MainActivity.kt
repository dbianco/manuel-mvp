package com.manuel.mvp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Placeholder entry point for the Manuel MVP scaffold.
 *
 * This activity exists only to prove that the Gradle/Kotlin/Compose scaffold builds and runs.
 * The real single-screen state machine (armed/listening/processing/responding/error) is
 * implemented in a later task (T020), not here.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ManuelPlaceholder()
        }
    }
}

@Composable
private fun ManuelPlaceholder() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Manuel")
            }
        }
    }
}
