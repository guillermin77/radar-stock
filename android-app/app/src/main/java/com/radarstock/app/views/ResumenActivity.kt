package com.radarstock.app.views

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.radarstock.app.adapters.ResumenProductoAdapter
import com.radarstock.app.databinding.ActivityResumenBinding
import com.radarstock.app.models.RespuestaApi
import com.radarstock.app.models.ResumenResponse
import com.radarstock.app.network.RetrofitClient
import com.radarstock.app.utils.SessionManager
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Currency
import java.util.Locale

/**
 * Pantalla de Resumen de Ventas y Métricas Financieras.
 * Acceso exclusivo para rol Administrador.
 * Permite filtrar por Hoy, 7 días, Este mes o Rango personalizado de fechas.
 */
class ResumenActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResumenBinding
    private val productoAdapter = ResumenProductoAdapter()

    // Filtros activos
    private var periodoSeleccionado: String = "hoy"
    private var fechaDesde: String = ""
    private var fechaHasta: String = ""

    private val formatoMoneda = NumberFormat.getCurrencyInstance(Locale("es", "CL")).apply {
        maximumFractionDigits = 0
        currency = Currency.getInstance("CLP")
    }

    private val formatoFecha = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val calendario = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Validar permisos: solo rol Administrador
        val rol = SessionManager.obtenerRol() ?: ""
        if (!rol.equals("Administrador", ignoreCase = true)) {
            Toast.makeText(this, "Acceso denegado: solo Administrador", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding = ActivityResumenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        inicializarFechasPorDefecto()
        configurarToolbar()
        configurarRecyclerView()
        configurarFiltrosPeriodo()
        configurarSelectoresFecha()
        configurarEventos()

        // Cargar por defecto las ventas de Hoy
        cargarResumen(periodo = "hoy")
    }

    private fun configurarToolbar() {
        setSupportActionBar(binding.toolbarResumen)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        binding.toolbarResumen.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun configurarRecyclerView() {
        binding.rvTopProductos.apply {
            layoutManager = LinearLayoutManager(this@ResumenActivity)
            adapter = productoAdapter
        }
    }

    private fun inicializarFechasPorDefecto() {
        val hoy = formatoFecha.format(calendario.time)
        fechaDesde = hoy
        fechaHasta = hoy
        actualizarBotonesFecha()
    }

    private fun actualizarBotonesFecha() {
        binding.btnFechaDesde.text = "Desde: $fechaDesde"
        binding.btnFechaHasta.text = "Hasta: $fechaHasta"
    }

    private fun configurarFiltrosPeriodo() {
        binding.chipGroupPeriodo.setOnCheckedStateChangeListener { _, checkedIds ->
            when {
                checkedIds.contains(binding.chipHoy.id) -> {
                    periodoSeleccionado = "hoy"
                    binding.layoutSelectorRango.visibility = View.GONE
                    cargarResumen(periodo = "hoy")
                }
                checkedIds.contains(binding.chip7Dias.id) -> {
                    periodoSeleccionado = "7dias"
                    binding.layoutSelectorRango.visibility = View.GONE
                    cargarResumen(periodo = "7dias")
                }
                checkedIds.contains(binding.chipMes.id) -> {
                    periodoSeleccionado = "mes"
                    binding.layoutSelectorRango.visibility = View.GONE
                    cargarResumen(periodo = "mes")
                }
                checkedIds.contains(binding.chipRango.id) -> {
                    periodoSeleccionado = "rango"
                    binding.layoutSelectorRango.visibility = View.VISIBLE
                    // Cargar con las fechas por defecto del rango
                    cargarResumen(desde = fechaDesde, hasta = fechaHasta)
                }
            }
        }
    }

    private fun configurarSelectoresFecha() {
        binding.btnFechaDesde.setOnClickListener {
            mostrarDatePicker(esDesde = true)
        }

        binding.btnFechaHasta.setOnClickListener {
            mostrarDatePicker(esDesde = false)
        }

        binding.btnAplicarRango.setOnClickListener {
            if (fechaDesde > fechaHasta) {
                Toast.makeText(
                    this,
                    "La fecha de inicio no puede ser mayor que la de término.",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            cargarResumen(desde = fechaDesde, hasta = fechaHasta)
        }
    }

    private fun mostrarDatePicker(esDesde: Boolean) {
        val dp = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val cal = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth)
                }
                val fechaStr = formatoFecha.format(cal.time)
                if (esDesde) {
                    fechaDesde = fechaStr
                } else {
                    fechaHasta = fechaStr
                }
                actualizarBotonesFecha()
            },
            calendario.get(Calendar.YEAR),
            calendario.get(Calendar.MONTH),
            calendario.get(Calendar.DAY_OF_MONTH)
        )
        dp.show()
    }

    private fun configurarEventos() {
        binding.btnReintentarResumen.setOnClickListener {
            if (periodoSeleccionado == "rango") {
                cargarResumen(desde = fechaDesde, hasta = fechaHasta)
            } else {
                cargarResumen(periodo = periodoSeleccionado)
            }
        }
    }

    /**
     * Consulta a api/resumen.php y actualiza los indicadores y la lista de los 10 productos más vendidos
     */
    private fun cargarResumen(
        periodo: String? = null,
        desde: String? = null,
        hasta: String? = null
    ) {
        mostrarCargando(true)

        lifecycleScope.launch {
            try {
                val respuesta: ResumenResponse = if (desde != null && hasta != null) {
                    RetrofitClient.apiService.getResumen(desde = desde, hasta = hasta)
                } else {
                    RetrofitClient.apiService.getResumen(periodo = periodo ?: "hoy")
                }

                mostrarCargando(false)
                mostrarDatos(respuesta)

            } catch (e: HttpException) {
                mostrarCargando(false)
                val mensajeApi = extraerMensajeDeError(e)
                mostrarError("Error del servidor: $mensajeApi")

            } catch (_: IOException) {
                mostrarCargando(false)
                mostrarError("No se pudo conectar con el servidor. Verifique su conexión.")

            } catch (e: Exception) {
                mostrarCargando(false)
                mostrarError("Error inesperado: ${e.message}")
            }
        }
    }

    private fun mostrarDatos(resumen: ResumenResponse) {
        binding.layoutTarjetasResumen.visibility = View.VISIBLE
        binding.layoutErrorConexion.visibility = View.GONE

        // Monto y cantidad formateados
        binding.tvTotalVendidoMonto.text = formatoMoneda.format(resumen.totalVendido)
        binding.tvCantidadVentasTotal.text = if (resumen.cantidadVentas == 1) "1 venta" else "${resumen.cantidadVentas} ventas"

        // Rango de fechas
        binding.tvRangoFechasActivo.text = "Período: ${resumen.fechaInicio} al ${resumen.fechaFin}"

        val listaProductos = resumen.productosMasVendidos

        if (resumen.cantidadVentas == 0 && listaProductos.isEmpty()) {
            binding.layoutSinVentas.visibility = View.VISIBLE
            binding.rvTopProductos.visibility = View.GONE
            binding.tvTituloTop.visibility = View.GONE
            binding.tvRangoFechasActivo.visibility = View.GONE
        } else {
            binding.layoutSinVentas.visibility = View.GONE
            binding.rvTopProductos.visibility = View.VISIBLE
            binding.tvTituloTop.visibility = View.VISIBLE
            binding.tvRangoFechasActivo.visibility = View.VISIBLE
            productoAdapter.actualizarLista(listaProductos)
        }
    }

    private fun mostrarCargando(cargando: Boolean) {
        binding.pbCargandoResumen.visibility = if (cargando) View.VISIBLE else View.GONE
        if (cargando) {
            binding.layoutSinVentas.visibility = View.GONE
            binding.layoutErrorConexion.visibility = View.GONE
        }
    }

    private fun mostrarError(mensaje: String) {
        binding.layoutErrorConexion.visibility = View.VISIBLE
        binding.tvMensajeErrorResumen.text = mensaje
        binding.layoutSinVentas.visibility = View.GONE
        binding.rvTopProductos.visibility = View.GONE
        binding.tvTituloTop.visibility = View.GONE
        binding.tvRangoFechasActivo.visibility = View.GONE
    }

    private fun extraerMensajeDeError(e: HttpException): String {
        return try {
            val cuerpoError = e.response()?.errorBody()?.string()
            if (!cuerpoError.isNullOrBlank()) {
                val apiResp = Gson().fromJson(cuerpoError, RespuestaApi::class.java)
                apiResp.mensaje ?: "Error del servidor (${e.code()})"
            } else {
                "Error del servidor (${e.code()})"
            }
        } catch (_: Exception) {
            "Error de comunicación (${e.code()})"
        }
    }
}

