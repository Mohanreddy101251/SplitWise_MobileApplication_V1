package com.spliwise

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import com.spliwise.auth.LoginScreen
import com.spliwise.auth.Profile
import com.spliwise.ui.MainApp
import com.spliwise.ui.SpliTheme

class MainActivity : FragmentActivity() {
    private var profile by mutableStateOf<Profile?>(null)
    private var openReview by mutableStateOf(false)
    private var stoppedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openReview = intent?.getStringExtra("open") == "review"
        setContent {
            SpliTheme {
                val p = profile
                if (p == null) {
                    LoginScreen(container.profiles) { container.profiles.setActive(it.phone); profile = it }
                } else {
                    val repo = remember(p.phone) { runCatching { container.repoFor(p.phone) }.getOrNull() }
                    val settings = remember(p.phone) { container.settings(p.phone) }
                    if (repo == null) {
                        // The vault key is gone (e.g. data kept after uninstall but the phone's key store was wiped)
                        com.spliwise.auth.VaultProblem(p, onBack = { profile = null }) {
                            container.profiles.delete(p); profile = null
                        }
                    } else MainApp(
                        repo = repo, settings = settings, profile = p, openReview = openReview,
                        onReviewOpened = { openReview = false },
                        onLock = { profile = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getStringExtra("open") == "review") openReview = true
    }

    override fun onStop() { super.onStop(); stoppedAt = System.currentTimeMillis() }

    /** Auto-lock after 2 minutes in the background. */
    override fun onStart() {
        super.onStart()
        if (stoppedAt > 0 && System.currentTimeMillis() - stoppedAt > 120_000) profile = null
    }
}
