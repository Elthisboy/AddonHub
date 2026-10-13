package com.appmcpe.hub.install

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.appmcpe.hub.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Servicio en primer plano que mantiene viva la descarga cuando el usuario sale de la app,
 * muestra el progreso en la notificación y avisa cuando todo está listo para importar.
 */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var finished = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            InstallManager.dismiss()
            stopSelf()
            return START_NOT_STICKY
        }

        createChannels()
        ServiceCompat.startForeground(
            this,
            PROGRESS_ID,
            progressNotification(InstallManager.session.value),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        acquireWakeLock()

        finished = false
        job?.cancel()
        job = scope.launch {
            InstallManager.session.collect { session ->
                if (finished) return@collect
                if (session == null || !session.stage.isWorking) {
                    finish(session)
                } else {
                    notify(PROGRESS_ID, progressNotification(session))
                    delay(500) // no saturar el sistema de notificaciones
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun finish(session: InstallSession?) {
        finished = true
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        releaseWakeLock()
        // Solo se avisa si el usuario está fuera de la app; si la está viendo, ya lo sabe.
        if (!InstallManager.appInForeground && session != null) {
            when (val stage = session.stage) {
                is Stage.ReadyToImport -> notify(
                    DONE_ID,
                    doneNotification(
                        "¡Descarga lista! 🎉",
                        "Toca para importar «${session.title}» en Minecraft",
                    ),
                )
                is Stage.Error -> notify(
                    DONE_ID,
                    doneNotification("No se pudo descargar", stage.message),
                )
                else -> {}
            }
        }
        stopSelf()
    }

    // ---------- Notificaciones ----------

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_PROGRESS, "Descargas en curso", NotificationManager.IMPORTANCE_LOW),
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_DONE, "Descargas listas", NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun progressNotification(session: InstallSession?): Notification {
        val stage = session?.stage
        val title: String
        var text = ""
        var percent = 0
        var indeterminate = true
        when (stage) {
            is Stage.Downloading -> {
                title = if (stage.count > 1) {
                    "Descargando ${stage.index + 1} de ${stage.count}"
                } else {
                    "Descargando…"
                }
                text = stage.name
                if (stage.progress >= 0f) {
                    percent = (stage.progress * 100).toInt()
                    indeterminate = false
                    text = "${stage.name} · $percent%"
                }
            }
            is Stage.Opening -> title = "Abriendo Minecraft…"
            else -> title = "Preparando descarga…"
        }
        val cancel = PendingIntent.getService(
            this,
            1,
            Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CH_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setProgress(100, percent, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent())
            .addAction(0, "Cancelar", cancel)
            .build()
    }

    private fun doneNotification(title: String, text: String): Notification =
        NotificationCompat.Builder(this, CH_DONE)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .build()

    private fun notify(id: Int, notification: Notification) {
        // Sin permiso de notificaciones (Android 13+) la descarga sigue, solo no se muestra el aviso.
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        NotificationManagerCompat.from(this).notify(id, notification)
    }

    // ---------- WakeLock: evita que la CPU se duerma con la pantalla apagada ----------

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AddonHub:download")
            .apply { acquire(MAX_WAKE_MS) }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    override fun onDestroy() {
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_CANCEL = "com.appmcpe.hub.CANCEL_DOWNLOAD"
        const val DONE_ID = 2
        private const val PROGRESS_ID = 1
        private const val CH_PROGRESS = "downloads"
        private const val CH_DONE = "downloads_done"
        private const val MAX_WAKE_MS = 30 * 60 * 1000L
    }
}
