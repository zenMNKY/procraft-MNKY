package io.github.artcraftandroid.procraft.canvas

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * All the app's settings (the gesture controls and every other preference) as one text to keep in
 * a file and take back in. The hotkeys are not in it: they have a file of their own
 * ([Hotkeys.exportJson]).
 */
object SettingsFile {
    /** The preference files, apart from the gestures' (those go through [GestureSettings]) and the hotkeys'. */
    private val Files = listOf("interface", "transform", "brushes", "canvas_presets")

    fun export(context: Context, gestures: GestureSettings): String {
        val prefs = JSONObject()
        for (file in Files) {
            val values = JSONObject()
            for ((key, value) in context.getSharedPreferences(file, Context.MODE_PRIVATE).all) {
                val entry = JSONObject()
                when (value) {
                    is Boolean -> entry.put("t", "bool").put("v", value)
                    is Int -> entry.put("t", "int").put("v", value)
                    is Long -> entry.put("t", "long").put("v", value)
                    is Float -> entry.put("t", "float").put("v", value.toDouble())
                    is String -> entry.put("t", "string").put("v", value)
                    is Set<*> -> entry.put("t", "set").put("v", JSONArray(value.filterIsInstance<String>()))
                    else -> continue
                }
                values.put(key, entry)
            }
            prefs.put(file, values)
        }
        return JSONObject()
            .put("app", "procraft-settings")
            .put("version", 1)
            .put("gestures", JSONObject(gestures.exportJson()))
            .put("preferences", prefs)
            .toString(2)
    }

    /**
     * Takes in what [export] wrote: false if [text] is not that. The gesture controls change at
     * once; the other settings are read when the app starts, so they show after it is started again.
     */
    fun import(context: Context, gestures: GestureSettings, text: String): Boolean {
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            return false
        }
        if (root.optString("app") != "procraft-settings") return false
        root.optJSONObject("gestures")?.let { gestures.importJson(it.toString()) }
        val prefs = root.optJSONObject("preferences") ?: return true
        for (file in Files) {
            val values = prefs.optJSONObject(file) ?: continue
            val edit = context.getSharedPreferences(file, Context.MODE_PRIVATE).edit()
            for (key in values.keys()) {
                val entry = values.optJSONObject(key) ?: continue
                when (entry.optString("t")) {
                    "bool" -> edit.putBoolean(key, entry.optBoolean("v"))
                    "int" -> edit.putInt(key, entry.optInt("v"))
                    "long" -> edit.putLong(key, entry.optLong("v"))
                    "float" -> edit.putFloat(key, entry.optDouble("v").toFloat())
                    "string" -> edit.putString(key, entry.optString("v"))
                    "set" -> {
                        val array = entry.optJSONArray("v") ?: continue
                        edit.putStringSet(key, (0 until array.length()).map { array.optString(it) }.toSet())
                    }
                }
            }
            edit.apply()
        }
        return true
    }
}
