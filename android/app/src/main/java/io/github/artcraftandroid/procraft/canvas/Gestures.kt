package io.github.artcraftandroid.procraft.canvas

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import io.github.artcraftandroid.procraft.core.GestureOption
import io.github.artcraftandroid.procraft.core.GesturePurpose
import io.github.artcraftandroid.procraft.core.KeptGestures
import io.github.artcraftandroid.procraft.core.TouchSettings
import io.github.artcraftandroid.procraft.core.Trigger
import io.github.artcraftandroid.procraft.core.gestureOptions
import io.github.artcraftandroid.procraft.core.gesturePurposeHeading
import io.github.artcraftandroid.procraft.core.gesturePurposeTitle
import io.github.artcraftandroid.procraft.core.gesturePurposes
import io.github.artcraftandroid.procraft.core.gestureTriggerLabel
import io.github.artcraftandroid.procraft.core.GestureSettings as CoreGestures

// Gesture controls: the pages, their switches, what starts on, the delays and the rule that a way
// of touching does one thing are the core's (gestures.rs); this keeps them in the app's settings.

/** A page's title (its row in the list) and heading. */
val GesturePurpose.title: String get() = gesturePurposeTitle(this)
val GesturePurpose.heading: String get() = gesturePurposeHeading(this)

/** A way of touching's name on its switch. */
val Trigger.label: String get() = gestureTriggerLabel(this)

/** The three things a side bar slider can change. */
private val SliderKinds = listOf("SIZE", "OPACITY", "FLOW")

/** The Colors panel's pickers, as General's switches name them. */
private val ColorPickers = listOf("COLOR_DISC", "COLOR_CLASSIC", "COLOR_HARMONY", "COLOR_VALUE", "COLOR_PALETTES")

/** What a pen button does held, each button doing one of its two ways at most. */
private val PenWays = listOf(
    setOf("PEN_BUTTON_PAN", "PEN_BUTTON_ZOOM"),
    setOf("PEN_SECOND_PAN", "PEN_SECOND_ZOOM"),
    // (not the pen's, but the same: one of two, switching one on switches the other off)
    setOf("COLOR_SLIDERS_RGB", "COLOR_SLIDERS_LAB"),
)

/** Every page's switches, in order. */
val GestureOptions: Map<GesturePurpose, List<GestureOption>> by lazy { gesturePurposes().associateWith { gestureOptions(it) } }

/** The gesture settings, kept in [prefs] by each switch's name and each page's delay. */
class GestureSettings(private val prefs: SharedPreferences) {
    private val core = CoreGestures(
        KeptGestures(
            switches = GestureOptions.flatMap { (p, options) -> options.map { "${p.name}.${it.key}" } }
                .filter { prefs.contains(it) }.associateWith { prefs.getBoolean(it, false) },
            delays = GesturePurpose.entries.map { "${it.name}.delay" }.filter { prefs.contains(it) }.associateWith { prefs.getFloat(it, 0f) },
        ),
    )

    // read wherever the settings are, so what shows them follows a change
    private var changes by mutableIntStateOf(0)

    fun isOn(p: GesturePurpose, key: String): Boolean = changes.let { core.isOn(p, key) }

    fun isOn(p: GesturePurpose, t: Trigger): Boolean = isOn(p, t.name)

    /** Turns a switch on or off; on, its trigger goes off everywhere else. */
    fun set(p: GesturePurpose, key: String, value: Boolean) {
        if (p == GesturePurpose.GENERAL && !allowed(key, value)) return
        put(p, key, value)
        if (p == GesturePurpose.GENERAL && value) tidyAfter(key)
    }

    private fun put(p: GesturePurpose, key: String, value: Boolean) {
        val edit = prefs.edit()
        for ((name, on) in core.set(p, key, value)) edit.putBoolean(name, on)
        edit.apply()
        changes++
    }

    private fun general(key: String) = isOn(GesturePurpose.GENERAL, key)

    /** Which of the three a side bar slider ([slot] 1 or 2) changes. */
    fun sliderKind(slot: Int): String =
        SliderKinds.firstOrNull { general("SLIDER${slot}_$it") } ?: if (slot == 1) "SIZE" else "OPACITY"

