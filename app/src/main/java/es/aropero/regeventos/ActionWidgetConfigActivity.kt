package es.aropero.regeventos

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatActivity
import es.aropero.regeventos.data.ActionConfigRepository
import es.aropero.regeventos.data.ThemeMode
import es.aropero.regeventos.ui.theme.RegEventosTheme

class ActionWidgetConfigActivity : AppCompatActivity() {
    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setApplicationLocales(
            ActionConfigRepository.getAppLanguage(this).toLocaleListCompat()
        )
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setResult(Activity.RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            val themeMode = remember { ActionConfigRepository.getThemeMode(this) }
            val isDark = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }

            RegEventosTheme(darkTheme = isDark) {
                val labelState = remember { mutableStateOf("") }
                val actions = remember { mutableStateListOf<String>() }

                LaunchedEffect(Unit) {
                    actions.clear()
                    actions.addAll(ActionConfigRepository.getActions(this@ActionWidgetConfigActivity))
                }

                fun saveAndFinish(label: String) {
                    ActionConfigRepository.addAction(this@ActionWidgetConfigActivity, label)
                    ActionConfigRepository.saveWidgetLabel(this@ActionWidgetConfigActivity, appWidgetId, label)
                    ActionWidgetProvider.updateAppWidget(
                        this@ActionWidgetConfigActivity,
                        AppWidgetManager.getInstance(this@ActionWidgetConfigActivity),
                        appWidgetId
                    )
                    val result = Intent().apply {
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    }
                    setResult(Activity.RESULT_OK, result)
                    finish()
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(text = stringResource(R.string.widget_text_label))
                    OutlinedTextField(
                        value = labelState.value,
                        onValueChange = { labelState.value = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.widget_text_hint)) }
                    )
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val label = labelState.value.trim()
                            if (label.isNotEmpty()) {
                                saveAndFinish(label)
                            }
                        }
                    ) {
                        Text(text = stringResource(R.string.save))
                    }

                    if (actions.isNotEmpty()) {
                        HorizontalDivider()
                        Text(text = stringResource(R.string.use_existing_button))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            actions.forEach { existing ->
                                OutlinedButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { saveAndFinish(existing) }
                                ) {
                                    Text(text = existing)
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.padding(top = 4.dp))
                        Text(text = stringResource(R.string.create_button_for_widget_hint))
                    }
                }
            }
        }
    }
}
