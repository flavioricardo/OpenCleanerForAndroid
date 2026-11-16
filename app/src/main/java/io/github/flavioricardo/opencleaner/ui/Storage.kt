package io.github.flavioricardo.opencleaner.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.flavioricardo.opencleaner.R
import io.github.flavioricardo.opencleaner.scan.DirNode

// The strip shows this many parts; the rest becomes one gray part.
private const val STRIP_PARTS = 4

/** Shades of the brand color for the strip, biggest part darkest. */
@Composable
internal fun stripColors(): List<Color> {
    val c = MaterialTheme.colorScheme.primary
    return listOf(c, c.copy(alpha = 0.72f), c.copy(alpha = 0.48f), c.copy(alpha = 0.28f))
}

/** One bar split by size, the same language as the storage bar on Health. */
@Composable
internal fun SizeStrip(parts: List<Long>, modifier: Modifier = Modifier) {
    val colors = stripColors()
    val rest = MaterialTheme.colorScheme.outlineVariant
    Row(modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        parts.filter { it > 0 }.forEachIndexed { i, bytes ->
            Box(Modifier.weight(bytes.toFloat()).height(12.dp).background(colors.getOrElse(i) { rest }))
        }
    }
}

/** Keeps the biggest parts and folds the rest into one, so the strip stays readable. */
internal fun <T> topParts(items: List<T>, bytes: (T) -> Long): Pair<List<T>, Long> =
    items.take(STRIP_PARTS) to items.drop(STRIP_PARTS).sumOf(bytes)

/** The desktop "Disk analyzer": where the space goes, folder by folder. Only for looking. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StoragePage(a: Analysis, usage: Pair<Long, Long>, onBack: () -> Unit) {
    val root = a.tree ?: return
    var path by remember { mutableStateOf(listOf(root)) }
    var biggest by remember { mutableStateOf(false) }
    val node = path.last()
    val children = remember(node) { node.children }
    val loose = node.bytes - children.sumOf { it.bytes }
    val rootName = stringResource(R.string.storage_root)
    BackHandler(enabled = path.size > 1) { path = path.dropLast(1) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 4.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (path.size > 1) path = path.dropLast(1) else onBack() }) {
                Icon(Lucide.ArrowLeft, stringResource(R.string.back))
            }
            Text(stringResource(R.string.storage_title), style = MaterialTheme.typography.titleLarge)
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Muted(
                    path.mapIndexed { i, n -> if (i == 0) rootName else n.file.name }.joinToString(" › "),
                    Modifier.padding(horizontal = 4.dp),
                )
            }
            item {
                Panel {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(size(node.bytes), style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.width(8.dp))
                        Muted(pluralStringResource(R.plurals.files, node.files, node.files), Modifier.padding(bottom = 6.dp))
                    }
                    val (top, rest) = topParts(children) { it.bytes }
                    if (top.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        SizeStrip(top.map { it.bytes } + rest + loose.coerceAtLeast(0))
                    }
                    if (path.size == 1) {
                        Spacer(Modifier.height(10.dp))
                        val hidden = (usage.first - root.bytes).coerceAtLeast(0)
                        Text(stringResource(R.string.storage_visible, size(root.bytes), size(usage.first)), style = MaterialTheme.typography.bodyMedium)
                        Muted(stringResource(R.string.storage_hidden, size(hidden)))
                    }
                }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !biggest, onClick = { biggest = false }, label = { Text(stringResource(R.string.storage_folders)) })
                    FilterChip(selected = biggest, onClick = { biggest = true }, label = { Text(stringResource(R.string.storage_biggest)) })
                }
            }
            if (!biggest) {
                itemsIndexed(children, key = { _, it -> it.file.path }) { i, child ->
                    FolderRow(child, node.bytes, stripColors().getOrElse(i) { MaterialTheme.colorScheme.outlineVariant }) {
                        path = path + child
                    }
                }
                if (loose > 0) item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Muted(stringResource(R.string.storage_loose), Modifier.weight(1f))
                        Text(size(loose), style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                val prefix = root.file.path + "/"
                items(a.biggest, key = { it.first.path }) { (file, bytes) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(file.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Muted(file.parent.orEmpty().plus("/").removePrefix(prefix))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(size(bytes), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderRow(node: DirNode, parentBytes: Long, color: Color, onOpen: () -> Unit) {
    val share = if (parentBytes > 0) node.bytes.toFloat() / parentBytes else 0f
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onOpen).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Lucide.Folder, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(node.file.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(
                Modifier.padding(top = 4.dp).fillMaxWidth(share.coerceIn(0.01f, 1f)).height(4.dp)
                    .clip(RoundedCornerShape(2.dp)).background(color),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(size(node.bytes), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Icon(Lucide.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
