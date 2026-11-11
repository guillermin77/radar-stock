package com.radarstock.app.adapters

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.radarstock.app.databinding.ItemCompararVendedorBinding
import com.radarstock.app.models.VendedorComparativo
import com.radarstock.app.utils.AuditoriaVentasHelper

/**
 * Adaptador para listar el comparativo de ventas por vendedor ordenado por monto.
 */
class CompararVendedoresAdapter(
    private val onVendedorClick: (VendedorComparativo) -> Unit
) : RecyclerView.Adapter<CompararVendedoresAdapter.CompararViewHolder>() {

    private val lista = mutableListOf<VendedorComparativo>()

    inner class CompararViewHolder(private val binding: ItemCompararVendedorBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: VendedorComparativo, posicion: Int) {
            binding.tvRankingVendedor.text = "#${posicion + 1}"
            binding.tvNombreVendedor.text = item.nombre
            binding.tvRolVendedor.text = item.rol

            val fondoEstado = GradientDrawable().apply {
                cornerRadius = 14f
                setColor(
                    if (item.esActivo) Color.parseColor("#DCFCE7") else Color.parseColor("#FEE2E2")
                )
            }
            binding.tvEstadoVendedor.background = fondoEstado
            binding.tvEstadoVendedor.text = if (item.esActivo) "Activo" else "Inactivo"
            binding.tvEstadoVendedor.setTextColor(
                if (item.esActivo) Color.parseColor("#15803D") else Color.parseColor("#B91C1C")
            )

            binding.tvTotalVendidoVendedor.text = AuditoriaVentasHelper.formatearMonedaClp(item.totalVendido)
            binding.tvCantidadVentasVendedor.text =
                if (item.cantidadVentas == 1) "1 venta" else "${item.cantidadVentas} ventas"
            binding.tvTicketPromedioVendedor.text =
                "Ticket prom.: ${AuditoriaVentasHelper.formatearMonedaClp(item.ticketPromedio)}"

            binding.root.setOnClickListener {
                onVendedorClick(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CompararViewHolder {
        val binding = ItemCompararVendedorBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CompararViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CompararViewHolder, position: Int) {
        holder.bind(lista[position], position)
    }

    override fun getItemCount(): Int = lista.size

    fun actualizarLista(nuevos: List<VendedorComparativo>) {
        lista.clear()
        lista.addAll(nuevos)
        notifyDataSetChanged()
    }
}

