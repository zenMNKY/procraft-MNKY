package io.github.artcraftandroid.procraft.canvas

import android.content.SharedPreferences
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import io.github.artcraftandroid.procraft.core.keyShortcutList
import org.json.JSONException
import org.json.JSONObject

/** A key press: the key's name as the shared shortcuts have it ("z", "[", "space", "left", "f5"), and the Ctrl (command), Shift and Alt keys held. */
data class Combo(val key: String, val ctrl: Boolean = false, val shift: Boolean = false, val alt: Boolean = false) {
    /** Written for keeping: "ctrl+shift+z". */
    fun text(): String = buildList {
        if (ctrl) add("ctrl")
        if (shift) add("shift")
        if (alt) add("alt")
        add(key)
    }.joinToString("+")

    /** Written for showing: "Ctrl+Shift+Z". */
    fun label(): String = buildList {
        if (ctrl) add("Ctrl")
        if (shift) add("Shift")
        if (alt) add("Alt")
        add(keyLabel(key))
    }.joinToString("+")

    companion object {
        /** A press as [text] writes it; null if it is not one. */
        fun parse(text: String): Combo? {
            val parts = text.split("+")
            val key = parts.lastOrNull()?.takeIf { it.isNotEmpty() } ?: return null
            val mods = parts.dropLast(1)
            if (mods.any { it != "ctrl" && it != "shift" && it != "alt" }) return null
            return Combo(key, "ctrl" in mods, "shift" in mods, "alt" in mods)
        }

        private fun keyLabel(key: String): String = when (key) {
            "space" -> "Space"
            "delete" -> "Delete"
            "backspace" -> "Backspace"
            "left" -> "Left"
            "right" -> "Right"
            "up" -> "Up"
            "down" -> "Down"
            else -> if (key.length == 1) key.uppercase() else key.replaceFirstChar { it.uppercase() }
        }
    }
}

/**
 * One key of the keyboard shortcuts' list that can be set to another press: [action] it does (a
 * row can have several keys, [position] of [count]), under the list's heading [group], and the
 * press the app starts out with ([default]).
 */
class HotkeySlot(
    val id: String,
    val group: String,
    val action: String,
    val position: Int,
    val count: Int,
    val default: Combo,
)

/**
 * The hotkeys: the shared shortcuts' list (crates/core keys.rs) with each of its keys settable by
 * the user. What the user sets is kept apart from the other settings, in [prefs].
 *
 * A press the user set for a slot does what that slot's default press does (the core's shortcut
 * for it is asked), and the slot's own default press does nothing while it is set to another.
 */
class Hotkeys(private val prefs: SharedPreferences) {
    /** Every key of the list that is a press (the drags are not). */
    val slots: List<HotkeySlot> = buildList {
        for (group in keyShortcutList(false, false)) {
            for (row in group.rows) {
                row.combos.forEachIndexed { i, c ->
                    add(HotkeySlot("${row.does}#$i", group.heading, row.does, i, row.combos.size, Combo(c.key, c.command, c.shift, c.alt)))
                }
            }
        }
    }

    /** The slots under the list's headings, in its order. */
    val groups: List<Pair<String, List<HotkeySlot>>> = slots.groupBy { it.group }.toList()

    /** What the user set, by slot (null: set to nothing); what is not here is as the app starts out. */
    private val set = HashMap<String, Combo?>()

    /** Counts every change, for the screen to look again. */
    var changes by mutableIntStateOf(0)
        private set

    init {
        val known = slots.map { it.id }.toSet()
        for ((id, text) in prefs.all) {
            if (id !in known || text !is String) continue
            set[id] = if (text.isEmpty()) null else (Combo.parse(text) ?: continue)
        }
    }

    /** The press [slot] is set to now, or null if none. */
    fun combo(slot: HotkeySlot): Combo? {
        changes.let { }
        return if (set.containsKey(slot.id)) set[slot.id] else slot.default
    }

    /** Whether [slot] is set to something other than what the app starts out with. */
    fun changed(slot: HotkeySlot): Boolean {
        changes.let { }
        return set.containsKey(slot.id)
    }

    private fun store(slot: HotkeySlot, combo: Combo?) {
        val edit = prefs.edit()
        if (combo == slot.default) {
            set.remove(slot.id)
            edit.remove(slot.id)
        } else {
            set[slot.id] = combo
            edit.putString(slot.id, combo?.text() ?: "")
        }
        edit.apply()
    }

    /**
     * Sets [slot] to [combo] (null: none). A slot that had that press loses it: its action is
     * returned.
     */
    fun assign(slot: HotkeySlot, combo: Combo?): String? {
        var took: String? = null
        if (combo != null) {
            for (other in slots) {
                if (other.id != slot.id && combo(other) == combo) {
                    store(other, null)
                    took = other.action
                }
            }
        }
        store(slot, combo)
        changes++
        return took
    }

    /** [slot] back to the press the app starts out with. */
    fun reset(slot: HotkeySlot): String? = assign(slot, slot.default)

    /** Every slot back to the presses the app starts out with. */
    fun resetAll() {
        set.clear()
        prefs.edit().clear().apply()
        changes++
    }

    /** What a press does. */
    sealed interface Way {
        /** Nothing is set for it: the core's shortcuts take it as it is. */
        object Same : Way

        /** It is a default press whose slot was set to another: it does nothing now. */
        object Blocked : Way

