package io.github.flavioricardo.opencleaner.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.flavioricardo.opencleaner.R
import io.github.flavioricardo.opencleaner.scan.Action
import io.github.flavioricardo.opencleaner.scan.AppInfo
import io.github.flavioricardo.opencleaner.scan.Finding
import io.github.flavioricardo.opencleaner.scan.Kind
import io.github.flavioricardo.opencleaner.scan.Phase
import io.github.flavioricardo.opencleaner.scan.ScanOptions
import io.github.flavioricardo.opencleaner.scan.analyze
import io.github.flavioricardo.opencleaner.scan.deleteFiles
import io.github.flavioricardo.opencleaner.system.allFilesIntent
import io.github.flavioricardo.opencleaner.system.clearCacheIntent
import io.github.flavioricardo.opencleaner.system.hasAllFiles
import io.github.flavioricardo.opencleaner.system.hasUsage
import io.github.flavioricardo.opencleaner.system.isInstalled
import io.github.flavioricardo.opencleaner.system.loadApps
import io.github.flavioricardo.opencleaner.system.rescan
import io.github.flavioricardo.opencleaner.system.storageUsage
import io.github.flavioricardo.opencleaner.system.uninstallIntent
import io.github.flavioricardo.opencleaner.system.usageIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** A finding plus what the user changed on screen. */
internal class Item(val f: Finding) {
    var checked by mutableStateOf(f.checked)
    /** Files the user unchecked in the detail screen, with their sizes. */
    val excluded = mutableStateMapOf<File, Long>()
    val files: List<File> get() = (f.action as? Action.DeleteFiles)?.files.orEmpty()
    val bytes: Long get() = f.bytes - excluded.values.sum()
    /** Things OpenCleaner can do itself; MANUAL_APP and APP_CACHE only open the app's settings. */
    val checkable: Boolean get() = f.action != null && f.kind != Kind.MANUAL_APP && f.kind != Kind.APP_CACHE
    val selected: Boolean get() = checked && checkable && (files.isEmpty() || excluded.size < files.size)
    val isSystemDialog: Boolean get() = f.action is Action.ClearAllCache || f.action is Action.Uninstall
}

internal enum class Tab(val label: Int, val icon: ImageVector) {
    HEALTH(R.string.tab_health, Lucide.HeartPulse),
    CLEAN(R.string.tab_clean, Lucide.BrushCleaning),
    APPS(R.string.tab_apps, Lucide.LayoutGrid),
    OPTIONS(R.string.tab_options, Lucide.Settings),
}

/** The options the shown analysis ran with, for labels like "Videos over 100 MB". */
internal val LocalScanOptions = staticCompositionLocalOf { ScanOptions() }

internal enum class Status { PENDING, RUNNING, DONE, WARN, SKIPPED }

/** One line of the analysis or cleaning progress. key is a Phase or an Item. */
internal class Step(val key: Any) {
    var status by mutableStateOf(Status.PENDING)
    var done by mutableIntStateOf(0)
    var total by mutableIntStateOf(0)
    /** Bytes freed (cleaning) or found; -1 = not shown. */
    var bytes by mutableLongStateOf(-1L)
}

internal class Report(val steps: List<Step>, val usedBefore: Long, val usedAfter: Long, val millis: Long, val stopped: Boolean)

internal val WHATSAPP_KINDS = listOf(Kind.WA_DUPLICATES, Kind.WA_BACKUPS, Kind.WA_STATUSES)
internal val SYSTEM_KINDS = listOf(Kind.CACHE)
internal val FILE_KINDS = listOf(Kind.APKS, Kind.THUMBNAILS, Kind.DUPLICATES, Kind.LARGE_VIDEOS, Kind.EMPTY_DIRS)

/** Everything the pages read about the last analysis. */
internal class Analysis(val items: List<Item>, val apps: Map<String, AppInfo>, val millis: Long, val options: ScanOptions) {
    fun of(kinds: List<Kind>) = items.filter { it.f.kind in kinds }
    val checkable get() = items.filter { it.checkable }
    val selectedBytes get() = checkable.filter { it.selected }.sumOf { it.bytes }
    val possibleBytes get() = checkable.sumOf { it.bytes }
    val fileCount get() = checkable.sumOf { maxOf(1, it.files.size) }
}

