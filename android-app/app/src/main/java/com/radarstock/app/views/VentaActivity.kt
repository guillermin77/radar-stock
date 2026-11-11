package com.radarstock.app.views

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.gson.Gson
import com.radarstock.app.adapters.ProductoGridAdapter
import com.radarstock.app.adapters.VentaDetalleAdapter
import com.radarstock.app.databinding.ActivityVentaBinding
import com.radarstock.app.databinding.LayoutBottomSheetVentaBinding
import com.radarstock.app.models.ItemVenta
import com.radarstock.app.models.Producto
import com.radarstock.app.models.RespuestaApi
import com.radarstock.app.models.VentaItemRequest
import com.radarstock.app.models.VentaRequest
import com.radarstock.app.network.RetrofitClient
import com.radarstock.app.utils.NotificationHelper
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import java.util.UUID

/**
 * Pantalla principal para el registro de ventas rápidas.
 * Cuadrícula de 2 columnas ordenada por más vendidos, filtrado por categorías,
 * gestión táctil con el pulgar, control estricto de stock y confirmación con idempotencia (UUID).
 */
class VentaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVentaBinding

    // Adaptador de la cuadrícula
    private lateinit var gridAdapter: ProductoGridAdapter

    // Carrito de compras en memoria: productoId -> ItemVenta
    private val carrito = linkedMapOf<Int, ItemVenta>()

    // Código de operación único para idempotencia (se conserva en reintentos por fallo de red)
    private var codigoOperacion: String = UUID.randomUUID().toString()

    // Formato de moneda chilena sin decimales
    private val formatoMoneda = NumberFormat.getCurrencyInstance(Locale("es", "CL")).apply {
        maximumFractionDigits = 0
        currency = Currency.getInstance("CLP")
    }

    // Paginación y estado
    private var paginaActual = 1
    private val limitePorPagina = 50
    private var esUltimaPagina = false
    private var estaCargando = false
    private var categoriaSeleccionada: String? = null

    // Componentes del BottomSheet de detalle de venta
    private var bottomSheetDialog: BottomSheetDialog? = null
    private var bindingBottomSheet: LayoutBottomSheetVentaBinding? = null
    private lateinit var detalleAdapter: VentaDetalleAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVentaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configurarToolbar()
        configurarRecyclerViewGrid()
        configurarBarraResumen()
        configurarEventos()

        cargarCategorias()
        reiniciarYRecargar()
    }

    private fun configurarToolbar() {
        setSupportActionBar(binding.toolbarVenta)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        binding.toolbarVenta.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun configurarRecyclerViewGrid() {
        gridAdapter = ProductoGridAdapter { producto ->
            agregarProductoAlCarrito(producto)
        }

        val gridLayoutManager = GridLayoutManager(this, 2)
        binding.rvProductosGrid.apply {
            layoutManager = gridLayoutManager
            adapter = gridAdapter

            // Scroll listener para paginación incremental (de 50 en 50)
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    val totalItems = gridLayoutManager.itemCount
                    val ultimoVisible = gridLayoutManager.findLastVisibleItemPosition()

                    if (!estaCargando && !esUltimaPagina && ultimoVisible >= totalItems - 6) {
                        cargarSiguientePagina()
                    }
                }
            })
        }
    }

    private fun configurarBarraResumen() {
        binding.cardBarraInferior.setOnClickListener {
            mostrarDetalleVenta()
        }
        actualizarResumen()

    }

    private fun configurarEventos() {
        binding.btnReintentarCarga.setOnClickListener {
            reiniciarYRecargar()
        }
    }

    /**
     * Agrega 1 unidad del producto al carrito comprobando el stock disponible.
     */
    private fun agregarProductoAlCarrito(producto: Producto) {
        if (producto.stockActual <= 0) {
            Toast.makeText(this, "Producto sin stock", Toast.LENGTH_SHORT).show()
            return
        }

        val itemExistente = carrito[producto.id]
        val cantidadActual = itemExistente?.cantidad ?: 0

        if (cantidadActual >= producto.stockActual) {
            Toast.makeText(
                this,
                "No hay más stock de ${producto.nombreProducto}",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (itemExistente != null) {
            itemExistente.cantidad++
        } else {
            carrito[producto.id] = ItemVenta(producto, 1)
        }

        actualizarResumen()
    }

    /**
     * Incrementa en 1 unidad un ítem desde el diálogo de detalle.
     */
    private fun sumarItem(item: ItemVenta) {
        if (item.cantidad >= item.producto.stockActual) {
            Toast.makeText(
                this,
                "No hay más stock de ${item.producto.nombreProducto}",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        item.cantidad++
        actualizarResumen()
    }

    /**
     * Reduce en 1 unidad un ítem desde el diálogo de detalle. Si llega a 0 se elimina.
     */
    private fun restarItem(item: ItemVenta) {
        item.cantidad--
        if (item.cantidad <= 0) {
            carrito.remove(item.producto.id)
        }
        actualizarResumen()
    }

    /**
     * Quita por completo un producto del carrito.
     */
    private fun eliminarItem(item: ItemVenta) {
        carrito.remove(item.producto.id)
        actualizarResumen()
    }

    /**
     * Vacía todos los productos agregados a la venta actual.
     */
    private fun vaciarVenta() {
        if (carrito.isEmpty()) {
            Toast.makeText(this, "La venta ya está vacía", Toast.LENGTH_SHORT).show()
            return
        }
        carrito.clear()
        actualizarResumen()
        Toast.makeText(this, "Venta vaciada", Toast.LENGTH_SHORT).show()
    }

    /**
     * Sincroniza la barra inferior, el modal desplegado y las tarjetas de la cuadrícula.
     */
    private fun actualizarResumen() {
        val totalUnidades = carrito.values.sumOf { it.cantidad }
        val totalMonto = carrito.values.sumOf { it.subtotal }
        val totalTiposProductos = carrito.size

        // 1. Barra inferior fija
        binding.tvCantidadResumen.text = if (totalUnidades == 1) {
            "1 producto (1 unidad)"
        } else {
            "$totalTiposProductos productos ($totalUnidades unidades)"
        }
        binding.tvTotalResumen.text = formatoMoneda.format(totalMonto)

        // 2. Notificar a la cuadrícula para actualizar los badges
        val mapeoCantidades = carrito.mapValues { it.value.cantidad }
        gridAdapter.actualizarCantidadesVenta(mapeoCantidades)

        // 3. Sincronizar modal si está visible
        bindingBottomSheet?.let { bs ->
            val lista = carrito.values.toList()
            detalleAdapter.actualizarItems(lista)

            bs.tvVentaVaciaModal.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
            bs.rvItemsDetalle.visibility = if (lista.isEmpty()) View.GONE else View.VISIBLE
            bs.tvTotalMontoModal.text = formatoMoneda.format(totalMonto)
            bs.btnConfirmarVentaModal.isEnabled = lista.isNotEmpty()
            bs.btnConfirmarVentaModal.text = if (lista.isNotEmpty()) {
                "Confirmar venta (${formatoMoneda.format(totalMonto)})"
            } else {
                "Confirmar venta"
            }
        }
    }

    /**
     * Despliega la lista de la venta actual en un BottomSheetDialog interactivo.
     */
    private fun mostrarDetalleVenta() {
        if (bottomSheetDialog == null) {
            bottomSheetDialog = BottomSheetDialog(this).apply {
                val sheetBinding = LayoutBottomSheetVentaBinding.inflate(layoutInflater)
                bindingBottomSheet = sheetBinding
                setContentView(sheetBinding.root)

                detalleAdapter = VentaDetalleAdapter(
                    onSumar = { sumarItem(it) },
                    onRestar = { restarItem(it) },
                    onEliminar = { eliminarItem(it) }
                )

                sheetBinding.rvItemsDetalle.apply {
                    layoutManager = LinearLayoutManager(this@VentaActivity)
                    adapter = detalleAdapter
                }

                sheetBinding.btnVaciarVenta.setOnClickListener {
                    AlertDialog.Builder(this@VentaActivity)
                        .setTitle("Vaciar Venta")
                        .setMessage("¿Deseas quitar todos los productos agregados a la venta?")
                        .setPositiveButton("Vaciar") { _, _ ->
                            vaciarVenta()
                        }
                        .setNegativeButton("Cancelar", null)
                        .show()
                }

                sheetBinding.btnConfirmarVentaModal.setOnClickListener {
                    confirmarVenta()
                }
            }
        }

        actualizarResumen()
        bottomSheetDialog?.apply {
            behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
            show()
        }
    }

    /**
     * Envía la venta a api/ventas.php con soporte de idempotencia y control de errores.
     */
    private fun confirmarVenta() {
        if (carrito.isEmpty()) {
            Toast.makeText(this, "Agregue al menos un producto a la venta.", Toast.LENGTH_SHORT).show()
            return
        }

        val bs = bindingBottomSheet
        bs?.btnConfirmarVentaModal?.isEnabled = false
        bs?.pbConfirmandoModal?.visibility = View.VISIBLE

        val itemsRequest = carrito.values.map {
            VentaItemRequest(
                productoId = it.producto.id,
                cantidad = it.cantidad
            )
        }
        val request = VentaRequest(
            codigoOperacion = codigoOperacion,
            items = itemsRequest
        )

        lifecycleScope.launch {
            try {
                val respuesta = RetrofitClient.apiService.registrarVenta(request)

                bs?.btnConfirmarVentaModal?.isEnabled = true
                bs?.pbConfirmandoModal?.visibility = View.GONE

                val totalFinal = formatoMoneda.format(respuesta.total ?: 0.0)
                Toast.makeText(
                    this@VentaActivity,
                    "Venta registrada: $totalFinal",
                    Toast.LENGTH_LONG
                ).show()

                // Procesar notificaciones locales de stock si hay alertas nuevas
                NotificationHelper.procesarYNotificarAlertas(this@VentaActivity, respuesta.alertasNuevas)

                // Vaciar la venta actual y generar un nuevo código de operación para la siguiente venta
                carrito.clear()
                codigoOperacion = UUID.randomUUID().toString()
                actualizarResumen()

                bottomSheetDialog?.dismiss()

                // Recargar el catálogo para ver el stock actualizado desde el servidor
                reiniciarYRecargar()

            } catch (e: HttpException) {
                bs?.btnConfirmarVentaModal?.isEnabled = true
                bs?.pbConfirmandoModal?.visibility = View.GONE

                val mensajeError = extraerMensajeDeError(e)

                if (e.code() == 409) {
                    // Conflicto de stock insuficiente
                    AlertDialog.Builder(this@VentaActivity)
                        .setTitle("Stock Insuficiente")
                        .setMessage(mensajeError)
                        .setPositiveButton("Entendido", null)
                        .show()

                    // Sincronizar stock con el servidor
                    reiniciarYRecargar()
                } else {
                    Toast.makeText(this@VentaActivity, mensajeError, Toast.LENGTH_LONG).show()
                }

            } catch (e: IOException) {
                // Fallo de conexión a Internet o servidor inaccesible
                bs?.btnConfirmarVentaModal?.isEnabled = true
                bs?.pbConfirmandoModal?.visibility = View.GONE

                Toast.makeText(
                    this@VentaActivity,
                    "Error de conexión. Se conservó la venta para reintentar.",
                    Toast.LENGTH_LONG
                ).show()
                // Se conserva el carrito y el mismo codigoOperacion para reintentar de forma segura

            } catch (e: Exception) {
                bs?.btnConfirmarVentaModal?.isEnabled = true
                bs?.pbConfirmandoModal?.visibility = View.GONE

                Toast.makeText(
                    this@VentaActivity,
                    "Error inesperado: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
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

    /**
     * Carga de categorías para la barra horizontal de filtros.
     */
    private fun cargarCategorias() {
        lifecycleScope.launch {
            try {
                val categorias = RetrofitClient.apiService.getCategorias()
                construirChipsCategorias(categorias)
            } catch (_: Exception) {
                construirChipsCategorias(emptyList())
            }
        }
    }

    private fun construirChipsCategorias(categorias: List<String>) {
        binding.chipGroupCategorias.removeAllViews()

        // Chip "Todas"
        val chipTodas = Chip(this).apply {
            text = "Todas"
            isCheckable = true
            isChecked = true
            setOnClickListener {
                if (categoriaSeleccionada != null) {
                    categoriaSeleccionada = null
                    reiniciarYRecargar()
                }
            }
        }
        binding.chipGroupCategorias.addView(chipTodas)

        // Chips dinámicos
        for (cat in categorias) {
            val chip = Chip(this).apply {
                text = cat
                isCheckable = true
                setOnClickListener {
                    if (categoriaSeleccionada != cat) {
                        categoriaSeleccionada = cat
                        reiniciarYRecargar()
                    }
                }
            }
            binding.chipGroupCategorias.addView(chip)
        }
    }

    /**
     * Reinicia los contadores de paginación y carga desde la página 1.
     */
    private fun reiniciarYRecargar() {
        paginaActual = 1
        esUltimaPagina = false
        cargarProductos(pagina = 1, esRecargaInicial = true)
    }

    private fun cargarSiguientePagina() {
        if (estaCargando || esUltimaPagina) return
        paginaActual++
        cargarProductos(pagina = paginaActual, esRecargaInicial = false)
    }

    /**
     * Consulta a api/productos.php con orden=vendidos y paginación de 50 en 50.
     */
    private fun cargarProductos(pagina: Int, esRecargaInicial: Boolean) {
        estaCargando = true

        if (esRecargaInicial) {
            binding.pbCargando.visibility = View.VISIBLE
            binding.layoutError.visibility = View.GONE
        } else {
            binding.pbPaginacion.visibility = View.VISIBLE
        }

        lifecycleScope.launch {
            try {
                val nuevosProductos = RetrofitClient.apiService.getProductos(
                    buscar = null,
                    categoria = categoriaSeleccionada,
                    orden = "vendidos",
                    pagina = pagina,
                    limite = limitePorPagina
                )

                if (esRecargaInicial) {
                    binding.pbCargando.visibility = View.GONE
                    gridAdapter.reemplazarProductos(nuevosProductos)
                } else {
                    binding.pbPaginacion.visibility = View.GONE
                    gridAdapter.agregarProductos(nuevosProductos)
                }

                // Si devuelve menos que el límite, alcanzamos el final
                if (nuevosProductos.size < limitePorPagina) {
                    esUltimaPagina = true
                }

                // Sincronizar badges con las cantidades que puedan estar en el carrito
                actualizarResumen()

            } catch (_: IOException) {
                if (esRecargaInicial) {
                    binding.pbCargando.visibility = View.GONE
                    binding.layoutError.visibility = View.VISIBLE
                    binding.tvMensajeError.text = "Sin conexión al servidor. Verifique su red."
                } else {
                    binding.pbPaginacion.visibility = View.GONE
                }
            } catch (e: Exception) {
                if (esRecargaInicial) {
                    binding.pbCargando.visibility = View.GONE
                    binding.layoutError.visibility = View.VISIBLE
                    binding.tvMensajeError.text = "Error al cargar productos: ${e.message}"
                } else {
                    binding.pbPaginacion.visibility = View.GONE
                }
            } finally {
                estaCargando = false
            }
        }
    }
}

