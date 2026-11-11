package com.radarstock.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.radarstock.app.databinding.ItemVentaDetalleLineaBinding
import com.radarstock.app.databinding.ItemVentaVendedorBinding
import com.radarstock.app.models.VentaConDetalle
import com.radarstock.app.utils.AuditoriaVentasHelper

/**
 * Adaptador para listar las ventas paginadas de un vendedor con expansión de detalle al tocar.
 */
class VentasVendedorAdapter :
    RecyclerView.Adapter<VentasVendedorAdapter.VentaViewHolder>() {

    private val lista = mutableListOf<VentaConDetalle>()
    private val expandidas = mutableSetOf<Int>()

    inner class VentaViewHolder(private val binding: ItemVentaVendedorBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(venta: VentaConDetalle) {
            binding.tvFechaVenta.text = AuditoriaVentasHelper.formatearFechaChile(venta.fecha)

            val textoItems = if (venta.cantidadItems == 1) "1 ítem" else "${venta.cantidadItems} ítems"
            binding.tvMetaVenta.text = "Venta #${venta.id} • $textoItems"
            binding.tvTotalVenta.text = AuditoriaVentasHelper.formatearMonedaClp(venta.total)

            val estaExpandida = expandidas.contains(venta.id)
            binding.layoutDetalles.visibility = if (estaExpandida) View.VISIBLE else View.GONE
            binding.tvEstadoDetalle.text = if (estaExpandida) "Ocultar detalle ▲" else "Ver detalle ▼"

            if (estaExpandida) {
                renderizarDetalles(venta)
            } else {
                binding.containerRenglones.removeAllViews()
            }

            binding.root.setOnClickListener {
                if (expandidas.contains(venta.id)) {
                    expandidas.remove(venta.id)
                } else {
                    expandidas.add(venta.id)
                }
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    notifyItemChanged(pos)
                }
            }
        }

        private fun renderizarDetalles(venta: VentaConDetalle) {
            val container = binding.containerRenglones
            container.removeAllViews()
            val inflater = LayoutInflater.from(binding.root.context)

            if (venta.detalles.isEmpty()) {
                val itemBinding = ItemVentaDetalleLineaBinding.inflate(inflater, container, false)
                itemBinding.tvProductoDetalle.text = "Sin detalle registrado"
                itemBinding.tvCantidadPrecio.text = ""
                itemBinding.tvSubtotalDetalle.text = ""
                container.addView(itemBinding.root)
                return
            }

            for (det in venta.detalles) {
                val itemBinding = ItemVentaDetalleLineaBinding.inflate(inflater, container, false)
                itemBinding.tvProductoDetalle.text = det.producto
                val precioStr = AuditoriaVentasHelper.formatearMonedaClp(det.precioUnitario)
                itemBinding.tvCantidadPrecio.text = "${det.cantidad} x $precioStr"
                itemBinding.tvSubtotalDetalle.text = AuditoriaVentasHelper.formatearMonedaClp(det.subtotal)
                container.addView(itemBinding.root)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VentaViewHolder {
        val binding = ItemVentaVendedorBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VentaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VentaViewHolder, position: Int) {
        holder.bind(lista[position])
    }

    override fun getItemCount(): Int = lista.size

    fun reemplazarLista(nuevas: List<VentaConDetalle>) {
        lista.clear()
        expandidas.clear()
        lista.addAll(nuevas)
        notifyDataSetChanged()
    }

    fun agregarPagina(mas: List<VentaConDetalle>) {
        if (mas.isEmpty()) return
        val inicio = lista.size
        lista.addAll(mas)
        notifyItemRangeInserted(inicio, mas.size)
    }
}

