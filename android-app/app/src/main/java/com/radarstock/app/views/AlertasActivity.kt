package com.radarstock.app.views

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.radarstock.app.R
import com.radarstock.app.adapters.AlertasAdapter
import com.radarstock.app.databinding.ActivityAlertasBinding
import com.radarstock.app.models.Alerta
import com.radarstock.app.models.ResumenAlertasResponse
import com.radarstock.app.network.RetrofitClient
import com.radarstock.app.utils.NotificationHelper
import kotlinx.coroutines.launch

/**
 * Pantalla de visualización y filtrado de Alertas de Stock y Vencimiento.
 * Disponible para Administrador y Vendedor.
 * Al tocar una alerta, permite abrir el detalle del producto (donde el Admin puede agregar stock).
 * Emite aviso sonoro y táctil cuando se detectan o modifican alertas.
 */
class AlertasActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlertasBinding
    private lateinit var adapter: AlertasAdapter

    private var tipoFiltro: String? = null
    private var paginaActual = 1
    private val limitePorPagina = 50
    private var hayMasPaginas = true
    private var cargando = false

    private var ultimoTotalAlertas: Int? = null
    private var ultimoConteoCriticos: Int? = null

    // Launcher para abrir detalle de producto y refrescar si hubo cambios de stock
    private val detalleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            recargarTodo()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlertasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        NotificationHelper.crearCanalNotificaciones(this)

        configurarToolbar()
        configurarRecyclerView()
        configurarFiltros()
        configurarSwipeRefreshManual()

        binding.btnReintentar.setOnClickListener {
            recargarTodo()
        }

        recargarTodo()
    }

    override fun onResume() {
        super.onResume()
        // Mantener las alertas sincronizadas si hubo ventas o cambios
        if (!cargando) {
            cargarResumenAlertas()
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

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu?.add(0, 1001, 0, "Recargar")?.apply {
            setIcon(R.drawable.ic_refresh)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            1001 -> {
                NotificationHelper.reproducirSonidoAlerta(this)
                recargarTodo()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun configurarRecyclerView() {
        adapter = AlertasAdapter { alerta ->
            abrirDetalleProducto(alerta)
        }

        val layoutManager = LinearLayoutManager(this)
        binding.rvAlertas.layoutManager = layoutManager
        binding.rvAlertas.adapter = adapter

        // Paginación continua de 50 en 50
        binding.rvAlertas.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy <= 0) return

                val totalItems = layoutManager.itemCount
                val ultimoVisible = layoutManager.findLastVisibleItemPosition()

                if (!cargando && hayMasPaginas && ultimoVisible >= totalItems - 5) {
                    cargarSiguientePagina()
                }
            }
        })
    }

    /**
     * Detección de gesto "Deslizar hacia abajo" en el inicio de la lista para recargar.
     */
    private fun configurarSwipeRefreshManual() {
        var startY = 0f
        binding.rvAlertas.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startY = e.y
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaY = e.y - startY
                        val estaEnElTope = !rv.canScrollVertically(-1)
                        if (estaEnElTope && deltaY > 180f && !cargando) {
                            startY = e.y
                            recargarTodo()
                            return false
                        }
                    }
                }
                return false
            }
        })
    }

    private fun configurarFiltros() {
        binding.chipGroupFiltros.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener

            val nuevoFiltro = when (checkedIds.first()) {
                R.id.chipTodas -> null
                R.id.chipAgotados -> "agotado"
                R.id.chipCriticos -> "critico"
                R.id.chipBajos -> "bajo"
                R.id.chipPorVencer -> "por_vencer"
                R.id.chipVencidos -> "vencido"
                else -> null
            }

            tipoFiltro = nuevoFiltro
            paginaActual = 1
            hayMasPaginas = true
            cargarAlertas(esRecarga = true)
        }
    }

    private fun recargarTodo() {
        paginaActual = 1
        hayMasPaginas = true
        cargarResumenAlertas()
        cargarAlertas(esRecarga = true)
    }

    /**
     * Obtiene los conteos desde api/alertas.php?resumen=1 y actualiza los Chips superiores.
     */
    private fun cargarResumenAlertas() {
        lifecycleScope.launch {
            try {
                val resumen = RetrofitClient.apiService.getResumenAlertas()
                actualizarTextosChips(resumen)
            } catch (e: Exception) {
                // Si falla el resumen, se gestiona junto con la carga de la lista
            }
        }
    }

    private fun actualizarTextosChips(resumen: ResumenAlertasResponse) {
        binding.chipTodas.text = "Todas (${resumen.total})"
        binding.chipAgotados.text = "Agotados (${resumen.agotados})"
        binding.chipCriticos.text = "Críticos (${resumen.criticos})"
        binding.chipBajos.text = "Bajos (${resumen.bajos})"
        binding.chipPorVencer.text = "Por vencer (${resumen.porVencer})"
        binding.chipVencidos.text = "Vencidos (${resumen.vencidos})"

        // Si se detecta un cambio en el total de alertas o en las críticas/agotadas
        val cambioDetectado = (ultimoTotalAlertas != null && (resumen.total != ultimoTotalAlertas || resumen.criticos != ultimoConteoCriticos))
        if (cambioDetectado) {
            NotificationHelper.reproducirSonidoAlerta(this)
            NotificationHelper.vibrarAlerta(this)
        }
        ultimoTotalAlertas = resumen.total
        ultimoConteoCriticos = resumen.criticos
    }

    /**
     * Carga listado paginado de alertas desde api/alertas.php
     */
    private fun cargarAlertas(esRecarga: Boolean) {
        if (cargando) return
        cargando = true

        if (esRecarga) {
            binding.progressBar.visibility = View.VISIBLE
            binding.layoutError.visibility = View.GONE
            binding.layoutVacio.visibility = View.GONE
            binding.tvBannerActualizando.visibility = View.VISIBLE
        } else {
            binding.pbPaginacion.visibility = View.VISIBLE
        }

        lifecycleScope.launch {
            try {
                val lista = RetrofitClient.apiService.getAlertas(
                    tipo = tipoFiltro,
                    pagina = paginaActual,
                    limite = limitePorPagina
                )

                binding.progressBar.visibility = View.GONE
                binding.pbPaginacion.visibility = View.GONE
                binding.tvBannerActualizando.visibility = View.GONE
                binding.layoutError.visibility = View.GONE
                cargando = false

                if (esRecarga) {
                    adapter.actualizarLista(lista)
                    if (lista.isEmpty()) {
                        binding.layoutVacio.visibility = View.VISIBLE
                    } else {
                        binding.layoutVacio.visibility = View.GONE
                    }
                } else {
                    adapter.agregarPagina(lista)
                }

                hayMasPaginas = lista.size >= limitePorPagina

            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.pbPaginacion.visibility = View.GONE
                binding.tvBannerActualizando.visibility = View.GONE
                cargando = false

                if (esRecarga && adapter.itemCount == 0) {
                    binding.layoutError.visibility = View.VISIBLE
                    binding.layoutVacio.visibility = View.GONE
                    binding.tvMensajeError.text = "No se pudo conectar con el servidor: ${e.localizedMessage ?: e.message}"
                } else {
                    Toast.makeText(
                        this@AlertasActivity,
                        "Error de conexión al cargar alertas: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun cargarSiguientePagina() {
        paginaActual++
        cargarAlertas(esRecarga = false)
    }

    private fun abrirDetalleProducto(alerta: Alerta) {
        val intent = Intent(this, DetalleProductoActivity::class.java).apply {
            putExtra("producto_id", alerta.productoId)
        }
        detalleLauncher.launch(intent)
    }
}
