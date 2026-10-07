package com.lifetrack.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.lifetrack.app.data.Dates
import com.lifetrack.app.data.Slot
import com.lifetrack.app.screentime.LimitWatchService
import com.lifetrack.app.screentime.UsageSync
import com.lifetrack.app.ui.LifeTrackNav
import com.lifetrack.app.ui.theme.LifeTrackTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /**
     * The day every screen was built for. Screens fix "today" when they are created, so a phone
     * that kept the app in memory overnight would open on yesterday's numbers - instead the
     * whole activity is rebuilt as soon as the date moves on.
     */
    private var builtFor: String = Dates.today()

    private val dayWatcher = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { restartIfNewDay() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Health Connect's "privacy policy" link (and Android 14+'s permission-usage screen) land
        // here; what they want to show is the policy, not the app.
        if (intent?.action in PRIVACY_ACTIONS) {
            runCatching { startActivity(privacyPolicyIntent()) }
            finish()
            return
        }
        enableEdgeToEdge()
        builtFor = Dates.today()

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
        if (isFinishing) {
            // Already handed over to a fresh instance for the new day; pass the tap along.
            if (intent.hasExtra(EXTRA_ROUTE)) {
                startActivity(Intent(intent).setClass(this, MainActivity::class.java))
            }
            return
        }
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

        /** Published from docs/privacy-policy.md by GitHub Pages. */
        const val PRIVACY_POLICY_URL = "https://111surajmaurya.github.io/LifeTrack/privacy-policy.html"

        private val PRIVACY_ACTIONS = setOf(
            "androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE",
            "android.intent.action.VIEW_PERMISSION_USAGE"
        )

        /** Opens the policy in the browser; LifeTrack itself has no internet access. */
        fun privacyPolicyIntent(): Intent =
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse(PRIVACY_POLICY_URL))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        fun deepLink(context: Context, route: String, slot: String? = null): Intent =
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_ROUTE, route)
                .apply { if (slot != null) putExtra(EXTRA_SLOT, slot) }
    }

    override fun onStart() {
        super.onStart()
        if (restartIfNewDay()) return
        // DATE_CHANGED covers midnight passing with the app on screen; the others a clock or zone change.
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(this, dayWatcher, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStop() {
        runCatching { unregisterReceiver(dayWatcher) }
        super.onStop()
    }

    /** Rebuilds the activity, and with it every screen's view model, when today isn't [builtFor]. */
    private fun restartIfNewDay(): Boolean {
        if (Dates.today() == builtFor || isFinishing) return false
        val next = Intent(this, MainActivity::class.java)
        pendingRoute.value?.let { next.putExtra(EXTRA_ROUTE, it) }
        startActivity(next)
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        return true
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
            // Back from a settings page, an access may have just been switched on or off.
            runCatching { LimitWatchService.sync(this@MainActivity) }
        }
    }
}
