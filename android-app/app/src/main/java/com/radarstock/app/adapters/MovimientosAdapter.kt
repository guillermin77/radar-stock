package com.radarstock.app.adapters

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.radarstock.app.databinding.ItemMovimientoBinding
import com.radarstock.app.models.MovimientoStock
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Adaptador para listar el historial de movimientos de stock en el RecyclerView.
 * Formatea fechas a hora de Chile (dd-MM-yyyy HH:mm) y colorea etiquetas y diferencias.
 */
class MovimientosAdapter(
    private val onMovimientoClick: ((MovimientoStock) -> Unit)? = null
) : RecyclerView.Adapter<MovimientosAdapter.MovimientoViewHolder>() {

    private val lista = mutableListOf<MovimientoStock>()

    private val formatoEntrada = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("America/Santiago")
    }

    private val formatoSalidaChile = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("America/Santiago")
    }

    inner class MovimientoViewHolder(private val binding: ItemMovimientoBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(mov: MovimientoStock) {
            // 1. Fecha formateada a hora de Chile (dd-MM-yyyy HH:mm)
            val fechaFormateada = try {
                val date = formatoEntrada.parse(mov.fecha)
                if (date != null) formatoSalidaChile.format(date) else mov.fecha
            } catch (_: Exception) {
                mov.fecha
            }
            binding.tvFecha.text = fechaFormateada

            // 2. Etiqueta del tipo de movimiento
            val (etiquetaTexto, colorTipo) = when (mov.tipo.lowercase()) {
                "ingreso" -> Pair("INGRESO", Color.parseColor("#15803D")) // Verde oscuro
                "conteo" -> Pair("CONTEO", Color.parseColor("#7C3AED"))  // Violeta
                "ajuste" -> Pair("AJUSTE", Color.parseColor("#EA580C"))  // Naranja oscuro
                else -> Pair(mov.tipo.uppercase(), Color.parseColor("#475569"))
            }
            binding.tvTipoBadge.text = etiquetaTexto
            binding.tvTipoBadge.backgroundTintList = ColorStateList.valueOf(colorTipo)
            binding.tvTipoBadge.setTextColor(Color.WHITE)

            // 3. Información del Producto
            binding.tvNombreProducto.text = mov.nombreProducto

            if (!mov.codigoBarras.isNullOrBlank()) {
                binding.tvCodigoBarras.text = "Código: ${mov.codigoBarras}"
                binding.tvCodigoBarras.visibility = View.VISIBLE
            } else {
                binding.tvCodigoBarras.visibility = View.GONE
            }

            // 4. Usuario responsable
            binding.tvUsuario.text = "Realizado por: ${mov.usuario}"

            // 5. Transición de Stock (antes → después)
            binding.tvTransicionStock.text = "Stock: ${mov.stockAntes} → ${mov.stockDespues}"

            // 6. Diferencia con color condicional
            val dif = mov.diferencia
            when {
                dif > 0 -> {
                    binding.tvDiferenciaBadge.text = "+$dif"
                    binding.tvDiferenciaBadge.setTextColor(Color.parseColor("#15803D"))
                    binding.tvDiferenciaBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#DCFCE7"))
                }
                dif < 0 -> {
                    binding.tvDiferenciaBadge.text = "$dif"
                    binding.tvDiferenciaBadge.setTextColor(Color.parseColor("#DC2626"))
                    binding.tvDiferenciaBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEE2E2"))
                }
                else -> {
                    binding.tvDiferenciaBadge.text = "0"
                    binding.tvDiferenciaBadge.setTextColor(Color.parseColor("#64748B"))
                    binding.tvDiferenciaBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F1F5F9"))
                }
            }

            // Listener de clic si aplica
            binding.root.setOnClickListener {
                onMovimientoClick?.invoke(mov)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MovimientoViewHolder {
        val binding = ItemMovimientoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MovimientoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MovimientoViewHolder, position: Int) {
        holder.bind(lista[position])
    }

    override fun getItemCount(): Int = lista.size

    fun actualizarLista(nuevos: List<MovimientoStock>) {
        lista.clear()
        lista.addAll(nuevos)
        notifyDataSetChanged()
    }

    fun agregarPagina(nuevos: List<MovimientoStock>) {
        val inicio = lista.size
        lista.addAll(nuevos)
        notifyItemRangeInserted(inicio, nuevos.size)
    }

    fun limpiar() {
        lista.clear()
        notifyDataSetChanged()
    }
}

