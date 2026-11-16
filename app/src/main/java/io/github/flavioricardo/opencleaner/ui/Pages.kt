package io.github.flavioricardo.opencleaner.ui

import android.graphics.Bitmap
import android.os.Environment
import android.text.format.Formatter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.flavioricardo.opencleaner.R
import io.github.flavioricardo.opencleaner.scan.Action
import io.github.flavioricardo.opencleaner.scan.Finding
import io.github.flavioricardo.opencleaner.scan.DirNode
import io.github.flavioricardo.opencleaner.scan.Kind
import io.github.flavioricardo.opencleaner.scan.Sensitive
import io.github.flavioricardo.opencleaner.scan.whatsAppMediaKind
import java.io.File
import io.github.flavioricardo.opencleaner.system.allFilesIntent
import io.github.flavioricardo.opencleaner.system.appSettingsIntent
import io.github.flavioricardo.opencleaner.system.usageIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val DAY = 24L * 60 * 60 * 1000

// ---------- Shared pieces ----------

@Composable
internal fun size(bytes: Long): String = Formatter.formatShortFileSize(LocalContext.current, bytes)

@Composable
internal fun PageTitle(title: String, subtitle: String) {
    Column(Modifier.padding(bottom = 4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun Panel(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val shape = RoundedCornerShape(12.dp)
    val border = CardDefaults.outlinedCardBorder()
    if (onClick != null) {
        Card(onClick, modifier.fillMaxWidth(), shape = shape, colors = colors, border = border) {
            Column(Modifier.padding(16.dp), content = content)
        }
    } else {
        Card(modifier.fillMaxWidth(), shape = shape, colors = colors, border = border) {
            Column(Modifier.padding(16.dp), content = content)
        }
    }
}

@Composable
internal fun Muted(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, modifier, style = MaterialTheme.typography.bodySmall, color = color)
}

@Composable
internal fun Overline(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun Logo() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Lucide.BrushCleaning, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimary)
        }
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Page(content: LazyListScope.() -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun EmptyState(icon: ImageVector, text: String, onAnalyze: () -> Unit) {
    Panel {
        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAnalyze) { Text(stringResource(R.string.analyze)) }
        }
    }
}

/** Card for a permission the analysis lacked. */
@Composable
private fun NeedsPanel(kind: Kind) {
    val ctx = LocalContext.current
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Lucide.TriangleAlert, null, tint = MaterialTheme.colorScheme.tertiary)
            Text(
                stringResource(if (kind == Kind.NEEDS_FILES_ACCESS) R.string.k_needs_files else R.string.k_needs_usage),
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = {
                ctx.open(if (kind == Kind.NEEDS_FILES_ACCESS) allFilesIntent(ctx) else usageIntent())
            }) { Text(stringResource(R.string.grant)) }
        }
    }
}

@Composable
internal fun title(f: Finding, short: Boolean = false): String = when (f.kind) {
    Kind.WA_DUPLICATES -> stringResource(if (short) R.string.s_wa_duplicates else R.string.k_wa_duplicates)
    Kind.WA_BACKUPS -> stringResource(if (short) R.string.s_wa_backups else R.string.k_wa_backups)
    Kind.WA_STATUSES -> stringResource(if (short) R.string.s_wa_statuses else R.string.k_wa_statuses)
    Kind.WA_OLD_VIDEOS -> stringResource(if (short) R.string.s_wa_old_videos else R.string.k_wa_old_videos, months())
    Kind.WA_OLD_IMAGES -> stringResource(if (short) R.string.s_wa_old_images else R.string.k_wa_old_images, months())
    Kind.WA_OLD_AUDIO -> stringResource(if (short) R.string.s_wa_old_audio else R.string.k_wa_old_audio, months())
    Kind.WA_OLD_DOCS -> stringResource(if (short) R.string.s_wa_old_docs else R.string.k_wa_old_docs, months())
    Kind.OLD_DOWNLOADS -> stringResource(R.string.k_old_downloads, months())
    Kind.OLD_SCREENSHOTS -> stringResource(R.string.k_old_screenshots, months())
    Kind.OLD_RECORDINGS -> stringResource(R.string.k_old_recordings, months())
    Kind.TRASH -> stringResource(R.string.k_trash)
    Kind.CACHE -> stringResource(R.string.k_cache)
    Kind.APKS -> stringResource(R.string.k_apks)
    Kind.THUMBNAILS -> stringResource(R.string.k_thumbnails)
    Kind.EMPTY_DIRS -> stringResource(R.string.k_empty_dirs)
    Kind.DUPLICATES -> stringResource(R.string.k_duplicates)
    Kind.LARGE_VIDEOS -> stringResource(R.string.k_large_videos, size(LocalScanOptions.current.minVideoBytes))
    Kind.UNUSED_APP -> if (short) f.label.orEmpty() else stringResource(R.string.k_unused_app, f.label.orEmpty())
    Kind.MANUAL_APP, Kind.APP_CACHE, Kind.PRIVACY_APP -> f.label.orEmpty()
    Kind.NEEDS_FILES_ACCESS -> stringResource(R.string.k_needs_files)
    Kind.NEEDS_USAGE_ACCESS -> stringResource(R.string.k_needs_usage)
}

