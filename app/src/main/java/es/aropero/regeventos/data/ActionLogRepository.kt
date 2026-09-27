package es.aropero.regeventos.data

import android.content.Context
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ActionLogRepository {
    private const val LOG_DIR_NAME = "regeventos_logs"
    private const val LEGACY_FILE_NAME = "regeventos_log.csv"
    private const val DELIMITER = ';'
    private const val HEADER_SIMPLE = "label;timestamp"
    private const val HEADER_WITH_DESCRIPTION = "label;timestamp;description"
    private const val LEGACY_HEADER_SIMPLE = "label,timestamp"
    private const val LEGACY_HEADER_WITH_DESCRIPTION = "label,timestamp,description"
    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    data class ActionLogEntry(val label: String, val timestamp: String, val description: String = "")

    fun createTimestamp(): String = LocalDateTime.now().format(timeFormatter)

    fun createEndOfDayTimestamp(date: LocalDate = LocalDate.now()): String =
        date.atTime(23, 59, 59).format(timeFormatter)

    private fun sanitizeField(value: String): String =
        value
            .replace("\n", " ")
            .replace(DELIMITER.toString(), " ")
            .replace(",", " ")

    fun appendLog(
        context: Context,
        label: String,
        timestamp: String = createTimestamp(),
        description: String = ""
    ) {
        val file = dailyLogFile(context)
        if (!file.exists()) file.writeText("$HEADER_WITH_DESCRIPTION\n")
        val safeLabel = sanitizeField(label)
        val safeDescription = sanitizeField(description)
        file.appendText(listOf(safeLabel, timestamp, safeDescription).joinToString(DELIMITER.toString()) + "\n")
    }

    fun updateLogEntry(
        context: Context,
        original: ActionLogEntry,
        newLabel: String,
        newTimestamp: String,
        newDescription: String
    ): Boolean {
        val date = runCatching { LocalDate.parse(original.timestamp.take(10), dateFormatter) }
            .getOrNull()
            ?: return false
        val file = dailyLogFile(context, date)
        if (!file.exists()) return false

        // Prevent cross-day edits: only allow timestamp updates within the same date
        val newDate = runCatching { LocalDate.parse(newTimestamp.take(10), dateFormatter) }
            .getOrNull()
            ?: return false
        if (newDate != date) return false

        val lines = file.readLines().toMutableList()
        val header = lines.firstOrNull()
        val startIndex =
            if (header in setOf(HEADER_SIMPLE, HEADER_WITH_DESCRIPTION, LEGACY_HEADER_SIMPLE, LEGACY_HEADER_WITH_DESCRIPTION)) 1 else 0

        for (i in startIndex until lines.size) {
            val (lineLabel, lineTimestamp, lineDescription) = parseLine(lines[i]) ?: continue

            if (lineLabel == original.label && lineTimestamp == original.timestamp) {
                val safeLabel = sanitizeField(newLabel)
                val safeDescription = sanitizeField(newDescription)
                lines[i] = listOf(safeLabel, newTimestamp, safeDescription).joinToString(DELIMITER.toString())
                file.writeText(lines.joinToString("\n", postfix = "\n"))
                return true
            }
        }

        return false
    }

    fun deleteLogEntry(
        context: Context,
        entry: ActionLogEntry
    ): Boolean {
        val date = runCatching { LocalDate.parse(entry.timestamp.take(10), dateFormatter) }
            .getOrNull()
            ?: return false
        val file = dailyLogFile(context, date)
        if (!file.exists()) return false

        val lines = file.readLines().toMutableList()
        val header = lines.firstOrNull()
        val startIndex =
            if (header in setOf(HEADER_SIMPLE, HEADER_WITH_DESCRIPTION, LEGACY_HEADER_SIMPLE, LEGACY_HEADER_WITH_DESCRIPTION)) 1 else 0

        for (i in startIndex until lines.size) {
            val (lineLabel, lineTimestamp, lineDescription) = parseLine(lines[i]) ?: continue

            if (
                lineLabel == entry.label &&
                lineTimestamp == entry.timestamp &&
                lineDescription == entry.description
            ) {
                lines.removeAt(i)
                file.writeText(lines.joinToString("\n", postfix = "\n"))
                return true
            }
        }

        return false
    }

    fun createExportArchive(context: Context): File? {
        val dailyLogs = getAvailableLogDates(context)
            .map { date -> dailyLogFile(context, date) }
            .filter { file -> file.exists() }
        val legacyFile = File(context.filesDir, LEGACY_FILE_NAME).takeIf { it.exists() }

        val filesToZip = buildList {
            addAll(dailyLogs.map { file -> file to "${LOG_DIR_NAME}/${file.name}" })
            legacyFile?.let { file -> add(file to file.name) }
        }

        if (filesToZip.isEmpty()) return null

        val exportDir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
        val zipFile = File(exportDir, "regeventos_logs_export_${System.currentTimeMillis()}.zip")

        ZipOutputStream(zipFile.outputStream()).use { output ->
            filesToZip.forEach { (file, entryName) -> addFileToZip(file, entryName, output) }
        }

        return zipFile
    }

    fun readLogs(context: Context): List<ActionLogEntry> = readLogsForDate(context, null)

    fun readLogsForDate(context: Context, date: LocalDate?): List<ActionLogEntry> {
        val entries = mutableListOf<ActionLogEntry>()

        if (date != null) {
            val targetFile = dailyLogFile(context, date)
            if (targetFile.exists()) entries += parseLogFile(targetFile)
        } else {
            // Load all daily log files
            logDir(context).listFiles { file -> file.isFile && file.extension == "csv" }
                ?.sortedByDescending { it.nameWithoutExtension }
                ?.forEach { file -> entries += parseLogFile(file) }

            // Backward compatibility: load legacy single file if it exists
            val legacyFile = File(context.filesDir, LEGACY_FILE_NAME)
            if (legacyFile.exists()) entries += parseLogFile(legacyFile)
        }

        return entries
            .filter { it.label.isNotBlank() && it.timestamp.isNotBlank() }
            .sortedByDescending { entry ->
                runCatching { LocalDateTime.parse(entry.timestamp, timeFormatter) }
                    .getOrDefault(LocalDateTime.MIN)
            }
    }

    fun getAvailableLogDates(context: Context): List<LocalDate> =
        logDir(context).listFiles { file -> file.isFile && file.extension == "csv" }
            ?.mapNotNull { file ->
                runCatching { LocalDate.parse(file.nameWithoutExtension, dateFormatter) }
                    .getOrNull()
            }
            ?.sortedDescending()
            ?: emptyList()

    private fun parseLogFile(file: File): List<ActionLogEntry> =
        file.readLines()
            .dropWhile { it.isBlank() }
            .run {
                when (firstOrNull()) {
                    HEADER_SIMPLE, HEADER_WITH_DESCRIPTION, LEGACY_HEADER_SIMPLE, LEGACY_HEADER_WITH_DESCRIPTION -> drop(1)
                    else -> this
                }
            }
            .mapNotNull { line ->
                parseLine(line)?.let { (label, timestamp, description) ->
                    ActionLogEntry(label, timestamp, description)
                }
            }

    private fun parseLine(line: String): Triple<String, String, String>? {
        val delimiter = when {
            line.contains(DELIMITER) -> DELIMITER
            line.contains(',') -> ','
            else -> return null
        }

        val parts = line.split(delimiter, limit = 3)
        if (parts.size < 2) return null

        val description = if (parts.size == 3) parts[2].trim() else ""
        val label = parts[0].trim()
        val timestamp = parts[1].trim()

        if (label.isBlank() || timestamp.isBlank()) return null

        return Triple(label, timestamp, description)
    }

    private fun dailyLogFile(context: Context, date: LocalDate = LocalDate.now()): File {
        val dir = logDir(context)
        return File(dir, "${date.format(dateFormatter)}.csv")
    }

    private fun logDir(context: Context): File = File(context.filesDir, LOG_DIR_NAME).apply {
        if (!exists()) mkdirs()
    }

    fun clearAllLogs(context: Context): Boolean {
        val legacyFile = File(context.filesDir, LEGACY_FILE_NAME)
        val dailyDir = logDir(context)

        val dailyDeleted = dailyDir.listFiles().orEmpty().all { file ->
            !file.exists() || file.delete()
        }
        val legacyDeleted = !legacyFile.exists() || legacyFile.delete()

        return dailyDeleted && legacyDeleted && (dailyDir.listFiles()?.isEmpty() ?: true) && !legacyFile.exists()
    }

    private fun addFileToZip(file: File, entryName: String, zipOutputStream: ZipOutputStream) {
        zipOutputStream.putNextEntry(ZipEntry(entryName))
        file.inputStream().use { input -> input.copyTo(zipOutputStream) }
        zipOutputStream.closeEntry()
    }
}
