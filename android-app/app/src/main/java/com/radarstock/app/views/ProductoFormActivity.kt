package com.radarstock.app.views

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.radarstock.app.databinding.ActivityProductoFormBinding
import com.radarstock.app.models.Producto
import com.radarstock.app.models.ProductoRequest
import com.radarstock.app.models.RespuestaApi
import com.radarstock.app.network.RetrofitClient
import com.radarstock.app.utils.EscanerCodigo
import com.radarstock.app.utils.SessionManager
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Formulario para creación y edición de productos (Acceso exclusivo Administrador).
 * Permite escaneo asistido de código de barras, guardado opcional de código (NULL si está vacío),
 * y gestión de errores 409 (código repetido) y 401 (sesión expirada).
 */
class ProductoFormActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProductoFormBinding

    private var productoAEditar: Producto? = null
    private var esModoEdicion: Boolean = false

    private val listaCategorias = mutableListOf<String>()
    private lateinit var categoriasAdapter: ArrayAdapter<String>
    private val OPCION_NUEVA_CATEGORIA = "+ Nueva categoría..."

    private val formatoFecha = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val calendario = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Validar rol: solo Administrador puede acceder a este formulario
        val rol = SessionManager.obtenerRol() ?: ""
        if (!rol.equals("Administrador", ignoreCase = true)) {
            Toast.makeText(this, "Acceso denegado: solo Administrador", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding = ActivityProductoFormBinding.inflate(layoutInflater)
        setContentView(binding.root)

        productoAEditar = intent.getSerializableExtra("producto") as? Producto
        esModoEdicion = (productoAEditar != null)

        configurarToolbar()
        configurarCategorias()
        configurarSelectorFecha()
        configurarVistasSegunModo()
        configurarEscanerCodigo()
        configurarBotones()
    }

    private fun configurarToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        binding.toolbar.title = if (esModoEdicion) "Editar Producto" else "Crear Producto"
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun configurarCategorias() {
        categoriasAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            listaCategorias
        )
        binding.actvCategoria.setAdapter(categoriasAdapter)

        // Cargar categorías desde el servidor
        lifecycleScope.launch {
            try {
                val cats = RetrofitClient.apiService.getCategorias()
                listaCategorias.clear()
                listaCategorias.addAll(cats)
                listaCategorias.add(OPCION_NUEVA_CATEGORIA)
                categoriasAdapter.notifyDataSetChanged()

                // Si estamos editando, reasegurar la selección
                if (esModoEdicion && !productoAEditar?.categoria.isNullOrBlank()) {
                    binding.actvCategoria.setText(productoAEditar?.categoria, false)
                }
            } catch (e: Exception) {
                val base = listOf("Bebidas", "Lácteos", "Abarrotes", "Panadería", "Aseo", "Snacks", OPCION_NUEVA_CATEGORIA)
                listaCategorias.clear()
                listaCategorias.addAll(base)
                categoriasAdapter.notifyDataSetChanged()
            }
        }

        binding.actvCategoria.setOnItemClickListener { _, _, position, _ ->
            val seleccion = listaCategorias[position]
            if (seleccion == OPCION_NUEVA_CATEGORIA) {
                mostrarDialogoNuevaCategoria()
            }
        }
    }

    private fun mostrarDialogoNuevaCategoria() {
        val input = EditText(this).apply {
            hint = "Nombre de la nueva categoría"
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(this)
            .setTitle("Nueva Categoría")
            .setView(input)
            .setPositiveButton("Crear") { _, _ ->
                val nueva = input.text.toString().trim()
                if (nueva.isNotEmpty()) {
                    if (!listaCategorias.contains(nueva)) {
                        listaCategorias.add(listaCategorias.size - 1, nueva)
                        categoriasAdapter.notifyDataSetChanged()
                    }
                    binding.actvCategoria.setText(nueva, false)
                }
            }
            .setNegativeButton("Cancelar") { _, _ ->
                binding.actvCategoria.setText("", false)
            }
            .show()
    }

    private fun configurarSelectorFecha() {
        val dateSetListener = DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
            calendario.set(Calendar.YEAR, year)
            calendario.set(Calendar.MONTH, month)
            calendario.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            binding.etFechaVencimiento.setText(formatoFecha.format(calendario.time))
            binding.cbNoVence.isChecked = false
        }

        val abrirDatePicker = View.OnClickListener {
            if (!binding.cbNoVence.isChecked) {
                DatePickerDialog(
                    this,
                    dateSetListener,
                    calendario.get(Calendar.YEAR),
                    calendario.get(Calendar.MONTH),
                    calendario.get(Calendar.DAY_OF_MONTH)
                ).show()
            }
        }

        binding.etFechaVencimiento.setOnClickListener(abrirDatePicker)
        binding.tilFechaVencimiento.setEndIconOnClickListener(abrirDatePicker)

        binding.cbNoVence.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.etFechaVencimiento.setText("")
                binding.tilFechaVencimiento.isEnabled = false
            } else {
                binding.tilFechaVencimiento.isEnabled = true
                if (binding.etFechaVencimiento.text.isNullOrBlank()) {
                    binding.etFechaVencimiento.setText(formatoFecha.format(calendario.time))
                }
            }
        }
    }

    private fun configurarVistasSegunModo() {
        val producto = productoAEditar
        if (esModoEdicion && producto != null) {
            binding.etNombre.setText(producto.nombreProducto)
            binding.actvCategoria.setText(producto.categoria ?: "", false)
            binding.etPrecio.setText(producto.precioVenta.toString())
            binding.etCodigoBarras.setText(producto.codigoBarras ?: "")

            // Stock actual en edición: editable para Administrador con registro de ajuste
            binding.etStockActual.setText(producto.stockActual.toString())
            binding.etStockActual.isEnabled = true
            binding.tilStockActual.hint = "Stock actual"
            binding.tilStockActual.helperText = "Si lo modificas, se registrará un movimiento de ajuste."

            binding.etStockMinimo.setText(producto.stockMinimo.toString())
            binding.etStockDeseado.setText(producto.stockDeseado.toString())

            // Fecha de vencimiento
            if (!producto.fechaVencimiento.isNullOrBlank()) {
                binding.cbNoVence.isChecked = false
                binding.tilFechaVencimiento.isEnabled = true
                binding.etFechaVencimiento.setText(producto.fechaVencimiento)
            } else {
                binding.cbNoVence.isChecked = true
                binding.tilFechaVencimiento.isEnabled = false
                binding.etFechaVencimiento.setText("")
            }

            binding.btnGuardar.text = "Guardar Cambios"
            binding.btnEliminar.visibility = View.VISIBLE

        } else {
            // Modo Creación
            binding.tilStockActual.hint = "Stock inicial"
            binding.tilStockActual.helperText = "Total de partida al ingresar el producto"
            binding.etStockActual.isEnabled = true
            binding.etStockActual.setText("0")
            binding.etStockMinimo.setText("5")
            binding.etStockDeseado.setText("0")

            binding.cbNoVence.isChecked = true
            binding.tilFechaVencimiento.isEnabled = false

            binding.btnGuardar.text = "Guardar Producto"
            binding.btnEliminar.visibility = View.GONE

            // Si se abrió tras escanear un código no registrado
            val codigoInicial = intent.getStringExtra("codigo_barras_inicial")
            if (!codigoInicial.isNullOrBlank()) {
                binding.etCodigoBarras.setText(codigoInicial)
                binding.etNombre.requestFocus()
            }
        }
    }

    /**
     * Configura el botón y el icono dentro del formulario para escanear código de barras.
     */
    private fun configurarEscanerCodigo() {
        val ejecutarEscaneo = View.OnClickListener {
            EscanerCodigo.escanear(
                context = this,
                onCodigoEscaneado = { codigo ->
                    binding.etCodigoBarras.setText(codigo)
                    Toast.makeText(this, "Código detectado: $codigo", Toast.LENGTH_SHORT).show()
                },
                onError = { errorMsg ->
                    Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
                }
            )
        }

        binding.btnEscanearCodigoForm.setOnClickListener(ejecutarEscaneo)
        binding.tilCodigoBarras.setEndIconOnClickListener(ejecutarEscaneo)
    }

    private fun configurarBotones() {
        binding.btnGuardar.setOnClickListener {
            validarYEnviar()
        }

        binding.btnEliminar.setOnClickListener {
            confirmarEliminacion()
        }
    }

    private fun validarYEnviar() {
        ocultarError()

        val nombre = binding.etNombre.text.toString().trim()
        val categoria = binding.actvCategoria.text.toString().trim().takeIf { it.isNotEmpty() && it != OPCION_NUEVA_CATEGORIA }
        val precioStr = binding.etPrecio.text.toString().trim()

        // Código de barras: Opcional (si queda vacío, se envía null para guardarse como NULL en la BD)
        val codigoBarrasRaw = binding.etCodigoBarras.text.toString().trim()
        val codigoBarras = if (codigoBarrasRaw.isNotEmpty()) codigoBarrasRaw else null

        val stockActualStr = binding.etStockActual.text.toString().trim()
        val stockMinimoStr = binding.etStockMinimo.text.toString().trim()
        val stockDeseadoStr = binding.etStockDeseado.text.toString().trim()
        val fechaVencimiento = if (binding.cbNoVence.isChecked) null else binding.etFechaVencimiento.text.toString().trim().takeIf { it.isNotEmpty() }

        // 1. Validar Nombre
        if (nombre.isEmpty()) {
            binding.tilNombre.error = "El nombre es obligatorio"
            binding.etNombre.requestFocus()
            return
        } else {
            binding.tilNombre.error = null
        }

        // 2. Validar Precio
        val precio = precioStr.toDoubleOrNull()
        if (precio == null || precio < 0) {
            binding.tilPrecio.error = "Ingresa un precio válido (>= 0)"
            binding.etPrecio.requestFocus()
            return
        } else {
            binding.tilPrecio.error = null
        }

        // 3. Validar Stock Inicial (en creación)
        val stockActual = stockActualStr.toIntOrNull() ?: 0
        if (stockActual < 0) {
            binding.tilStockActual.error = "El stock debe ser >= 0"
            return
        } else {
            binding.tilStockActual.error = null
        }

        // 4. Validar Stock Mínimo
        val stockMinimo = stockMinimoStr.toIntOrNull() ?: 5
        if (stockMinimo < 0) {
            binding.tilStockMinimo.error = "El stock mínimo debe ser >= 0"
            return
        } else {
            binding.tilStockMinimo.error = null
        }

        // 5. Validar Stock Deseado
        val stockDeseado = stockDeseadoStr.toIntOrNull() ?: 0
        if (stockDeseado < 0) {
            binding.tilStockDeseado.error = "El stock deseado debe ser >= 0"
            return
        } else {
            binding.tilStockDeseado.error = null
        }

        // Construir Request con código de barras opcional (null si vacío)
        val request = ProductoRequest(
            nombreProducto = nombre,
            categoria = categoria,
            precioVenta = precio,
            codigoBarras = codigoBarras,
            stockActual = stockActual,
            stockMinimo = stockMinimo,
            stockDeseado = stockDeseado,
            fechaVencimiento = fechaVencimiento
        )

        ejecutarGuardado(request)
    }

    private fun ejecutarGuardado(request: ProductoRequest) {
        mostrarCargando(true)

        lifecycleScope.launch {
            try {
                if (esModoEdicion) {
                    val id = productoAEditar!!.id
                    RetrofitClient.apiService.editarProducto(id, request)
                    Toast.makeText(this@ProductoFormActivity, "Producto actualizado correctamente", Toast.LENGTH_SHORT).show()
                } else {
                    RetrofitClient.apiService.crearProducto(request)
                    Toast.makeText(this@ProductoFormActivity, "Producto creado exitosamente", Toast.LENGTH_SHORT).show()
                }

                mostrarCargando(false)
                setResult(RESULT_OK)
                finish()

            } catch (e: HttpException) {
                mostrarCargando(false)
                val codigoHttp = e.code()
                val mensajeApi = extraerMensajeDeError(e)

                when (codigoHttp) {
                    401 -> {
                        manejarSesionExpirada()
                    }
                    409 -> {
                        // Código de barras repetido u otro conflicto
                        AlertDialog.Builder(this@ProductoFormActivity)
                            .setTitle("Código Ya Registrado")
                            .setMessage(mensajeApi)
                            .setPositiveButton("Entendido", null)
                            .show()
                        mostrarError(mensajeApi)
                    }
                    else -> {
                        mostrarError(mensajeApi)
                    }
                }
            } catch (e: IOException) {
                mostrarCargando(false)
                mostrarError("No se pudo conectar con el servidor. Verifica tu conexión.")
            } catch (e: Exception) {
                mostrarCargando(false)
                mostrarError("Error inesperado: ${e.message}")
            }
        }
    }

    private fun confirmarEliminacion() {
        val producto = productoAEditar ?: return

        AlertDialog.Builder(this)
            .setTitle("Eliminar Producto")
            .setMessage("¿Estás seguro de que deseas eliminar «${producto.nombreProducto}»? Esta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ ->
                ejecutarEliminacion(producto.id)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun ejecutarEliminacion(id: Int) {
        mostrarCargando(true)

        lifecycleScope.launch {
            try {
                val respuesta = RetrofitClient.apiService.eliminarProducto(id)
                mostrarCargando(false)
                Toast.makeText(this@ProductoFormActivity, respuesta.mensaje ?: "Producto eliminado", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()

            } catch (e: HttpException) {
                mostrarCargando(false)
                val mensajeApi = extraerMensajeDeError(e)
                if (e.code() == 401) {
                    manejarSesionExpirada()
                    return@launch
                }
                AlertDialog.Builder(this@ProductoFormActivity)
                    .setTitle("No se puede eliminar")
                    .setMessage(mensajeApi)
                    .setPositiveButton("Entendido", null)
                    .show()
                mostrarError(mensajeApi)
            } catch (e: IOException) {
                mostrarCargando(false)
                mostrarError("No se pudo conectar con el servidor.")
            } catch (e: Exception) {
                mostrarCargando(false)
                mostrarError("Error inesperado: ${e.message}")
            }
        }
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
            val cuerpoError = e.response()?.errorBody()?.string()
            if (!cuerpoError.isNullOrBlank()) {
                val apiResp = Gson().fromJson(cuerpoError, RespuestaApi::class.java)
                apiResp.mensaje ?: "Error del servidor (${e.code()})"
            } else {
                "Error del servidor (${e.code()})"
            }
        } catch (_: Exception) {
            "Error del servidor (${e.code()})"
        }
    }

    private fun mostrarCargando(cargando: Boolean) {
        binding.pbGuardando.visibility = if (cargando) View.VISIBLE else View.GONE
        binding.btnGuardar.isEnabled = !cargando
        binding.btnEliminar.isEnabled = !cargando
    }

    private fun mostrarError(mensaje: String) {
        binding.tvErrorBanner.text = mensaje
        binding.tvErrorBanner.visibility = View.VISIBLE
    }

    private fun ocultarError() {
        binding.tvErrorBanner.visibility = View.GONE
    }
}
