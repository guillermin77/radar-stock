package com.radarstock.app.models

import com.google.gson.annotations.SerializedName
import java.io.Serializable

/**
 * Representa un producto individual dentro de la lista de items enviada a api/ventas.php
 */
data class VentaItemRequest(
    @SerializedName("producto_id")
    val productoId: Int,

    @SerializedName("cantidad")
    val cantidad: Int
)

/**
 * Petición principal enviada a api/ventas.php con soporte de idempotencia (codigo_operacion)
 */
data class VentaRequest(
    @SerializedName("codigo_operacion")
    val codigoOperacion: String,

    @SerializedName("items")
    val items: List<VentaItemRequest>
)

/**
 * Respuesta JSON devuelta por api/ventas.php
 */
data class VentaResponse(
    @SerializedName("estado")
    val estado: String,

    @SerializedName("mensaje")
    val mensaje: String? = null,

    @SerializedName("venta_id")
    val ventaId: Int? = null,

    @SerializedName("total")
    val total: Double? = null,

    @SerializedName("fecha")
    val fecha: String? = null,

    @SerializedName("alertas_nuevas")
    val alertasNuevas: List<Alerta>? = null
)

/**
 * Modelo en memoria que representa un producto agregado al carrito en VentaActivity
 */
data class ItemVenta(
    val producto: Producto,
    var cantidad: Int
) : Serializable {
    val subtotal: Double
        get() = producto.precioVenta * cantidad
}
