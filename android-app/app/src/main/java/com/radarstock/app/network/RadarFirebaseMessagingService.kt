package com.radarstock.app.network

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.radarstock.app.R
import com.radarstock.app.models.FcmTokenRequest
import com.radarstock.app.utils.NotificationHelper
import com.radarstock.app.utils.SessionManager
import com.radarstock.app.views.AlertasActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Servicio receptor de notificaciones push de Firebase Cloud Messaging (FCM).
 * Maneja la recepción de alertas remotas y la actualización del token del dispositivo.
 */
class RadarFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)

        // Si el usuario es Administrador y tiene sesión activa, actualizar el token en el servidor
        val rol = SessionManager.obtenerRol()
        if (SessionManager.haySesion() && rol.equals("Administrador", ignoreCase = true)) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    RetrofitClient.apiService.registrarFcmToken(FcmTokenRequest(token))
                } catch (e: Exception) {
                    // Silenciar error en background
                }
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // Asegurar que el canal de notificaciones exista
        NotificationHelper.crearCanalNotificaciones(this)

        val titulo = remoteMessage.notification?.title
            ?: remoteMessage.data["titulo"]
            ?: "Alerta de Stock - Radar Stock"

        val cuerpo = remoteMessage.notification?.body
            ?: remoteMessage.data["cuerpo"]
            ?: "Uno o más productos han bajado de su stock mínimo."

        val tipo = remoteMessage.data["tipo"] ?: "critico"

        val iconoRes = when (tipo.lowercase()) {
            "agotado" -> R.drawable.ic_alerta_agotado
            "critico" -> R.drawable.ic_alerta_critico
            "bajo" -> R.drawable.ic_alerta_bajo
            else -> R.drawable.ic_alerta_critico
        }

        val intent = Intent(this, AlertasActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, NotificationHelper.CANAL_ID)
            .setSmallIcon(iconoRes)
            .setContentTitle(titulo)
            .setContentText(cuerpo)
            .setStyle(NotificationCompat.BigTextStyle().bigText(cuerpo))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        val notificationManager = NotificationManagerCompat.from(this)
        try {
            notificationManager.notify((System.currentTimeMillis() % 100000).toInt(), builder.build())
        } catch (_: SecurityException) {
            // Manejar si no tiene permiso de notificaciones en Android 13+
        }
    }
}