@Composable
private fun months() = LocalScanOptions.current.oldMonths

@Composable
internal fun hint(kind: Kind): String? = when (kind) {
    Kind.WA_OLD_VIDEOS, Kind.WA_OLD_IMAGES, Kind.WA_OLD_AUDIO, Kind.WA_OLD_DOCS -> stringResource(R.string.hint_wa_old)
    Kind.OLD_DOWNLOADS, Kind.OLD_SCREENSHOTS, Kind.OLD_RECORDINGS -> stringResource(R.string.hint_videos)
    Kind.TRASH -> stringResource(R.string.hint_trash)
    Kind.WA_DUPLICATES -> stringResource(R.string.wa_duplicates_warning)
    Kind.WA_BACKUPS -> stringResource(R.string.hint_backups)
    Kind.WA_STATUSES -> stringResource(R.string.hint_statuses)
    Kind.CACHE -> stringResource(R.string.hint_cache)
    Kind.APKS -> stringResource(R.string.hint_apks)
    Kind.THUMBNAILS -> stringResource(R.string.hint_thumbnails)
    Kind.DUPLICATES -> stringResource(R.string.hint_duplicates)
    Kind.LARGE_VIDEOS -> stringResource(R.string.hint_videos)
    Kind.UNUSED_APP -> stringResource(R.string.hint_uninstall)
    else -> null
}

internal fun kindIcon(kind: Kind): ImageVector = when (kind) {
    Kind.WA_DUPLICATES, Kind.DUPLICATES -> Lucide.Copy
    Kind.WA_BACKUPS -> Lucide.Archive
    Kind.WA_STATUSES -> Lucide.Eye
    Kind.WA_OLD_VIDEOS, Kind.OLD_RECORDINGS -> Lucide.Film
    Kind.WA_OLD_IMAGES, Kind.OLD_SCREENSHOTS -> Lucide.Image
    Kind.WA_OLD_AUDIO -> Lucide.Mic
    Kind.WA_OLD_DOCS -> Lucide.FileText
    Kind.OLD_DOWNLOADS -> Lucide.Download
    Kind.TRASH -> Lucide.Trash2
    Kind.PRIVACY_APP -> Lucide.Shield
    Kind.CACHE -> Lucide.Database
    Kind.APKS -> Lucide.Download
    Kind.THUMBNAILS -> Lucide.Image
    Kind.EMPTY_DIRS -> Lucide.Folder
    Kind.LARGE_VIDEOS -> Lucide.Film
    Kind.UNUSED_APP -> Lucide.Package
    Kind.MANUAL_APP -> Lucide.ExternalLink
    Kind.APP_CACHE -> Lucide.Database
    Kind.NEEDS_FILES_ACCESS, Kind.NEEDS_USAGE_ACCESS -> Lucide.TriangleAlert
}

/** "312 files" or "24 folders"; empty for items without files. */
@Composable
internal fun countText(item: Item): String {
    val n = item.files.size - item.excluded.size
    return when {
        item.files.isEmpty() -> ""
        item.f.kind == Kind.EMPTY_DIRS -> pluralStringResource(R.plurals.folders, n, n)
        else -> pluralStringResource(R.plurals.files, n, n)
    }
}

