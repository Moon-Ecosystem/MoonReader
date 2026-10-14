package com.example

import android.content.Intent
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ui.MangaApp
import com.example.ui.MangaViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MangaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only on first launch: after a rotation the ViewModel already has the file open
        if (savedInstanceState == null) openViewIntent(intent)
        setContent {
            MangaApp(viewModel = viewModel)
        }
    }

    // singleTask: a running app gets "Open with" files and the AniList login redirect here
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openViewIntent(intent)
    }

    /** "Open with MangaReader": reads the CBZ another app handed over; or finishes the AniList login. */
    private fun openViewIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        if (uri.scheme == "mangareader" && uri.host == "anilist-auth") {
            // Implicit grant: the token comes in the fragment, e.g. #access_token=...&token_type=Bearer
            val token = uri.fragment?.split('&')
                ?.firstOrNull { it.startsWith("access_token=") }
                ?.substringAfter('=')
            viewModel.completeAniListLogin(token)
            return
        }
        val name = runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: "Comic"
        // Plain file paths are read directly; content URIs through their provider
        val source = if (uri.scheme == "file") uri.path ?: return else uri.toString()
        viewModel.openExternalFile(source, name.substringBeforeLast('.'))
    }
}
