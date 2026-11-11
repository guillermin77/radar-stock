package com.radarstock.app.models

import com.google.gson.annotations.SerializedName
import java.io.Serializable

/**
 * Modelo de datos para Productos del almacén.
 * Mapea la respuesta JSON de api/productos.php con todos los campos del catálogo.
 * Implementa Serializable para permitir su transferencia entre actividades.
 */
data class Producto(
    @SerializedName("id")
    val id: Int,

    @SerializedName("codigo_barras")
    val codigoBarras: String? = null,

    @SerializedName("nombre_producto")
    val nombreProducto: String,

    @SerializedName("categoria")
    val categoria: String? = null,

    @SerializedName("precio_venta")
    val precioVenta: Double,

    @SerializedName("stock_actual")
    val stockActual: Int,

    @SerializedName("stock_minimo")
    val stockMinimo: Int,

    @SerializedName("stock_deseado")
    val stockDeseado: Int = 0,

    @SerializedName("fecha_vencimiento")
    val fechaVencimiento: String? = null
) : Serializable
