package com.iktishafat.teleprompter

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.iktishafat.teleprompter.domain.remote.RemoteCommand
import com.iktishafat.teleprompter.ui.AppNavHost
import com.iktishafat.teleprompter.ui.theme.IktishafatTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    /** A document handed to the app via "Open with" / "Share", imported by the UI. */
    private val pendingImport = MutableStateFlow<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)

        val container = appContainer
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.settings.settings.collect { s ->
                    container.remote.mapper.volumeKeysEnabled = s.volumeKeysControlPrompter
                }
            }
        }

        setContent {
            IktishafatTheme {
                AppNavHost(
                    container = container,
                    pendingImport = pendingImport,
                    onImportConsumed = { pendingImport.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        }
        if (uri != null) pendingImport.value = uri
    }

    /**
     * Bluetooth keyboards, clickers, foot pedals and media remotes arrive here as key
     * events. They are only consumed while a prompter screen is listening.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val remote = appContainer.remote
        if (remote.handles(event.keyCode)) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                val cmd = remote.mapper.map(event.keyCode)
                val isToggle = cmd == RemoteCommand.PLAY_PAUSE || cmd == RemoteCommand.RESTART ||
                    cmd == RemoteCommand.TOGGLE_MIRROR || cmd == RemoteCommand.EXIT
                // Holding a key repeats speed/scroll commands but must not toggle play/pause repeatedly.
                if (event.repeatCount > 0 && isToggle) return true
                remote.onKey(event.keyCode)
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
