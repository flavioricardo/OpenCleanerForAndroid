package io.github.flavioricardo.opencleaner.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.flavioricardo.opencleaner.R
import io.github.flavioricardo.opencleaner.scan.Action
import io.github.flavioricardo.opencleaner.scan.Finding
import io.github.flavioricardo.opencleaner.scan.Kind
import io.github.flavioricardo.opencleaner.scan.analyze
import io.github.flavioricardo.opencleaner.scan.deleteFiles
import io.github.flavioricardo.opencleaner.system.allFilesIntent
import io.github.flavioricardo.opencleaner.system.appSettingsIntent
import io.github.flavioricardo.opencleaner.system.clearCacheIntent
import io.github.flavioricardo.opencleaner.system.hasAllFiles
import io.github.flavioricardo.opencleaner.system.hasUsage
import io.github.flavioricardo.opencleaner.system.loadApps
import io.github.flavioricardo.opencleaner.system.rescan
import io.github.flavioricardo.opencleaner.system.storageUsage
import io.github.flavioricardo.opencleaner.system.uninstallIntent
import io.github.flavioricardo.opencleaner.system.usageIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** A finding plus what the user changed on screen. */
private class Item(val f: Finding) {
    var checked by mutableStateOf(f.checked)
    /** Files the user unchecked in the detail screen, with their sizes. */
    val excluded = mutableStateMapOf<File, Long>()
    val files: List<File> get() = (f.action as? Action.DeleteFiles)?.files.orEmpty()
    val bytes: Long get() = f.bytes - excluded.values.sum()
    val checkable: Boolean get() = f.action != null && f.kind != Kind.MANUAL_APP
}

private sealed interface Screen {
    data object Home : Screen
    data class Detail(val item: Item) : Screen
    data class Result(val freed: Long) : Screen
}

@Composable
fun App(resumeTick: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val filesOk = remember(resumeTick) { hasAllFiles() }
    val usageOk = remember(resumeTick) { hasUsage(ctx) }
    val usage = remember(resumeTick) { storageUsage() }
    var skipWelcome by rememberSaveable { mutableStateOf(false) }
    var items by remember { mutableStateOf<List<Item>?>(null) }
    var busy by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    val queue = remember { ArrayDeque<Intent>() }
    var usedBefore by remember { mutableLongStateOf(0L) }

    // System dialogs (clear cache, uninstall) run one after another; a cancelled one just moves on.
    lateinit var next: () -> Unit
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { next() }
    next = {
        val intent = queue.removeFirstOrNull()
        if (intent == null) {
            busy = null
            screen = Screen.Result(maxOf(0L, usedBefore - storageUsage().first))
        } else {
            try {
                launcher.launch(intent)
            } catch (e: ActivityNotFoundException) {
                next()
            }
        }
    }

    fun runAnalysis() {
        scope.launch {
            busy = R.string.analyzing
            error = null
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    analyze(
                        storage = if (hasAllFiles()) Environment.getExternalStorageDirectory() else null,
                        apps = loadApps(ctx),
                        now = System.currentTimeMillis(),
                    )
                }
            }
            result.onSuccess { items = it.map(::Item) }.onFailure { error = it.toString() }
            busy = null
            screen = Screen.Home
        }
    }

    fun resolve(list: List<Item>) {
        val selected = list.filter { it.checked && it.checkable }
        scope.launch {
            busy = R.string.resolving
            usedBefore = storageUsage().first
            val files = selected.flatMap { item -> item.files.filter { it !in item.excluded } }
            withContext(Dispatchers.IO) { deleteFiles(files) }
            rescan(ctx, files)
            queue.clear()
            selected.forEach {
                when (val a = it.f.action) {
                    Action.ClearAllCache -> queue.addLast(clearCacheIntent())
                    is Action.Uninstall -> queue.addLast(uninstallIntent(a.pkg))
                    else -> Unit
                }
            }
            next()
        }
    }

    Scaffold { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val current = screen
            when {
                !skipWelcome && (!filesOk || !usageOk) -> Welcome(
                    filesOk = filesOk,
                    usageOk = usageOk,
                    onGrantFiles = { ctx.open(allFilesIntent(ctx)) },
                    onGrantUsage = { ctx.open(usageIntent()) },
                    onContinue = { skipWelcome = true },
                )
                busy != null -> Busy(busy!!)
                current is Screen.Detail -> Detail(current.item) { screen = Screen.Home }
                current is Screen.Result -> Result(
                    freed = current.freed,
                    manual = items.orEmpty().filter { it.f.kind == Kind.MANUAL_APP },
                    onOpen = { ctx.open(it) },
                    onAgain = { runAnalysis() },
                    onBack = { items = null; screen = Screen.Home },
                )
                else -> Home(
                    usage = usage,
                    items = items,
                    error = error,
                    onAnalyze = { runAnalysis() },
                    onResolve = { resolve(items.orEmpty()) },
                    onOpenItem = { screen = Screen.Detail(it) },
                    onIntent = { ctx.open(it) },
                )
            }
        }
    }
}

