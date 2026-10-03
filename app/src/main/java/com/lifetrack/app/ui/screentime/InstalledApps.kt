package com.lifetrack.app.ui.screentime

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One launchable app on the device, as offered in the "add app" picker. */
data class InstalledApp(val packageName: String, val label: String)

/**
 * Everything we ask [PackageManager] for. Every lookup degrades to the caller's fallback,
 * because a tracked package can be uninstalled at any time and its stored label is then the
 * only name we have left.
 */
internal object InstalledApps {

    private const val ICON_PX = 96

    /** Apps with a launcher entry, deduplicated, alphabetical. Our own package is left out. */
    fun launchable(context: Context): List<InstalledApp> = try {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        else
            pm.queryIntentActivities(intent, 0)
        resolved.asSequence()
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == context.packageName) return@mapNotNull null
                InstalledApp(pkg, info.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: pkg)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .toList()
    } catch (t: Throwable) {
        emptyList()
    }

    fun label(context: Context, packageName: String, fallback: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
            .takeIf { it.isNotBlank() } ?: fallback
    } catch (t: Throwable) {
        fallback
    }

    fun isInstalled(context: Context, packageName: String): Boolean = try {
        context.packageManager.getApplicationInfo(packageName, 0)
        true
    } catch (t: Throwable) {
        false
    }

    fun icon(context: Context, packageName: String): ImageBitmap? = try {
        // Explicit size: some launcher icons report no intrinsic bounds.
        context.packageManager.getApplicationIcon(packageName)
            .toBitmap(ICON_PX, ICON_PX)
            .asImageBitmap()
    } catch (t: Throwable) {
        null
    }
}

/** The real app name where the app is installed, the stored label where it is not. */
@Composable
internal fun rememberAppLabel(packageName: String, fallback: String): String {
    val context = LocalContext.current
    return remember(packageName, fallback) {
        InstalledApps.label(context.applicationContext, packageName, fallback)
    }
}

/** Loads the launcher icon off the main thread; null until it is ready, or if there is none. */
@Composable
internal fun rememberAppIcon(packageName: String): State<ImageBitmap?> {
    val context = LocalContext.current
    return produceState<ImageBitmap?>(initialValue = null, packageName) {
        value = withContext(Dispatchers.IO) { InstalledApps.icon(context.applicationContext, packageName) }
    }
}
