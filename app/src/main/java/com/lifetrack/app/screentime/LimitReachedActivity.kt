package com.lifetrack.app.screentime

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lifetrack.app.data.Dates
import com.lifetrack.app.ui.theme.LifeTrackTheme
import com.lifetrack.app.ui.theme.Space
import com.lifetrack.app.ui.theme.accents

/**
 * Shown on top of the home screen when [LimitGuardService] closes an app. There is no
 * "five more minutes" button on purpose: the way past a limit is to change it in LifeTrack,
 * which takes long enough to be a decision rather than a reflex.
 */
class LimitReachedActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val label = intent.getStringExtra(EXTRA_LABEL) ?: "This app"
        val used = intent.getLongExtra(EXTRA_USED_MIN, 0)
        val limit = intent.getIntExtra(EXTRA_LIMIT_MIN, 0)
        val opening = intent.getBooleanExtra(EXTRA_OPENING, true)

        setContent {
            LifeTrackTheme {
                val a = accents()
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        Modifier.fillMaxSize().padding(horizontal = Space.xl),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("⏳", style = MaterialTheme.typography.displayMedium)
                        Spacer(Modifier.height(Space.lg))
                        Text(
                            if (opening) "$label didn't open" else "$label was closed",
                            style = MaterialTheme.typography.titleMedium,
                            color = a.negative,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(Space.xs))
                        Text(
                            "Daily time limit reached",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(Space.sm))
                        Text(
                            "You've used $label for ${Dates.formatMinutes(used.toInt())} today and " +
                                "your limit is ${Dates.formatMinutes(limit)}, so LifeTrack " +
                                (if (opening) "stopped it from opening." else "closed it.") +
                                " It opens again tomorrow.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(Space.xl))
                        Button(
                            onClick = { finish() },
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) { Text("OK") }
                        Spacer(Modifier.height(Space.sm))
                        Text(
                            "To change the limit: LifeTrack → All → Screen time.",
                            style = MaterialTheme.typography.bodySmall,
                            color = a.screen,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    companion object {
        private const val EXTRA_LABEL = "label"
        private const val EXTRA_USED_MIN = "used_min"
        private const val EXTRA_LIMIT_MIN = "limit_min"
        private const val EXTRA_OPENING = "opening"

        fun intent(context: Context, label: String, usedMin: Long, limitMin: Int, opening: Boolean): Intent =
            Intent(context, LimitReachedActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_LABEL, label)
                .putExtra(EXTRA_USED_MIN, usedMin)
                .putExtra(EXTRA_LIMIT_MIN, limitMin)
                .putExtra(EXTRA_OPENING, opening)
    }
}
