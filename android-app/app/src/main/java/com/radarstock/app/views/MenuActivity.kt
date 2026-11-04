package com.radarstock.app.views

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.radarstock.app.databinding.ActivityMenuBinding
import com.radarstock.app.utils.SessionManager

/**
 * Menú principal de la aplicación.
 * Muestra las opciones operativas según el rol del usuario (Administrador o Vendedor).
 */
class MenuActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMenuBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configurarVistaSegunRol()
        configurarEventos()
    }

    private fun configurarVistaSegunRol() {
        val nombre = SessionManager.obtenerNombre() ?: "Usuario"
        val rol = SessionManager.obtenerRol() ?: "Vendedor"

        binding.tvNombreUsuario.text = nombre
        binding.tvRolUsuario.text = "Rol: $rol"

        // Los botones de administración solo son visibles para el Administrador
        val esAdmin = rol.equals("Administrador", ignoreCase = true)
        binding.layoutAdmin.visibility = if (esAdmin) View.VISIBLE else View.GONE
    }

    private fun configurarEventos() {
        // Opciones comunes (Administrador y Vendedor)
        binding.btnVenta.setOnClickListener {
            val intent = Intent(this, VentaActivity::class.java)
            startActivity(intent)
        }

        binding.btnProductos.setOnClickListener {
            val intent = Intent(this, ProductosActivity::class.java)
            startActivity(intent)
        }

        binding.btnAlertas.setOnClickListener {
            mostrarProximamente("Alertas")
        }

        // Opciones exclusivas de Administrador
        binding.btnResumenVentas.setOnClickListener {
            val intent = Intent(this, ResumenActivity::class.java)
            startActivity(intent)
        }

        binding.btnUsuarios.setOnClickListener {
            mostrarProximamente("Usuarios")
        }

        binding.btnEscaner.setOnClickListener {
            mostrarProximamente("Escáner")
        }

        // Cerrar sesión
        binding.btnCerrarSesion.setOnClickListener {
            cerrarSesion()
        }
    }

    private fun mostrarProximamente(modulo: String) {
        Toast.makeText(this, "Próximamente: $modulo", Toast.LENGTH_SHORT).show()
    }

    private fun cerrarSesion() {
        SessionManager.cerrarSesion()
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}
