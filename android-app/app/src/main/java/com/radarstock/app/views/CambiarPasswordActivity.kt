package com.radarstock.app.views

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.radarstock.app.databinding.ActivityCambiarPasswordBinding
import com.radarstock.app.models.CambiarPasswordRequest
import com.radarstock.app.models.RespuestaApi
import com.radarstock.app.network.RetrofitClient
import com.radarstock.app.utils.SessionManager
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

/**
 * Pantalla para cambiar la contraseña del usuario actualmente autenticado (cualquier rol).
 * Al completarse con éxito, se invalida la sesión y se redirige a LoginActivity.
 */
class CambiarPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCambiarPasswordBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCambiarPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configurarToolbar()
        configurarEventos()
    }

    private fun configurarToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun configurarEventos() {
        binding.btnGuardar.setOnClickListener {
            ocultarTeclado()
            intentarCambiarPassword()
        }
    }

    private fun intentarCambiarPassword() {
        val passActual = binding.etPasswordActual.text.toString()
        val passNueva = binding.etPasswordNueva.text.toString()
        val passConfirmacion = binding.etPasswordConfirmacion.text.toString()

        var valido = true

        if (passActual.isEmpty()) {
            binding.tilPasswordActual.error = "Ingresa tu contraseña actual"
            valido = false
        } else {
            binding.tilPasswordActual.error = null
        }

        if (passNueva.length < 8) {
            binding.tilPasswordNueva.error = "La nueva contraseña debe tener mínimo 8 caracteres"
            valido = false
        } else {
            binding.tilPasswordNueva.error = null
        }

        if (passNueva != passConfirmacion) {
            binding.tilPasswordConfirmacion.error = "Las contraseñas no coinciden"
            valido = false
        } else {
            binding.tilPasswordConfirmacion.error = null
        }

        if (!valido) return

        mostrarCargando(true)
        ocultarError()

        lifecycleScope.launch {
            try {
                val req = CambiarPasswordRequest(
                    passwordActual = passActual,
                    passwordNueva = passNueva
                )
                RetrofitClient.apiService.cambiarPassword(req)

                // Éxito: cerrar sesión inmediatamente
                SessionManager.cerrarSesion()

                Toast.makeText(
                    this@CambiarPasswordActivity,
                    "Contraseña actualizada con éxito. Inicia sesión con tu nueva contraseña.",
                    Toast.LENGTH_LONG
                ).show()

                // Redirigir a Login limpiando la pila de actividades
                val intent = Intent(this@CambiarPasswordActivity, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                finish()

            } catch (e: HttpException) {
                mostrarCargando(false)
                val errorMsg = obtenerMensajeError(e)
                mostrarError(errorMsg)
            } catch (e: IOException) {
                mostrarCargando(false)
                mostrarError("No se pudo conectar con el servidor.")
            } catch (e: Exception) {
                mostrarCargando(false)
                mostrarError("Error inesperado: ${e.message}")
            }
        }
    }

    private fun mostrarCargando(cargando: Boolean) {
        binding.progressBar.visibility = if (cargando) View.VISIBLE else View.GONE
        binding.btnGuardar.isEnabled = !cargando
    }

    private fun mostrarError(mensaje: String) {
        binding.tvError.text = mensaje
        binding.tvError.visibility = View.VISIBLE
    }

    private fun ocultarError() {
        binding.tvError.visibility = View.GONE
    }

    private fun obtenerMensajeError(e: HttpException): String {
        return try {
            val errorJson = e.response()?.errorBody()?.string()
            if (!errorJson.isNullOrBlank()) {
                val resp = Gson().fromJson(errorJson, RespuestaApi::class.java)
                resp.mensaje ?: "Error del servidor (${e.code()})"
            } else {
                "Error del servidor (${e.code()})"
            }
        } catch (_: Exception) {
            "Error del servidor (${e.code()})"
        }
    }

    private fun ocultarTeclado() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        currentFocus?.let {
            imm?.hideSoftInputFromWindow(it.windowToken, 0)
        }
    }
}

