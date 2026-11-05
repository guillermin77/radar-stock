package com.radarstock.app.models

import com.google.gson.annotations.SerializedName
import java.io.Serializable

/**
 * Modelo de datos para una Alerta de stock o vencimiento devuelta por api/alertas.php
 */
data class Alerta(
    @SerializedName("producto_id")
    val productoId: Int,

    @SerializedName("nombre_producto")
    val nombreProducto: String,

    @SerializedName("tipo")
    val tipo: String, // 'vencido', 'agotado', 'critico', 'por_vencer', 'bajo'

    @SerializedName("mensaje")
    val mensaje: String,

    @SerializedName("stock_actual")
    val stockActual: Int,

    @SerializedName("stock_minimo")
    val stockMinimo: Int,

    @SerializedName("stock_deseado")
    val stockDeseado: Int,

    @SerializedName("fecha_vencimiento")
    val fechaVencimiento: String? = null,

    @SerializedName("dias_para_vencer")
    val diasParaVencer: Int? = null
) : Serializable

/**
 * Conteos devueltos por api/alertas.php?resumen=1
 */
data class ResumenAlertasResponse(
    @SerializedName("agotados")
    val agotados: Int = 0,

    @SerializedName("criticos")
    val criticos: Int = 0,

    @SerializedName("bajos")
    val bajos: Int = 0,

    @SerializedName("por_vencer")
    val porVencer: Int = 0,

    @SerializedName("vencidos")
    val vencidos: Int = 0,

    @SerializedName("total")
    val total: Int = 0
) : Serializable

