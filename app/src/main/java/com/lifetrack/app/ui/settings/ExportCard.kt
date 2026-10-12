package com.lifetrack.app.ui.settings

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lifetrack.app.LifeTrackApp
import com.lifetrack.app.data.Retention
import com.lifetrack.app.export.ExportFormat
import com.lifetrack.app.export.ExportKind
import com.lifetrack.app.export.ExportRange
import com.lifetrack.app.export.ExportTables
import com.lifetrack.app.export.Exporter
import com.lifetrack.app.ui.components.LifeCard
import com.lifetrack.app.ui.components.SectionLabel
import com.lifetrack.app.ui.components.SegmentedPicker
import com.lifetrack.app.ui.theme.Space
import kotlinx.coroutines.launch

/** The Settings card that opens [ExportSheet]. */
@Composable
fun ExportCard() {
    var open by remember { mutableStateOf(false) }
    LifeCard(onClick = { open = true }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionLabel("Download your data")
                Text(
                    "Meals, habits and steps as CSV, Excel or PDF. LifeTrack keeps the last " +
                        "${Retention.DAYS / 30} months; older days are removed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(Space.sm))
            Icon(Icons.Outlined.FileDownload, contentDescription = null)
        }
    }
    if (open) ExportSheet(onDismiss = { open = false })
}

/**
 * "Save as" whose file type follows the name: one launcher serves all three formats, which
 * the stock contract can't do because it fixes the MIME type when it is created.
 */
private class SaveAs : ActivityResultContracts.CreateDocument("application/octet-stream") {
    override fun createIntent(context: Context, input: String): Intent =
        super.createIntent(context, input).setType(ExportFormat.forFileName(input).mime)
}

/**
 * Pick what, how far back, and which format; Android's own "Save as" screen then decides
 * where - Downloads, Drive, anywhere - so LifeTrack needs no storage access to do it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf(ExportKind.Meals) }
    var range by remember { mutableStateOf(ExportRange.Week) }
    var format by remember { mutableStateOf(ExportFormat.Csv) }
    var working by remember { mutableStateOf(false) }

    val saveAs = rememberLauncherForActivityResult(SaveAs()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult   // backed out of the picker
        val repo = (context.applicationContext as LifeTrackApp).repository
        working = true
        scope.launch {
            val message = when (val r = Exporter.export(context, repo, kind, range, format, uri)) {
                is Exporter.Result.Saved -> "Saved ${kind.label.lowercase()} - ${r.rows} rows"
                Exporter.Result.Empty -> "Nothing to download: no ${kind.label.lowercase()} in ${range.label.lowercase()}"
                is Exporter.Result.Failed -> "Couldn't save the file: ${r.reason}"
            }
            working = false
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!working) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = Space.screen, end = Space.screen, bottom = Space.xxl),
            verticalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            Text("Download your data", style = MaterialTheme.typography.titleLarge)

            SectionLabel("What", Modifier.padding(top = Space.sm))
            SegmentedPicker(ExportKind.entries, kind, { kind = it }, { it.label }, Modifier.fillMaxWidth())

            SectionLabel("Period", Modifier.padding(top = Space.sm))
            SegmentedPicker(ExportRange.entries, range, { range = it }, { it.short }, Modifier.fillMaxWidth())

            SectionLabel("Format", Modifier.padding(top = Space.sm))
            SegmentedPicker(ExportFormat.entries, format, { format = it }, { it.label }, Modifier.fillMaxWidth())

            Text(
                when (format) {
                    ExportFormat.Csv -> "A plain table any spreadsheet or app can open."
                    ExportFormat.Excel -> "An .xlsx workbook, one sheet per table."
                    ExportFormat.Pdf -> "A report ready to print or share."
                } + " You choose where it is saved; LifeTrack sends nothing anywhere.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.xs)
            )

            Button(
                onClick = { saveAs.launch(ExportTables.fileName(kind, range, format)) },
                enabled = !working,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().padding(top = Space.sm)
            ) {
                if (working) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(Space.sm))
                    Text("Preparing…")
                } else {
                    Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Space.sm))
                    Text("Download ${format.label}")
                }
            }
        }
    }
}
