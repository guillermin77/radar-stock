package com.radarstock.app.models

import com.google.gson.annotations.SerializedName

/**
 * Petición para registrar el token FCM del dispositivo en el servidor.
 */
data class FcmTokenRequest(
    @SerializedName("fcm_token")
    val fcmToken: String
)

