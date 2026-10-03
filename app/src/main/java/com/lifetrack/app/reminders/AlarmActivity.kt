package com.lifetrack.app.reminders

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifetrack.app.data.Dates
import com.lifetrack.app.ui.theme.LifeTrackTheme
import com.lifetrack.app.ui.theme.Space
import com.lifetrack.app.ui.theme.accents
import kotlinx.coroutines.delay
import java.time.LocalTime

/**
 * The ringing screen. Launched by the full-screen intent on [AlarmService]'s notification, so it
 * has to be able to come up over the keyguard on its own.
 */
class AlarmActivity : ComponentActivity() {

    private var reminderId = -1L
    private var snoozeMinutes = 5

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        reminderId = intent.getLongExtra(ReminderScheduler.EXTRA_ID, -1L)
        val label = intent.getStringExtra(AlarmService.EXTRA_LABEL)?.takeIf { it.isNotBlank() }
            ?: "Alarm"
        snoozeMinutes = intent.getIntExtra(AlarmService.EXTRA_SNOOZE_MINUTES, 5)

        showOverKeyguard()

        // BACK on a ringing alarm is nearly always a fumble in the dark. Silently killing the
        // alarm on that is how people oversleep, so it snoozes instead - never a silent dismiss.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = snooze()
        })

        setContent {
            LifeTrackTheme {
                val ringing by AlarmState.ringing.collectAsStateWithLifecycle()
                // Dismissed from the shade, rang itself out, or the service died: nothing left
                // to show, so get out of the user's way.
                LaunchedEffect(ringing) { if (ringing != reminderId) finish() }

                AlarmScreen(
                    label = label,
                    snoozeMinutes = snoozeMinutes,
                    onSnooze = ::snooze,
                    onDismiss = ::dismiss
                )
            }
        }
    }

    private fun showOverKeyguard() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Only ask on an insecure keyguard: on a PIN/pattern device this would put the unlock
        // prompt in front of the alarm, which is the opposite of what we want.
        val km = getSystemService(KeyguardManager::class.java)
        if (km != null && km.isKeyguardLocked && !km.isKeyguardSecure) {
            runCatching { km.requestDismissKeyguard(this, null) }
        }
    }

    private fun snooze() {
        startServiceAction(AlarmService.snoozeIntent(this, reminderId))
        finish()
    }

    private fun dismiss() {
        startServiceAction(AlarmService.stopIntent(this, reminderId))
        finish()
    }

    private fun startServiceAction(intent: Intent) {
        // The service is already foreground, but startForegroundService is the safe call if it
        // was killed and this screen outlived it.
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
            else startService(intent)
        }
    }

    companion object {
        fun intent(context: Context, id: Long, label: String, snoozeMinutes: Int): Intent =
            Intent(context, AlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)   // singleInstance keeps it a single window
                .putExtra(ReminderScheduler.EXTRA_ID, id)
                .putExtra(AlarmService.EXTRA_LABEL, label)
                .putExtra(AlarmService.EXTRA_SNOOZE_MINUTES, snoozeMinutes)
    }
}

@Composable
private fun AlarmScreen(
    label: String,
    snoozeMinutes: Int,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit
) {
    val accent = accents().alarms
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = LocalTime.now()
        }
    }

    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            accent.copy(alpha = 0.26f),
                            MaterialTheme.colorScheme.surface,
                            accent.copy(alpha = 0.14f)
                        )
                    )
                )
                .padding(horizontal = Space.xl, vertical = Space.xxl)
        ) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(Space.xxl))
                    PulsingBell(accent)
                    Spacer(Modifier.height(Space.xl))
                    Text(
                        Dates.clockLabel(now.hour, now.minute),
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        label,
                        style = MaterialTheme.typography.headlineSmall,
                        color = accent,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = Space.sm)
                    )
                }

                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Space.lg)
                ) {
                    Button(
                        onClick = onSnooze,
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier.fillMaxWidth().height(76.dp)
                    ) {
                        Text(
                            "Snooze $snoozeMinutes min",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    HoldToDismiss(accent = accent, onDismiss = onDismiss)
                }
            }
        }
    }
}

/** The icon sits inside a ring that breathes, so a silent (muted-stream) alarm still reads. */
@Composable
private fun PulsingBell(accent: Color) {
    val pulse = rememberInfiniteTransition(label = "alarm-pulse")
    val scale by pulse.animateFloat(
        initialValue = 1f, targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            tween(1_100, easing = FastOutSlowInEasing), RepeatMode.Reverse
        ),
        label = "alarm-pulse-scale"
    )
    val glow by pulse.animateFloat(
        initialValue = 0.35f, targetValue = 0.08f,
        animationSpec = infiniteRepeatable(
            tween(1_100, easing = FastOutSlowInEasing), RepeatMode.Reverse
        ),
        label = "alarm-pulse-glow"
    )

    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(180.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(accent.copy(alpha = glow))
        )
        Box(
            Modifier
                .size(112.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.20f)),
            contentAlignment = Alignment.Center
        ) {
            Text("⏰", style = MaterialTheme.typography.displaySmall)
        }
    }
}

/**
 * Dismiss is a press-and-hold with a filling ring, deliberately harder than Snooze: a single
 * tap in a half-asleep swipe should never end the alarm for good, and holding for a second is
 * about the smallest gesture that proves the user is actually awake.
 */
@Composable
private fun HoldToDismiss(accent: Color, onDismiss: () -> Unit) {
    val progress = remember { Animatable(0f) }
    var holding by remember { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    LaunchedEffect(holding) {
        if (holding) {
            progress.animateTo(1f, tween(HOLD_MILLIS))
            onDismiss()
        } else {
            progress.animateTo(0f, tween(180))
        }
    }

    Box(
        Modifier
            .size(120.dp)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    holding = true
                    tryAwaitRelease()
                    holding = false
                })
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 6.dp.toPx()
            val inset = stroke / 2
            drawArc(
                color = muted.copy(alpha = 0.30f),
                startAngle = -90f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            if (progress.value > 0f) {
                drawArc(
                    color = accent,
                    startAngle = -90f, sweepAngle = 360f * progress.value, useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }
        Text(
            if (holding) "Hold…" else "Hold to\ndismiss",
            style = MaterialTheme.typography.labelLarge,
            color = muted,
            textAlign = TextAlign.Center
        )
    }
}

private const val HOLD_MILLIS = 1_100
