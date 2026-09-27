package es.aropero.regeventos.data

import android.content.Context
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object DailyTextFieldRepository {
    private const val PREFS_NAME = "regeventos_config"
    private const val KEY_VALUE_PREFIX = "text_field_value_"
    private const val KEY_LAST_UPDATED_DATE = "text_fields_last_updated"
    private const val KEY_LAST_LOGGED_DATE = "text_fields_last_logged"
    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveValue(context: Context, label: String, value: String) {
        val today = LocalDate.now().format(dateFormatter)
        prefs(context).edit()
            .putString("$KEY_VALUE_PREFIX$label", value)
            .putString(KEY_LAST_UPDATED_DATE, today)
            .apply()
    }

    fun getValues(context: Context, labels: Collection<String>): Map<String, String> {
        val preferences = prefs(context)
        return labels.associateWith { label ->
            preferences.getString("$KEY_VALUE_PREFIX$label", "") ?: ""
        }
    }

    fun clearValues(context: Context, labels: Collection<String>) {
        val editor = prefs(context).edit()
        labels.forEach { label -> editor.remove("$KEY_VALUE_PREFIX$label") }
        editor.apply()
    }

    fun clearIfOutdated(context: Context, labels: Collection<String>): Boolean {
        val lastUpdated = getDate(context, KEY_LAST_UPDATED_DATE) ?: return false
        if (lastUpdated.isBefore(LocalDate.now())) {
            clearValues(context, labels)
            setDate(context, KEY_LAST_UPDATED_DATE, LocalDate.now())
            return true
        }

        return false
    }

    private fun getDate(context: Context, key: String): LocalDate? =
        prefs(context).getString(key, null)?.let { stored ->
            runCatching { LocalDate.parse(stored, dateFormatter) }.getOrNull()
        }

    private fun setDate(context: Context, key: String, date: LocalDate) {
        prefs(context).edit().putString(key, date.format(dateFormatter)).apply()
    }

    fun markLogged(context: Context, date: LocalDate) {
        setDate(context, KEY_LAST_LOGGED_DATE, date)
    }

    fun getPendingLogDate(context: Context): LocalDate? {
        val lastUpdated = getDate(context, KEY_LAST_UPDATED_DATE) ?: return null
        val lastLogged = getDate(context, KEY_LAST_LOGGED_DATE)
        return if (lastUpdated.isBefore(LocalDate.now()) && lastLogged != lastUpdated) {
            lastUpdated
        } else {
            null
        }
    }
}
