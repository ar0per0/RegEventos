package es.aropero.regeventos

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.RemoteViews
import android.widget.Toast
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import es.aropero.regeventos.data.ActionConfigRepository
import es.aropero.regeventos.data.ActionLogRepository
import es.aropero.regeventos.data.ThemeMode

class ActionWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { appWidgetId ->
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { widgetId ->
            ActionConfigRepository.removeWidgetLabel(context, widgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_LOG) {
            val label = intent.getStringExtra(EXTRA_LABEL)
            val widgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
            if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                showToast(context, context.getString(R.string.widget_missing_id_error))
                return
            }

            val resolvedLabel = label ?: ActionConfigRepository.getWidgetLabel(context, widgetId)
            if (resolvedLabel.isNullOrBlank()) {
                showToast(context, context.getString(R.string.widget_missing_label_error))
                return
            }

            ActionLogRepository.appendLog(context, resolvedLabel)
            val manager = AppWidgetManager.getInstance(context)
            pulseWidget(context, manager, widgetId)

            showWidgetConfirmation(context, resolvedLabel)
        }
    }

    companion object {
        const val ACTION_LOG = "es.aropero.regeventos.LOG_ACTION"
        const val EXTRA_LABEL = "extra_label"
        private const val CONFIRMATION_CHANNEL_ID = "widget_action_confirmation"
        private const val FEEDBACK_RESET_MS = 1600L

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val label = ActionConfigRepository.getWidgetLabel(context, appWidgetId)
                ?: context.getString(R.string.default_action_label)
            val isDark = resolveIsDark(context)
            val views = buildRemoteViews(
                context = context,
                appWidgetId = appWidgetId,
                label = label,
                isDark = isDark,
                buttonState = WidgetButtonState.DEFAULT
            )

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun showWidgetConfirmation(context: Context, label: String) {
            val toastMessage = context.getString(R.string.widget_log_saved_message, label)
            val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionChecker.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == PermissionChecker.PERMISSION_GRANTED
            } else {
                true
            }

            if (hasPermission) {
                val manager = NotificationManagerCompat.from(context)
                ensureChannel(context, manager)

                val notification = NotificationCompat.Builder(context, CONFIRMATION_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification_check)
                    .setContentTitle(context.getString(R.string.app_name))
                    .setContentText(toastMessage)
                    .setAutoCancel(true)
                    .build()

                runCatching { manager.notify(label.hashCode(), notification) }
                    .onFailure { showToast(context, toastMessage) }
            }

            triggerHaptic(context)
            showToast(context, toastMessage)
        }

        private fun showToast(context: Context, message: String) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    context.applicationContext,
                    message,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        private fun ensureChannel(context: Context, manager: NotificationManagerCompat) {
            if (!manager.areNotificationsEnabled()) return

            val channel = NotificationChannelCompat.Builder(
                CONFIRMATION_CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_DEFAULT
            )
                .setName(context.getString(R.string.widget_confirmation_channel_name))
                .setDescription(context.getString(R.string.widget_confirmation_channel_description))
                .build()

            manager.createNotificationChannel(channel)
        }

        private fun resolveIsDark(context: Context): Boolean {
            return when (ActionConfigRepository.getThemeMode(context)) {
                ThemeMode.SYSTEM ->
                    (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                        Configuration.UI_MODE_NIGHT_YES

                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }
        }

        private fun buildRemoteViews(
            context: Context,
            appWidgetId: Int,
            label: String,
            isDark: Boolean,
            buttonState: WidgetButtonState
        ): RemoteViews {
            val backgroundColor = ContextCompat.getColor(
                context,
                if (isDark) R.color.widget_background_dark else R.color.widget_background_light
            )
            val textColor = ContextCompat.getColor(
                context,
                if (isDark) R.color.widget_text_dark else R.color.widget_text_light
            )
            val buttonBackground = when (buttonState) {
                WidgetButtonState.CONFIRMING ->
                    if (isDark) R.drawable.widget_button_background_feedback_dark else R.drawable.widget_button_background_feedback_light

                WidgetButtonState.DEFAULT ->
                    if (isDark) R.drawable.widget_button_background_dark else R.drawable.widget_button_background_light
            }
            val buttonText = when (buttonState) {
                WidgetButtonState.CONFIRMING -> context.getString(R.string.widget_register_confirmed)
                WidgetButtonState.DEFAULT -> context.getString(R.string.widget_register_button)
            }
            val buttonTextColor = if (buttonState == WidgetButtonState.CONFIRMING) {
                ContextCompat.getColor(context, R.color.widget_button_feedback_text)
            } else {
                ContextCompat.getColor(context, R.color.widget_button_text)
            }

            return RemoteViews(context.packageName, R.layout.widget_action).apply {
                setTextViewText(R.id.widgetLabel, label)
                setInt(R.id.widgetContainer, "setBackgroundColor", backgroundColor)
                setTextColor(R.id.widgetLabel, textColor)
                setInt(R.id.widgetButton, "setBackgroundResource", buttonBackground)
                setTextColor(R.id.widgetButton, buttonTextColor)
                setTextViewText(R.id.widgetButton, buttonText)

                val intent = Intent(context, ActionWidgetProvider::class.java).apply {
                    action = ACTION_LOG
                    putExtra(EXTRA_LABEL, label)
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    appWidgetId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                setOnClickPendingIntent(R.id.widgetButton, pendingIntent)
            }
        }

        private fun pulseWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val label = ActionConfigRepository.getWidgetLabel(context, appWidgetId)
                ?: context.getString(R.string.default_action_label)
            val isDark = resolveIsDark(context)
            val emphasizedViews = buildRemoteViews(
                context = context,
                appWidgetId = appWidgetId,
                label = label,
                isDark = isDark,
                buttonState = WidgetButtonState.CONFIRMING
            )

            appWidgetManager.updateAppWidget(appWidgetId, emphasizedViews)

            Handler(Looper.getMainLooper()).postDelayed({
                updateAppWidget(context, appWidgetManager, appWidgetId)
            }, FEEDBACK_RESET_MS)
        }

        private fun triggerHaptic(context: Context) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    manager.defaultVibrator.vibrate(
                        VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                } else {
                    val vibrator = context.getSystemService(Vibrator::class.java)
                    vibrator?.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE))
                }
            }
        }

        private enum class WidgetButtonState {
            DEFAULT,
            CONFIRMING
        }
    }
}
