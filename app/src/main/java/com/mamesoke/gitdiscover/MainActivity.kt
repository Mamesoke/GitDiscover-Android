package com.mamesoke.gitdiscover

import android.os.Bundle
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mamesoke.gitdiscover.app.GitDiscoverApp
import com.mamesoke.gitdiscover.ui.theme.GitDiscoverTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GitDiscoverTheme {
                GitDiscoverApp(
                    viewModel = viewModel(factory = (application as GitDiscoverApplication).container.searchViewModelFactory),
                    openLink = { url -> openBrowser(url) }
                )
            }
        }
    }

    private fun openBrowser(url: String): Boolean {
        return try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}