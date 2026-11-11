package com.radarstock.app.views

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.radarstock.app.adapters.CompararVendedoresAdapter
import com.radarstock.app.databinding.ActivityCompararVendedoresBinding
import com.radarstock.app.models.CompararVendedoresResponse
import com.radarstock.app.network.RetrofitClient
import com.radarstock.app.utils.AuditoriaVentasHelper
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Pantalla para comparar el volumen y monto de ventas de todos los vendedores (Solo Administrador).
 * Al tocar un vendedor abre su pantalla de detalle VentasVendedorActivity conservando el período seleccionado.
 */
class CompararVendedoresActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCompararVendedoresBinding
    private lateinit var adapter: CompararVendedoresAdapter

    private var rangoActual: String? = "hoy"
    private var fechaDesde: String = ""
    private var fechaHasta: String = ""

    private val zonaChile = TimeZone.getTimeZone("America/Santiago")
    private val formatoIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = zonaChile
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!AuditoriaVentasHelper.validarAccesoAdministrador(this)) return

        binding = ActivityCompararVendedoresBinding.inflate(layoutInflater)
        setContentView(binding.root)

        inicializarFechasPorDefecto()
        configurarToolbar()
        configurarRecyclerView()
        configurarChipsPeriodo()
        configurarSelectoresRango()
        configurarEventos()

        cargarComparativo()
    }

    private fun inicializarFechasPorDefecto() {
        val hoy = formatoIso.format(Calendar.getInstance(zonaChile).time)
        fechaDesde = hoy
        fechaHasta = hoy
        actualizarTextosFechas()
    }

    private fun actualizarTextosFechas() {
        binding.btnFechaDesde.text = "Desde: $fechaDesde"
        binding.btnFechaHasta.text = "Hasta: $fechaHasta"
    }

    private fun configurarToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun configurarRecyclerView() {
        adapter = CompararVendedoresAdapter { vendedor ->
            val intent = Intent(this, VentasVendedorActivity::class.java).apply {
                putExtra("usuario_id", vendedor.usuarioId)
                putExtra("usuario_nombre", vendedor.nombre)
                putExtra("usuario_rol", vendedor.rol)
                putExtra("usuario_activo", vendedor.activo)
                if (rangoActual != null) {
                    putExtra("rango", rangoActual)
                } else {
                    putExtra("desde", fechaDesde)
                    putExtra("hasta", fechaHasta)
                }
            }
            startActivity(intent)
        }
        binding.rvVendedores.layoutManager = LinearLayoutManager(this)
        binding.rvVendedores.adapter = adapter
    }

    private fun configurarChipsPeriodo() {
        binding.chipGroupPeriodo.setOnCheckedStateChangeListener { _, checkedIds ->
            when {
                checkedIds.contains(binding.chipHoy.id) -> {
                    rangoActual = "hoy"
                    binding.layoutSelectorRango.visibility = View.GONE
                    cargarComparativo()
                }
                checkedIds.contains(binding.chip7Dias.id) -> {
                    rangoActual = "7dias"
                    binding.layoutSelectorRango.visibility = View.GONE
                    cargarComparativo()
                }
                checkedIds.contains(binding.chipMes.id) -> {
                    rangoActual = "mes"
                    binding.layoutSelectorRango.visibility = View.GONE
                    cargarComparativo()
                }
                checkedIds.contains(binding.chipRango.id) -> {
                    rangoActual = null
                    binding.layoutSelectorRango.visibility = View.VISIBLE
                    cargarComparativo()
                }
            }
        }
    }

    private fun configurarSelectoresRango() {
        binding.btnFechaDesde.setOnClickListener { mostrarDatePicker(esDesde = true) }
        binding.btnFechaHasta.setOnClickListener { mostrarDatePicker(esDesde = false) }
        binding.btnAplicarRango.setOnClickListener {
            if (fechaDesde > fechaHasta) {
                Toast.makeText(
                    this,
                    "La fecha de inicio no puede ser mayor que la de término.",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            cargarComparativo()
        }
    }

    private fun mostrarDatePicker(esDesde: Boolean) {
        val cal = Calendar.getInstance(zonaChile)
        val fechaTexto = if (esDesde) fechaDesde else fechaHasta
        try {
            formatoIso.parse(fechaTexto)?.let { cal.time = it }
        } catch (_: Exception) {
        }

        val dp = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val seleccionado = Calendar.getInstance(zonaChile).apply {
                    set(year, month, dayOfMonth, 0, 0, 0)
                }
                val fechaStr = formatoIso.format(seleccionado.time)
                if (esDesde) fechaDesde = fechaStr else fechaHasta = fechaStr
                actualizarTextosFechas()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )
        dp.datePicker.maxDate = System.currentTimeMillis()
        dp.show()
    }

    private fun configurarEventos() {
        binding.btnReintentar.setOnClickListener {
            cargarComparativo()
        }
        binding.btnExportarCsv.setOnClickListener {
            val rangoParam = rangoActual
            val desdeParam = if (rangoActual == null) fechaDesde else null
            val hastaParam = if (rangoActual == null) fechaHasta else null
            AuditoriaVentasHelper.descargarYCompartirCsv(
                activity = this,
                scope = lifecycleScope,
                usuarioId = null,
                rango = rangoParam,
                desde = desdeParam,
                hasta = hastaParam
            ) { exportando ->
                binding.btnExportarCsv.isEnabled = !exportando
                binding.btnExportarCsv.text = if (exportando) "Generando CSV..." else "Exportar CSV"
            }
        }
    }

    private fun cargarComparativo() {
        binding.progressBar.visibility = View.VISIBLE
        binding.layoutError.visibility = View.GONE
        binding.tvEmpty.visibility = View.GONE
        binding.rvVendedores.visibility = View.GONE
        binding.cardTotalGeneral.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val resp = RetrofitClient.apiService.getCompararVendedores(
                    rango = rangoActual,
                    desde = if (rangoActual == null) fechaDesde else null,
                    hasta = if (rangoActual == null) fechaHasta else null
                )

                binding.progressBar.visibility = View.GONE
                mostrarResultado(resp)
            } catch (e: HttpException) {
                binding.progressBar.visibility = View.GONE
                if (e.code() == 401) {
                    AuditoriaVentasHelper.redirigirALoginPorSesionExpirada(this@CompararVendedoresActivity)
                    return@launch
                }
                mostrarError(AuditoriaVentasHelper.extraerMensajeErrorHttp(e))
            } catch (e: IOException) {
                binding.progressBar.visibility = View.GONE
                mostrarError("Error de conexión. Verifica tu internet.")
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                mostrarError("Error inesperado: ${e.message}")
            }
        }
    }

    private fun mostrarResultado(resp: CompararVendedoresResponse) {
        binding.cardTotalGeneral.visibility = View.VISIBLE
        binding.tvTotalGeneralMonto.text = AuditoriaVentasHelper.formatearMonedaClp(resp.totalGeneral)
        binding.tvCantidadTotalVentas.text =
            if (resp.cantidadVentasTotal == 1) "1 venta total" else "${resp.cantidadVentasTotal} ventas totales"

        if (resp.vendedores.isEmpty()) {
            adapter.actualizarLista(emptyList())
            binding.rvVendedores.visibility = View.GONE
            binding.tvEmpty.visibility = View.VISIBLE
        } else {
            adapter.actualizarLista(resp.vendedores)
            binding.tvEmpty.visibility = View.GONE
            binding.rvVendedores.visibility = View.VISIBLE
        }
    }

    private fun mostrarError(mensaje: String) {
        binding.cardTotalGeneral.visibility = View.GONE
        binding.rvVendedores.visibility = View.GONE
        binding.tvEmpty.visibility = View.GONE
        binding.layoutError.visibility = View.VISIBLE
        binding.tvMensajeError.text = mensaje
    }
}

