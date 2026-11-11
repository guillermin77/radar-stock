package com.radarstock.app.adapters

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.radarstock.app.databinding.ItemConteoProductoBinding
import com.radarstock.app.models.Producto

/**
 * Representa un producto en la lista de conteo activo.
 */
data class ItemConteo(
    val producto: Producto,
    var contado: Int = producto.stockActual,
    var resaltar: Boolean = false
) {
    val diferencia: Int
        get() = contado - producto.stockActual
}

/**
 * Adaptador para listar los productos en el Conteo de Inventario.
 * Gestiona el cálculo en vivo de diferencias, resaltado de duplicados y eliminación de fila.
 */
class ConteoAdapter(
    private val onEliminarItem: (Int) -> Unit,
    private val onCambioValor: () -> Unit
) : RecyclerView.Adapter<ConteoAdapter.ConteoViewHolder>() {

    val items = mutableListOf<ItemConteo>()

    inner class ConteoViewHolder(val binding: ItemConteoProductoBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var watcherActual: TextWatcher? = null

        fun bind(item: ItemConteo) {
            binding.tvNombreProducto.text = item.producto.nombreProducto
            binding.tvStockSistema.text = "Stock en sistema: ${item.producto.stockActual} uds"

            val cod = item.producto.codigoBarras
            if (!cod.isNullOrBlank()) {
                binding.tvCodigoBarras.text = "Cód: $cod"
                binding.tvCodigoBarras.visibility = View.VISIBLE
            } else {
                binding.tvCodigoBarras.visibility = View.GONE
            }

            // Resaltado si se escaneó por segunda vez
            if (item.resaltar) {
                binding.cardItemConteo.setCardBackgroundColor(Color.parseColor("#FEF3C7")) // Ámbar claro
                binding.cardItemConteo.strokeColor = Color.parseColor("#EAB308")
                binding.cardItemConteo.strokeWidth = 6
            } else {
                binding.cardItemConteo.setCardBackgroundColor(Color.parseColor("#FFFFFF"))
                binding.cardItemConteo.strokeColor = Color.parseColor("#E2E8F0")
                binding.cardItemConteo.strokeWidth = 2
            }

            // Eliminar watcher previo para evitar callbacks erróneos en reciclado
            watcherActual?.let { binding.etContado.removeTextChangedListener(it) }

            // Cargar valor actual de contado
            binding.etContado.setText(item.contado.toString())

            // Crear y adjuntar nuevo watcher
            val nuevoWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val nuevoContado = s?.toString()?.toIntOrNull() ?: 0
                    if (item.contado != nuevoContado) {
                        item.contado = nuevoContado
                        actualizarVistaDiferencia(item.diferencia)
                        onCambioValor()
                    }
                }
                override fun afterTextChanged(s: Editable?) {}
            }
            watcherActual = nuevoWatcher
            binding.etContado.addTextChangedListener(nuevoWatcher)

            // Actualizar vista de diferencia según estado actual
            actualizarVistaDiferencia(item.diferencia)

            // Botón Quitar del conteo
            binding.btnEliminarItem.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onEliminarItem(pos)
                }
            }
        }

        private fun actualizarVistaDiferencia(diferencia: Int) {
            val fondo = GradientDrawable().apply {
                cornerRadius = 12f
            }

            when {
                // Exacto: VERDE (#16A34A)
                diferencia == 0 -> {
                    fondo.setColor(Color.parseColor("#DCFCE7"))
                    binding.tvDiferenciaBadge.background = fondo
                    binding.tvDiferenciaBadge.setTextColor(Color.parseColor("#16A34A"))
                    binding.tvDiferenciaBadge.text = "Diferencia: 0"

                    binding.tvEstadoDiferencia.text = "Stock exacto"
                    binding.tvEstadoDiferencia.setTextColor(Color.parseColor("#16A34A"))
                }
                // Falta: ROJO (#DC2626)
                diferencia < 0 -> {
                    fondo.setColor(Color.parseColor("#FEE2E2"))
                    binding.tvDiferenciaBadge.background = fondo
                    binding.tvDiferenciaBadge.setTextColor(Color.parseColor("#DC2626"))
                    binding.tvDiferenciaBadge.text = "Diferencia: $diferencia"

                    binding.tvEstadoDiferencia.text = "Faltan ${-diferencia} uds"
                    binding.tvEstadoDiferencia.setTextColor(Color.parseColor("#DC2626"))
                }
                // Sobra: AZUL (#2563EB)
                else -> {
                    fondo.setColor(Color.parseColor("#DBEAFE"))
                    binding.tvDiferenciaBadge.background = fondo
                    binding.tvDiferenciaBadge.setTextColor(Color.parseColor("#2563EB"))
                    binding.tvDiferenciaBadge.text = "Diferencia: +$diferencia"

                    binding.tvEstadoDiferencia.text = "Sobran $diferencia uds"
                    binding.tvEstadoDiferencia.setTextColor(Color.parseColor("#2563EB"))
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConteoViewHolder {
        val binding = ItemConteoProductoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ConteoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ConteoViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}

