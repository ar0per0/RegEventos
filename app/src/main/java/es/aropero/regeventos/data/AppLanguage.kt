package es.aropero.regeventos.data

import androidx.core.os.LocaleListCompat

enum class AppLanguage {
    SYSTEM,
    SPANISH,
    ENGLISH;

    fun toLocaleListCompat(): LocaleListCompat = when (this) {
        SYSTEM -> LocaleListCompat.getEmptyLocaleList()
        SPANISH -> LocaleListCompat.forLanguageTags("es")
        ENGLISH -> LocaleListCompat.forLanguageTags("en")
    }
}
