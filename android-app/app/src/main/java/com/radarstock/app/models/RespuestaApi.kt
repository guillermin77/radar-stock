package com.radarstock.app.models

import com.google.gson.annotations.SerializedName

/**
 * Respuesta genérica de la API para operaciones con estado y mensaje (ej. DELETE o errores)
 */
data class RespuestaApi(
    @SerializedName("estado")
    val estado: String? = null,

    @SerializedName("mensaje")
    val mensaje: String? = null
)