@Composable
fun App(resumeTick: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val filesOk = remember(resumeTick) { hasAllFiles() }
    val usageOk = remember(resumeTick) { hasUsage(ctx) }
    var skipWelcome by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(Tab.HEALTH) }
    var analysis by remember { mutableStateOf<Analysis?>(null) }
    var detail by remember { mutableStateOf<Item?>(null) }
    var steps by remember { mutableStateOf<List<Step>?>(null) }
    var cleaning by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<Report?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var usageTick by remember { mutableIntStateOf(0) }
    var analysisJob by remember { mutableStateOf<Job?>(null) }
    val prefs = remember { Prefs(ctx.applicationContext) }
    var confirming by remember { mutableStateOf(false) }
    var stopRequested by remember { mutableStateOf(false) }
    val usage = remember(resumeTick, usageTick) { storageUsage() }

    // System dialogs (clear cache, uninstall) are awaited one at a time; they return a result code.
    val dialogClosed = remember { Channel<Int>(Channel.CONFLATED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        dialogClosed.trySend(it.resultCode)
    }

    /** The dialog's result code, or null when it could not open. */
    suspend fun awaitDialog(intent: Intent): Int? {
        dialogClosed.tryReceive()
        try {
            launcher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            return null
        } catch (e: SecurityException) {
            return null
        }
        return dialogClosed.receive()
    }

    // steps is set before launching, so a second tap finds a run in progress and does nothing.
    fun runAnalysis() {
        if (steps != null) return
        val list = Phase.entries.map(::Step)
        steps = list
        cleaning = false
        val options = prefs.scanOptions()
        analysisJob = scope.launch {
            error = null
            report = null
            val start = SystemClock.elapsedRealtime()
            val job = coroutineContext.job
            fun at(phase: Phase, done: Int, total: Int) {
                job.ensureActive() // Cancel stops the scan at its next progress report.
                list.forEach {
                    val p = it.key as Phase
                    if (p == phase) {
                        it.status = Status.RUNNING
                        it.done = done
                        it.total = total
                    } else if (p < phase && it.status == Status.RUNNING) {
                        it.status = Status.DONE
                    }
                }
            }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val apps = loadApps(ctx) { done, total -> at(Phase.APPS, done, total) }
                    val storage = if (hasAllFiles()) Environment.getExternalStorageDirectory() else null
                    val findings = analyze(storage, apps, System.currentTimeMillis(), options, ::at)
                    list.forEach {
                        val needsFiles = it.key != Phase.APPS && storage == null
                        val needsUsage = it.key == Phase.APPS && apps == null
                        it.status = if (needsFiles || needsUsage) Status.WARN else Status.DONE
                    }
                    val items = findings.map(::Item).onEach { item ->
                        if (item.checkable) prefs.rememberedChoice(item.f.kind)?.let { item.checked = it }
                    }
                    Analysis(items, apps.orEmpty().associateBy { it.pkg }, SystemClock.elapsedRealtime() - start, options)
                }
            }
            result.onSuccess { analysis = it }.onFailure { error = it.toString() }
            delay(500) // Let the finished steps show for a moment.
            steps = null
        }
    }

    fun cancelAnalysis() {
        analysisJob?.cancel()
        analysisJob = null
        steps = null
    }

    fun clean() {
        confirming = false
        val selected = analysis?.checkable.orEmpty().filter { it.selected }
        if (selected.isEmpty() || steps != null) return
        prefs.rememberChoices(analysis?.items.orEmpty())
        // A folder protected after the analysis must hold too: check again right before deleting.
        val guard = prefs.scanOptions().protectedDirs
        fun guarded(f: File) = guard.any {
            f.path == it.path || f.path.startsWith(it.path + File.separator) || it.path.startsWith(f.path + File.separator)
        }
        // Files first, then the system dialogs, so the long part runs without waiting on the user.
        val list = (selected.filterNot { it.isSystemDialog } + selected.filter { it.isSystemDialog }).map(::Step)
        steps = list
        cleaning = true
        stopRequested = false
        scope.launch {
            val start = SystemClock.elapsedRealtime()
            val before = storageUsage().first
            val deleted = mutableListOf<File>()
            for (step in list) {
                val item = step.key as Item
                if (stopRequested) {
                    step.status = Status.SKIPPED
                    continue
                }
                step.status = Status.RUNNING
                when (val action = item.f.action) {
                    is Action.DeleteFiles -> {
                        val files = item.files.filter { it !in item.excluded && !guarded(it) }
                        step.total = files.size
                        step.bytes = withContext(Dispatchers.IO) {
                            deleteFiles(files, shouldStop = { stopRequested }) { step.done = it }
                        }
                        deleted += files.take(step.done)
                        // Stopped in the middle: say how far it got.
                        step.status = if (step.done < files.size) Status.WARN else Status.DONE
                    }
                    Action.ClearAllCache -> {
                        val usedBefore = storageUsage().first
                        val result = awaitDialog(clearCacheIntent())
                        // The system may free the space a moment after the dialog closes.
                        var freed = 0L
                        for (i in 1..6) {
                            freed = maxOf(0L, usedBefore - storageUsage().first)
                            if (freed > 0) break
                            delay(500)
                        }
                        step.bytes = freed
                        // RESULT_OK: Android cleared the external caches, even if they were already empty.
                        step.status = if (result == Activity.RESULT_OK || freed > 0) Status.DONE else Status.WARN
                    }
                    is Action.Uninstall -> {
                        awaitDialog(uninstallIntent(action.pkg))
                        // The package manager may finish removing it a moment after the dialog closes.
                        var gone = false
                        for (i in 1..6) {
                            gone = !isInstalled(ctx, action.pkg)
                            if (gone) break
                            delay(500)
                        }
                        step.bytes = if (gone) item.f.bytes else 0L
                        step.status = if (gone) Status.DONE else Status.WARN
                    }
                    else -> step.status = Status.DONE
                }
            }
            rescan(ctx, deleted)
            report = Report(list, before, storageUsage().first, SystemClock.elapsedRealtime() - start, stopRequested)
            steps = null
            usageTick++
        }
    }

    fun requestClean() {
        if (prefs.confirm) confirming = true else clean()
    }

    fun resetSelection() {
        prefs.forgetChoices()
        analysis?.items?.forEach {
            it.checked = it.f.checked
            it.excluded.clear()
        }
    }

    fun closeReport() {
        report = null
        analysis = null
        tab = Tab.HEALTH
    }

    val current = analysis
    val detailItem = detail
    val finished = report
    val running = steps
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (running == null && finished == null && detailItem == null && (skipWelcome || filesOk && usageOk)) {
                Column {
                    if ((tab == Tab.CLEAN || tab == Tab.APPS) && current != null && current.checkable.isNotEmpty()) {
                        SelectionBar(current, onClean = ::requestClean)
                    }
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        Tab.entries.forEach {
                            NavigationBarItem(
                                selected = tab == it,
                                onClick = { tab = it },
                                icon = { Icon(it.icon, contentDescription = null) },
                                label = { Text(stringResource(it.label)) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { pad ->
        if (confirming && current != null) {
            ConfirmDialog(current, onConfirm = ::clean, onDismiss = { confirming = false })
        }
        val liveOptions = remember(prefs.videoMb, prefs.unusedDays, prefs.protectedDirs) { prefs.scanOptions() }
        CompositionLocalProvider(LocalScanOptions provides (current?.options ?: liveOptions)) {
        Box(Modifier.padding(pad).fillMaxSize()) {
            when {
                !skipWelcome && (!filesOk || !usageOk) -> Welcome(
                    filesOk = filesOk,
                    usageOk = usageOk,
                    onGrantFiles = { ctx.open(allFilesIntent(ctx)) },
                    onGrantUsage = { ctx.open(usageIntent()) },
                    onContinue = { skipWelcome = true },
                )
                running != null -> {
                    // Back does what the button under the steps does.
                    val onCancel = if (cleaning) ({ stopRequested = true }) else ::cancelAnalysis
                    BackHandler(onBack = onCancel)
                    StepsPage(running, cleaning, stopping = cleaning && stopRequested, onCancel = onCancel)
                }
                finished != null -> {
                    BackHandler(onBack = ::closeReport)
                    ResultPage(
                        report = finished,
                        manual = current?.items.orEmpty().filter { it.f.kind == Kind.MANUAL_APP },
                        onAgain = { closeReport(); runAnalysis() },
                        onClose = ::closeReport,
                    )
                }
                detailItem != null -> {
                    BackHandler { detail = null }
                    DetailPage(detailItem) { detail = null }
                }
                else -> {
                    BackHandler(enabled = tab != Tab.HEALTH) { tab = Tab.HEALTH }
                    when (tab) {
                    Tab.HEALTH -> HealthPage(
                        usage = usage,
                        analysis = current,
                        error = error,
                        filesOk = filesOk,
                        usageOk = usageOk,
                        onAnalyze = ::runAnalysis,
                        onClean = ::requestClean,
                        onTab = { tab = it },
                    )
                    Tab.CLEAN -> CleanPage(current, onAnalyze = ::runAnalysis, onOpen = { detail = it })
                    Tab.APPS -> AppsPage(current, onAnalyze = ::runAnalysis)
                    Tab.OPTIONS -> OptionsPage(prefs, filesOk, usageOk, onResetSelection = ::resetSelection)
                    }
                }
            }
        }
        }
    }
}

/** "Clean 4.5 GB?" before anything is deleted (Options: ask before cleaning). */
@Composable
private fun ConfirmDialog(a: Analysis, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val count = a.checkable.count { it.selected }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.confirm_title, size(a.selectedBytes))) },
        text = { Text(pluralStringResource(R.plurals.confirm_text, count, count)) },
        confirmButton = { Button(onClick = onConfirm) { Text(stringResource(R.string.confirm_clean)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

internal fun Context.open(intent: Intent) {
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // Settings screen missing on this device: nothing else to do.
    }
}
