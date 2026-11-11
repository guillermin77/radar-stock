package com.radarstock.app.views

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.util.Pair
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import com.radarstock.app.R
import com.radarstock.app.adapters.MovimientosAdapter
import com.radarstock.app.databinding.ActivityMovimientosBinding
import com.radarstock.app.network.RetrofitClient
import com.radarstock.app.utils.SessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Pantalla de Historial de Movimientos de Stock (Auditoría).
 * Exclusiva para el rol Administrador.
 * Muestra ingresos, conteos y ajustes con filtros por tipo, fecha y búsqueda.
 * Si recibe HTTP 401, limpia la sesión y redirige al Login.
 */
class MovimientosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMovimientosBinding
    private lateinit var adapter: MovimientosAdapter

    private var productoIdFiltro: Int? = null
    private var tipoFiltro: String? = null
    private var desdeFiltro: String? = null
    private var hastaFiltro: String? = null
    private var busquedaFiltro: String? = null

    private var paginaActual = 1
    private val porPagina = 30
    private var totalPaginas = 1
    private var cargando = false
    private var jobBusqueda: Job? = null

    private val tzChile = TimeZone.getTimeZone("America/Santiago")
    private val formatoIsoFecha = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
        timeZone = tzChile
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Validar autorización de rol: Solo Administrador
        val rol = SessionManager.obtenerRol() ?: ""
        if (!rol.equals("Administrador", ignoreCase = true)) {
            Toast.makeText(this, "Acceso denegado: solo Administrador", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding = ActivityMovimientosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 2. Extraer parámetros de inicio (ej. filtrado por producto específico desde DetalleProductoActivity)
        val idExtra = intent.getIntExtra("producto_id", -1)
        if (idExtra > 0) {
            productoIdFiltro = idExtra
            val nombreExtra = intent.getStringExtra("nombre_producto") ?: "Producto #$idExtra"
            binding.toolbar.subtitle = nombreExtra
        }

        configurarToolbar()
        configurarRecyclerView()
        configurarBuscador()
        configurarFiltrosTipo()
        configurarFiltrosFecha()

        binding.btnReintentar.setOnClickListener {
            recargarTodo()
        }

        recargarTodo()
    }

    private fun configurarToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu?.add(0, 2001, 0, "Recargar")?.apply {
            setIcon(R.drawable.ic_refresh)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            2001 -> {
                recargarTodo()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun configurarRecyclerView() {
        adapter = MovimientosAdapter()
        val layoutManager = LinearLayoutManager(this)
        binding.rvMovimientos.layoutManager = layoutManager
        binding.rvMovimientos.adapter = adapter

        // Paginación continua al llegar al final del scroll
        binding.rvMovimientos.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy <= 0) return

                val totalItems = layoutManager.itemCount
                val ultimoVisible = layoutManager.findLastVisibleItemPosition()

                if (!cargando && paginaActual < totalPaginas && ultimoVisible >= totalItems - 5) {
                    cargarSiguientePagina()
                }
            }
        })
    }

    private fun configurarBuscador() {
        binding.etBuscar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                busquedaFiltro = binding.etBuscar.text.toString().trim().takeIf { it.isNotEmpty() }
                recargarTodo()
                true
            } else false
        }

        binding.etBuscar.doAfterTextChanged { text ->
            jobBusqueda?.cancel()
            jobBusqueda = lifecycleScope.launch {
                delay(500)
                val nuevoTexto = text?.toString()?.trim().takeIf { !it.isNullOrEmpty() }
                if (nuevoTexto != busquedaFiltro) {
                    busquedaFiltro = nuevoTexto
                    recargarTodo()
                }
            }
        }
    }

    private fun configurarFiltrosTipo() {
        binding.chipGroupTipo.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener

            tipoFiltro = when (checkedIds.first()) {
                R.id.chipTipoTodos -> null
                R.id.chipTipoIngresos -> "ingreso"
                R.id.chipTipoConteos -> "conteo"
                R.id.chipTipoAjustes -> "ajuste"
                else -> null
            }

            recargarTodo()
        }
    }

    private fun configurarFiltrosFecha() {
        binding.chipGroupFecha.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener

            val cal = Calendar.getInstance(tzChile)

            when (checkedIds.first()) {
                R.id.chipFechaTodas -> {
                    desdeFiltro = null
                    hastaFiltro = null
                    recargarTodo()
                }
                R.id.chipFechaHoy -> {
                    val hoyStr = formatoIsoFecha.format(cal.time)
                    desdeFiltro = hoyStr
                    hastaFiltro = hoyStr
                    recargarTodo()
                }
                R.id.chipFecha7Dias -> {
                    val hastaStr = formatoIsoFecha.format(cal.time)
                    cal.add(Calendar.DAY_OF_YEAR, -6)
                    val desdeStr = formatoIsoFecha.format(cal.time)
                    desdeFiltro = desdeStr
                    hastaFiltro = hastaStr
                    recargarTodo()
                }
                R.id.chipFechaMes -> {
                    val hastaStr = formatoIsoFecha.format(cal.time)
                    cal.set(Calendar.DAY_OF_MONTH, 1)
                    val desdeStr = formatoIsoFecha.format(cal.time)
                    desdeFiltro = desdeStr
                    hastaFiltro = hastaStr
                    recargarTodo()
                }
                R.id.chipFechaRango -> {
                    abrirSelectorRangoFechas()
                }
            }
        }
    }

    private fun abrirSelectorRangoFechas() {
        val constraints = CalendarConstraints.Builder()
            .setValidator(DateValidatorPointBackward.now())
            .build()

        val dateRangePicker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("Seleccionar rango de fechas")
            .setCalendarConstraints(constraints)
            .build()

        dateRangePicker.addOnPositiveButtonClickListener { selection: Pair<Long, Long>? ->
            if (selection != null && selection.first != null && selection.second != null) {
                // Las marcas de tiempo UTC devueltas por MaterialDatePicker
                val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                cal.timeInMillis = selection.first
                val desdeDate = cal.time

                cal.timeInMillis = selection.second
                val hastaDate = cal.time

                val sdfUtc = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }

                desdeFiltro = sdfUtc.format(desdeDate)
                hastaFiltro = sdfUtc.format(hastaDate)

                val sdfVisual = SimpleDateFormat("dd/MM", Locale.getDefault()).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                binding.chipFechaRango.text = "${sdfVisual.format(desdeDate)} - ${sdfVisual.format(hastaDate)}"
                recargarTodo()
            }
        }

        dateRangePicker.addOnNegativeButtonClickListener {
            // Si cancela y no tenía rango seleccionado, regresar al chip "Todas las fechas"
            if (desdeFiltro == null) {
                binding.chipFechaTodas.isChecked = true
            }
        }

        dateRangePicker.show(supportFragmentManager, "RANGO_MOVIMIENTOS")
    }

    private fun recargarTodo() {
        paginaActual = 1
        cargarMovimientos(esRecarga = true)
    }

    private fun cargarSiguientePagina() {
        paginaActual++
        cargarMovimientos(esRecarga = false)
    }

    private fun cargarMovimientos(esRecarga: Boolean) {
        if (cargando) return
        cargando = true

        if (esRecarga) {
            binding.progressBar.visibility = View.VISIBLE
            binding.layoutError.visibility = View.GONE
            binding.layoutVacio.visibility = View.GONE
        } else {
            binding.pbPaginacion.visibility = View.VISIBLE
        }

        lifecycleScope.launch {
            try {
                val respuesta = RetrofitClient.apiService.getMovimientos(
                    productoId = productoIdFiltro,
                    q = busquedaFiltro,
                    tipo = tipoFiltro,
                    desde = desdeFiltro,
                    hasta = hastaFiltro,
                    pagina = paginaActual,
                    porPagina = porPagina
                )

                binding.progressBar.visibility = View.GONE
                binding.pbPaginacion.visibility = View.GONE
                binding.layoutError.visibility = View.GONE
                cargando = false

                totalPaginas = respuesta.totalPaginas

                if (esRecarga) {
                    adapter.actualizarLista(respuesta.movimientos)
                    if (respuesta.movimientos.isEmpty()) {
                        binding.layoutVacio.visibility = View.VISIBLE
                    } else {
                        binding.layoutVacio.visibility = View.GONE
                    }
                } else {
                    adapter.agregarPagina(respuesta.movimientos)
                }

            } catch (e: HttpException) {
                binding.progressBar.visibility = View.GONE
                binding.pbPaginacion.visibility = View.GONE
                cargando = false

                // Si la sesión expiró o el token fue revocado (401), limpiar sesión y salir
                if (e.code() == 401) {
                    manejarSesionExpirada()
                    return@launch
                }

                if (esRecarga && adapter.itemCount == 0) {
                    binding.layoutError.visibility = View.VISIBLE
                    binding.layoutVacio.visibility = View.GONE
                    binding.tvMensajeError.text = "Error al conectar con el servidor (${e.code()})."
                } else {
                    Toast.makeText(this@MovimientosActivity, "Error de red: ${e.message()}", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.pbPaginacion.visibility = View.GONE
                cargando = false

                if (esRecarga && adapter.itemCount == 0) {
                    binding.layoutError.visibility = View.VISIBLE
                    binding.layoutVacio.visibility = View.GONE
                    binding.tvMensajeError.text = "Error de conexión: ${e.localizedMessage ?: e.message}"
                } else {
                    Toast.makeText(this@MovimientosActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun manejarSesionExpirada() {
        Toast.makeText(this, "Tu sesión ha expirado. Ingresa nuevamente.", Toast.LENGTH_LONG).show()
        SessionManager.cerrarSesion()
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}

