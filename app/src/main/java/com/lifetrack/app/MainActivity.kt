package com.lifetrack.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.lifetrack.app.data.Slot
import com.lifetrack.app.screentime.UsageSync
import com.lifetrack.app.ui.LifeTrackNav
import com.lifetrack.app.ui.theme.LifeTrackTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // No permission dialogs here: every access is asked for from the Settings tab, which a
        // fresh install opens on, so nothing is requested before its purpose has been shown.

        handleDeepLink(intent)

        setContent {
            LifeTrackTheme {
                LifeTrackNav(
                    pendingRoute = pendingRoute,
                    onRouteHandled = { pendingRoute.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    /** Where a tapped notification wants to land. Consumed once by [LifeTrackNav]. */
    private val pendingRoute = MutableStateFlow<String?>(null)

    private fun handleDeepLink(intent: Intent?) {
        val route = intent?.getStringExtra(EXTRA_ROUTE) ?: return
        intent.removeExtra(EXTRA_ROUTE)   // a rotation must not navigate a second time
        val slot = intent.getStringExtra(EXTRA_SLOT)
        if (slot != null) {
            // "Log breakfast" should open Calories already filing under Breakfast.
            val repo = (application as LifeTrackApp).repository
            lifecycleScope.launch(Dispatchers.IO) { repo.setActiveSlot(Slot.from(slot)) }
        }
        pendingRoute.value = route
    }

    companion object {
        const val EXTRA_ROUTE = "open_route"
        const val EXTRA_SLOT = "open_slot"
        const val ROUTE_CALORIES = "calories"
        const val ROUTE_ROUTINE = "routine"
        const val ROUTE_PLAN = "routine/plan"

        fun deepLink(context: Context, route: String, slot: String? = null): Intent =
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_ROUTE, route)
                .apply { if (slot != null) putExtra(EXTRA_SLOT, slot) }
    }

    /**
     * Screen time is the one figure that keeps moving while the app is closed, so take a
     * snapshot every time we come back rather than only when its own tab is opened.
     */
    override fun onResume() {
        super.onResume()
        val repo = (application as LifeTrackApp).repository
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { UsageSync.snapshotToday(this@MainActivity, repo) }
        }
    }
}
