package io.github.flavioricardo.opencleaner.ui

import android.app.Activity
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
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
import io.github.flavioricardo.opencleaner.scan.DirNode
import io.github.flavioricardo.opencleaner.scan.scan
import io.github.flavioricardo.opencleaner.scan.deleteFiles
import io.github.flavioricardo.opencleaner.system.allFilesIntent
import io.github.flavioricardo.opencleaner.system.clearCacheIntent
import io.github.flavioricardo.opencleaner.system.deleteRequests
import io.github.flavioricardo.opencleaner.system.loadTrash
import io.github.flavioricardo.opencleaner.system.mediaUris
import io.github.flavioricardo.opencleaner.system.trashRequests
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
    /** Things OpenCleaner can do itself; items that only open the app's settings are not. */
    val checkable: Boolean get() = f.action != null && f.action !is Action.OpenAppSettings
    val selected: Boolean get() = checked && checkable && (files.isEmpty() || excluded.size < files.size)
    val isSystemDialog: Boolean
        get() = f.action is Action.ClearAllCache || f.action is Action.Uninstall || f.action is Action.EmptyTrash
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
    /** Media files of this item sent to the Android trash instead of deleted. */
    var toTrash by mutableIntStateOf(0)
}

/** The cleaning step that moves the collected media to the Android trash. */
internal data object TrashMove

internal class Report(val steps: List<Step>, val usedBefore: Long, val usedAfter: Long, val millis: Long, val stopped: Boolean)

internal val WHATSAPP_KINDS = listOf(
    Kind.WA_DUPLICATES, Kind.WA_BACKUPS, Kind.WA_STATUSES,
    Kind.WA_OLD_VIDEOS, Kind.WA_OLD_IMAGES, Kind.WA_OLD_AUDIO, Kind.WA_OLD_DOCS,
)
internal val SYSTEM_KINDS = listOf(Kind.CACHE, Kind.TRASH)
internal val OLD_KINDS = listOf(Kind.OLD_DOWNLOADS, Kind.OLD_SCREENSHOTS, Kind.OLD_RECORDINGS)
internal val FILE_KINDS = listOf(Kind.APKS, Kind.THUMBNAILS, Kind.DUPLICATES, Kind.LARGE_VIDEOS, Kind.EMPTY_DIRS)

/** Everything the pages read about the last analysis. */
internal class Analysis(
    val items: List<Item>,
    val apps: Map<String, AppInfo>,
    val millis: Long,
    val options: ScanOptions,
    val tree: DirNode?,
    val biggest: List<Pair<File, Long>>,
) {
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
    var storageView by remember { mutableStateOf(false) }
    var stopRequested by remember { mutableStateOf(false) }
    val usage = remember(resumeTick, usageTick) { storageUsage() }

    // System dialogs (clear cache, uninstall) are awaited one at a time; they return a result code.
    val dialogClosed = remember { Channel<Int>(Channel.CONFLATED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        dialogClosed.trySend(it.resultCode)
    }

    // MediaStore trash and delete requests come as PendingIntents.
    val senderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        dialogClosed.trySend(it.resultCode)
    }

    /** The request's result code, or null when it could not open. */
    suspend fun awaitSender(request: () -> PendingIntent): Int? {
        dialogClosed.tryReceive()
        try {
            senderLauncher.launch(IntentSenderRequest.Builder(request().intentSender).build())
        } catch (e: Exception) {
            return null // Not in MediaStore any more, or the dialog is missing: nothing to wait for.
        }
        return dialogClosed.receive()
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
                    val trash = if (storage != null) loadTrash(ctx) else null
                    val result = scan(storage, apps, System.currentTimeMillis(), options, trash, ::at)
                    val findings = result.findings
                    list.forEach {
                        val needsFiles = it.key != Phase.APPS && storage == null
                        val needsUsage = it.key == Phase.APPS && apps == null
                        it.status = if (needsFiles || needsUsage) Status.WARN else Status.DONE
                    }
                    val items = findings.map(::Item).onEach { item ->
                        if (item.checkable) prefs.rememberedChoice(item.f.kind)?.let { item.checked = it }
                    }
                    Analysis(
                        items, apps.orEmpty().associateBy { it.pkg }, SystemClock.elapsedRealtime() - start, options,
                        result.tree, result.biggest,
                    )
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
        // Files first, then the trash, then the system dialogs, so the long part runs without
        // waiting on the user.
        val trashStep = if (prefs.useTrash && selected.any { it.f.action is Action.DeleteFiles }) Step(TrashMove) else null
        val list = selected.filterNot { it.isSystemDialog }.map(::Step) + listOfNotNull(trashStep) +
            selected.filter { it.isSystemDialog }.map(::Step)
        steps = list
        cleaning = true
        stopRequested = false
        scope.launch {
            val start = SystemClock.elapsedRealtime()
            val before = storageUsage().first
            val deleted = mutableListOf<File>()
            val toTrash = mutableListOf<Pair<Uri, Long>>()
            for (step in list) {
                if (stopRequested) {
                    step.status = Status.SKIPPED
                    continue
                }
                step.status = Status.RUNNING
                if (step.key == TrashMove) {
                    // One system dialog per batch; a batch the user cancels stays where it was.
                    step.total = toTrash.size
                    var bytes = 0L
                    var cancelled = false
                    for (batch in toTrash.chunked(500)) {
                        val result = awaitSender { trashRequests(ctx, batch.map { it.first }).single() }
                        if (result == Activity.RESULT_OK) {
                            bytes += batch.sumOf { it.second }
                            step.done += batch.size
                        } else {
                            cancelled = true
                        }
                    }
                    step.bytes = bytes
                    step.status = if (cancelled) Status.WARN else Status.DONE
                    continue
                }
                val item = step.key as Item
                when (val action = item.f.action) {
                    is Action.DeleteFiles -> {
                        val files = item.files.filter { it !in item.excluded && !guarded(it) }
                        // With the trash on, photos, videos and audio wait for the trash step.
                        val media = if (trashStep == null) emptyMap<File, Pair<Uri, Long>>() else withContext(Dispatchers.IO) {
                            mediaUris(ctx, files).mapValues { (f, uri) -> uri to f.length() }
                        }
                        val direct = files.filter { it !in media }
                        step.total = direct.size
                        step.toTrash = media.size
                        toTrash += media.values
                        step.bytes = withContext(Dispatchers.IO) {
                            deleteFiles(direct, shouldStop = { stopRequested }) { step.done = it }
                        }
                        deleted += direct.take(step.done)
                        // Stopped in the middle: say how far it got.
                        step.status = if (step.done < direct.size) Status.WARN else Status.DONE
                    }
                    is Action.EmptyTrash -> {
                        val uris = action.uris.map(Uri::parse)
                        var ok = true
                        for (batch in uris.chunked(500)) {
                            if (awaitSender { deleteRequests(ctx, batch).single() } != Activity.RESULT_OK) ok = false
                        }
                        step.bytes = if (ok) item.f.bytes else 0L
                        step.status = if (ok) Status.DONE else Status.WARN
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
            if (running == null && finished == null && detailItem == null && !storageView && (skipWelcome || filesOk && usageOk)) {
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
        val liveOptions = remember(prefs.videoMb, prefs.unusedDays, prefs.protectedDirs, prefs.oldMonths) { prefs.scanOptions() }
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
                storageView && current?.tree != null -> {
                    BackHandler { storageView = false }
                    StoragePage(current, usage) { storageView = false }
                }
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
                        onStorage = { storageView = true },
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