@Composable
internal fun AppIcon(pkg: String) {
    val ctx = LocalContext.current
    val bitmap = remember(pkg) {
        runCatching {
            val d = ctx.packageManager.getApplicationIcon(pkg)
            val b = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(b)
            d.setBounds(0, 0, 96, 96)
            d.draw(c)
            b.asImageBitmap()
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(bitmap, null, Modifier.size(36.dp))
    } else {
        Icon(Lucide.Package, null, Modifier.size(36.dp).padding(6.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "Analysis finished: X in N items (T s)". */
@Composable
private fun AnalysisLine(a: Analysis) {
    Text(
        stringResource(R.string.analysis_done, size(a.possibleBytes), a.fileCount, a.millis / 1000.0),
        style = MaterialTheme.typography.titleSmall,
    )
}

// ---------- Health ----------

@Composable
internal fun HealthPage(
    usage: Pair<Long, Long>,
    analysis: Analysis?,
    error: String?,
    filesOk: Boolean,
    usageOk: Boolean,
    onAnalyze: () -> Unit,
    onClean: () -> Unit,
    onTab: (Tab) -> Unit,
    onStorage: () -> Unit,
) {
    Page {
        item { Logo() }
        item { PageTitle(stringResource(R.string.health_title), stringResource(R.string.health_subtitle)) }
        item { StoragePanel(usage, analysis, onAnalyze, onClean, onStorage) }
        if (error != null) item {
            Panel { Text(stringResource(R.string.error, error), color = MaterialTheme.colorScheme.error) }
        }
        if (analysis == null) {
            item {
                Panel {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Lucide.ShieldCheck, null, tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.welcome_privacy), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        } else {
            item { AnalysisLine(analysis) }
            item { AreaGrid(analysis, filesOk, usageOk, onTab) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StoragePanel(
    usage: Pair<Long, Long>,
    analysis: Analysis?,
    onAnalyze: () -> Unit,
    onClean: () -> Unit,
    onStorage: () -> Unit,
) {
    val (used, total) = usage
    val selected = minOf(analysis?.selectedBytes ?: 0L, used)
    val usedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
    val brand = MaterialTheme.colorScheme.primary
    val stripe = MaterialTheme.colorScheme.primaryContainer
    val track = MaterialTheme.colorScheme.surfaceVariant
    Panel {
        Overline(stringResource(R.string.storage_internal))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(stringResource(R.string.free_amount, size(total - used)), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(8.dp))
            Muted(stringResource(R.string.of_total, size(total)), Modifier.padding(bottom = 6.dp))
        }
        Spacer(Modifier.height(12.dp))
        Canvas(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp))) {
            drawRect(track)
            if (total <= 0) return@Canvas
            val usedEnd = size.width * used / total
            val selStart = size.width * (used - selected) / total
            drawRect(usedColor, size = Size(selStart, size.height))
            if (selected > 0) {
                clipRect(left = selStart, right = usedEnd) {
                    drawRect(brand)
                    var x = selStart - size.height
                    while (x < usedEnd) {
                        drawLine(stripe, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = 3.dp.toPx())
                        x += 7.dp.toPx()
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Legend(usedColor, stringResource(R.string.legend_used), size(used - selected))
            if (analysis != null) Legend(brand, stringResource(R.string.legend_selected), size(selected))
            Legend(track, stringResource(R.string.legend_free), size(total - used))
        }
        Spacer(Modifier.height(12.dp))
        if (analysis == null) {
            Muted(stringResource(R.string.analyze_hint))
            Spacer(Modifier.height(12.dp))
            Button(onClick = onAnalyze, modifier = Modifier.fillMaxWidth()) {
                Icon(Lucide.HeartPulse, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.analyze))
            }
        } else {
            val count = analysis.checkable.count { it.selected }
            Text(
                pluralStringResource(R.plurals.selected_summary, count, size(analysis.selectedBytes), count, size(analysis.possibleBytes)),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onClean, enabled = count > 0, modifier = Modifier.weight(1f)) {
                    Icon(Lucide.BrushCleaning, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.clean, size(analysis.selectedBytes)))
                }
                OutlinedButton(onClick = onAnalyze) {
                    Icon(Lucide.RefreshCw, stringResource(R.string.analyze_again), Modifier.size(18.dp))
                }
            }
            if (analysis.tree != null) {
                TextButton(onClick = onStorage) {
                    Icon(Lucide.HardDrive, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.storage_more))
                }
            }
        }
    }
}

@Composable
internal fun Legend(color: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Muted(label)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AreaGrid(a: Analysis, filesOk: Boolean, usageOk: Boolean, onTab: (Tab) -> Unit) {
    val needsFiles = stringResource(R.string.needs_files_short)
    val needsUsage = stringResource(R.string.needs_usage_short)
    val unused = a.items.filter { it.f.kind == Kind.UNUSED_APP }
    val perAppCache = a.of(listOf(Kind.APP_CACHE))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AreaCard(
                Lucide.MessageCircle, stringResource(R.string.area_whatsapp),
                if (filesOk) size(a.of(WHATSAPP_KINDS).sumOf { it.bytes }) else "—",
                if (filesOk) stringResource(R.string.area_whatsapp_sub) else needsFiles,
                Modifier.weight(1f),
            ) { onTab(Tab.CLEAN) }
            AreaCard(
                Lucide.Database, stringResource(R.string.area_cache),
                if (usageOk) size(a.of(SYSTEM_KINDS + Kind.APP_CACHE).sumOf { it.bytes }) else "—",
                when {
                    !usageOk -> needsUsage
                    !filesOk -> needsFiles
                    else -> stringResource(
                        if (perAppCache.isEmpty()) R.string.area_cache_sub_only else R.string.area_cache_sub,
                        size(a.of(SYSTEM_KINDS).sumOf { it.bytes }),
                    )
                },
                Modifier.weight(1f),
            ) { onTab(if (perAppCache.isNotEmpty()) Tab.APPS else Tab.CLEAN) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AreaCard(
                Lucide.Package, stringResource(R.string.area_unused),
                if (usageOk) unused.size.toString() else "—",
                if (usageOk) stringResource(R.string.area_unused_sub, size(unused.sumOf { it.bytes }), a.options.unusedDays) else needsUsage,
                Modifier.weight(1f),
                attention = unused.isNotEmpty(),
            ) { onTab(Tab.APPS) }
            AreaCard(
                Lucide.FolderOpen, stringResource(R.string.area_files),
                if (filesOk) size(a.of(FILE_KINDS + OLD_KINDS).sumOf { it.bytes }) else "—",
                if (filesOk) stringResource(R.string.area_files_sub) else needsFiles,
                Modifier.weight(1f),
            ) { onTab(Tab.CLEAN) }
        }
    }
}

@Composable
private fun AreaCard(
    icon: ImageVector,
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier,
    attention: Boolean = false,
    onClick: () -> Unit,
) {
    Panel(modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            color = if (attention) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
        )
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, minLines = 2)
    }
}

// ---------- Clean ----------

@Composable
internal fun CleanPage(analysis: Analysis?, onAnalyze: () -> Unit, onOpen: (Item) -> Unit) {
    Page {
        item { PageTitle(stringResource(R.string.clean_title), stringResource(R.string.clean_subtitle)) }
        if (analysis == null) {
            item { EmptyState(Lucide.BrushCleaning, stringResource(R.string.empty_clean), onAnalyze) }
            return@Page
        }
        item { AnalysisLine(analysis) }
        analysis.items.filter { it.f.kind == Kind.NEEDS_FILES_ACCESS || it.f.kind == Kind.NEEDS_USAGE_ACCESS }
            .forEach { item { NeedsPanel(it.f.kind) } }
        val groups = listOf(
            Triple(Lucide.MessageCircle, R.string.group_whatsapp, WHATSAPP_KINDS),
            Triple(Lucide.Smartphone, R.string.group_system, SYSTEM_KINDS),
            Triple(Lucide.FolderOpen, R.string.group_files, FILE_KINDS),
            Triple(Lucide.Clock, R.string.group_old, OLD_KINDS),
        )
        for ((icon, label, kinds) in groups) {
            val list = analysis.of(kinds)
            if (list.isEmpty()) continue
            item {
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(label), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        Muted(size(list.sumOf { it.bytes }))
                    }
                    if (kinds == WHATSAPP_KINDS) analysis.tree?.let { WhatsAppStrip(it) }
                    list.forEach {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        ItemRow(it, onOpen)
                    }
                }
            }
        }
        if (analysis.of(WHATSAPP_KINDS + SYSTEM_KINDS + FILE_KINDS + OLD_KINDS).isEmpty()) {
            item { Panel { Text(stringResource(R.string.nothing)) } }
        }
    }
}

@Composable
private fun ItemRow(item: Item, onOpen: (Item) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = item.files.isNotEmpty()) { onOpen(item) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = item.checked, onCheckedChange = { item.checked = it })
        Icon(kindIcon(item.f.kind), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title(item.f, short = true), style = MaterialTheme.typography.bodyLarge)
            val sub = listOfNotNull(countText(item).ifEmpty { null }, hint(item.f.kind)).joinToString(" · ")
            if (sub.isNotEmpty()) Muted(sub)
        }
        Spacer(Modifier.width(8.dp))
        if (item.bytes > 0) Text(size(item.bytes), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        if (item.files.isNotEmpty()) {
            Icon(Lucide.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Sticky bar over the tabs: what is selected and the button that cleans it. */
@Composable
internal fun SelectionBar(a: Analysis, onClean: () -> Unit) {
    val count = a.checkable.count { it.selected }
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    val apps = a.checkable.filter { it.f.kind == Kind.UNUSED_APP }
                    val rest = a.checkable - apps.toSet()
                    Muted(
                        if (apps.isEmpty()) stringResource(R.string.selection_items_clean, rest.count { it.selected }, rest.size)
                        else stringResource(
                            R.string.selection_items,
                            rest.count { it.selected }, rest.size, apps.count { it.selected }, apps.size,
                        ),
                    )
                    Text(
                        stringResource(R.string.selection_bytes, size(a.selectedBytes), size(a.possibleBytes)),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Button(onClick = onClean, enabled = count > 0) {
                    Text(stringResource(R.string.clean, size(a.selectedBytes)))
                }
            }
        }
    }
}

// ---------- Apps ----------

@Composable
internal fun AppsPage(analysis: Analysis?, onAnalyze: () -> Unit) {
    val ctx = LocalContext.current
    Page {
        item { PageTitle(stringResource(R.string.apps_title), stringResource(R.string.apps_subtitle)) }
        if (analysis == null) {
            item { EmptyState(Lucide.LayoutGrid, stringResource(R.string.empty_apps), onAnalyze) }
            return@Page
        }
        if (analysis.items.any { it.f.kind == Kind.NEEDS_USAGE_ACCESS }) {
            item { NeedsPanel(Kind.NEEDS_USAGE_ACCESS) }
            return@Page
        }
        val unused = analysis.items.filter { it.f.kind == Kind.UNUSED_APP }
        val manual = analysis.items.filter { it.f.kind == Kind.MANUAL_APP }
        val now = System.currentTimeMillis()
        item {
            Panel {
                SectionHeader(Lucide.Package, stringResource(R.string.unused_title, analysis.options.unusedDays), stringResource(R.string.hint_uninstall))
                if (unused.isEmpty()) Muted(stringResource(R.string.unused_none, analysis.options.unusedDays), Modifier.padding(top = 8.dp))
                unused.forEach { item ->
                    val pkg = (item.f.action as Action.Uninstall).pkg
                    val last = analysis.apps[pkg]?.lastUsed ?: 0L
                    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = item.checked, onCheckedChange = { item.checked = it })
                        AppIcon(pkg)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.f.label.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                            val days = ((now - last) / DAY).toInt()
                            Muted(
                                if (last == 0L) stringResource(R.string.never_opened)
                                else pluralStringResource(R.plurals.last_used_days, days, days),
                            )
                        }
                        Text(size(item.bytes), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        item { PrivacyPanel(analysis) }
        val perAppCache = analysis.items.filter { it.f.kind == Kind.APP_CACHE }
        if (perAppCache.isNotEmpty()) item {
            Panel {
                SectionHeader(Lucide.Database, stringResource(R.string.app_cache_title), stringResource(R.string.app_cache_sub))
                perAppCache.forEach {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    ManualRow(it, stringResource(R.string.app_cache_amount, size(it.f.bytes))) { pkg -> ctx.open(appSettingsIntent(pkg)) }
                }
            }
        }
        item {
            Panel {
                SectionHeader(Lucide.Info, stringResource(R.string.only_you), stringResource(R.string.only_you_sub))
                if (manual.isEmpty()) Muted(stringResource(R.string.only_you_none), Modifier.padding(top = 8.dp))
                manual.forEach {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    ManualRow(it, stringResource(R.string.manual_data, size(it.f.bytes))) { pkg -> ctx.open(appSettingsIntent(pkg)) }
                }
            }
        }
    }
}

@Composable
internal fun SectionHeader(icon: ImageVector, title: String, subtitle: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, Modifier.size(18.dp).padding(top = 2.dp), tint = MaterialTheme.colorScheme.primary)
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Muted(subtitle)
        }
    }
}

@Composable
internal fun ManualRow(item: Item, detail: String, onOpen: (String) -> Unit) {
    val pkg = (item.f.action as Action.OpenAppSettings).pkg
    Row(verticalAlignment = Alignment.CenterVertically) {
        AppIcon(pkg)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(item.f.label.orEmpty(), style = MaterialTheme.typography.bodyLarge)
            Muted(detail)
        }
        TextButton(onClick = { onOpen(pkg) }) {
            Text(stringResource(R.string.open))
            Spacer(Modifier.width(4.dp))
            Icon(Lucide.ExternalLink, null, Modifier.size(16.dp))
        }
    }
}

