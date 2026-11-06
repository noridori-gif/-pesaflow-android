package tz.pesaflow.sms

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object Notifier {
    private const val CHANNEL = "miamala"

    fun ensureChannel(c: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL, "Miamala mipya", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Taarifa muamala unapoingizwa kwenye PesaFlow"
            }
            c.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    fun transactions(c: Context, summaries: List<String>) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        ensureChannel(c)

        val open = Prefs.serverUrl(c)?.let {
            PendingIntent.getActivity(c, 0, Intent(Intent.ACTION_VIEW, Uri.parse("$it/dashboard")), PendingIntent.FLAG_IMMUTABLE)
        }
        val title = if (summaries.size == 1) "Imeongezwa kwenye PesaFlow" else "Miamala ${summaries.size} imeongezwa"
        val n = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title)
            .setContentText(summaries.first())
            .setStyle(NotificationCompat.InboxStyle().also { s -> summaries.take(6).forEach { s.addLine(it) } })
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(c).notify((System.currentTimeMillis() % 100000).toInt(), n)
        } catch (_: SecurityException) { }
    }
}
