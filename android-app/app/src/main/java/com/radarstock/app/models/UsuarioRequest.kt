package com.radarstock.app.models

import com.google.gson.annotations.SerializedName

/**
 * Petición para crear, editar, activar/desactivar o resetear contraseña de un usuario.
 */
data class UsuarioRequest(
    @SerializedName("id")
    val id: Int? = null,

    @SerializedName("nombre")
    val nombre: String? = null,

    @SerializedName("correo")
    val correo: String? = null,

    @SerializedName("password")
    val password: String? = null,

    @SerializedName("password_nueva")
    val passwordNueva: String? = null,

    @SerializedName("rol")
    val rol: String? = null,

    @SerializedName("accion")
    val accion: String? = null
)