// ---------- Detail ----------

@Composable
internal fun DetailPage(item: Item, onBack: () -> Unit) {
    val root = remember { Environment.getExternalStorageDirectory().path + "/" }
    val left = item.files.size - item.excluded.size
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 4.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Lucide.ArrowLeft, stringResource(R.string.back)) }
            Text(title(item.f), style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                Panel(Modifier.padding(bottom = 6.dp)) {
                    hint(item.f.kind)?.let {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            val warn = item.f.kind == Kind.WA_DUPLICATES || item.f.kind == Kind.LARGE_VIDEOS
                            Icon(
                                if (warn) Lucide.TriangleAlert else Lucide.Info, null, Modifier.size(18.dp),
                                tint = if (warn) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                            )
                            Text(it, style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    Text(
                        stringResource(R.string.detail_summary, left, item.files.size, size(item.bytes)),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Muted(stringResource(R.string.detail_hint))
                }
            }
            items(item.files, key = { it.path }) { file ->
                // Each stat goes through FUSE: keep it off the main thread.
                var bytes by remember(file) { mutableLongStateOf(0L) }
                LaunchedEffect(file) { bytes = withContext(Dispatchers.IO) { file.length() } }
                val keep = file !in item.excluded
                fun toggle(on: Boolean) {
                    // A tap can come before the size loads: then stat it now, once.
                    if (on) item.excluded.remove(file) else item.excluded[file] = if (bytes > 0) bytes else file.length()
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface)
                        .clickable { toggle(!keep) }.padding(end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = keep, onCheckedChange = ::toggle)
                    Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                        Text(file.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Muted(file.parent.orEmpty().plus("/").removePrefix(root), maxLines = 1)
                    }
                    if (bytes > 0) Text(size(bytes), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun Muted(text: String, maxLines: Int) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

// ---------- Welcome ----------

@Composable
internal fun Welcome(
    filesOk: Boolean,
    usageOk: Boolean,
    onGrantFiles: () -> Unit,
    onGrantUsage: () -> Unit,
    onContinue: () -> Unit,
) {
    Page {
        item { Logo() }
        item { PageTitle(stringResource(R.string.welcome_title), stringResource(R.string.welcome_subtitle)) }
        item {
            PermissionPanel(Lucide.FolderOpen, stringResource(R.string.perm_files), stringResource(R.string.welcome_files), filesOk, onGrantFiles)
        }
        item {
            PermissionPanel(Lucide.LayoutGrid, stringResource(R.string.perm_usage), stringResource(R.string.welcome_usage), usageOk, onGrantUsage)
        }
        item {
            Row(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Lucide.ShieldCheck, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Muted(stringResource(R.string.welcome_privacy))
            }
        }
        item {
            TextButton(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.continue_anyway))
            }
        }
    }
}

@Composable
private fun PermissionPanel(icon: ImageVector, title: String, text: String, granted: Boolean, onGrant: () -> Unit) {
    Panel {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(2.dp))
                Muted(text)
                Spacer(Modifier.height(10.dp))
                if (granted) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Lucide.CircleCheck, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.granted), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    Button(onClick = onGrant) { Text(stringResource(R.string.grant)) }
                }
            }
        }
    }
}

