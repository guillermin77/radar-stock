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

/**
 * Petición enviada a api/stock_ingreso.php para registrar ingreso atómico con auditoría
 */
data class StockIngresoRequest(
    @SerializedName("producto_id")
    val productoId: Int,

    @SerializedName("cantidad")
    val cantidad: Int
)

/**
 * Respuesta devuelta por api/stock_ingreso.php
 */
data class StockIngresoResponse(
    @SerializedName("mensaje")
    val mensaje: String? = null,

    @SerializedName("movimiento_id")
    val movimientoId: Int? = null,

    @SerializedName("producto_id")
    val productoId: Int,

    @SerializedName("nombre_producto")
    val nombreProducto: String? = null,

    @SerializedName("cantidad_ingresada")
    val cantidadIngresada: Int? = null,

    @SerializedName("stock_anterior")
    val stockAnterior: Int? = null,

    @SerializedName("stock_actual")
    val stockActual: Int
)

/**
 * Elemento de producto enviado en la lista de api/inventario_conteo.php
 */
data class ConteoItemRequest(
    @SerializedName("producto_id")
    val productoId: Int,

    @SerializedName("contado")
    val contado: Int
)

/**
 * Detalle de cada producto en la respuesta de api/inventario_conteo.php
 */
data class ConteoDetalle(
    @SerializedName("producto_id")
    val productoId: Int,

    @SerializedName("nombre_producto")
    val nombreProducto: String,

    @SerializedName("stock_anterior")
    val stockAnterior: Int,

    @SerializedName("stock_contado")
    val stockContado: Int,

    @SerializedName("diferencia")
    val diferencia: Int,

    @SerializedName("modificado")
    val modificado: Boolean
)

/**
 * Respuesta devuelta por api/inventario_conteo.php tras aplicar el conteo
 */
data class ConteoResponse(
    @SerializedName("mensaje")
    val mensaje: String,

    @SerializedName("total_procesados")
    val totalProcesados: Int,

    @SerializedName("total_modificados")
    val totalModificados: Int,

    @SerializedName("detalles")
    val detalles: List<ConteoDetalle> = emptyList()
)
