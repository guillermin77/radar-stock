package com.radarstock.app.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.radarstock.app.databinding.ItemProductoBinding
import com.radarstock.app.models.Producto
import java.text.NumberFormat
import java.util.Locale

/**
 * Adaptador para listar los productos en el RecyclerView usando ViewBinding.
 * Incluye soporte para clics en elementos y paginación incremental.
 */
class ProductoAdapter(
    private var onProductoClick: ((Producto) -> Unit)? = null
) : RecyclerView.Adapter<ProductoAdapter.ProductoViewHolder>() {

    private val productos = mutableListOf<Producto>()

    private val formatoMoneda = NumberFormat.getCurrencyInstance(Locale("es", "CL")).apply {
        maximumFractionDigits = 0
    }

    inner class ProductoViewHolder(private val binding: ItemProductoBinding) :
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

            // Stock actual: alerta en ROJO (#DC2626) si es 0
            when {
                producto.stockActual == 0 -> {
                    binding.tvStock.text = "Sin stock (0 unid.)"
                    binding.tvStock.setTextColor(Color.parseColor("#DC2626"))
                }
                producto.stockActual <= producto.stockMinimo -> {
                    binding.tvStock.text = "Stock bajo: ${producto.stockActual} unid. (mín. ${producto.stockMinimo})"
                    binding.tvStock.setTextColor(Color.parseColor("#D97706"))
                }
                else -> {
                    binding.tvStock.text = "Stock: ${producto.stockActual} unidades"
                    binding.tvStock.setTextColor(Color.parseColor("#334155"))
                }
            }

            // Precio formateado
            binding.tvPrecio.text = formatoMoneda.format(producto.precioVenta)

            // Evento clic en el producto
            binding.root.setOnClickListener {
                onProductoClick?.invoke(producto)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductoViewHolder {
        val binding = ItemProductoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ProductoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProductoViewHolder, position: Int) {
        holder.bind(productos[position])
    }

    override fun getItemCount(): Int = productos.size

    fun setOnProductoClickListener(listener: (Producto) -> Unit) {
        this.onProductoClick = listener
    }

    fun actualizarLista(nuevaLista: List<Producto>) {
        productos.clear()
        productos.addAll(nuevaLista)
        notifyDataSetChanged()
    }

    fun agregarProductos(nuevos: List<Producto>) {
        val posicionInicio = productos.size
        productos.addAll(nuevos)
        notifyItemRangeInserted(posicionInicio, nuevos.size)
    }

    fun obtenerLista(): List<Producto> = productos
}
