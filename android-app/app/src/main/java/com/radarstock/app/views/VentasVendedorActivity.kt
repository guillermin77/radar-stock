package com.radarstock.app.views

import android.app.DatePickerDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.radarstock.app.adapters.ResumenProductoAdapter
import com.radarstock.app.adapters.VentasVendedorAdapter
import com.radarstock.app.databinding.ActivityVentasVendedorBinding
import com.radarstock.app.models.VentasPorVendedorResponse
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
 * Pantalla de auditoría de ventas por vendedor (Solo Administrador).
 * Muestra métricas, Top productos más vendidos, lista paginada de ventas con detalle expandible y exportación CSV.
 */
class VentasVendedorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVentasVendedorBinding
    private val masVendidosAdapter = ResumenProductoAdapter()
    private val ventasAdapter = VentasVendedorAdapter()

    private var usuarioId: Int = -1
    private var rangoActual: String? = "hoy"
    private var fechaDesde: String = ""
    private var fechaHasta: String = ""

    private var paginaActual = 1
    private val porPagina = 30
    private var totalPaginas = 1
    private var cargando = false

    private val zonaChile = TimeZone.getTimeZone("America/Santiago")
    private val formatoIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = zonaChile
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!AuditoriaVentasHelper.validarAccesoAdministrador(this)) return

        usuarioId = intent.getIntExtra("usuario_id", -1)
        if (usuarioId <= 0) {
            Toast.makeText(this, "Vendedor no especificado", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding = ActivityVentasVendedorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        inicializarEncabezadoInicial()
        inicializarFechasPorDefecto()
        configurarToolbar()
        configurarRecyclerViews()
        configurarChipsPeriodo()
        configurarSelectoresRango()
        configurarEventos()
        aplicarFiltroInicialDesdeIntent()

        recargarTodo()
    }

    private fun inicializarEncabezadoInicial() {
        val nombre = intent.getStringExtra("usuario_nombre") ?: "Vendedor #$usuarioId"
        val rol = intent.getStringExtra("usuario_rol") ?: "Vendedor"
        val activo = intent.getIntExtra("usuario_activo", 1) == 1
        actualizarEncabezadoVendedor(nombre, rol, activo)
    }

    private fun actualizarEncabezadoVendedor(nombre: String, rol: String, activo: Boolean) {
        binding.tvNombreVendedorHeader.text = nombre
        binding.tvRolVendedorHeader.text = "Rol: $rol"

        val fondo = GradientDrawable().apply {
            cornerRadius = 16f
            setColor(if (activo) Color.parseColor("#DCFCE7") else Color.parseColor("#FEE2E2"))
        }
        binding.tvEstadoVendedorHeader.background = fondo
        binding.tvEstadoVendedorHeader.text = if (activo) "Activo" else "Inactivo"
        binding.tvEstadoVendedorHeader.setTextColor(
            if (activo) Color.parseColor("#15803D") else Color.parseColor("#B91C1C")
        )
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

    private fun aplicarFiltroInicialDesdeIntent() {
        val rangoExtra = intent.getStringExtra("rango")
        val desdeExtra = intent.getStringExtra("desde")
        val hastaExtra = intent.getStringExtra("hasta")

        when {
            !desdeExtra.isNullOrBlank() && !hastaExtra.isNullOrBlank() -> {
                rangoActual = null
                fechaDesde = desdeExtra
                fechaHasta = hastaExtra
                actualizarTextosFechas()
                binding.chipRango.isChecked = true
                binding.layoutSelectorRango.visibility = View.VISIBLE
            }
            rangoExtra == "7dias" -> {
                rangoActual = "7dias"
                binding.chip7Dias.isChecked = true
            }
            rangoExtra == "mes" -> {
                rangoActual = "mes"
                binding.chipMes.isChecked = true
            }
            else -> {
                rangoActual = "hoy"
                binding.chipHoy.isChecked = true
            }
        }
    }

    private fun configurarToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun configurarRecyclerViews() {
        binding.rvMasVendidos.apply {
            layoutManager = LinearLayoutManager(this@VentasVendedorActivity)
            adapter = masVendidosAdapter
        }
        binding.rvVentas.apply {
            layoutManager = LinearLayoutManager(this@VentasVendedorActivity)
            adapter = ventasAdapter
        }
    }

    private fun configurarChipsPeriodo() {
        binding.chipGroupPeriodo.setOnCheckedStateChangeListener { _, checkedIds ->
            when {
                checkedIds.contains(binding.chipHoy.id) -> {
                    rangoActual = "hoy"
                    binding.layoutSelectorRango.visibility = View.GONE
                    recargarTodo()
                }
                checkedIds.contains(binding.chip7Dias.id) -> {
                    rangoActual = "7dias"
                    binding.layoutSelectorRango.visibility = View.GONE
                    recargarTodo()
                }
                checkedIds.contains(binding.chipMes.id) -> {
                    rangoActual = "mes"
                    binding.layoutSelectorRango.visibility = View.GONE
                    recargarTodo()
                }
                checkedIds.contains(binding.chipRango.id) -> {
                    rangoActual = null
                    binding.layoutSelectorRango.visibility = View.VISIBLE
                    recargarTodo()
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
            recargarTodo()
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
            recargarTodo()
        }
        binding.btnCargarMas.setOnClickListener {
            if (!cargando && paginaActual < totalPaginas) {
                paginaActual++
                cargarVentas(esRecarga = false)
            }
        }
        binding.btnExportarCsv.setOnClickListener {
            val rangoParam = rangoActual
            val desdeParam = if (rangoActual == null) fechaDesde else null
            val hastaParam = if (rangoActual == null) fechaHasta else null
            AuditoriaVentasHelper.descargarYCompartirCsv(
                activity = this,
                scope = lifecycleScope,
                usuarioId = usuarioId,
                rango = rangoParam,
                desde = desdeParam,
                hasta = hastaParam
            ) { exportando ->
                binding.btnExportarCsv.isEnabled = !exportando
                binding.btnExportarCsv.text = if (exportando) "Generando CSV..." else "Exportar CSV"
            }
        }
    }

    private fun recargarTodo() {
        paginaActual = 1
        cargarVentas(esRecarga = true)
    }

    private fun cargarVentas(esRecarga: Boolean) {
        if (cargando) return
        cargando = true

        if (esRecarga) {
            binding.progressBar.visibility = View.VISIBLE
            binding.layoutError.visibility = View.GONE
            binding.layoutContenidoDatos.visibility = View.GONE
        } else {
            binding.progressBarMas.visibility = View.VISIBLE
            binding.btnCargarMas.isEnabled = false
        }

        lifecycleScope.launch {
            try {
                val resp = RetrofitClient.apiService.getVentasPorVendedor(
                    usuarioId = usuarioId,
                    rango = rangoActual,
                    desde = if (rangoActual == null) fechaDesde else null,
                    hasta = if (rangoActual == null) fechaHasta else null,
                    pagina = paginaActual,
                    porPagina = porPagina
                )

                cargando = false
                binding.progressBar.visibility = View.GONE
                binding.progressBarMas.visibility = View.GONE
                binding.btnCargarMas.isEnabled = true

                renderizarRespuesta(resp, esRecarga)
            } catch (e: HttpException) {
                cargando = false
                binding.progressBar.visibility = View.GONE
                binding.progressBarMas.visibility = View.GONE
                binding.btnCargarMas.isEnabled = true

                if (e.code() == 401) {
                    AuditoriaVentasHelper.redirigirALoginPorSesionExpirada(this@VentasVendedorActivity)
                    return@launch
                }

                val msg = AuditoriaVentasHelper.extraerMensajeErrorHttp(e)
                manejarErrorCarga(msg, esRecarga)
            } catch (e: IOException) {
                cargando = false
                binding.progressBar.visibility = View.GONE
                binding.progressBarMas.visibility = View.GONE
                binding.btnCargarMas.isEnabled = true
                manejarErrorCarga("Error de conexión. Verifica tu internet.", esRecarga)
            } catch (e: Exception) {
                cargando = false
                binding.progressBar.visibility = View.GONE
                binding.progressBarMas.visibility = View.GONE
                binding.btnCargarMas.isEnabled = true
                manejarErrorCarga("Error inesperado: ${e.message}", esRecarga)
            }
        }
    }

    private fun renderizarRespuesta(resp: VentasPorVendedorResponse, esRecarga: Boolean) {
        binding.layoutContenidoDatos.visibility = View.VISIBLE
        binding.layoutError.visibility = View.GONE

        actualizarEncabezadoVendedor(
            resp.vendedor.nombre,
            resp.vendedor.rol,
            resp.vendedor.esActivo
        )

        binding.tvTotalVendido.text = AuditoriaVentasHelper.formatearMonedaClp(resp.totalVendido)
        binding.tvCantidadVentas.text = resp.cantidadVentas.toString()
        binding.tvTicketPromedio.text = AuditoriaVentasHelper.formatearMonedaClp(resp.ticketPromedio)

        totalPaginas = resp.totalPaginas.coerceAtLeast(1)

        if (esRecarga) {
            masVendidosAdapter.actualizarLista(resp.productosMasVendidos)
            ventasAdapter.reemplazarLista(resp.ventas)

            val sinVentas = resp.cantidadVentas == 0 && resp.ventas.isEmpty()
            binding.layoutVacio.visibility = if (sinVentas) View.VISIBLE else View.GONE
            binding.layoutSeccionMasVendidos.visibility =
                if (resp.productosMasVendidos.isNotEmpty()) View.VISIBLE else View.GONE
            binding.layoutSeccionVentas.visibility =
                if (resp.ventas.isNotEmpty()) View.VISIBLE else View.GONE
        } else {
            ventasAdapter.agregarPagina(resp.ventas)
        }

        binding.btnCargarMas.visibility =
            if (paginaActual < totalPaginas) View.VISIBLE else View.GONE
    }

    private fun manejarErrorCarga(mensaje: String, esRecarga: Boolean) {
        if (esRecarga) {
            binding.layoutContenidoDatos.visibility = View.GONE
            binding.layoutError.visibility = View.VISIBLE
            binding.tvMensajeError.text = mensaje
        } else {
            paginaActual = (paginaActual - 1).coerceAtLeast(1)
            Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
        }
    }
}