    /**
     * Whether General's switch [key] may be set to [value]: a slider always changes one of the
     * three, and the Colors panel always offers a picker, so the last one on stays on.
     */
    private fun allowed(key: String, value: Boolean): Boolean {
        if (value) return true
        if (key.startsWith("SLIDER1_") || key.startsWith("SLIDER2_") || key.startsWith("COLOR_SLIDERS_")) return false
        if (key in ColorPickers) return ColorPickers.count { it != key && general(it) } > 0
        return true
    }

    /** [key] was switched on: what it rules out goes off (a slider and the pen's buttons keep one of their ways). */
    private fun tidyAfter(key: String) {
        val g = GesturePurpose.GENERAL
        PenWays.firstOrNull { key in it }?.forEach { if (it != key) put(g, it, false) }
        if (key.startsWith("SLIDER1_") || key.startsWith("SLIDER2_")) {
            val slot = key[6].digitToInt()
            val kind = key.substringAfter('_')
            val other = 3 - slot
            // what this slider had before it took [kind]: the other slider takes it if it had [kind]
            val before = SliderKinds.firstOrNull { it != kind && general("SLIDER${slot}_$it") } ?: if (slot == 1) "SIZE" else "OPACITY"
            val otherHad = sliderKind(other)
            for (k in SliderKinds) if (k != kind) put(g, "SLIDER${slot}_$k", false)
            if (otherHad == kind) {
                put(g, "SLIDER${other}_$kind", false)
                put(g, "SLIDER${other}_$before", true)
                for (k in SliderKinds) if (k != before) put(g, "SLIDER${other}_$k", false)
            }
        }
    }

    /** What [t] does, if anything. */
    fun purposeOf(t: Trigger): GesturePurpose? = changes.let { core.purposeOf(t) }

    /** A page's delay, seconds. */
    fun delay(p: GesturePurpose): Float = changes.let { core.delay(p) }

    fun setDelay(p: GesturePurpose, seconds: Float) {
        prefs.edit().putFloat("${p.name}.delay", core.setDelay(p, seconds)).apply()
        changes++
    }

    /** Every switch and delay as the app ships them. */
    fun resetAll() {
        core.resetAll()
        val all = core.kept()
        val edit = prefs.edit()
        for (name in all.switches.keys + all.delays.keys) edit.remove(name)
        edit.apply()
        changes++
    }

    /** Every switch and delay as a text to keep in a file ([importJson] reads it back). */
    fun exportJson(): String {
        val all = core.kept()
        val switches = org.json.JSONObject()
        for ((name, on) in all.switches) switches.put(name, on)
        val delays = org.json.JSONObject()
        for ((name, seconds) in all.delays) delays.put(name, seconds.toDouble())
        return org.json.JSONObject()
            .put("app", "procraft-gesture-settings")
            .put("version", 1)
            .put("switches", switches)
            .put("delays", delays)
            .toString(2)
    }

    /** Takes in what [exportJson] wrote: false if [text] is not that. Names this app doesn't know are left out. */
    fun importJson(text: String): Boolean {
        val root = try {
            org.json.JSONObject(text)
        } catch (e: org.json.JSONException) {
            return false
        }
        val switches = root.optJSONObject("switches") ?: return false
        val delays = root.optJSONObject("delays")
        val wanted = buildList {
            for ((p, options) in GestureOptions) {
                for (o in options) {
                    val name = "${p.name}.${o.key}"
                    if (switches.has(name)) add(Triple(p, o.key, switches.optBoolean(name, o.default)))
                }
            }
        }
        // the switches that go off first, so that one coming on doesn't turn off another that comes on too
        for ((p, key, on) in wanted) if (!on) set(p, key, false)
        for ((p, key, on) in wanted) if (on) set(p, key, true)
        if (delays != null) {
            for (p in GesturePurpose.entries) {
                val name = "${p.name}.delay"
                if (delays.has(name)) {
                    val seconds = delays.optDouble(name, Double.NaN)
                    if (!seconds.isNaN()) setDelay(p, seconds.toFloat())
                }
            }
        }
        return true
    }

    /** The settings as the canvas's touches (in the shared core) take them. */
    fun touchSettings(rapidUndoMs: Double): TouchSettings = changes.let { core.touchSettings(rapidUndoMs) }

    companion object {
        // as the core keeps them (gestures.rs)
        const val MIN_DELAY_S = 0.1f
        const val MAX_DELAY_S = 1.5f
    }
}
