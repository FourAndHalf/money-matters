package com.moneymatters.app.feature.market

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.moneymatters.app.MainActivity
import com.moneymatters.app.R
import com.moneymatters.app.core.market.AlertEventEntity

private const val CHANNEL = "market-alerts"

/** Posts price alerts as notifications. Alerts are always kept in the app too, so nothing depends on this being allowed. */
object AlertNotifier {
    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun notify(context: Context, events: List<AlertEventEntity>) {
        if (events.isEmpty() || !canNotify(context)) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL, "Price alerts", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val title = if (events.size == 1) "Price alert" else "${events.size} price alerts"
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(events.first().message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(events.joinToString("\n") { it.message }))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        @Suppress("MissingPermission") // checked by canNotify above
        NotificationManagerCompat.from(context).notify(1001, builder.build())
    }
}

/** Returns a function that asks for the notification permission once, on Android 13 and later, and does nothing otherwise. */
@Composable
fun rememberNotificationPermissionRequest(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    return { if (!AlertNotifier.canNotify(context)) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
}
