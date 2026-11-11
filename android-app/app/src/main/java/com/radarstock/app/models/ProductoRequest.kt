package com.radarstock.app.models

import com.google.gson.annotations.SerializedName

/**
 * Datos enviados en POST o PUT a api/productos.php
 */
data class ProductoRequest(
    @SerializedName("nombre_producto")
    val nombreProducto: String,

    @SerializedName("categoria")
    val categoria: String? = null,

    @SerializedName("precio_venta")
    val precioVenta: Double,

    @SerializedName("codigo_barras")
    val codigoBarras: String? = null,

    @SerializedName("stock_actual")
    val stockActual: Int? = null,

    @SerializedName("stock_minimo")
    val stockMinimo: Int,

    @SerializedName("stock_deseado")
    val stockDeseado: Int,

    @SerializedName("fecha_vencimiento")
    val fechaVencimiento: String? = null
)

