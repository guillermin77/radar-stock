package com.radarstock.app.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.radarstock.app.databinding.ItemProductoGridBinding
import com.radarstock.app.models.Producto
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Adaptador para mostrar productos como botones en una cuadrícula de 2 columnas (GridLayoutManager).
 * Optimizado para selección rápida con el pulgar.
 */
class ProductoGridAdapter(
    private val onProductoClick: (Producto) -> Unit
) : RecyclerView.Adapter<ProductoGridAdapter.ProductoGridViewHolder>() {

    private val productos = mutableListOf<Producto>()
    // Mapeo: productoId -> cantidad agregada en la venta actual
    private val cantidadesEnVenta = mutableMapOf<Int, Int>()

    private val formatoMoneda = NumberFormat.getCurrencyInstance(Locale("es", "CL")).apply {
        maximumFractionDigits = 0
        currency = Currency.getInstance("CLP")
    }

    inner class ProductoGridViewHolder(private val binding: ItemProductoGridBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(producto: Producto) {
            binding.tvNombreProducto.text = producto.nombreProducto

            // Categoría
            if (!producto.categoria.isNullOrBlank()) {
                binding.tvCategoria.text = producto.categoria
                binding.tvCategoria.visibility = View.VISIBLE
            } else {
                binding.tvCategoria.visibility = View.GONE
            }

            // Precio formateado en pesos chilenos sin decimales
            binding.tvPrecio.text = formatoMoneda.format(producto.precioVenta)

            // Cantidad agregada en la venta actual (badge)
            val cantidadAgregada = cantidadesEnVenta[producto.id] ?: 0
            if (cantidadAgregada > 0) {
                binding.tvBadgeCantidad.text = "x$cantidadAgregada"
                binding.tvBadgeCantidad.visibility = View.VISIBLE
            } else {
                binding.tvBadgeCantidad.visibility = View.GONE
            }

            // Gestión de stock y estado deshabilitado en gris si es 0
            if (producto.stockActual <= 0) {
                binding.cardProducto.isEnabled = false
                binding.cardProducto.isClickable = false
                binding.cardProducto.setCardBackgroundColor(Color.parseColor("#F1F5F9"))
                binding.cardProducto.strokeColor = Color.parseColor("#CBD5E1")
                binding.cardProducto.alpha = 0.65f

                binding.tvNombreProducto.setTextColor(Color.parseColor("#94A3B8"))
                binding.tvPrecio.setTextColor(Color.parseColor("#94A3B8"))
                binding.tvStock.text = "Sin stock (0)"
                binding.tvStock.setTextColor(Color.parseColor("#DC2626"))
            } else {
                binding.cardProducto.isEnabled = true
                binding.cardProducto.isClickable = true
                binding.cardProducto.alpha = 1.0f

                if (cantidadAgregada > 0) {
                    binding.cardProducto.setCardBackgroundColor(Color.parseColor("#F0FDF4")) // Tinte verde suave si está en carrito
                    binding.cardProducto.strokeColor = Color.parseColor("#86EFAC")
                } else {
                    binding.cardProducto.setCardBackgroundColor(Color.parseColor("#FFFFFF"))
                    binding.cardProducto.strokeColor = Color.parseColor("#E2E8F0")
                }

                binding.tvNombreProducto.setTextColor(Color.parseColor("#0F172A"))
                binding.tvPrecio.setTextColor(Color.parseColor("#1E3A8A"))

                val disponible = producto.stockActual - cantidadAgregada
                binding.tvStock.text = "Stock: ${producto.stockActual} (disp: $disponible)"
                binding.tvStock.setTextColor(if (disponible <= 0) Color.parseColor("#DC2626") else Color.parseColor("#475569"))

                binding.cardProducto.setOnClickListener {
                    onProductoClick(producto)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductoGridViewHolder {
        val binding = ItemProductoGridBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ProductoGridViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProductoGridViewHolder, position: Int) {
        holder.bind(productos[position])
    }

    override fun getItemCount(): Int = productos.size

    fun reemplazarProductos(nuevos: List<Producto>) {
        productos.clear()
        productos.addAll(nuevos)
        notifyDataSetChanged()
    }

    fun agregarProductos(nuevos: List<Producto>) {
        val posicionInicio = productos.size
        productos.addAll(nuevos)
        notifyItemRangeInserted(posicionInicio, nuevos.size)
    }

    fun actualizarCantidadesVenta(cantidades: Map<Int, Int>) {
        cantidadesEnVenta.clear()
        cantidadesEnVenta.putAll(cantidades)
        notifyDataSetChanged()
    }

    fun obtenerLista(): List<Producto> = productos
}

