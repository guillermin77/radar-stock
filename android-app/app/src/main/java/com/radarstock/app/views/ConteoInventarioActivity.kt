package com.radarstock.app.views

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.radarstock.app.adapters.ConteoAdapter
import com.radarstock.app.adapters.ItemConteo
import com.radarstock.app.databinding.ActivityConteoInventarioBinding
import com.radarstock.app.models.ConteoItemRequest
import com.radarstock.app.models.Producto
import com.radarstock.app.models.RespuestaApi
import com.radarstock.app.network.RetrofitClient
import com.radarstock.app.utils.EscanerCodigo
import com.radarstock.app.utils.SessionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

/**
 * Pantalla de Conteo Físico de Inventario (Solo Administrador).
 * Permite ingresar productos escaneando o buscando por nombre, registrar el stock contado,
 * calcular diferencias en tiempo real y guardar todo en una sola transacción atómica.
 */
class ConteoInventarioActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConteoInventarioBinding

    // Lista de productos del almacén para búsqueda rápida por nombre y código
    private val catalogoProductos = mutableListOf<Producto>()
    private lateinit var buscadorAdapter: ArrayAdapter<String>
    private val nombresCatalogo = mutableListOf<String>()

    // Adaptador del RecyclerView de conteo
    private lateinit var conteoAdapter: ConteoAdapter

    // Control de guardado y confirmación de salida
    private var estaGuardando = false
    private var conteoGuardado = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Validar rol Administrador
        val rol = SessionManager.obtenerRol() ?: ""
        if (!rol.equals("Administrador", ignoreCase = true)) {
            Toast.makeText(this, "Acceso denegado: solo Administrador", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding = ActivityConteoInventarioBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configurarToolbar()
        configurarRecyclerView()
        configurarBuscador()
        configurarEscaner()
        configurarBotonGuardar()
        configurarControlSalida()

        cargarCatalogoProductos()
    }

    private fun configurarToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        binding.toolbar.setNavigationOnClickListener {
            verificarSalida { finish() }
        }
    }

    private fun configurarControlSalida() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                verificarSalida {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    /**
     * Pide confirmación al usuario si intenta salir con un conteo sin guardar
     */
    private fun verificarSalida(onConfirmarSalida: () -> Unit) {
        if (!conteoGuardado && conteoAdapter.items.isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("¿Descartar Conteo?")
                .setMessage("Tienes ${conteoAdapter.items.size} productos en conteo sin guardar. Si sales ahora, se perderán los datos ingresados.")
                .setPositiveButton("Descartar y salir") { _, _ ->
                    onConfirmarSalida()
                }
                .setNegativeButton("Continuar conteo", null)
                .show()
        } else {
            onConfirmarSalida()
        }
    }

    private fun configurarRecyclerView() {
        conteoAdapter = ConteoAdapter(
            onEliminarItem = { posicion ->
                if (posicion in 0 until conteoAdapter.items.size) {
                    conteoAdapter.items.removeAt(posicion)
                    conteoAdapter.notifyItemRemoved(posicion)
                    actualizarEstadoVistas()
                }
            },
            onCambioValor = {
                actualizarResumenConteo()
            }
        )

        binding.rvConteoProductos.apply {
            layoutManager = LinearLayoutManager(this@ConteoInventarioActivity)
            adapter = conteoAdapter
        }

        actualizarEstadoVistas()
    }

    private fun configurarBuscador() {
        buscadorAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            nombresCatalogo
        )
        binding.actvBuscarProducto.setAdapter(buscadorAdapter)

        binding.actvBuscarProducto.setOnItemClickListener { _, _, position, _ ->
            val nombreSeleccionado = buscadorAdapter.getItem(position)
            val producto = catalogoProductos.firstOrNull { it.nombreProducto == nombreSeleccionado }
            if (producto != null) {
                agregarProductoAlConteo(producto)
            }
            binding.actvBuscarProducto.setText("", false)
        }
    }

    private fun configurarEscaner() {
        binding.btnEscanearCodigo.setOnClickListener {
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
    }

    /**
     * Procesa un código escaneado: busca primero en el catálogo local y si no está consulta al servidor.
     */
    private fun procesarCodigoEscaneado(codigo: String) {
        val productoLocal = catalogoProductos.firstOrNull {
            it.codigoBarras.equals(codigo, ignoreCase = true)
        }

        if (productoLocal != null) {
            agregarProductoAlConteo(productoLocal)
            return
        }

        // Consultar al servidor por código
        lifecycleScope.launch {
            try {
                val productoRemoto = RetrofitClient.apiService.getProductoPorCodigo(codigo)
                agregarProductoAlConteo(productoRemoto)
            } catch (e: HttpException) {
                if (e.code() == 404) {
                    Toast.makeText(this@ConteoInventarioActivity, "No se encontró ningún producto con el código $codigo", Toast.LENGTH_LONG).show()
                } else if (e.code() == 401) {
                    manejarSesionExpirada()
                } else {
                    Toast.makeText(this@ConteoInventarioActivity, extraerMensajeDeError(e), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ConteoInventarioActivity, "Error al buscar producto: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Agrega un producto a la lista de conteo. Si ya existe, no lo duplica y resalta la fila existente.
     */
    private fun agregarProductoAlConteo(producto: Producto) {
        val indiceExistente = conteoAdapter.items.indexOfFirst { it.producto.id == producto.id }

        if (indiceExistente != -1) {
            // Ya está en el conteo: resaltar la fila sin duplicar
            val item = conteoAdapter.items[indiceExistente]
            item.resaltar = true
            conteoAdapter.notifyItemChanged(indiceExistente)
            binding.rvConteoProductos.smoothScrollToPosition(indiceExistente)

            Toast.makeText(
                this,
                "«${producto.nombreProducto}» ya está en el conteo",
                Toast.LENGTH_SHORT
            ).show()

            // Desactivar el resaltado tras 2 segundos
            lifecycleScope.launch {
                delay(2000)
                item.resaltar = false
                conteoAdapter.notifyItemChanged(indiceExistente)
            }
        } else {
            // Agregar al inicio del conteo con stock contado igual al stock del sistema de partida
            val nuevoItem = ItemConteo(producto = producto, contado = producto.stockActual)
            conteoAdapter.items.add(0, nuevoItem)
            conteoAdapter.notifyItemInserted(0)
            binding.rvConteoProductos.scrollToPosition(0)

            actualizarEstadoVistas()
            Toast.makeText(this, "Agregado: ${producto.nombreProducto}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun configurarBotonGuardar() {
        binding.btnGuardarConteo.setOnClickListener {
            if (conteoAdapter.items.isEmpty()) {
                Toast.makeText(this, "Agrega al menos un producto al conteo antes de guardar", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            confirmarGuardadoConteo()
        }
    }

    /**
     * Muestra resumen de confirmación antes de impactar la base de datos
     */
    private fun confirmarGuardadoConteo() {
        val total = conteoAdapter.items.size
        val cambiaran = conteoAdapter.items.count { it.diferencia != 0 }
        val sinCambios = total - cambiaran

        AlertDialog.Builder(this)
            .setTitle("¿Confirmar Guardado de Conteo?")
            .setMessage(
                "Resumen del conteo físico:\n\n" +
                "• Total de productos contados: $total\n" +
                "• Productos que cambiarán su stock: $cambiaran\n" +
                "• Productos que se mantienen iguales: $sinCambios\n\n" +
                "¿Deseas aplicar estos cambios de stock al inventario de forma definitiva?"
            )
            .setPositiveButton("Guardar Conteo") { _, _ ->
                ejecutarGuardadoConteo()
            }
            .setNegativeButton("Revisar", null)
            .show()
    }

    /**
     * Envía todo el conteo en una sola llamada atómica a api/inventario_conteo.php
     */
    private fun ejecutarGuardadoConteo() {
        if (estaGuardando) return
        estaGuardando = true

        binding.btnGuardarConteo.isEnabled = false
        binding.pbGuardando.visibility = View.VISIBLE

        val listaRequest = conteoAdapter.items.map { item ->
            ConteoItemRequest(
                productoId = item.producto.id,
                contado = item.contado
            )
        }

        lifecycleScope.launch {
            try {
                val respuesta = RetrofitClient.apiService.guardarConteo(listaRequest)
                conteoGuardado = true

                binding.pbGuardando.visibility = View.GONE
                binding.btnGuardarConteo.isEnabled = true
                estaGuardando = false

                AlertDialog.Builder(this@ConteoInventarioActivity)
                    .setTitle("Conteo Guardado Exitosamente")
                    .setMessage(
                        "${respuesta.mensaje}\n\n" +
                        "• Productos procesados: ${respuesta.totalProcesados}\n" +
                        "• Productos modificados: ${respuesta.totalModificados}\n\n" +
                        "Los movimientos de auditoría quedaron registrados en el historial."
                    )
                    .setCancelable(false)
                    .setPositiveButton("Aceptar") { _, _ ->
                        finish()
                    }
                    .show()

            } catch (e: HttpException) {
                binding.pbGuardando.visibility = View.GONE
                binding.btnGuardarConteo.isEnabled = true
                estaGuardando = false

                when (e.code()) {
                    401 -> manejarSesionExpirada()
                    409 -> {
                        AlertDialog.Builder(this@ConteoInventarioActivity)
                            .setTitle("Conflicto en Conteo")
                            .setMessage(extraerMensajeDeError(e))
                            .setPositiveButton("Entendido", null)
                            .show()
                    }
                    else -> {
                        Toast.makeText(this@ConteoInventarioActivity, extraerMensajeDeError(e), Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: IOException) {
                binding.pbGuardando.visibility = View.GONE
                binding.btnGuardarConteo.isEnabled = true
                estaGuardando = false
                Toast.makeText(this@ConteoInventarioActivity, "Error de conexión al enviar el conteo.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                binding.pbGuardando.visibility = View.GONE
                binding.btnGuardarConteo.isEnabled = true
                estaGuardando = false
                Toast.makeText(this@ConteoInventarioActivity, "Error inesperado: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Carga el catálogo general de productos para agilizar búsquedas locales
     */
    private fun cargarCatalogoProductos() {
        binding.pbCargandoCatalogo.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val productos = RetrofitClient.apiService.getProductos(limite = 100)
                catalogoProductos.clear()
                catalogoProductos.addAll(productos)

                nombresCatalogo.clear()
                nombresCatalogo.addAll(productos.map { it.nombreProducto })
                buscadorAdapter.notifyDataSetChanged()

            } catch (e: HttpException) {
                if (e.code() == 401) manejarSesionExpirada()
            } catch (_: Exception) {
                // Falla silenciosa de precarga: el usuario aún puede consultar por código
            } finally {
                binding.pbCargandoCatalogo.visibility = View.GONE
            }
        }
    }

    private fun actualizarEstadoVistas() {
        val hayItems = conteoAdapter.items.isNotEmpty()
        binding.rvConteoProductos.visibility = if (hayItems) View.VISIBLE else View.GONE
        binding.layoutConteoVacio.visibility = if (hayItems) View.GONE else View.VISIBLE
        binding.layoutResumenBar.visibility = if (hayItems) View.VISIBLE else View.GONE

        actualizarResumenConteo()
    }

    private fun actualizarResumenConteo() {
        val total = conteoAdapter.items.size
        val conDiferencias = conteoAdapter.items.count { it.diferencia != 0 }

        binding.tvTotalItemsConteo.text = "Productos en conteo: $total"
        binding.tvTotalConDiferencias.text = "Con diferencias: $conDiferencias"
    }

    private fun manejarSesionExpirada() {
        SessionManager.cerrarSesion()
        Toast.makeText(this, "Tu sesión ha expirado. Inicia sesión nuevamente.", Toast.LENGTH_LONG).show()
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
}

