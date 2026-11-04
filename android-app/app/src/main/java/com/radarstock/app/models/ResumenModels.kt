package com.radarstock.app.models

import com.google.gson.annotations.SerializedName
import java.io.Serializable

/**
 * Representa un producto dentro del Top 10 de más vendidos en el resumen
 */
data class ProductoVendido(
    @SerializedName("nombre")
    val nombre: String,

    @SerializedName("unidades")
    val unidades: Int,

    @SerializedName("monto")
    val monto: Double
) : Serializable

/**
 * Respuesta JSON devuelta por api/resumen.php
 */
data class ResumenResponse(
    @SerializedName("estado")
    val estado: String,

    @SerializedName("periodo")
    val periodo: String,

    @SerializedName("fecha_inicio")
    val fechaInicio: String,

    @SerializedName("fecha_fin")
    val fechaFin: String,

    @SerializedName("total_vendido")
    val totalVendido: Double,

    @SerializedName("cantidad_ventas")
    val cantidadVentas: Int,

    @SerializedName("productos_mas_vendidos")
    val productosMasVendidos: List<ProductoVendido> = emptyList()
) : Serializable