private fun Context.open(intent: Intent) {
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // Settings screen missing on this device: nothing else to do.
    }
}

@Composable
private fun size(bytes: Long): String = Formatter.formatShortFileSize(LocalContext.current, bytes)

@Composable
private fun title(f: Finding): String = when (f.kind) {
    Kind.WA_DUPLICATES -> stringResource(R.string.k_wa_duplicates)
    Kind.WA_BACKUPS -> stringResource(R.string.k_wa_backups)
    Kind.WA_STATUSES -> stringResource(R.string.k_wa_statuses)
    Kind.CACHE -> stringResource(R.string.k_cache)
    Kind.APKS -> stringResource(R.string.k_apks)
    Kind.THUMBNAILS -> stringResource(R.string.k_thumbnails)
    Kind.EMPTY_DIRS -> stringResource(R.string.k_empty_dirs)
    Kind.DUPLICATES -> stringResource(R.string.k_duplicates)
    Kind.LARGE_VIDEOS -> stringResource(R.string.k_large_videos)
    Kind.UNUSED_APP -> stringResource(R.string.k_unused_app, f.label.orEmpty())
    Kind.MANUAL_APP -> stringResource(R.string.k_manual_app, f.label.orEmpty())
    Kind.NEEDS_FILES_ACCESS -> stringResource(R.string.k_needs_files)
    Kind.NEEDS_USAGE_ACCESS -> stringResource(R.string.k_needs_usage)
}

@Composable
private fun hint(kind: Kind): String? = when (kind) {
    Kind.WA_DUPLICATES -> stringResource(R.string.wa_duplicates_warning)
    Kind.WA_BACKUPS -> stringResource(R.string.hint_backups)
    Kind.CACHE -> stringResource(R.string.hint_cache)
    Kind.THUMBNAILS -> stringResource(R.string.hint_thumbnails)
    Kind.UNUSED_APP -> stringResource(R.string.hint_uninstall)
    else -> null
}

@Composable
private fun Welcome(
    filesOk: Boolean,
    usageOk: Boolean,
    onGrantFiles: () -> Unit,
    onGrantUsage: () -> Unit,
    onContinue: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.headlineSmall)
        PermissionRow(stringResource(R.string.welcome_files), filesOk, onGrantFiles)
        PermissionRow(stringResource(R.string.welcome_usage), usageOk, onGrantUsage)
        Text(stringResource(R.string.welcome_privacy), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onContinue) { Text(stringResource(R.string.continue_anyway)) }
    }
}

@Composable
private fun PermissionRow(text: String, granted: Boolean, onGrant: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text)
        if (granted) {
            Text(stringResource(R.string.granted), color = MaterialTheme.colorScheme.primary)
        } else {
            Button(onClick = onGrant) { Text(stringResource(R.string.grant)) }
        }
    }
}

