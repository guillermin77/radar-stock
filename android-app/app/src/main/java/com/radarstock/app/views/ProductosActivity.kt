package com.radarstock.app.views

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.radarstock.app.adapters.ProductoAdapter
import com.radarstock.app.databinding.ActivityProductosBinding
import com.radarstock.app.network.RetrofitClient
import kotlinx.coroutines.launch

/**
 * Pantalla principal del inventario de productos.
 * Consume el endpoint api/productos.php mediante Retrofit y Corrutinas.
 */
class ProductosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProductosBinding
    private val productoAdapter = ProductoAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configurarRecyclerView()
        configurarEventos()
        cargarProductos()
    }

    private fun configurarRecyclerView() {
        binding.rvProductos.apply {
            layoutManager = LinearLayoutManager(this@ProductosActivity)
            adapter = productoAdapter
        }
    }

    private fun configurarEventos() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        binding.btnReintentar.setOnClickListener {
            cargarProductos()
        }
    }

    /**
     * Realiza la llamada a la API en segundo plano usando lifecycleScope (corrutinas)
     */
    private fun cargarProductos() {
        mostrarCargando()

        lifecycleScope.launch {
            try {
                val productos = RetrofitClient.apiService.getProductos()

                if (productos.isEmpty()) {
                    mostrarVacio()
                } else {
                    mostrarLista(productos)
                }
            } catch (e: Exception) {
                mostrarError(e.localizedMessage ?: "Fallo de conexión desconocido")
            }
        }
    }

    private fun mostrarCargando() {
        binding.progressBar.visibility = View.VISIBLE
        binding.rvProductos.visibility = View.GONE
        binding.layoutError.visibility = View.GONE
        binding.layoutVacio.visibility = View.GONE
    }

    private fun mostrarLista(productos: List<com.radarstock.app.models.Producto>) {
        binding.progressBar.visibility = View.GONE
        binding.layoutError.visibility = View.GONE
        binding.layoutVacio.visibility = View.GONE
        binding.rvProductos.visibility = View.VISIBLE
        productoAdapter.actualizarLista(productos)
    }

    private fun mostrarError(detalleError: String) {
        binding.progressBar.visibility = View.GONE
        binding.rvProductos.visibility = View.GONE
        binding.layoutVacio.visibility = View.GONE
        binding.layoutError.visibility = View.VISIBLE
        binding.tvMensajeError.text = "Error: $detalleError\n\nAsegúrate de que XAMPP esté corriendo y que la URL en Constants.kt apunte al host correcto."
    }

    private fun mostrarVacio() {
        binding.progressBar.visibility = View.GONE
        binding.rvProductos.visibility = View.GONE
        binding.layoutError.visibility = View.GONE
        binding.layoutVacio.visibility = View.VISIBLE
    }
}