        /** It was set for a slot: it does what that slot's default press [combo] does. */
        class Use(val combo: Combo) : Way
    }

    fun translate(press: Combo): Way {
        if (set.isEmpty()) return Way.Same
        for (slot in slots) if (set.containsKey(slot.id) && set[slot.id] == press) return Way.Use(slot.default)
        for (slot in slots) if (set.containsKey(slot.id) && slot.default == press) return Way.Blocked
        return Way.Same
    }

    /** Every slot's press as a text to keep in a file ([importJson] reads it back). */
    fun exportJson(): String {
        val keys = JSONObject()
        for (slot in slots) keys.put(slot.id, combo(slot)?.text() ?: "")
        return JSONObject().put("app", "procraft-hotkeys").put("version", 1).put("keys", keys).toString(2)
    }

    /** Takes in what [exportJson] wrote: false if [text] is not that. Slots it does not name go back to the app's own. */
    fun importJson(text: String): Boolean {
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            return false
        }
        val keys = root.optJSONObject("keys") ?: return false
        set.clear()
        prefs.edit().clear().apply()
        for (slot in slots) {
            if (!keys.has(slot.id)) continue
            val written = keys.optString(slot.id, "")
            val combo = if (written.isEmpty()) null else (Combo.parse(written) ?: continue)
            store(slot, combo)
        }
        changes++
        return true
    }

    companion object {
        /** The name the shared shortcuts give the key [keyCode], or null for a key they have no name for (a modifier alone, say). */
        fun nameOf(keyCode: Int): String? = when (keyCode) {
            in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> ('a' + (keyCode - KeyEvent.KEYCODE_A)).toString()
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> ('0' + (keyCode - KeyEvent.KEYCODE_0)).toString()
            in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12 -> "f" + (keyCode - KeyEvent.KEYCODE_F1 + 1)
            KeyEvent.KEYCODE_LEFT_BRACKET -> "["
            KeyEvent.KEYCODE_RIGHT_BRACKET -> "]"
            KeyEvent.KEYCODE_SEMICOLON -> ";"
            KeyEvent.KEYCODE_COMMA -> ","
            KeyEvent.KEYCODE_PERIOD -> "."
            KeyEvent.KEYCODE_MINUS -> "-"
            KeyEvent.KEYCODE_EQUALS -> "="
            KeyEvent.KEYCODE_SLASH -> "/"
            KeyEvent.KEYCODE_BACKSLASH -> "\\"
            KeyEvent.KEYCODE_APOSTROPHE -> "'"
            KeyEvent.KEYCODE_GRAVE -> "`"
            KeyEvent.KEYCODE_TAB -> "tab"
            KeyEvent.KEYCODE_ENTER -> "enter"
            KeyEvent.KEYCODE_SPACE -> "space"
            KeyEvent.KEYCODE_DEL -> "backspace"
            KeyEvent.KEYCODE_FORWARD_DEL -> "delete"
            KeyEvent.KEYCODE_DPAD_LEFT -> "left"
            KeyEvent.KEYCODE_DPAD_RIGHT -> "right"
            KeyEvent.KEYCODE_DPAD_UP -> "up"
            KeyEvent.KEYCODE_DPAD_DOWN -> "down"
            else -> null
        }

        /** The key code of a key named as [nameOf] does, or null. */
        fun codeOf(name: String): Int? = when {
            name.length == 1 && name[0] in 'a'..'z' -> KeyEvent.KEYCODE_A + (name[0] - 'a')
            name.length == 1 && name[0] in '0'..'9' -> KeyEvent.KEYCODE_0 + (name[0] - '0')
            name.length in 2..3 && name[0] == 'f' && name.substring(1).toIntOrNull()?.let { it in 1..12 } == true -> KeyEvent.KEYCODE_F1 + name.substring(1).toInt() - 1
            name == "[" -> KeyEvent.KEYCODE_LEFT_BRACKET
            name == "]" -> KeyEvent.KEYCODE_RIGHT_BRACKET
            name == ";" -> KeyEvent.KEYCODE_SEMICOLON
            name == "," -> KeyEvent.KEYCODE_COMMA
            name == "." -> KeyEvent.KEYCODE_PERIOD
            name == "-" -> KeyEvent.KEYCODE_MINUS
            name == "=" -> KeyEvent.KEYCODE_EQUALS
            name == "/" -> KeyEvent.KEYCODE_SLASH
            name == "\\" -> KeyEvent.KEYCODE_BACKSLASH
            name == "'" -> KeyEvent.KEYCODE_APOSTROPHE
            name == "`" -> KeyEvent.KEYCODE_GRAVE
            name == "tab" -> KeyEvent.KEYCODE_TAB
            name == "enter" -> KeyEvent.KEYCODE_ENTER
            name == "space" -> KeyEvent.KEYCODE_SPACE
            name == "delete" -> KeyEvent.KEYCODE_FORWARD_DEL
            name == "backspace" -> KeyEvent.KEYCODE_DEL
            name == "left" -> KeyEvent.KEYCODE_DPAD_LEFT
            name == "right" -> KeyEvent.KEYCODE_DPAD_RIGHT
            name == "up" -> KeyEvent.KEYCODE_DPAD_UP
            name == "down" -> KeyEvent.KEYCODE_DPAD_DOWN
            else -> null
        }
    }
}
