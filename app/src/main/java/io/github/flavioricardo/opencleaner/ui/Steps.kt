package io.github.flavioricardo.opencleaner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.flavioricardo.opencleaner.R
import io.github.flavioricardo.opencleaner.scan.Action
import io.github.flavioricardo.opencleaner.scan.Phase
import io.github.flavioricardo.opencleaner.system.appSettingsIntent

/** Analysis or cleaning in progress: one line per step. */
@Composable
internal fun StepsPage(steps: List<Step>, cleaning: Boolean) {
    val finished = steps.count { it.status == Status.DONE || it.status == Status.WARN }
    val current = steps.firstOrNull { it.status == Status.RUNNING }
    val partial = current?.takeIf { it.total > 0 }?.let { it.done.toFloat() / it.total } ?: 0f
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PageTitle(
                stringResource(if (cleaning) R.string.cleaning_title else R.string.analyzing_title),
                stringResource(if (cleaning) R.string.cleaning_subtitle else R.string.analyzing_subtitle),
            )
        }
        item {
            LinearProgressIndicator(
                progress = { (finished + partial) / steps.size.coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth().height(6.dp),
            )
        }
        item {
            Panel {
                steps.forEachIndexed { i, step ->
                    if (i > 0) HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    StepRow(step)
                }
            }
        }
    }
}

@Composable
private fun StepRow(step: Step) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (step.status) {
            Status.PENDING -> Icon(Lucide.Circle, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.outlineVariant)
            Status.RUNNING -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.5.dp)
            Status.DONE -> Icon(Lucide.CircleCheck, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Status.WARN -> Icon(Lucide.TriangleAlert, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.tertiary)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stepLabel(step),
                style = MaterialTheme.typography.bodyLarge,
                color = if (step.status == Status.PENDING) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (step.status == Status.RUNNING) FontWeight.SemiBold else FontWeight.Normal,
            )
            stepDetail(step)?.let { Muted(it) }
        }
        if (step.bytes >= 0 && step.status != Status.PENDING && step.status != Status.RUNNING) {
            Spacer(Modifier.width(8.dp))
            Text(size(step.bytes), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun stepLabel(step: Step): String = when (val key = step.key) {
    is Phase -> stringResource(
        when (key) {
            Phase.APPS -> R.string.phase_apps
            Phase.FILES -> R.string.phase_files
            Phase.WHATSAPP -> R.string.phase_whatsapp
            Phase.DUPLICATES -> R.string.phase_duplicates
        },
    )
    is Item -> title(key.f)
    else -> ""
}

@Composable
private fun stepDetail(step: Step): String? {
    val key = step.key
    if (key is Phase) return when (step.status) {
        Status.PENDING -> null
        Status.WARN -> stringResource(R.string.step_no_permission)
        Status.RUNNING -> when (key) {
            Phase.APPS -> if (step.total > 0) stringResource(R.string.step_apps_running, step.done, step.total) else null
            Phase.FILES -> pluralStringResource(R.plurals.files_read, step.done, step.done)
            Phase.WHATSAPP -> stringResource(R.string.step_whatsapp_running)
            Phase.DUPLICATES -> if (step.total > 0) stringResource(R.string.step_comparing, step.done, step.total)
            else stringResource(R.string.step_grouping)
        }
        Status.DONE -> when (key) {
            Phase.APPS -> pluralStringResource(R.plurals.apps_read, step.total, step.total)
            Phase.FILES -> pluralStringResource(R.plurals.files, step.done, step.done)
            Phase.WHATSAPP -> null
            Phase.DUPLICATES -> pluralStringResource(R.plurals.files_compared, step.total, step.total)
        }
    }
    val item = key as Item
    return when (step.status) {
        Status.PENDING -> if (item.isSystemDialog) stringResource(R.string.step_android_asks) else countText(item).ifEmpty { null }
        Status.RUNNING -> if (item.isSystemDialog) stringResource(R.string.step_waiting_confirm)
        else stringResource(R.string.step_deleting, step.done, step.total)
        Status.DONE -> if (item.isSystemDialog) null else pluralStringResource(R.plurals.files, step.total, step.total)
        Status.WARN -> stringResource(
            if (item.f.action is Action.Uninstall) R.string.step_not_uninstalled else R.string.step_cache_not_freed,
        )
    }
}

@Composable
internal fun ResultPage(
    report: Report,
    manual: List<Item>,
    onAgain: () -> Unit,
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PageTitle(
                stringResource(R.string.result_title),
                stringResource(R.string.result_subtitle, report.millis / 1000.0),
            )
        }
        item {
            Panel {
                Overline(stringResource(R.string.result_freed))
                Text(
                    size(maxOf(0L, report.usedBefore - report.usedAfter)),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Muted(stringResource(R.string.result_before_after, size(report.usedBefore), size(report.usedAfter)))
                Spacer(Modifier.height(8.dp))
                report.steps.forEach {
                    HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    StepRow(it)
                }
            }
        }
        if (manual.isNotEmpty()) item {
            Panel {
                SectionHeader(Lucide.Info, stringResource(R.string.only_you), stringResource(R.string.only_you_sub))
                manual.forEach {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    ManualRow(it) { pkg -> ctx.open(appSettingsIntent(pkg)) }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAgain, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.analyze_again)) }
                OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.back_overview)) }
            }
        }
    }
}
