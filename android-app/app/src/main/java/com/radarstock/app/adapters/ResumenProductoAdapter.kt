package com.radarstock.app.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.radarstock.app.databinding.ItemResumenProductoBinding
import com.radarstock.app.models.ProductoVendido
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Adaptador para listar los 10 productos más vendidos en el resumen de ventas.
 */
class ResumenProductoAdapter :
    RecyclerView.Adapter<ResumenProductoAdapter.ResumenProductoViewHolder>() {

    private val listaProductos = mutableListOf<ProductoVendido>()

    private val formatoMoneda = NumberFormat.getCurrencyInstance(Locale("es", "CL")).apply {
        maximumFractionDigits = 0
        currency = Currency.getInstance("CLP")
    }

    inner class ResumenProductoViewHolder(private val binding: ItemResumenProductoBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(producto: ProductoVendido, posicion: Int) {
            binding.tvRanking.text = "#${posicion + 1}"
            binding.tvNombreProductoTop.text = producto.nombre
            binding.tvUnidadesTop.text = if (producto.unidades == 1) "1 unidad vendida" else "${producto.unidades} unidades vendidas"
            binding.tvMontoTop.text = formatoMoneda.format(producto.monto)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResumenProductoViewHolder {
        val binding = ItemResumenProductoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ResumenProductoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ResumenProductoViewHolder, position: Int) {
        holder.bind(listaProductos[position], position)
    }

    override fun getItemCount(): Int = listaProductos.size

    fun actualizarLista(nuevos: List<ProductoVendido>) {
        listaProductos.clear()
        listaProductos.addAll(nuevos)
        notifyDataSetChanged()
    }
}

