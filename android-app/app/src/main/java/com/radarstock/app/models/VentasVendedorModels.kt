package com.radarstock.app.models

import com.google.gson.annotations.SerializedName

/**
 * Datos básicos del vendedor devueltos por api/ventas_vendedor.php
 */
data class VendedorInfo(
    @SerializedName("id")
    val id: Int,

    @SerializedName("nombre")
    val nombre: String,

    @SerializedName("rol")
    val rol: String,

    @SerializedName("activo")
    val activo: Int = 1
) {
    val esActivo: Boolean
        get() = activo == 1
}

/**
 * Renglón del detalle de una venta
 */
data class VentaItemDetalle(
    @SerializedName("producto")
    val producto: String,

    @SerializedName("cantidad")
    val cantidad: Int,

    @SerializedName("precio_unitario")
    val precioUnitario: Double,

    @SerializedName("subtotal")
    val subtotal: Double
)

/**
 * Venta individual con su detalle de productos para expandir/colapsar en la lista
 */
data class VentaConDetalle(
    @SerializedName("id")
    val id: Int,

    @SerializedName("fecha")
    val fecha: String,

    @SerializedName("total")
    val total: Double,

    @SerializedName("cantidad_items")
    val cantidadItems: Int,

    @SerializedName("detalles")
    val detalles: List<VentaItemDetalle> = emptyList()
)

/**
 * Respuesta JSON de api/ventas_vendedor.php cuando se envía usuario_id
 */
data class VentasPorVendedorResponse(
    @SerializedName("estado")
    val estado: String,

    @SerializedName("periodo")
    val periodo: String,

    @SerializedName("fecha_inicio")
    val fechaInicio: String,

    @SerializedName("fecha_fin")
    val fechaFin: String,

    @SerializedName("vendedor")
    val vendedor: VendedorInfo,

    @SerializedName("total_vendido")
    val totalVendido: Double,

    @SerializedName("cantidad_ventas")
    val cantidadVentas: Int,

    @SerializedName("ticket_promedio")
    val ticketPromedio: Double,

    @SerializedName("productos_mas_vendidos")
    val productosMasVendidos: List<ProductoVendido> = emptyList(),

    @SerializedName("pagina")
    val pagina: Int,

    @SerializedName("por_pagina")
    val porPagina: Int,

    @SerializedName("total")
    val total: Int,

    @SerializedName("total_paginas")
    val totalPaginas: Int,

    @SerializedName("ventas")
    val ventas: List<VentaConDetalle> = emptyList()
)

/**
 * Fila del comparativo de vendedores
 */
data class VendedorComparativo(
    @SerializedName("usuario_id")
    val usuarioId: Int,

    @SerializedName("nombre")
    val nombre: String,

    @SerializedName("rol")
    val rol: String,

    @SerializedName("activo")
    val activo: Int = 1,

    @SerializedName("cantidad_ventas")
    val cantidadVentas: Int,

    @SerializedName("total_vendido")
    val totalVendido: Double,

    @SerializedName("ticket_promedio")
    val ticketPromedio: Double
) {
    val esActivo: Boolean
        get() = activo == 1
}

/**
 * Respuesta JSON de api/ventas_vendedor.php cuando NO se envía usuario_id
 */
data class CompararVendedoresResponse(
    @SerializedName("estado")
    val estado: String,

    @SerializedName("periodo")
    val periodo: String,

    @SerializedName("fecha_inicio")
    val fechaInicio: String,

    @SerializedName("fecha_fin")
    val fechaFin: String,

    @SerializedName("total_general")
    val totalGeneral: Double,

    @SerializedName("cantidad_ventas_total")
    val cantidadVentasTotal: Int,

    @SerializedName("vendedores")
    val vendedores: List<VendedorComparativo> = emptyList()
)

