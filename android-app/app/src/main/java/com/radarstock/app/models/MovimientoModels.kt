package com.radarstock.app.models

import com.google.gson.annotations.SerializedName
import java.io.Serializable

/**
 * Modelo de un registro de movimiento de auditoría de stock.
 */
data class MovimientoStock(
    @SerializedName("id")
    val id: Int,

    @SerializedName("fecha")
    val fecha: String,

    @SerializedName("producto_id")
    val productoId: Int,

    @SerializedName("nombre_producto")
    val nombreProducto: String,

    @SerializedName("codigo_barras")
    val codigoBarras: String?,

    @SerializedName("usuario")
    val usuario: String,

    @SerializedName("tipo")
    val tipo: String,

    @SerializedName("stock_antes")
    val stockAntes: Int,

    @SerializedName("stock_despues")
    val stockDespues: Int,

    @SerializedName("diferencia")
    val diferencia: Int
) : Serializable

/**
 * Respuesta paginada devuelta por api/movimientos.php
 */
data class MovimientosResponse(
    @SerializedName("pagina")
    val pagina: Int,

    @SerializedName("por_pagina")
    val porPagina: Int,

    @SerializedName("total")
    val total: Int,

    @SerializedName("total_paginas")
    val totalPaginas: Int,

    @SerializedName("movimientos")
    val movimientos: List<MovimientoStock>
) : Serializable

