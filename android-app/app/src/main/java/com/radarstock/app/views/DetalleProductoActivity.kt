package com.radarstock.app.views

import android.app.Activity
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.radarstock.app.databinding.ActivityDetalleProductoBinding
import com.radarstock.app.models.Producto
import com.radarstock.app.models.StockRequest
import com.radarstock.app.network.RetrofitClient
import com.radarstock.app.utils.SessionManager
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

/**
 * Pantalla de detalle de producto.
 * Muestra información completa. El Administrador cuenta con acciones de edición y aumento
 * de stock en tiempo real con confirmación, mientras que el Vendedor tiene acceso de solo consulta.
 */
class DetalleProductoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetalleProductoBinding
    private var productoActual: Producto? = null

    private val formatoMoneda = NumberFormat.getCurrencyInstance(Locale("es", "CL")).apply {
        maximumFractionDigits = 0
    }

    // Launcher para abrir edición y capturar cambios
    private val editarLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Notificar a la lista que hubo cambios y cerrar detalle
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetalleProductoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        productoActual = intent.getSerializableExtra("producto") as? Producto
        val prod = productoActual
        if (prod == null) {
            Toast.makeText(this, "Error al cargar datos del producto", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        configurarToolbar()
        mostrarDatosProducto(prod)
        configurarAccionesSegunRol()
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

    private fun mostrarDatosProducto(producto: Producto) {
        binding.tvNombreProducto.text = producto.nombreProducto

        // Categoría
        if (!producto.categoria.isNullOrBlank()) {
            binding.tvCategoria.text = producto.categoria
            binding.tvCategoria.visibility = View.VISIBLE
        } else {
            binding.tvCategoria.visibility = View.GONE
        }

        // Código de barras
        binding.tvCodigoBarras.text = if (!producto.codigoBarras.isNullOrBlank()) {
            producto.codigoBarras
        } else {
            "Sin código de barras registrado"
        }

        // Precio
        binding.tvPrecio.text = formatoMoneda.format(producto.precioVenta)

        // Stock actual (en rojo si es 0)
        if (producto.stockActual == 0) {
            binding.tvStockActual.text = "0 unidades (Sin stock)"
            binding.tvStockActual.setTextColor(Color.parseColor("#DC2626"))
        } else {
            binding.tvStockActual.text = "${producto.stockActual} unidades"
            binding.tvStockActual.setTextColor(Color.parseColor("#0F172A"))
        }

        // Parámetros de inventario
        binding.tvStockMinimo.text = "${producto.stockMinimo} unidades"
        binding.tvStockDeseado.text = "${producto.stockDeseado} unidades"

        // Fecha de vencimiento
        binding.tvFechaVencimiento.text = if (!producto.fechaVencimiento.isNullOrBlank()) {
            producto.fechaVencimiento
        } else {
            "Sin fecha de caducidad"
        }
    }

    private fun configurarAccionesSegunRol() {
        val rol = SessionManager.obtenerRol() ?: "Vendedor"
        val esAdmin = rol.equals("Administrador", ignoreCase = true)

        // Las acciones de administración solo son visibles para el Administrador
        binding.layoutAccionesAdmin.visibility = if (esAdmin) View.VISIBLE else View.GONE

        // Acción: Agregar Stock
        binding.btnAgregarStock.setOnClickListener {
            mostrarDialogoAgregarStock()
        }

        // Acción: Editar Producto
        binding.btnEditarProducto.setOnClickListener {
            val intent = Intent(this, ProductoFormActivity::class.java).apply {
                putExtra("producto", productoActual)
            }
            editarLauncher.launch(intent)
        }
    }

    /**
     * Muestra diálogo interactivo para ingresar unidades que calcula y muestra
     * el stock nuevo antes de confirmar la transacción en la API.
     */
    private fun mostrarDialogoAgregarStock() {
        val prod = productoActual ?: return
        val stockActual = prod.stockActual

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 10)
        }

        val tvStockActualInfo = TextView(this).apply {
            text = "Stock actual: $stockActual unidades"
            textSize = 14f
            setTextColor(Color.parseColor("#64748B"))
        }

        val etCantidad = EditText(this).apply {
            hint = "¿Cuántas unidades llegaron?"
            inputType = InputType.TYPE_CLASS_NUMBER
            setPadding(0, 30, 0, 30)
        }

        val tvStockNuevoPreview = TextView(this).apply {
            text = "Stock nuevo: $stockActual unidades"
            textSize = 15f
            setTextColor(Color.parseColor("#1E3A8A"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 10, 0, 20)
        }

        etCantidad.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val cant = s?.toString()?.toIntOrNull() ?: 0
                val nuevo = stockActual + cant
                tvStockNuevoPreview.text = "Stock nuevo: $nuevo unidades"
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        layout.addView(tvStockActualInfo)
        layout.addView(etCantidad)
        layout.addView(tvStockNuevoPreview)

        AlertDialog.Builder(this)
            .setTitle("Agregar Stock")
            .setView(layout)
            .setPositiveButton("Confirmar") { _, _ ->
                val cantidad = etCantidad.text.toString().toIntOrNull()
                if (cantidad != null && cantidad > 0) {
                    ejecutarAumentoStock(prod.id, cantidad)
                } else {
                    Toast.makeText(this, "Ingresa una cantidad mayor a 0", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun ejecutarAumentoStock(productoId: Int, cantidad: Int) {
        lifecycleScope.launch {
            try {
                val resp = RetrofitClient.apiService.agregarStock(StockRequest(productoId, cantidad))
                // Actualizar modelo local y vista
                productoActual = productoActual?.copy(stockActual = resp.stockActual)
                productoActual?.let { mostrarDatosProducto(it) }

                Toast.makeText(
                    this@DetalleProductoActivity,
                    "Stock actualizado a ${resp.stockActual} unidades",
                    Toast.LENGTH_SHORT
                ).show()

                // Notificar que hubo cambios de stock
                setResult(Activity.RESULT_OK)

            } catch (e: Exception) {
                Toast.makeText(
                    this@DetalleProductoActivity,
                    "Error al actualizar stock: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
