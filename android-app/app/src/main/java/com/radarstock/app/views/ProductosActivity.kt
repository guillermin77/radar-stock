package com.radarstock.app.views

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.gson.Gson
import com.radarstock.app.adapters.ProductoAdapter
import com.radarstock.app.databinding.ActivityProductosBinding
import com.radarstock.app.models.Producto
import com.radarstock.app.models.RespuestaApi
import com.radarstock.app.models.StockIngresoRequest
import com.radarstock.app.network.RetrofitClient
import com.radarstock.app.utils.EscanerCodigo
import com.radarstock.app.utils.SessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

/**
 * Pantalla principal del catálogo de inventario de productos.
 * Soporta búsqueda en tiempo real, filtrado por categorías, paginación incremental,
 * visualización según rol, y escaneo rápido de códigos de barras (exclusivo para Administrador).
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
        configurarBotonesAccion()
        configurarEventos()

        cargarCategorias()
        reiniciarYRecargar()

        // Si se solicitó abrir el escáner desde el Menú Principal
        if (intent.getBooleanExtra("abrir_escaner", false)) {
            intent.removeExtra("abrir_escaner")
            iniciarEscaneo()
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

        // Clic en un producto: abrir DetalleProductoActivity
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

    /**
     * Configura botones flotantes de acción.
     * Solo visibles para el rol Administrador. El Vendedor NUNCA ve el botón de escanear.
     */
    private fun configurarBotonesAccion() {
        val rol = SessionManager.obtenerRol() ?: "Vendedor"
        val esAdmin = rol.equals("Administrador", ignoreCase = true)

        // Botón Escanear: exclusivo Administrador
        binding.btnEscanear.visibility = if (esAdmin) View.VISIBLE else View.GONE
        binding.btnEscanear.setOnClickListener {
            iniciarEscaneo()
        }

        // Botón Flotante «+»: exclusivo Administrador
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
     * Inicia el escaneo de código de barras mediante Google Play Services
     */
    private fun iniciarEscaneo() {
        EscanerCodigo.escanear(
            context = this,
            onCodigoEscaneado = { codigo ->
                procesarCodigoEscaneado(codigo)
            },
            onError = { errorMsg ->
                Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
            }
        )
    }

    /**
     * Procesa el código de barras escaneado:
     * - Si existe: pide ingreso de stock y suma unidades con stock_ingreso.
     * - Si no existe (404): abre ProductoFormActivity con el código ya rellenado.
     */
    private fun procesarCodigoEscaneado(codigo: String) {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val producto = RetrofitClient.apiService.getProductoPorCodigo(codigo)
                binding.progressBar.visibility = View.GONE
                mostrarDialogoIngresoStock(producto)

            } catch (e: HttpException) {
                binding.progressBar.visibility = View.GONE
                when (e.code()) {
                    404 -> {
                        // El producto no existe: abrir formulario de creación con el código precargado
                        abrirFormularioNuevoProducto(codigo)
                    }
                    401 -> {
                        manejarSesionExpirada()
                    }
                    409 -> {
                        mostrarAlertaConflicto(extraerMensajeDeError(e))
                    }
                    else -> {
                        Toast.makeText(this@ProductosActivity, extraerMensajeDeError(e), Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: IOException) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@ProductosActivity, "Error de red al consultar el código.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@ProductosActivity, "Error inesperado: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Muestra diálogo interactivo de ingreso de stock cuando el producto ya existe.
     */
    private fun mostrarDialogoIngresoStock(producto: Producto) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 10)
        }

        val tvInfo = TextView(this).apply {
            text = "Stock actual: ${producto.stockActual} unidades"
            textSize = 14f
            setTextColor(Color.parseColor("#64748B"))
        }

        val etCantidad = EditText(this).apply {
            hint = "¿Cuántas unidades ingresaron?"
            inputType = InputType.TYPE_CLASS_NUMBER
            setPadding(0, 30, 0, 30)
        }

        val tvPreview = TextView(this).apply {
            text = "Stock nuevo: ${producto.stockActual} unidades"
            textSize = 15f
            setTextColor(Color.parseColor("#1E3A8A"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 10, 0, 20)
        }

        etCantidad.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val cant = s?.toString()?.toIntOrNull() ?: 0
                val nuevo = producto.stockActual + cant
                tvPreview.text = "Stock nuevo: $nuevo unidades"
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        layout.addView(tvInfo)
        layout.addView(etCantidad)
        layout.addView(tvPreview)

        AlertDialog.Builder(this)
            .setTitle(producto.nombreProducto)
            .setView(layout)
            .setPositiveButton("Confirmar") { _, _ ->
                val cantidad = etCantidad.text.toString().toIntOrNull()
                if (cantidad != null && cantidad > 0) {
                    ejecutarIngresoStock(producto.id, cantidad, producto.nombreProducto)
                } else {
                    Toast.makeText(this, "Debes ingresar una cantidad entera mayor a 0", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /**
     * Ejecuta la llamada atómica a api/stock_ingreso.php
     */
    private fun ejecutarIngresoStock(productoId: Int, cantidad: Int, nombreProducto: String) {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val respuesta = RetrofitClient.apiService.stockIngreso(
                    StockIngresoRequest(productoId = productoId, cantidad = cantidad)
                )
                binding.progressBar.visibility = View.GONE

                AlertDialog.Builder(this@ProductosActivity)
                    .setTitle("Ingreso Exitoso")
                    .setMessage("Producto: $nombreProducto\nUnidades ingresadas: +$cantidad\nStock nuevo: ${respuesta.stockActual} unidades")
                    .setPositiveButton("Aceptar") { _, _ ->
                        reiniciarYRecargar()
                    }
                    .show()

            } catch (e: HttpException) {
                binding.progressBar.visibility = View.GONE
                when (e.code()) {
                    401 -> manejarSesionExpirada()
                    409 -> mostrarAlertaConflicto(extraerMensajeDeError(e))
                    else -> Toast.makeText(this@ProductosActivity, extraerMensajeDeError(e), Toast.LENGTH_LONG).show()
                }
            } catch (e: IOException) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@ProductosActivity, "Error de red al actualizar stock.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@ProductosActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Abre el formulario de creación de producto con el código de barras ya prellenado.
     */
    private fun abrirFormularioNuevoProducto(codigo: String) {
        Toast.makeText(this, "Producto no registrado. Completa los datos para crearlo.", Toast.LENGTH_SHORT).show()
        val intent = Intent(this, ProductoFormActivity::class.java).apply {
            putExtra("codigo_barras_inicial", codigo)
        }
        formLauncher.launch(intent)
    }

    private fun mostrarAlertaConflicto(mensaje: String) {
        AlertDialog.Builder(this)
            .setTitle("Conflicto")
            .setMessage(mensaje)
            .setPositiveButton("Entendido", null)
            .show()
    }

    private fun manejarSesionExpirada() {
        SessionManager.cerrarSesion()
        Toast.makeText(this, "Tu sesión ha expirado o no tienes autorización. Inicia sesión nuevamente.", Toast.LENGTH_LONG).show()
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun extraerMensajeDeError(e: HttpException): String {
        return try {
            val cuerpo = e.response()?.errorBody()?.string()
            if (!cuerpo.isNullOrBlank()) {
                val apiResp = Gson().fromJson(cuerpo, RespuestaApi::class.java)
                apiResp.mensaje ?: "Error del servidor (${e.code()})"
            } else {
                "Error del servidor (${e.code()})"
            }
        } catch (_: Exception) {
            "Error del servidor (${e.code()})"
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

            } catch (e: HttpException) {
                if (e.code() == 401) {
                    manejarSesionExpirada()
                    return@launch
                }
                if (esNuevaConsulta) {
                    mostrarError("Error del servidor (${e.code()}): ${extraerMensajeDeError(e)}")
                }
            } catch (e: IOException) {
                if (esNuevaConsulta) {
                    mostrarError("No se pudo conectar con el servidor. Verifica tu conexión de red.")
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
