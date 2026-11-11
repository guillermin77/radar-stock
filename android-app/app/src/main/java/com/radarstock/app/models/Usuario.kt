package com.radarstock.app.models

import com.google.gson.annotations.SerializedName

/**
 * Modelo de datos que representa a un usuario del sistema Radar Stock.
 */
data class Usuario(
    @SerializedName("id")
    val id: Int,

    @SerializedName("nombre")
    val nombre: String,

    @SerializedName("correo")
    val correo: String,

    @SerializedName("rol")
    val rol: String,

    @SerializedName("activo")
    val activo: Int = 1,

    @SerializedName("fecha_creacion")
    val fechaCreacion: String? = null
) {
    val esActivo: Boolean
        get() = activo == 1

    val esAdmin: Boolean
        get() = rol.equals("Administrador", ignoreCase = true)
}

