package com.radarstock.app.views

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.radarstock.app.adapters.ProductoAdapter
import com.radarstock.app.databinding.ActivityProductosBinding
import com.radarstock.app.models.Producto
import com.radarstock.app.network.RetrofitClient
import com.radarstock.app.utils.SessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Pantalla principal del catálogo de inventario de productos.
 * Soporta búsqueda en tiempo real con debounce de 300 ms, filtrado por categorías,
 * paginación incremental de 50 en 50 y navegación hacia el detalle.
 */
class ProductosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProductosBinding
    private val productoAdapter = ProductoAdapter()

    // Variables de paginación y estado
    private var paginaActual = 1
    private val limitePorPagina = 50
    private var esUltimaPagina = false
    private var estaCargando = false

    // Filtros activos
    private var textoBusqueda = ""
    private var categoriaSeleccionada: String? = null

    // Job para debounce de búsqueda (300 ms)
    private var searchJob: Job? = null

    // Launchers para refrescar la lista al crear, editar o eliminar productos
    private val formLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            reiniciarYRecargar()
        }
    }

    private val detalleLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            reiniciarYRecargar()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configurarToolbar()
        configurarRecyclerView()
        configurarBuscador()
        configurarBotonFlotante()
        configurarEventos()

        cargarCategorias()
        reiniciarYRecargar()
    }

    private fun configurarToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun configurarRecyclerView() {
        binding.rvProductos.apply {
            layoutManager = LinearLayoutManager(this@ProductosActivity)
            adapter = productoAdapter

            // Detección de scroll para paginación incremental
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    val lm = layoutManager as? LinearLayoutManager ?: return
                    val totalItems = lm.itemCount
                    val ultimoVisible = lm.findLastVisibleItemPosition()

                    if (!estaCargando && !esUltimaPagina && ultimoVisible >= totalItems - 3) {
                        cargarSiguientePagina()
                    }
                }
            })
        }

        // Clic en un producto: abrir DetalleProductoActivity y esperar resultado para refrescar si hubo cambios
        productoAdapter.setOnProductoClickListener { producto ->
            val intent = Intent(this, DetalleProductoActivity::class.java).apply {
                putExtra("producto", producto)
            }
            detalleLauncher.launch(intent)
        }
    }

    private fun configurarBuscador() {
        binding.etBuscar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    delay(300) // Debounce de 300 ms antes de consultar
                    textoBusqueda = s?.toString()?.trim() ?: ""
                    reiniciarYRecargar()
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun configurarBotonFlotante() {
        val rol = SessionManager.obtenerRol() ?: "Vendedor"
        val esAdmin = rol.equals("Administrador", ignoreCase = true)

        // Botón flotante «+» visible solo para Administrador: abre ProductoFormActivity
        binding.fabAgregarProducto.visibility = if (esAdmin) View.VISIBLE else View.GONE
        binding.fabAgregarProducto.setOnClickListener {
            val intent = Intent(this, ProductoFormActivity::class.java)
            formLauncher.launch(intent)
        }
    }

    private fun configurarEventos() {
        binding.btnReintentar.setOnClickListener {
            reiniciarYRecargar()
        }
    }

    /**
     * Carga dinámica de categorías desde api/categorias.php
     */
    private fun cargarCategorias() {
        lifecycleScope.launch {
            try {
                val categorias = RetrofitClient.apiService.getCategorias()
                construirChipsCategorias(categorias)
            } catch (e: Exception) {
                // Si falla la carga de categorías, dejamos al menos la opción "Todas"
                construirChipsCategorias(emptyList())
            }
        }
    }

    private fun construirChipsCategorias(categorias: List<String>) {
        binding.chipGroupCategorias.removeAllViews()

        // Chip por defecto: "Todas"
        val chipTodas = Chip(this).apply {
            text = "Todas"
            isCheckable = true
            isChecked = true
            id = View.generateViewId()
        }
        binding.chipGroupCategorias.addView(chipTodas)

        categorias.forEach { cat ->
            val chip = Chip(this).apply {
                text = cat
                isCheckable = true
                id = View.generateViewId()
            }
            binding.chipGroupCategorias.addView(chip)
        }

        binding.chipGroupCategorias.setOnCheckedStateChangeListener { group, checkedIds ->
            val selectedId = checkedIds.firstOrNull()
            if (selectedId == null || selectedId == chipTodas.id) {
                categoriaSeleccionada = null
            } else {
                val selectedChip = group.findViewById<Chip>(selectedId)
                categoriaSeleccionada = selectedChip?.text?.toString()
            }
            reiniciarYRecargar()
        }
    }

    private fun reiniciarYRecargar() {
        paginaActual = 1
        esUltimaPagina = false
        cargarProductos(esNuevaConsulta = true)
    }

    private fun cargarSiguientePagina() {
        paginaActual++
        cargarProductos(esNuevaConsulta = false)
    }

    private fun cargarProductos(esNuevaConsulta: Boolean) {
        if (estaCargando) return
        estaCargando = true

        if (esNuevaConsulta) {
            mostrarCargandoInicial()
        } else {
            binding.pbPaginacion.visibility = View.VISIBLE
        }

        lifecycleScope.launch {
            try {
                val buscarParam = if (textoBusqueda.isNotEmpty()) textoBusqueda else null
                val lista = RetrofitClient.apiService.getProductos(
                    buscar = buscarParam,
                    categoria = categoriaSeleccionada,
                    pagina = paginaActual,
                    limite = limitePorPagina
                )

                if (lista.size < limitePorPagina) {
                    esUltimaPagina = true
                }

                if (esNuevaConsulta) {
                    productoAdapter.actualizarLista(lista)
                    if (lista.isEmpty()) {
                        mostrarVacio()
                    } else {
                        mostrarLista()
                    }
                } else {
                    productoAdapter.agregarProductos(lista)
                }

            } catch (e: IOException) {
                if (esNuevaConsulta) {
                    mostrarError("No se pudo conectar con el servidor. Verifica que XAMPP esté activo.")
                }
            } catch (e: Exception) {
                if (esNuevaConsulta) {
                    mostrarError("Error al cargar productos: ${e.message}")
                }
            } finally {
                estaCargando = false
                binding.pbPaginacion.visibility = View.GONE
            }
        }
    }

    private fun mostrarCargandoInicial() {
        binding.progressBar.visibility = View.VISIBLE
        binding.rvProductos.visibility = View.GONE
        binding.layoutVacio.visibility = View.GONE
        binding.layoutError.visibility = View.GONE
    }

    private fun mostrarLista() {
        binding.progressBar.visibility = View.GONE
        binding.rvProductos.visibility = View.VISIBLE
        binding.layoutVacio.visibility = View.GONE
        binding.layoutError.visibility = View.GONE
    }

    private fun mostrarVacio() {
        binding.progressBar.visibility = View.GONE
        binding.rvProductos.visibility = View.GONE
        binding.layoutVacio.visibility = View.VISIBLE
        binding.layoutError.visibility = View.GONE
    }

    private fun mostrarError(detalle: String) {
        binding.progressBar.visibility = View.GONE
        binding.rvProductos.visibility = View.GONE
        binding.layoutVacio.visibility = View.GONE
        binding.tvErrorDetalle.text = detalle
        binding.layoutError.visibility = View.VISIBLE
    }
}
