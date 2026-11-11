package com.radarstock.app.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.radarstock.app.R
import com.radarstock.app.models.Alerta
import com.radarstock.app.views.AlertasActivity

/**
 * Gestor de notificaciones locales de stock, reproducción de sonido/vibración de alertas
 * y control de permisos en Android 13+.
 */
object NotificationHelper {

    const val CANAL_ID = "canal_alertas_stock_v2"
    const val CANAL_NOMBRE = "Alertas de stock"
    const val CANAL_DESCRIPCION = "Notificaciones y alarmas sonoras sobre productos con stock agotado, crítico o bajo"

    private const val PREFS_NOTIF = "radar_stock_notificaciones"
    private const val KEY_PERMISO_SOLICITADO = "permiso_notificaciones_solicitado"
    private const val KEY_PREFIJO_ESTADO = "alerta_estado_producto_"
    private const val NOTIF_ID_GRUPO = 9999

    /**
     * Crea el canal de notificación con sonido explícito y alta prioridad si el dispositivo corre Android 8.0+.
     */
    fun crearCanalNotificaciones(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Limpiar canal anterior si existía para refrescar la configuración de audio
            try {
                manager.deleteNotificationChannel("canal_alertas_stock")
            } catch (_: Exception) {}

            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .build()

            val channel = NotificationChannel(
                CANAL_ID,
                CANAL_NOMBRE,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CANAL_DESCRIPCION
                enableLights(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 150, 300)
                setSound(soundUri, audioAttributes)
            }

            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Reproduce un sonido audible de alerta/alarma en el dispositivo,
     * garantizando que el usuario escuche el aviso tanto en primer plano
     * como si las notificaciones de barra de estado están silenciadas o restringidas.
     */
    fun reproducirSonidoAlerta(context: Context) {
        try {
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val ringtone = RingtoneManager.getRingtone(context.applicationContext, soundUri)
            ringtone?.play()
        } catch (_: Exception) {
            try {
                val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 450)
            } catch (_: Exception) {}
        }
    }

    /**
     * Emite una vibración corta de alerta.
     */
    fun vibrarAlerta(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 250, 100, 250), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 250, 100, 250), -1)
            }
        } catch (_: Exception) {}
    }

    /**
     * Determina si se debe solicitar el permiso POST_NOTIFICATIONS (una sola vez tras el primer login).
     */
    fun debeSolicitarPermiso(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return false
        }
        val prefs = context.getSharedPreferences(PREFS_NOTIF, Context.MODE_PRIVATE)
        val yaSolicitado = prefs.getBoolean(KEY_PERMISO_SOLICITADO, false)
        if (yaSolicitado) {
            return false
        }
        val yaOtorgado = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        return !yaOtorgado
    }

    /**
     * Marca que el permiso ya fue presentado al usuario para no volver a insistir.
     */
    fun marcarPermisoSolicitado(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NOTIF, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_PERMISO_SOLICITADO, true).apply()
    }

    /**
     * Procesa la lista de alertas_nuevas tras confirmar una venta.
     * Emite aviso sonoro/vibratorio y publica notificación en la barra de estado.
     */
    fun procesarYNotificarAlertas(context: Context, alertasNuevas: List<Alerta>?) {
        if (alertasNuevas.isNullOrEmpty()) return

        val prefs = context.getSharedPreferences(PREFS_NOTIF, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        val alertasANotificar = mutableListOf<Alerta>()

        for (alerta in alertasNuevas) {
            val clave = "$KEY_PREFIJO_ESTADO${alerta.productoId}"
            val ultimoEstado = prefs.getString(clave, null)

            // Si el estado del producto cambió (ej: pasó de normal a bajo, o de bajo a crítico/agotado)
            if (ultimoEstado != alerta.tipo) {
                alertasANotificar.add(alerta)
                editor.putString(clave, alerta.tipo)
            }
        }
        editor.apply()

        if (alertasANotificar.isEmpty()) return

        // 1. Emitir alerta sonora y vibración inmediatamente
        reproducirSonidoAlerta(context)
        vibrarAlerta(context)

        // 2. Verificar permiso de notificaciones en la barra de estado
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return
        }

        crearCanalNotificaciones(context)

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

        val intent = Intent(context, AlertasActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationManager = NotificationManagerCompat.from(context)

        if (alertasANotificar.size <= 3) {
            // Notificación individual por cada alerta
            for (alerta in alertasANotificar) {
                val iconoRes = when (alerta.tipo.lowercase()) {
                    "agotado" -> R.drawable.ic_alerta_agotado
                    "critico" -> R.drawable.ic_alerta_critico
                    "bajo" -> R.drawable.ic_alerta_bajo
                    else -> R.drawable.ic_alerta_critico
                }

                val notificacion = NotificationCompat.Builder(context, CANAL_ID)
                    .setSmallIcon(iconoRes)
                    .setContentTitle(alerta.nombreProducto)
                    .setContentText(alerta.mensaje)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(alerta.mensaje))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                    .setSound(soundUri)
                    .build()

                try {
                    notificationManager.notify(alerta.productoId, notificacion)
                } catch (_: SecurityException) {
                }
            }
        } else {
            // Notificación agrupada si son más de 3
            val inboxStyle = NotificationCompat.InboxStyle()
                .setBigContentTitle("Radar Stock: ${alertasANotificar.size} alertas de stock")
                .setSummaryText("Productos con stock bajo o crítico")

            for (alerta in alertasANotificar) {
                inboxStyle.addLine("${alerta.nombreProducto}: ${alerta.mensaje}")
            }

            val notificacionAgrupada = NotificationCompat.Builder(context, CANAL_ID)
                .setSmallIcon(R.drawable.ic_alerta_critico)
                .setContentTitle("Radar Stock: ${alertasANotificar.size} alertas de stock")
                .setContentText("Se detectaron ${alertasANotificar.size} productos con stock insuficiente tras la venta.")
                .setStyle(inboxStyle)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setSound(soundUri)
                .build()

            try {
                notificationManager.notify(NOTIF_ID_GRUPO, notificacionAgrupada)
            } catch (_: SecurityException) {
            }
        }
    }

    /**
     * Limpia el registro de alerta de un producto cuando se le repone stock,
     * para que vuelva a alertar si en el futuro baja nuevamente.
     */
    fun limpiarEstadoAlerta(context: Context, productoId: Int) {
        val prefs = context.getSharedPreferences(PREFS_NOTIF, Context.MODE_PRIVATE)
        prefs.edit().remove("$KEY_PREFIJO_ESTADO$productoId").apply()
    }
}
