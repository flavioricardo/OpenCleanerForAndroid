package io.github.flavioricardo.opencleaner.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.flavioricardo.opencleaner.R
import io.github.flavioricardo.opencleaner.system.allFilesIntent
import io.github.flavioricardo.opencleaner.system.usageIntent

private const val REPO_URL = "https://github.com/flavioricardo/OpenCleanerForAndroid"

/** Like the desktop Options page: changes apply at once. */
@Composable
internal fun OptionsPage(prefs: Prefs, filesOk: Boolean, usageOk: Boolean, onResetSelection: () -> Unit) {
    val ctx = LocalContext.current
    val restored = stringResource(R.string.selection_restored)
    val notInternal = stringResource(R.string.protect_internal_only)
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // "primary:DCIM/Camera" is DCIM/Camera on the internal shared storage.
        val id = DocumentsContract.getTreeDocumentId(uri)
        val rel = id.substringAfter(':', "").trim('/')
        if (!id.startsWith("primary:") || rel.isEmpty()) {
            Toast.makeText(ctx, notInternal, Toast.LENGTH_LONG).show()
        } else {
            prefs.protect(rel)
        }
    }
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull().orEmpty()
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PageTitle(stringResource(R.string.options_title), stringResource(R.string.options_subtitle)) }
        item {
            Panel {
                Overline(stringResource(R.string.options_cleanup))
                SwitchRow(stringResource(R.string.opt_confirm), stringResource(R.string.opt_confirm_sub), prefs.confirm) {
                    prefs.confirm = it
                }
                Divider()
                SwitchRow(stringResource(R.string.opt_remember), stringResource(R.string.opt_remember_sub), prefs.rememberSelection) {
                    prefs.rememberSelection = it
                }
                Divider()
                ChoiceRow(stringResource(R.string.opt_videos), prefs.videoMb, Prefs.VIDEO_MB, { size(it * 1024L * 1024) }) {
                    prefs.videoMb = it
                }
                Divider()
                ChoiceRow(
                    stringResource(R.string.opt_unused),
                    prefs.unusedDays,
                    Prefs.UNUSED_DAYS,
                    { pluralStringResource(R.plurals.days, it, it) },
                ) { prefs.unusedDays = it }
                Divider()
                ChoiceRow(
                    stringResource(R.string.opt_old),
                    prefs.oldMonths,
                    Prefs.OLD_MONTHS,
                    { pluralStringResource(R.plurals.months, it, it) },
                ) { prefs.oldMonths = it }
                Divider()
                SwitchRow(stringResource(R.string.opt_trash), stringResource(R.string.opt_trash_sub), prefs.useTrash) {
                    prefs.useTrash = it
                }
                Divider()
                TextButton(onClick = {
                    onResetSelection()
                    Toast.makeText(ctx, restored, Toast.LENGTH_SHORT).show()
                }) { Text(stringResource(R.string.opt_reset_selection)) }
            }
        }
        item {
            Panel {
                Overline(stringResource(R.string.options_protected))
                Muted(stringResource(R.string.options_protected_sub), Modifier.padding(top = 4.dp))
                prefs.protectedDirs.forEach { rel ->
                    Divider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Lucide.Shield, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text(rel, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        IconButton(onClick = { prefs.unprotect(rel) }) {
                            Icon(Lucide.X, stringResource(R.string.remove), Modifier.size(18.dp))
                        }
                    }
                }
                Divider()
                TextButton(onClick = { pickFolder.launch(null) }) {
                    Icon(Lucide.Plus, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.protect_add))
                }
            }
        }
        item {
            Panel {
                Overline(stringResource(R.string.options_permissions))
                LinkRow(
                    if (filesOk) Lucide.CircleCheck else Lucide.TriangleAlert, filesOk,
                    stringResource(R.string.perm_files),
                    stringResource(if (filesOk) R.string.granted else R.string.not_granted),
                ) { ctx.open(allFilesIntent(ctx)) }
                Divider()
                LinkRow(
                    if (usageOk) Lucide.CircleCheck else Lucide.TriangleAlert, usageOk,
                    stringResource(R.string.perm_usage),
                    stringResource(if (usageOk) R.string.granted else R.string.not_granted),
                ) { ctx.open(usageIntent()) }
                // Per-app language exists from Android 13; before it, the app follows the system.
                if (Build.VERSION.SDK_INT >= 33) {
                    Divider()
                    LinkRow(Lucide.Languages, true, stringResource(R.string.opt_language), stringResource(R.string.opt_language_sub)) {
                        ctx.open(Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", ctx.packageName, null)))
                    }
                }
            }
        }
        item {
            Panel {
                Overline(stringResource(R.string.options_about))
                Text(
                    stringResource(R.string.about_version, version),
                    Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Muted(stringResource(R.string.about_privacy))
                Divider()
                LinkRow(Lucide.ExternalLink, true, stringResource(R.string.about_source), REPO_URL.removePrefix("https://")) {
                    ctx.open(Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL)))
                }
            }
        }
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Muted(subtitle)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun <T> ChoiceRow(label: String, value: T, options: List<T>, text: @Composable (T) -> String, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Box {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text(value), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge)
                Icon(Lucide.ChevronDown, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach {
                    DropdownMenuItem(text = { Text(text(it)) }, onClick = {
                        onPick(it)
                        open = false
                    })
                }
            }
        }
    }
}

@Composable
private fun LinkRow(icon: ImageVector, ok: Boolean, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon, null, Modifier.size(20.dp),
            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Muted(subtitle)
        }
        Text(stringResource(R.string.open), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
    }
}
