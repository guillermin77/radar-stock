package com.radarstock.app.views

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.radarstock.app.databinding.ActivityMenuBinding
import com.radarstock.app.network.RetrofitClient
import com.radarstock.app.utils.NotificationHelper
import com.radarstock.app.utils.SessionManager
import kotlinx.coroutines.launch

/**
 * Menú principal de la aplicación.
 * Muestra las opciones operativas según el rol del usuario (Administrador o Vendedor).
 * Muestra en el botón «Alertas» el conteo total de alertas actualizado cada vez que se vuelve a la pantalla.
 * Solicita el permiso POST_NOTIFICATIONS con explicación una sola vez tras el primer login.
 */
class MenuActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMenuBinding

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Si el usuario lo acepta o lo rechaza, la aplicación sigue funcionando con normalidad
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)

        NotificationHelper.crearCanalNotificaciones(this)
        solicitarPermisoNotificacionesSiAplica()

        configurarVistaSegunRol()
        configurarEventos()
    }

    override fun onResume() {
        super.onResume()
        // Actualizar el número de alertas en el botón cada vez que se vuelve al menú
        actualizarContadorAlertas()
    }

    private fun solicitarPermisoNotificacionesSiAplica() {
        if (NotificationHelper.debeSolicitarPermiso(this)) {
            NotificationHelper.marcarPermisoSolicitado(this)

            AlertDialog.Builder(this)
                .setTitle("Alertas de Stock")
                .setMessage("Radar Stock te enviará notificaciones oportunas cuando un producto se agote o alcance su nivel de stock mínimo tras una venta para ayudarte a no perder ventas.")
                .setPositiveButton("Permitir") { _, _ ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                .setNegativeButton("Ahora no", null)
                .show()
        }
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
            val intent = Intent(this, AlertasActivity::class.java)
            startActivity(intent)
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

    private fun actualizarContadorAlertas() {
        lifecycleScope.launch {
            try {
                val resumen = RetrofitClient.apiService.getResumenAlertas()
                val total = resumen.total
                binding.btnAlertas.text = if (total > 0) "Alertas ($total)" else "Alertas (0)"
            } catch (e: Exception) {
                // Si la red no responde, mantener el texto base
            }
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
