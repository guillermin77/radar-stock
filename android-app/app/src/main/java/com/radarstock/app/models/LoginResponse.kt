package com.radarstock.app.models

import com.google.gson.annotations.SerializedName

/**
 * Respuesta JSON exitosa de api/login.php
 */
data class LoginResponse(
    @SerializedName("token")
    val token: String,

    @SerializedName("usuario")
    val usuario: UsuarioResponse
)

data class UsuarioResponse(
    @SerializedName("id")
    val id: Int,

    @SerializedName("nombre")
    val nombre: String,

    @SerializedName("rol")
    val rol: String
)

