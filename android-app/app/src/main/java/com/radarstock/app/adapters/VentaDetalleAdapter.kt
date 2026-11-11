package com.radarstock.app.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.radarstock.app.databinding.ItemVentaDetalleBinding
import com.radarstock.app.models.ItemVenta
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Adaptador para mostrar la lista desplegable de productos agregados a la venta.
 * Permite incrementar, reducir o quitar productos con controles táctiles amplios.
 */
class VentaDetalleAdapter(
    private val onSumar: (ItemVenta) -> Unit,
    private val onRestar: (ItemVenta) -> Unit,
    private val onEliminar: (ItemVenta) -> Unit
) : RecyclerView.Adapter<VentaDetalleAdapter.VentaDetalleViewHolder>() {

    private val items = mutableListOf<ItemVenta>()

    private val formatoMoneda = NumberFormat.getCurrencyInstance(Locale("es", "CL")).apply {
        maximumFractionDigits = 0
        currency = Currency.getInstance("CLP")
    }

    inner class VentaDetalleViewHolder(private val binding: ItemVentaDetalleBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ItemVenta) {
            binding.tvNombreItem.text = item.producto.nombreProducto
            binding.tvPrecioUnitarioItem.text = "${formatoMoneda.format(item.producto.precioVenta)} c/u"
            binding.tvSubtotalItem.text = formatoMoneda.format(item.subtotal)
            binding.tvCantidadItem.text = item.cantidad.toString()

            binding.btnSumar.setOnClickListener {
                onSumar(item)
            }

            binding.btnRestar.setOnClickListener {
                onRestar(item)
            }

            binding.btnQuitarItem.setOnClickListener {
                onEliminar(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VentaDetalleViewHolder {
        val binding = ItemVentaDetalleBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VentaDetalleViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VentaDetalleViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun actualizarItems(nuevos: List<ItemVenta>) {
        items.clear()
        items.addAll(nuevos)
        notifyDataSetChanged()
    }
}

