package es.aropero.regeventos

import android.content.Intent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.ActivityNotFoundException
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Checkbox
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import android.widget.Toast
import es.aropero.regeventos.data.ActionConfigRepository
import es.aropero.regeventos.data.AppLanguage
import es.aropero.regeventos.data.ActionLogRepository
import es.aropero.regeventos.data.ActionLogRepository.ActionLogEntry
import es.aropero.regeventos.data.DailyTextFieldRepository
import es.aropero.regeventos.data.ThemeMode
import es.aropero.regeventos.ui.theme.RegEventosTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.delay
import java.time.LocalDate
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatActivity
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setApplicationLocales(
            ActionConfigRepository.getAppLanguage(this).toLocaleListCompat()
        )
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeModeState = remember { mutableStateOf(ActionConfigRepository.getThemeMode(this)) }
            val appLanguageState = remember { mutableStateOf(ActionConfigRepository.getAppLanguage(this)) }
            val isDarkTheme = when (themeModeState.value) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }

            RegEventosTheme(darkTheme = isDarkTheme) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ActionTracker(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(16.dp),
                        themeMode = themeModeState.value,
                        appLanguage = appLanguageState.value,
                        onThemeChange = { mode ->
                            themeModeState.value = mode
                            ActionConfigRepository.setThemeMode(this, mode)
                            val manager = AppWidgetManager.getInstance(this)
                            val widgetIds = manager.getAppWidgetIds(
                                ComponentName(this, ActionWidgetProvider::class.java)
                            )
                            widgetIds.forEach { id ->
                                ActionWidgetProvider.updateAppWidget(this, manager, id)
                            }
                        },
                        onLanguageChange = { language ->
                            val previousLanguage = appLanguageState.value
                            appLanguageState.value = language
                            ActionConfigRepository.setAppLanguage(this, language)
                            AppCompatDelegate.setApplicationLocales(language.toLocaleListCompat())
                            if (previousLanguage != language) {
                                recreate()
                            }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionTracker(
    modifier: Modifier = Modifier,
    themeMode: ThemeMode,
    appLanguage: AppLanguage,
    onThemeChange: (ThemeMode) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit
) {
    val context = LocalContext.current
    val isDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val actions = remember { mutableStateListOf<String>() }
    val textFields = remember { mutableStateListOf<String>() }
    val textFieldValues = remember { mutableStateMapOf<String, String>() }
    val logs = remember { mutableStateListOf<ActionLogEntry>() }
    val availableDates = remember { mutableStateListOf<LocalDate>() }
    val selectedDate = remember { mutableStateOf<LocalDate?>(LocalDate.now()) }
    val selectedLog = remember { mutableStateOf<ActionLogEntry?>(null) }
    val editLabel = remember { mutableStateOf("") }
    val editDescription = remember { mutableStateOf("") }
    val editTime = remember { mutableStateOf("") }
    val newAction = remember { mutableStateOf("") }
    val newTextField = remember { mutableStateOf("") }
    val showSettings = remember { mutableStateOf(false) }
    val showWidgetNotice = remember { mutableStateOf(!ActionConfigRepository.isWidgetRebootNoticeDismissed(context)) }
    val showDeleteConfirmation = remember { mutableStateOf(false) }
    val logListState = rememberLazyListState()
    val pullDownAccumulated = remember { mutableFloatStateOf(0f) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm:ss") }
    val timestampFormatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss") }
    val dateMenuExpanded = remember { mutableStateOf(false) }
    val themeMenuExpanded = remember { mutableStateOf(false) }
    val languageMenuExpanded = remember { mutableStateOf(false) }
    val pressedButtonId = remember { mutableStateOf<String?>(null) }

    fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun themeLabel(mode: ThemeMode): String = when (mode) {
        ThemeMode.SYSTEM -> context.getString(R.string.theme_system)
        ThemeMode.LIGHT -> context.getString(R.string.theme_light)
        ThemeMode.DARK -> context.getString(R.string.theme_dark)
    }

    fun languageLabel(language: AppLanguage): String = when (language) {
        AppLanguage.SYSTEM -> context.getString(R.string.language_system)
        AppLanguage.SPANISH -> context.getString(R.string.language_spanish)
        AppLanguage.ENGLISH -> context.getString(R.string.language_english)
    }


    fun refreshAvailableDates() {
        availableDates.clear()
        availableDates.addAll(ActionLogRepository.getAvailableLogDates(context))
        if (!availableDates.contains(LocalDate.now())) {
            availableDates.add(LocalDate.now())
        }
        availableDates.sortByDescending { it }
    }

    fun clearSelection() {
        selectedLog.value = null
        editLabel.value = ""
        editDescription.value = ""
        editTime.value = ""
    }

    fun reloadLogsForSelection() {
        logs.clear()
        logs.addAll(ActionLogRepository.readLogsForDate(context, selectedDate.value))
    }

    fun appendLogAndRefresh(label: String, timestamp: String = ActionLogRepository.createTimestamp()) {
        ActionLogRepository.appendLog(context, label, timestamp)
        val today = LocalDate.now()
        if (!availableDates.contains(today)) {
            refreshAvailableDates()
        }
        reloadLogsForSelection()
    }

    val pullRefreshThreshold = 120f
    val pullDownRefreshConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source != NestedScrollSource.UserInput) {
                    pullDownAccumulated.floatValue = 0f
                    return Offset.Zero
                }

                if (logListState.firstVisibleItemIndex == 0 &&
                    logListState.firstVisibleItemScrollOffset == 0 &&
                    available.y > 0f
                ) {
                    pullDownAccumulated.floatValue += available.y
                    if (pullDownAccumulated.floatValue >= pullRefreshThreshold) {
                        reloadLogsForSelection()
                        pullDownAccumulated.floatValue = 0f
                    }
                    return Offset(0f, available.y)
                }

                pullDownAccumulated.floatValue = 0f
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                pullDownAccumulated.floatValue = 0f
                return Velocity.Zero
            }
        }
    }

    fun logTextField(label: String) {
        val value = (textFieldValues[label] ?: "").trim()
        if (value.isEmpty()) {
            showToast(context.getString(R.string.error_complete_field_before_logging))
            return
        }

        val entryLabel = "$label: $value"
        DailyTextFieldRepository.markLogged(context, LocalDate.now())
        appendLogAndRefresh(entryLabel)
    }

    fun markButtonPressed(buttonId: String) {
        pressedButtonId.value = buttonId
    }

    @Composable
    fun feedbackButtonColors(isPressed: Boolean) = if (isPressed) {
        val containerColor = ContextCompat.getColor(
            context,
            if (isDarkTheme) {
                R.color.widget_button_background_feedback_dark
            } else {
                R.color.widget_button_background_feedback_light
            }
        )
        val textColor = ContextCompat.getColor(context, R.color.widget_button_feedback_text)
        ButtonDefaults.buttonColors(
            containerColor = Color(containerColor),
            contentColor = Color(textColor)
        )
    } else {
        ButtonDefaults.buttonColors()
    }

    LaunchedEffect(pressedButtonId.value) {
        if (pressedButtonId.value != null) {
            delay(1600)
            pressedButtonId.value = null
        }
    }


    LaunchedEffect(context) {
        actions.clear()
        actions.addAll(ActionConfigRepository.getActions(context))

        textFields.clear()
        textFields.addAll(ActionConfigRepository.getTextFields(context))
        textFieldValues.clear()
        textFieldValues.putAll(DailyTextFieldRepository.getValues(context, textFields))

        if (DailyTextFieldRepository.clearIfOutdated(context, textFields)) {
            textFields.forEach { label -> textFieldValues[label] = "" }
        }

        refreshAvailableDates()

        reloadLogsForSelection()

    }

    LaunchedEffect(logListState) {
        snapshotFlow {
            logListState.firstVisibleItemIndex == 0 &&
                logListState.firstVisibleItemScrollOffset == 0 &&
                logListState.isScrollInProgress
        }
            .distinctUntilChanged()
            .filter { it }
            .collect {
                reloadLogsForSelection()
            }
    }

    LaunchedEffect(selectedDate.value) {
        if (logListState.firstVisibleItemIndex != 0 || logListState.firstVisibleItemScrollOffset != 0) {
            logListState.scrollToItem(0)
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.verticalScroll(rememberScrollState())
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = { showSettings.value = !showSettings.value }) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.cd_settings)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = if (showSettings.value) stringResource(R.string.hide_settings) else stringResource(R.string.settings))
            }
        }

        if (showSettings.value) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (showWidgetNotice.value) {
                    val doNotShowAgain = remember { mutableStateOf(false) }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.widget_reboot_notice),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Checkbox(
                                    checked = doNotShowAgain.value,
                                    onCheckedChange = { checked ->
                                        doNotShowAgain.value = checked
                                        if (checked) {
                                            ActionConfigRepository.setWidgetRebootNoticeDismissed(context, true)
                                            showWidgetNotice.value = false
                                        }
                                    }
                                )
                                Text(text = stringResource(R.string.do_not_show_again))
                            }
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.theme_preference_label))
                    Box {
                        OutlinedButton(onClick = { themeMenuExpanded.value = true }) {
                            Text(text = themeLabel(themeMode))
                        }
                        DropdownMenu(
                            expanded = themeMenuExpanded.value,
                            onDismissRequest = { themeMenuExpanded.value = false }
                        ) {
                            ThemeMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(text = themeLabel(mode)) },
                                    onClick = {
                                        onThemeChange(mode)
                                        themeMenuExpanded.value = false
                                    }
                                )
                            }
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.language_preference_label))
                    Box {
                        OutlinedButton(onClick = { languageMenuExpanded.value = true }) {
                            Text(text = languageLabel(appLanguage))
                        }
                        DropdownMenu(
                            expanded = languageMenuExpanded.value,
                            onDismissRequest = { languageMenuExpanded.value = false }
                        ) {
                            AppLanguage.entries.forEach { language ->
                                DropdownMenuItem(
                                    text = { Text(text = languageLabel(language)) },
                                    onClick = {
                                        onLanguageChange(language)
                                        languageMenuExpanded.value = false
                                    }
                                )
                            }
                        }
                    }
                }

                if (actions.isEmpty()) {
                    Text(text = stringResource(R.string.empty_actions_hint))
                }
                Text(text = stringResource(R.string.button_settings_title))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = newAction.value,
                        onValueChange = { newAction.value = it },
                        label = { Text(stringResource(R.string.button_text_label)) },
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = {
                        val label = newAction.value.trim()
                        if (label.isNotEmpty()) {
                            ActionConfigRepository.addAction(context, label)
                            if (!actions.contains(label)) {
                                actions.add(label)
                            }
                            newAction.value = ""
                        }
                    }) {
                        Text(text = stringResource(R.string.create))
                    }
                }

                Text(text = stringResource(R.string.text_fields_settings_title))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = newTextField.value,
                        onValueChange = { newTextField.value = it },
                        label = { Text(stringResource(R.string.field_name_label)) },
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = {
                        val label = newTextField.value.trim()
                        if (label.isNotEmpty()) {
                            ActionConfigRepository.addTextField(context, label)
                            if (!textFields.contains(label)) {
                                textFields.add(label)
                            }
                            if (!textFieldValues.containsKey(label)) {
                                textFieldValues[label] = ""
                            }
                            newTextField.value = ""
                        }
                    }) {
                        Text(text = stringResource(R.string.create))
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = {
                        val zipFile = ActionLogRepository.createExportArchive(context)
                        if (zipFile == null) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.no_logs_to_export),
                                Toast.LENGTH_SHORT
                            ).show()
                            return@Button
                        }

                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            zipFile
                        )

                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/zip"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }

                        runCatching {
                            context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.export_logs)))
                        }.onFailure {
                            val message = if (it is ActivityNotFoundException) {
                                context.getString(R.string.error_no_share_app)
                            } else {
                                context.getString(R.string.error_open_export_menu)
                            }
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Text(text = stringResource(R.string.export_logs))
                    }

                    Button(
                        onClick = { showDeleteConfirmation.value = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text(text = stringResource(R.string.delete_logs))
                    }
                }

                if (showDeleteConfirmation.value) {
                    AlertDialog(
                        onDismissRequest = { showDeleteConfirmation.value = false },
                        title = { Text(text = stringResource(R.string.delete_all_logs_title)) },
                        text = { Text(text = stringResource(R.string.delete_all_logs_message)) },
                        confirmButton = {
                            TextButton(onClick = {
                                val cleared = ActionLogRepository.clearAllLogs(context)
                                reloadLogsForSelection()
                                refreshAvailableDates()
                                clearSelection()

                                val message = if (cleared) {
                                    context.getString(R.string.logs_deleted_success)
                                } else {
                                    context.getString(R.string.logs_deleted_error)
                                }
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                showDeleteConfirmation.value = false
                            }) {
                                Text(text = stringResource(R.string.yes_delete))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteConfirmation.value = false }) {
                                Text(text = stringResource(R.string.cancel))
                            }
                        }
                    )
                }

                if (actions.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = stringResource(R.string.current_buttons_title))
                        actions.forEachIndexed { index, label ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = label, modifier = Modifier.weight(1f))
                                IconButton(
                                    onClick = {
                                        val updated = ActionConfigRepository.moveAction(context, label, -1)
                                        actions.clear()
                                        actions.addAll(updated)
                                    },
                                    enabled = index > 0
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.KeyboardArrowUp,
                                        contentDescription = stringResource(R.string.cd_move_up)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val updated = ActionConfigRepository.moveAction(context, label, 1)
                                        actions.clear()
                                        actions.addAll(updated)
                                    },
                                    enabled = index < actions.lastIndex
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.KeyboardArrowDown,
                                        contentDescription = stringResource(R.string.cd_move_down)
                                    )
                                }
                                OutlinedButton(onClick = {
                                    ActionConfigRepository.removeAction(context, label)
                                    actions.remove(label)
                                }) {
                                    Text(text = stringResource(R.string.delete))
                                }
                            }
                        }
                    }
                }

                if (textFields.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = stringResource(R.string.current_text_fields_title))
                        textFields.forEachIndexed { index, label ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = label, modifier = Modifier.weight(1f))
                                IconButton(
                                    onClick = {
                                        val updated = ActionConfigRepository.moveTextField(context, label, -1)
                                        textFields.clear()
                                        textFields.addAll(updated)
                                    },
                                    enabled = index > 0
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.KeyboardArrowUp,
                                        contentDescription = stringResource(R.string.cd_move_up)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val updated = ActionConfigRepository.moveTextField(context, label, 1)
                                        textFields.clear()
                                        textFields.addAll(updated)
                                    },
                                    enabled = index < textFields.lastIndex
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.KeyboardArrowDown,
                                        contentDescription = stringResource(R.string.cd_move_down)
                                    )
                                }
                                OutlinedButton(onClick = {
                                    ActionConfigRepository.removeTextField(context, label)
                                    textFields.remove(label)
                                    textFieldValues.remove(label)
                                    DailyTextFieldRepository.clearValues(context, listOf(label))
                                }) {
                                    Text(text = stringResource(R.string.delete))
                                }
                            }
                        }
                    }
                }
            }
        }

        if (actions.isNotEmpty()) {
            Text(text = stringResource(R.string.quick_actions_title))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                actions.chunked(2).forEach { pair ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        pair.forEach { label ->
                                Button(
                                    modifier = Modifier.weight(1f),
                                    colors = feedbackButtonColors(pressedButtonId.value == "action:$label"),
                                    onClick = {
                                        markButtonPressed("action:$label")
                                        appendLogAndRefresh(label)
                                    }
                                ) {
                                    Text(text = label)
                            }
                        }
                        if (pair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        if (textFields.isNotEmpty()) {
            Text(text = stringResource(R.string.text_fields_title))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                textFields.forEach { label ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = textFieldValues[label] ?: "",
                            onValueChange = { newValue ->
                                textFieldValues[label] = newValue
                                DailyTextFieldRepository.saveValue(context, label, newValue)
                            },
                            label = { Text(label) },
                            modifier = Modifier.weight(1f)
                        )

                        Button(
                            colors = feedbackButtonColors(pressedButtonId.value == "text:$label"),
                            onClick = {
                                markButtonPressed("text:$label")
                                logTextField(label)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = stringResource(R.string.cd_log)
                            )
                        }
                    }
                }
            }
        }

        val headerText = buildString {
            append(context.getString(R.string.event_log_title))
            if (selectedDate.value != null) {
                append(" (")
                append(logs.size)
                append(")")
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = headerText, modifier = Modifier.weight(1f))
            Box {
                OutlinedButton(onClick = { dateMenuExpanded.value = true }) {
                    Text(
                        text = selectedDate.value?.format(dateFormatter) ?: stringResource(R.string.all)
                    )
                }
                DropdownMenu(
                    expanded = dateMenuExpanded.value,
                    onDismissRequest = { dateMenuExpanded.value = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.all)) },
                        onClick = {
                            selectedDate.value = null
                            dateMenuExpanded.value = false
                            reloadLogsForSelection()
                        }
                    )
                    availableDates.forEach { date ->
                        DropdownMenuItem(
                            text = { Text(date.format(dateFormatter)) },
                            onClick = {
                                selectedDate.value = date
                                dateMenuExpanded.value = false
                                reloadLogsForSelection()
                            }
                        )
                    }
                }
            }
        }

        if (selectedLog.value != null) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = stringResource(R.string.edit_selected_event_title))
                OutlinedTextField(
                    value = editLabel.value,
                    onValueChange = { editLabel.value = it },
                    label = { Text(text = stringResource(R.string.text)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = editTime.value,
                    onValueChange = { editTime.value = it },
                    label = { Text(text = stringResource(R.string.time_format_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = editDescription.value,
                    onValueChange = { editDescription.value = it },
                    label = { Text(text = stringResource(R.string.description)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val original = selectedLog.value ?: return@Button
                        val updatedLabel = editLabel.value.trim()
                        val updatedDescription = editDescription.value.trim()
                        val updatedTime = editTime.value.trim()
                        val parsedTime = runCatching { LocalTime.parse(updatedTime, timeFormatter) }.getOrNull()
                        if (updatedLabel.isEmpty()) {
                            showToast(context.getString(R.string.error_text_empty))
                            return@Button
                        }
                        if (parsedTime == null) {
                            showToast(context.getString(R.string.error_invalid_time_format))
                            return@Button
                        }
                        if (updatedLabel.isNotEmpty()) {
                            val originalDate = runCatching { LocalDate.parse(original.timestamp.take(10), dateFormatter) }
                                .getOrNull()
                                ?: return@Button
                            val newTimestamp = originalDate.atTime(parsedTime).format(timestampFormatter)
                            val updated = ActionLogRepository.updateLogEntry(
                                context,
                                original,
                                updatedLabel,
                                newTimestamp,
                                updatedDescription
                            )
                            if (updated) {
                                reloadLogsForSelection()
                                clearSelection()
                            }
                        }
                    }) {
                        Text(text = stringResource(R.string.save))
                    }
                    Button(
                        onClick = {
                            val original = selectedLog.value ?: return@Button
                            val deleted = ActionLogRepository.deleteLogEntry(context, original)
                            if (deleted) {
                                reloadLogsForSelection()
                                refreshAvailableDates()
                                clearSelection()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text(text = stringResource(R.string.delete))
                    }
                    OutlinedButton(onClick = {
                        clearSelection()
                    }) {
                        Text(text = stringResource(R.string.cancel))
                    }
                }
            }
        }

    LazyColumn(
        state = logListState,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp, max = 520.dp)
            .nestedScroll(pullDownRefreshConnection)
    ) {
            items(logs) { entry ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedLog.value = entry
                            editLabel.value = entry.label
                            editDescription.value = entry.description
                            editTime.value = entry.timestamp.substringAfter(" ", "").ifBlank { "00:00:00" }
                        }
                ) {
                    Text(text = stringResource(R.string.log_entry_label_time, entry.label, entry.timestamp))
                    if (entry.description.isNotBlank()) {
                        Text(text = stringResource(R.string.log_entry_description, entry.description))
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ActionTrackerPreview() {
    RegEventosTheme {
        ActionTracker(themeMode = ThemeMode.SYSTEM, appLanguage = AppLanguage.SYSTEM, onThemeChange = {}, onLanguageChange = {})
    }
}