@Composable
private fun Busy(message: Int) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(stringResource(message))
    }
}

@Composable
private fun Home(
    usage: Pair<Long, Long>,
    items: List<Item>?,
    error: String?,
    onAnalyze: () -> Unit,
    onResolve: () -> Unit,
    onOpenItem: (Item) -> Unit,
    onIntent: (Intent) -> Unit,
) {
    val ctx = LocalContext.current
    val (used, total) = usage
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { if (total > 0) used.toFloat() / total else 0f },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.used_of, size(used), size(total)), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        error?.let { Text(stringResource(R.string.error, it), color = MaterialTheme.colorScheme.error) }

        if (items == null) {
            Button(onClick = onAnalyze, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.analyze)) }
        } else {
            val actionable = items.filter { it.checkable }
            val manual = items.filter { it.f.kind == Kind.MANUAL_APP }
            val needs = items.filter { it.f.action == null }
            val selected = actionable.filter { it.checked }.sumOf { it.bytes }

            Text(
                if (actionable.isEmpty()) stringResource(R.string.nothing)
                else stringResource(R.string.can_free, size(actionable.sumOf { it.bytes })),
                style = MaterialTheme.typography.titleMedium,
            )
            LazyColumn(Modifier.weight(1f)) {
                items(needs) { item ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(title(item.f), Modifier.weight(1f))
                        TextButton(onClick = {
                            onIntent(if (item.f.kind == Kind.NEEDS_USAGE_ACCESS) usageIntent() else allFilesIntent(ctx))
                        }) { Text(stringResource(R.string.grant)) }
                    }
                }
                items(actionable) { item -> ItemRow(item, onOpenItem) }
                if (manual.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.only_you),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                        )
                    }
                    items(manual) { item -> ManualRow(item, onIntent) }
                }
            }
            Button(onClick = onResolve, enabled = actionable.any { it.checked }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.resolve, size(selected)))
            }
            TextButton(onClick = onAnalyze, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.analyze_again)) }
        }
    }
}

@Composable
private fun ItemRow(item: Item, onOpen: (Item) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = item.files.isNotEmpty()) { onOpen(item) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = item.checked, onCheckedChange = { item.checked = it })
        Column(Modifier.weight(1f)) {
            Text(title(item.f))
            hint(item.f.kind)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            if (item.bytes > 0) size(item.bytes)
            else pluralStringResource(R.plurals.folders, item.files.size, item.files.size),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ManualRow(item: Item, onIntent: (Intent) -> Unit) {
    val pkg = (item.f.action as Action.OpenAppSettings).pkg
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title(item.f))
            Text(size(item.f.bytes), style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = { onIntent(appSettingsIntent(pkg)) }) { Text(stringResource(R.string.open)) }
    }
}

@Composable
private fun Detail(item: Item, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val root = remember { Environment.getExternalStorageDirectory().path + "/" }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        Text(title(item.f), style = MaterialTheme.typography.titleLarge)
        hint(item.f.kind)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        Text(size(item.bytes), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(item.files, key = { it.path }) { file ->
                val bytes = remember(file) { file.length() }
                val keep = file !in item.excluded
                Row(
                    Modifier.fillMaxWidth().clickable {
                        if (keep) item.excluded[file] = bytes else item.excluded.remove(file)
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = keep, onCheckedChange = { if (it) item.excluded.remove(file) else item.excluded[file] = bytes })
                    Text(file.path.removePrefix(root), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    if (bytes > 0) Text(size(bytes), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun Result(
    freed: Long,
    manual: List<Item>,
    onOpen: (Intent) -> Unit,
    onAgain: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.freed, size(freed)), style = MaterialTheme.typography.headlineSmall)
        if (manual.isNotEmpty()) {
            Text(stringResource(R.string.only_you), style = MaterialTheme.typography.titleSmall)
            manual.forEach { ManualRow(it, onOpen) }
        }
        Button(onClick = onAgain, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.analyze_again)) }
    }
}
