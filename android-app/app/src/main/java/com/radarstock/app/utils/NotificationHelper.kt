package com.radarstock.app.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.radarstock.app.R
import com.radarstock.app.models.Alerta
import com.radarstock.app.views.AlertasActivity

/**
 * Gestor de notificaciones locales de stock y control de permisos en Android 13+.
 */
object NotificationHelper {

    const val CANAL_ID = "canal_alertas_stock"
    const val CANAL_NOMBRE = "Alertas de stock"
    const val CANAL_DESCRIPCION = "Notificaciones sobre productos con stock agotado, crítico o bajo tras una venta"

    private const val PREFS_NOTIF = "radar_stock_notificaciones"
    private const val KEY_PERMISO_SOLICITADO = "permiso_notificaciones_solicitado"
    private const val KEY_PREFIJO_ESTADO = "alerta_estado_producto_"
    private const val NOTIF_ID_GRUPO = 9999

    /**
     * Crea el canal de notificación si el dispositivo corre Android 8.0 (API 26) o superior.
     */
    fun crearCanalNotificaciones(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CANAL_ID,
                CANAL_NOMBRE,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CANAL_DESCRIPCION
                enableLights(true)
                enableVibration(true)
            }

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
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
     * Aplica la regla: no avisar dos veces la misma alerta de un producto mientras su estado no cambie.
     * Si son hasta 3 alertas, muestra una por cada producto; si son más de 3, agrupa en una sola.
     */
    fun procesarYNotificarAlertas(context: Context, alertasNuevas: List<Alerta>?) {
        if (alertasNuevas.isNullOrEmpty()) return

        // Verificar si la app tiene permiso para enviar notificaciones
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return
        }

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

        crearCanalNotificaciones(context)

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
                    .build()

                try {
                    notificationManager.notify(alerta.productoId, notificacion)
                } catch (e: SecurityException) {
                    // Manejo de seguridad en caso de restricción en tiempo de ejecución
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
                .build()

            try {
                notificationManager.notify(NOTIF_ID_GRUPO, notificacionAgrupada)
            } catch (e: SecurityException) {
                // Manejo de seguridad
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

