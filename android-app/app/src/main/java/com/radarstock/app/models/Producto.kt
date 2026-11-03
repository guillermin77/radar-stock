package com.radarstock.app.models

import com.google.gson.annotations.SerializedName

/**
 * Modelo de datos para Productos del almacén
 * Mapea directamente la respuesta JSON de api/productos.php
 */
data class Producto(
    @SerializedName("id")
    val id: Int,

    @SerializedName("nombre_producto")
    val nombreProducto: String,

    @SerializedName("categoria")
    val categoria: String?,

    @SerializedName("stock_actual")
    val stockActual: Int,

    @SerializedName("stock_minimo")
    val stockMinimo: Int,

    @SerializedName("precio_venta")
    val precioVenta: Double,

    @SerializedName("fecha_vencimiento")
    val fechaVencimiento: String?
)

