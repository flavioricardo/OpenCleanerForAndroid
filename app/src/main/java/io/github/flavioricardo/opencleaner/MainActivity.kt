package io.github.flavioricardo.opencleaner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import io.github.flavioricardo.opencleaner.ui.App
import io.github.flavioricardo.opencleaner.ui.OpenCleanerTheme

class MainActivity : ComponentActivity() {
    // Bumped on every resume, so the UI re-checks permissions granted in Settings.
    private val resumes = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { OpenCleanerTheme { App(resumes.intValue) } }
    }

    override fun onResume() {
        super.onResume()
        resumes.intValue++
    }
}