/** How WhatsApp media splits by type, from the folder sizes: what the 33 GB really are. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhatsAppStrip(tree: DirNode) {
    val parts = remember(tree) {
        val sums = LinkedHashMap<Kind?, Long>()
        WA_MEDIA_TYPES.forEach { sums[it] = 0L }
        for (root in WA_MEDIA_DIRS) {
            val media = tree.find(File(tree.file, root)) ?: continue
            for (child in media.children) {
                val kind = whatsAppMediaKind(child.file.name)
                sums[kind] = (sums[kind] ?: 0L) + child.bytes
            }
        }
        sums.filterValues { it > 0 }.toList()
    }
    if (parts.isEmpty()) return
    val colors = stripColors()
    Spacer(Modifier.height(8.dp))
    Muted(stringResource(R.string.wa_media_total, size(parts.sumOf { it.second })))
    Spacer(Modifier.height(6.dp))
    SizeStrip(parts.map { it.second })
    Spacer(Modifier.height(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        parts.forEachIndexed { i, (kind, bytes) ->
            Legend(colors.getOrElse(i) { MaterialTheme.colorScheme.outlineVariant }, stringResource(mediaLabel(kind)), size(bytes))
        }
    }
}

private val WA_MEDIA_DIRS = listOf(
    "Android/media/com.whatsapp/WhatsApp/Media",
    "Android/media/com.whatsapp.w4b/WhatsApp Business/Media",
)

// Strip order; null is everything else (stickers, GIFs, statuses...).
private val WA_MEDIA_TYPES = listOf(Kind.WA_OLD_VIDEOS, Kind.WA_OLD_IMAGES, Kind.WA_OLD_AUDIO, Kind.WA_OLD_DOCS, null)

private fun mediaLabel(kind: Kind?): Int = when (kind) {
    Kind.WA_OLD_VIDEOS -> R.string.wa_videos
    Kind.WA_OLD_IMAGES -> R.string.wa_images
    Kind.WA_OLD_AUDIO -> R.string.wa_audio
    Kind.WA_OLD_DOCS -> R.string.wa_docs
    else -> R.string.wa_other
}

/** Apps you installed that can use location all the time, the camera or the microphone. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrivacyPanel(a: Analysis) {
    val ctx = LocalContext.current
    val apps = a.items.filter { it.f.kind == Kind.PRIVACY_APP }
    Panel {
        SectionHeader(Lucide.Shield, stringResource(R.string.privacy_title), stringResource(R.string.privacy_sub))
        if (apps.isEmpty()) Muted(stringResource(R.string.privacy_none), Modifier.padding(top = 8.dp))
        apps.forEach { item ->
            val pkg = (item.f.action as Action.OpenAppSettings).pkg
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(pkg)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.f.label.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        a.apps[pkg]?.sensitive.orEmpty().sortedBy { it.ordinal }.forEach { Chip(stringResource(sensitiveLabel(it))) }
                    }
                }
                TextButton(onClick = { ctx.open(appSettingsIntent(pkg)) }) { Text(stringResource(R.string.open)) }
            }
        }
    }
}

private fun sensitiveLabel(s: Sensitive): Int = when (s) {
    Sensitive.BACKGROUND_LOCATION -> R.string.sens_location
    Sensitive.CAMERA -> R.string.sens_camera
    Sensitive.MICROPHONE -> R.string.sens_mic
}

@Composable
private fun Chip(text: String) {
    Text(
        text,
        Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.tertiary,
    )
}
