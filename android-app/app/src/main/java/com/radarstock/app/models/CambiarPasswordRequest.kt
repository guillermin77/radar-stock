package com.radarstock.app.models

import com.google.gson.annotations.SerializedName

/**
 * Petición para que el usuario actual autenticado cambie su propia contraseña.
 */
data class CambiarPasswordRequest(
    @SerializedName("password_actual")
    val passwordActual: String,

    @SerializedName("password_nueva")
    val passwordNueva: String
)

