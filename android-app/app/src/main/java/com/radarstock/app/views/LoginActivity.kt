package com.radarstock.app.views

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.radarstock.app.databinding.ActivityLoginBinding
import com.radarstock.app.models.LoginRequest
import com.radarstock.app.network.RetrofitClient
import com.radarstock.app.utils.SessionManager
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

/**
 * Pantalla de inicio de sesión.
 * Valida credenciales contra api/login.php y almacena token, nombre y rol.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sesionExpirada = intent.getBooleanExtra("SESION_EXPIRADA", false)

        // Si ya existe una sesión activa y no proviene de un 401, derivar directamente al Menú
        if (!sesionExpirada && SessionManager.haySesion()) {
            navegarAlMenu()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configurarEventos()

        // Mostrar aviso de sesión cerrada o usuario desactivado
        if (sesionExpirada) {
            val mensaje = intent.getStringExtra("MENSAJE_EXPIRADA") 
                ?: "Tu sesión ha expirado o tu usuario fue desactivado. Inicia sesión nuevamente."
            mostrarError(mensaje)
            AlertDialog.Builder(this)
                .setTitle("Sesión finalizada")
                .setMessage(mensaje)
                .setPositiveButton("Entendido", null)
                .show()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("SESION_EXPIRADA", false)) {
            val mensaje = intent.getStringExtra("MENSAJE_EXPIRADA") 
                ?: "Tu sesión ha expirado o tu usuario fue desactivado. Inicia sesión nuevamente."
            mostrarError(mensaje)
            AlertDialog.Builder(this)
                .setTitle("Sesión finalizada")
                .setMessage(mensaje)
                .setPositiveButton("Entendido", null)
                .show()
        }
    }

    private fun configurarEventos() {
        binding.btnIngresar.setOnClickListener {
            ocultarTeclado()
            intentarLogin()
        }
    }

    private fun intentarLogin() {
        val correo = binding.etCorreo.text.toString().trim()
        val password = binding.etPassword.text.toString()

        if (correo.isEmpty()) {
            binding.tilCorreo.error = "Ingresa tu correo"
            return
        } else {
            binding.tilCorreo.error = null
        }

        if (password.isEmpty()) {
            binding.tilPassword.error = "Ingresa tu contraseña"
            return
        } else {
            binding.tilPassword.error = null
        }

        mostrarCargando(true)
        ocultarError()

        lifecycleScope.launch {
            try {
                val respuesta = RetrofitClient.apiService.login(LoginRequest(correo, password))

                // Guardar datos en SharedPreferences
                SessionManager.guardarSesion(
                    token = respuesta.token,
                    nombre = respuesta.usuario.nombre,
                    rol = respuesta.usuario.rol
                )

                mostrarCargando(false)
                navegarAlMenu()

            } catch (e: HttpException) {
                mostrarCargando(false)
                if (e.code() == 401) {
                    mostrarError("Correo o contraseña incorrectos")
                } else {
                    mostrarError("Error del servidor (${e.code()}). Intenta de nuevo.")
                }
            } catch (e: IOException) {
                mostrarCargando(false)
                mostrarError("No se pudo conectar con el servidor")
            } catch (e: Exception) {
                mostrarCargando(false)
                mostrarError("Error inesperado: ${e.message}")
            }
        }
    }

    private fun mostrarCargando(cargando: Boolean) {
        binding.progressBar.visibility = if (cargando) View.VISIBLE else View.GONE
        binding.btnIngresar.isEnabled = !cargando
    }

    private fun mostrarError(mensaje: String) {
        binding.tvError.text = mensaje
        binding.tvError.visibility = View.VISIBLE
    }

    private fun ocultarError() {
        binding.tvError.visibility = View.GONE
    }

    private fun navegarAlMenu() {
        val intent = Intent(this, MenuActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun ocultarTeclado() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        currentFocus?.let {
            imm?.hideSoftInputFromWindow(it.windowToken, 0)
        }
    }
}
