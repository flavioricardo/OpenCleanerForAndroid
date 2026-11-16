package io.github.flavioricardo.opencleaner.ui

import android.content.Context
import android.os.Environment
import androidx.compose.runtime.mutableStateOf
import io.github.flavioricardo.opencleaner.scan.Kind
import io.github.flavioricardo.opencleaner.scan.ScanOptions
import java.io.File

/** The Options page, saved on the phone. Every change is written at once (desktop: "valem na hora"). */
internal class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("options", Context.MODE_PRIVATE)

    private val confirmState = mutableStateOf(sp.getBoolean("confirm", true))
    var confirm: Boolean
        get() = confirmState.value
        set(v) {
            confirmState.value = v
            sp.edit().putBoolean("confirm", v).apply()
        }

    private val rememberState = mutableStateOf(sp.getBoolean("remember", true))
    var rememberSelection: Boolean
        get() = rememberState.value
        set(v) {
            rememberState.value = v
            sp.edit().putBoolean("remember", v).apply()
        }

    private val videoState = mutableStateOf(sp.getInt("video_mb", 100))
    var videoMb: Int
        get() = videoState.value
        set(v) {
            videoState.value = v
            sp.edit().putInt("video_mb", v).apply()
        }

    private val unusedState = mutableStateOf(sp.getInt("unused_days", 30))
    var unusedDays: Int
        get() = unusedState.value
        set(v) {
            unusedState.value = v
            sp.edit().putInt("unused_days", v).apply()
        }

    private val oldState = mutableStateOf(sp.getInt("old_months", 6))
    var oldMonths: Int
        get() = oldState.value
        set(v) {
            oldState.value = v
            sp.edit().putInt("old_months", v).apply()
        }

    // Off by default: trashed media still takes space until the trash empties (30 days).
    private val trashState = mutableStateOf(sp.getBoolean("use_trash", false))
    var useTrash: Boolean
        get() = trashState.value
        set(v) {
            trashState.value = v
            sp.edit().putBoolean("use_trash", v).apply()
        }

    /** Paths relative to shared storage, like "DCIM/Camera". */
    private val protectedState = mutableStateOf(sp.getStringSet("protected", emptySet())!!.sorted())
    val protectedDirs: List<String> get() = protectedState.value

    fun protect(rel: String) = setProtected((protectedDirs + rel).distinct().sorted())

    fun unprotect(rel: String) = setProtected(protectedDirs - rel)

    private fun setProtected(list: List<String>) {
        protectedState.value = list
        sp.edit().putStringSet("protected", list.toSet()).apply()
    }

    fun scanOptions() = ScanOptions(
        minVideoBytes = videoMb * 1024L * 1024,
        unusedDays = unusedDays,
        oldMonths = oldMonths,
        protectedDirs = protectedDirs.map { File(Environment.getExternalStorageDirectory(), it) },
    )

    /** The checkbox the user left on this kind the last time they cleaned, or null. */
    fun rememberedChoice(kind: Kind): Boolean? =
        if (rememberSelection && kind !in NOT_REMEMBERED && sp.contains("sel_$kind")) sp.getBoolean("sel_$kind", false) else null

    fun rememberChoices(items: List<Item>) {
        if (!rememberSelection) return
        val e = sp.edit()
        items.filter { it.checkable && it.f.kind !in NOT_REMEMBERED }.forEach { e.putBoolean("sel_${it.f.kind}", it.checked) }
        e.apply()
    }

    fun forgetChoices() {
        val e = sp.edit()
        sp.all.keys.filter { it.startsWith("sel_") }.forEach(e::remove)
        e.apply()
    }

    companion object {
        // Apps are chosen one by one; personal media and the trash always start unchecked (spec).
        private val NOT_REMEMBERED = setOf(
            Kind.UNUSED_APP, Kind.LARGE_VIDEOS, Kind.TRASH,
            Kind.WA_OLD_VIDEOS, Kind.WA_OLD_IMAGES, Kind.WA_OLD_AUDIO, Kind.WA_OLD_DOCS,
            Kind.OLD_DOWNLOADS, Kind.OLD_SCREENSHOTS, Kind.OLD_RECORDINGS,
        )

        val VIDEO_MB = listOf(100, 250, 500, 1000)
        val UNUSED_DAYS = listOf(30, 60, 90, 180)
        val OLD_MONTHS = listOf(3, 6, 12)
    }
}
