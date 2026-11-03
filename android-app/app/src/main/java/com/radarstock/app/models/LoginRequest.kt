package com.radarstock.app.models

import com.google.gson.annotations.SerializedName

/**
 * Cuerpo enviado en la petición POST a api/login.php
 */
data class LoginRequest(
    @SerializedName("correo")
    val correo: String,

    @SerializedName("password")
    val password: String
)

