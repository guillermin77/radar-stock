package com.radarstock.app.models

import com.google.gson.annotations.SerializedName

/**
 * Petición enviada a api/stock.php para sumar unidades
 */
data class StockRequest(
    @SerializedName("producto_id")
    val productoId: Int,

    @SerializedName("cantidad")
    val cantidad: Int
)

/**
 * Respuesta devuelta por api/stock.php tras actualizar el inventario
 */
data class StockResponse(
    @SerializedName("estado")
    val estado: String,

    @SerializedName("producto_id")
    val productoId: Int,

    @SerializedName("nombre_producto")
    val nombreProducto: String,

    @SerializedName("stock_anterior")
    val stockAnterior: Int,

    @SerializedName("cantidad_agregada")
    val cantidadAgregada: Int,

    @SerializedName("stock_actual")
    val stockActual: Int,

    @SerializedName("stock_nuevo")
    val stockNuevo: Int
)

