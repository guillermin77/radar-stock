package com.radarstock.app.adapters

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.radarstock.app.R
import com.radarstock.app.databinding.ItemAlertaBinding
import com.radarstock.app.models.Alerta

/**
 * Adaptador para listar las alertas de inventario en el RecyclerView.
 * Aplica diseño diferenciado por severidad (icono, etiqueta textual y color).
 */
class AlertasAdapter(
    private val onAlertaClick: (Alerta) -> Unit
) : RecyclerView.Adapter<AlertasAdapter.AlertaViewHolder>() {

    private val alertas = mutableListOf<Alerta>()

    inner class AlertaViewHolder(private val binding: ItemAlertaBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(alerta: Alerta) {
            binding.tvNombreProducto.text = alerta.nombreProducto
            binding.tvMensajeAlerta.text = alerta.mensaje

            // Configurar icono, etiqueta textual y color según el nivel de severidad
            val configSeveridad = obtenerConfigSeveridad(alerta.tipo)

            binding.ivIconoAlerta.setImageResource(configSeveridad.iconoRes)
            binding.ivIconoAlerta.imageTintList = ColorStateList.valueOf(configSeveridad.colorPrincipal)
            binding.layoutIconoAlerta.backgroundTintList = ColorStateList.valueOf(configSeveridad.colorFondoIcono)

            binding.tvEtiquetaTipo.text = configSeveridad.etiquetaTexto
            binding.tvEtiquetaTipo.backgroundTintList = ColorStateList.valueOf(configSeveridad.colorPrincipal)
            binding.tvEtiquetaTipo.setTextColor(Color.WHITE)

            binding.cardAlerta.strokeColor = configSeveridad.colorBorde

            // Mostrar detalles contextuales
            when (alerta.tipo.lowercase()) {
                "vencido", "por_vencer" -> {
                    binding.tvDetalleStock.text = "Stock: ${alerta.stockActual} unid."
                    binding.tvDetalleStock.visibility = View.VISIBLE

                    val dias = alerta.diasParaVencer
                    val fecha = alerta.fechaVencimiento ?: ""
                    binding.tvDetalleVencimiento.text = when {
                        dias == null -> "Vencimiento: $fecha"
                        dias < 0 -> "Venció hace ${-dias} días ($fecha)"
                        dias == 0 -> "Vence hoy ($fecha)"
                        else -> "Vence en $dias días ($fecha)"
                    }
                    binding.tvDetalleVencimiento.visibility = View.VISIBLE
                }
                else -> {
                    // Alertas de stock (agotado, critico, bajo)
                    binding.tvDetalleStock.text = "Stock: ${alerta.stockActual} | Mín: ${alerta.stockMinimo} | Deseado: ${alerta.stockDeseado}"
                    binding.tvDetalleStock.visibility = View.VISIBLE
                    binding.tvDetalleVencimiento.visibility = View.GONE
                }
            }

            // Click listener
            binding.cardAlerta.setOnClickListener {
                onAlertaClick(alerta)
            }
        }
    }

    private data class SeveridadConfig(
        val iconoRes: Int,
        val etiquetaTexto: String,
        val colorPrincipal: Int,
        val colorFondoIcono: Int,
        val colorBorde: Int
    )

    private fun obtenerConfigSeveridad(tipo: String): SeveridadConfig {
        return when (tipo.lowercase()) {
            "vencido" -> SeveridadConfig(
                iconoRes = R.drawable.ic_alerta_vencido,
                etiquetaTexto = "[VENCIDO]",
                colorPrincipal = Color.parseColor("#DC2626"), // Rojo oscuro
                colorFondoIcono = Color.parseColor("#FEE2E2"),
                colorBorde = Color.parseColor("#FCA5A5")
            )
            "agotado" -> SeveridadConfig(
                iconoRes = R.drawable.ic_alerta_agotado,
                etiquetaTexto = "[SIN STOCK]",
                colorPrincipal = Color.parseColor("#EA580C"), // Naranja rojizo
                colorFondoIcono = Color.parseColor("#FFEDD5"),
                colorBorde = Color.parseColor("#FDBA74")
            )
            "critico" -> SeveridadConfig(
                iconoRes = R.drawable.ic_alerta_critico,
                etiquetaTexto = "[CRÍTICO]",
                colorPrincipal = Color.parseColor("#D97706"), // Ámbar / naranja
                colorFondoIcono = Color.parseColor("#FEF3C7"),
                colorBorde = Color.parseColor("#FCD34D")
            )
            "por_vencer" -> SeveridadConfig(
                iconoRes = R.drawable.ic_alerta_por_vencer,
                etiquetaTexto = "[POR VENCER]",
                colorPrincipal = Color.parseColor("#7C3AED"), // Violeta
                colorFondoIcono = Color.parseColor("#EDE9FE"),
                colorBorde = Color.parseColor("#C4B5FD")
            )
            "bajo" -> SeveridadConfig(
                iconoRes = R.drawable.ic_alerta_bajo,
                etiquetaTexto = "[STOCK BAJO]",
                colorPrincipal = Color.parseColor("#CA8A04"), // Amarillo oscuro
                colorFondoIcono = Color.parseColor("#FEF9C3"),
                colorBorde = Color.parseColor("#FEF08A")
            )
            else -> SeveridadConfig(
                iconoRes = R.drawable.ic_alerta_critico,
                etiquetaTexto = "[ALERTA]",
                colorPrincipal = Color.parseColor("#64748B"), // Gris pizarra
                colorFondoIcono = Color.parseColor("#F1F5F9"),
                colorBorde = Color.parseColor("#CBD5E1")
            )
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlertaViewHolder {
        val binding = ItemAlertaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AlertaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AlertaViewHolder, position: Int) {
        holder.bind(alertas[position])
    }

    override fun getItemCount(): Int = alertas.size

    fun actualizarLista(nuevasAlertas: List<Alerta>) {
        alertas.clear()
        alertas.addAll(nuevasAlertas)
        notifyDataSetChanged()
    }

    fun agregarPagina(nuevasAlertas: List<Alerta>) {
        val posicionInicio = alertas.size
        alertas.addAll(nuevasAlertas)
        notifyItemRangeInserted(posicionInicio, nuevasAlertas.size)
    }

    fun limpiar() {
        alertas.clear()
        notifyDataSetChanged()
    }
}

