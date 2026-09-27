package es.aropero.regeventos.data

import android.content.Context
import android.content.SharedPreferences

object ActionConfigRepository {
    private const val PREFS_NAME = "regeventos_config"
    private const val KEY_ACTIONS = "regeventos"
    private const val KEY_ACTIONS_ORDERED = "regeventos_ordered"
    private const val KEY_TEXT_FIELDS = "text_fields"
    private const val KEY_TEXT_FIELDS_ORDERED = "text_fields_ordered"
    private const val WIDGET_PREFIX = "widget_label_"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_WIDGET_REBOOT_NOTICE_DISMISSED = "widget_reboot_notice_dismissed"
    private const val KEY_APP_LANGUAGE = "app_language"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun loadOrderedActions(context: Context): MutableList<String> {
        val stored = prefs(context).getString(KEY_ACTIONS_ORDERED, null) ?: return mutableListOf()
        val parsed = runCatching {
            val array = org.json.JSONArray(stored)
            MutableList(array.length()) { index -> array.optString(index).trim() }
        }.getOrElse { mutableListOf() }

        return parsed.filter { it.isNotBlank() }.distinct().toMutableList()
    }

    private fun saveOrderedActions(context: Context, actions: List<String>) {
        val normalized = actions.map { it.trim() }.filter { it.isNotBlank() }
        val array = org.json.JSONArray()
        normalized.forEach { array.put(it) }
        prefs(context).edit()
            .putString(KEY_ACTIONS_ORDERED, array.toString())
            .putStringSet(KEY_ACTIONS, normalized.toSet())
            .apply()
    }

    fun getActions(context: Context): List<String> =
        loadOrderedActions(context).ifEmpty {
            prefs(context).getStringSet(KEY_ACTIONS, emptySet())
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.sorted()
                ?: emptyList()
        }

    fun getTextFields(context: Context): List<String> =
        loadOrderedTextFields(context).ifEmpty {
            prefs(context).getStringSet(KEY_TEXT_FIELDS, emptySet())
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.sorted()
                ?: emptyList()
        }

    fun addAction(context: Context, label: String) {
        val normalized = label.trim()
        if (normalized.isBlank()) return

        val current = loadOrderedActions(context).ifEmpty { getActions(context).toMutableList() }
        if (current.contains(normalized)) return
        current.add(normalized)
        saveOrderedActions(context, current)
    }

    fun addTextField(context: Context, label: String) {
        val normalized = label.trim()
        if (normalized.isBlank()) return

        val current = loadOrderedTextFields(context).ifEmpty { getTextFields(context).toMutableList() }
        if (current.contains(normalized)) return
        current.add(normalized)
        saveOrderedTextFields(context, current)
    }

    fun removeAction(context: Context, label: String) {
        val normalized = label.trim()
        if (normalized.isBlank()) return

        val current = loadOrderedActions(context).ifEmpty { getActions(context).toMutableList() }
        current.removeAll { it == normalized }
        saveOrderedActions(context, current)
    }

    fun moveAction(context: Context, label: String, direction: Int): List<String> {
        val normalized = label.trim()
        if (normalized.isBlank() || direction == 0) return getActions(context)

        val current = loadOrderedActions(context).ifEmpty { getActions(context).toMutableList() }
        val index = current.indexOf(normalized)
        if (index == -1) return getActions(context)

        val newIndex = (index + direction).coerceIn(0, current.lastIndex)
        if (newIndex == index) return current

        current.removeAt(index)
        current.add(newIndex, normalized)
        saveOrderedActions(context, current)
        return current
    }

    fun removeTextField(context: Context, label: String) {
        val normalized = label.trim()
        if (normalized.isBlank()) return

        val current = loadOrderedTextFields(context).ifEmpty { getTextFields(context).toMutableList() }
        current.removeAll { it == normalized }
        saveOrderedTextFields(context, current)
    }

    fun moveTextField(context: Context, label: String, direction: Int): List<String> {
        val normalized = label.trim()
        if (normalized.isBlank() || direction == 0) return getTextFields(context)

        val current = loadOrderedTextFields(context).ifEmpty { getTextFields(context).toMutableList() }
        val index = current.indexOf(normalized)
        if (index == -1) return getTextFields(context)

        val newIndex = (index + direction).coerceIn(0, current.lastIndex)
        if (newIndex == index) return current

        current.removeAt(index)
        current.add(newIndex, normalized)
        saveOrderedTextFields(context, current)
        return current
    }

    private fun loadOrderedTextFields(context: Context): MutableList<String> {
        val stored = prefs(context).getString(KEY_TEXT_FIELDS_ORDERED, null) ?: return mutableListOf()
        val parsed = runCatching {
            val array = org.json.JSONArray(stored)
            MutableList(array.length()) { index -> array.optString(index).trim() }
        }.getOrElse { mutableListOf() }

        return parsed.filter { it.isNotBlank() }.distinct().toMutableList()
    }

    private fun saveOrderedTextFields(context: Context, labels: List<String>) {
        val normalized = labels.map { it.trim() }.filter { it.isNotBlank() }
        val array = org.json.JSONArray()
        normalized.forEach { array.put(it) }
        prefs(context).edit()
            .putString(KEY_TEXT_FIELDS_ORDERED, array.toString())
            .putStringSet(KEY_TEXT_FIELDS, normalized.toSet())
            .apply()
    }

    fun saveWidgetLabel(context: Context, widgetId: Int, label: String) {
        prefs(context).edit().putString("$WIDGET_PREFIX$widgetId", label).apply()
    }

    fun getWidgetLabel(context: Context, widgetId: Int): String? =
        prefs(context).getString("$WIDGET_PREFIX$widgetId", null)


    fun removeWidgetLabel(context: Context, widgetId: Int) {
        prefs(context).edit().remove("$WIDGET_PREFIX$widgetId").apply()
    }

    fun getThemeMode(context: Context): ThemeMode {
        val stored = prefs(context).getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
        return runCatching { ThemeMode.valueOf(stored ?: ThemeMode.SYSTEM.name) }.getOrDefault(ThemeMode.SYSTEM)
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        prefs(context).edit().putString(KEY_THEME_MODE, mode.name).apply()
    }



    fun getAppLanguage(context: Context): AppLanguage {
        val stored = prefs(context).getString(KEY_APP_LANGUAGE, AppLanguage.SYSTEM.name)
        return runCatching { AppLanguage.valueOf(stored ?: AppLanguage.SYSTEM.name) }
            .getOrDefault(AppLanguage.SYSTEM)
    }

    fun setAppLanguage(context: Context, language: AppLanguage) {
        prefs(context).edit().putString(KEY_APP_LANGUAGE, language.name).apply()
    }

    fun isWidgetRebootNoticeDismissed(context: Context): Boolean =
        prefs(context).getBoolean(KEY_WIDGET_REBOOT_NOTICE_DISMISSED, false)

    fun setWidgetRebootNoticeDismissed(context: Context, dismissed: Boolean) {
        prefs(context).edit().putBoolean(KEY_WIDGET_REBOOT_NOTICE_DISMISSED, dismissed).apply()
    }
}
