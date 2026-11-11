package com.radarstock.app.views

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.radarstock.app.adapters.UsuariosAdapter
import com.radarstock.app.databinding.ActivityUsuariosBinding
import com.radarstock.app.databinding.DialogResetPasswordBinding
import com.radarstock.app.databinding.DialogUsuarioFormBinding
import com.radarstock.app.models.RespuestaApi
import com.radarstock.app.models.Usuario
import com.radarstock.app.models.UsuarioRequest
import com.radarstock.app.network.RetrofitClient
import com.radarstock.app.utils.AuditoriaVentasHelper
import com.radarstock.app.utils.SessionManager
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

/**
 * Pantalla de gestión de usuarios para el rol Administrador.
 * Permite listar, crear, editar, activar/desactivar y resetear contraseñas.
 */
class UsuariosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUsuariosBinding
    private lateinit var adapter: UsuariosAdapter

    private val rolesPermitidos = arrayOf("Vendedor", "Administrador")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Verificación de seguridad en la app: solo Administrador
        val rolActual = SessionManager.obtenerRol()
        if (!rolActual.equals("Administrador", ignoreCase = true)) {
            Toast.makeText(this, "Acceso restringido a Administradores", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding = ActivityUsuariosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configurarToolbar()
        configurarRecyclerView()
        configurarEventos()

        cargarUsuarios()
    }

    private fun configurarToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun configurarRecyclerView() {
        adapter = UsuariosAdapter { usuario ->
            mostrarOpcionesUsuario(usuario)
        }
        binding.rvUsuarios.layoutManager = LinearLayoutManager(this)
        binding.rvUsuarios.adapter = adapter
    }

    private fun configurarEventos() {
        binding.btnNuevoUsuario.setOnClickListener {
            mostrarDialogCrearUsuario()
        }
        binding.btnCompararVendedores.setOnClickListener {
            val intent = Intent(this, CompararVendedoresActivity::class.java)
            startActivity(intent)
        }
    }

    private fun cargarUsuarios() {
        mostrarCargando(true)

        lifecycleScope.launch {
            try {
                val usuarios = RetrofitClient.apiService.getUsuarios()
                mostrarCargando(false)
                adapter.setUsuarios(usuarios)

                binding.tvEmptyUsuarios.visibility = if (usuarios.isEmpty()) View.VISIBLE else View.GONE
            } catch (e: HttpException) {
                mostrarCargando(false)
                if (e.code() == 401) {
                    AuditoriaVentasHelper.redirigirALoginPorSesionExpirada(this@UsuariosActivity)
                    return@launch
                }
                val errorMsg = obtenerMensajeError(e)
                Toast.makeText(this@UsuariosActivity, errorMsg, Toast.LENGTH_LONG).show()
            } catch (e: IOException) {
                mostrarCargando(false)
                Toast.makeText(this@UsuariosActivity, "Error de conexión con el servidor", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                mostrarCargando(false)
                Toast.makeText(this@UsuariosActivity, "Error inesperado: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun mostrarOpcionesUsuario(usuario: Usuario) {
        val opcionEstado = if (usuario.esActivo) "Desactivar usuario" else "Activar usuario"
        val opciones = arrayOf("Ver ventas", "Editar datos", "Resetear contraseña", opcionEstado)

        AlertDialog.Builder(this)
            .setTitle("${usuario.nombre} (${usuario.rol})")
            .setItems(opciones) { _, which ->
                when (which) {
                    0 -> abrirVentasDeUsuario(usuario)
                    1 -> mostrarDialogEditarUsuario(usuario)
                    2 -> mostrarDialogResetearPassword(usuario)
                    3 -> {
                        if (usuario.esActivo) {
                            confirmarDesactivacion(usuario)
                        } else {
                            cambiarEstadoUsuario(usuario, "activar")
                        }
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun abrirVentasDeUsuario(usuario: Usuario) {
        val intent = Intent(this, VentasVendedorActivity::class.java).apply {
            putExtra("usuario_id", usuario.id)
            putExtra("usuario_nombre", usuario.nombre)
            putExtra("usuario_rol", usuario.rol)
            putExtra("usuario_activo", usuario.activo)
        }
        startActivity(intent)
    }

    private fun mostrarDialogCrearUsuario() {
        val dialogBinding = DialogUsuarioFormBinding.inflate(LayoutInflater.from(this))
        dialogBinding.tvDialogTitulo.text = "Nuevo Usuario"
        dialogBinding.tilPassword.visibility = View.VISIBLE

        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, rolesPermitidos)
        dialogBinding.spRol.adapter = spinnerAdapter
        dialogBinding.spRol.setSelection(0) // Vendedor por defecto

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setPositiveButton("Guardar", null) // Lo sobreescribimos para controlar la validación
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.setOnShowListener {
            val btnGuardar = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            btnGuardar.setOnClickListener {
                val nombre = dialogBinding.etNombre.text.toString().trim()
                val correo = dialogBinding.etCorreo.text.toString().trim()
                val password = dialogBinding.etPassword.text.toString()
                val rol = dialogBinding.spRol.selectedItem.toString()

                // Validaciones en la App
                var valido = true
                if (nombre.isEmpty()) {
                    dialogBinding.tilNombre.error = "Ingresa el nombre"
                    valido = false
                } else {
                    dialogBinding.tilNombre.error = null
                }

                if (correo.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
                    dialogBinding.tilCorreo.error = "Ingresa un correo válido"
                    valido = false
                } else {
                    dialogBinding.tilCorreo.error = null
                }

                if (password.length < 8) {
                    dialogBinding.tilPassword.error = "Mínimo 8 caracteres"
                    valido = false
                } else {
                    dialogBinding.tilPassword.error = null
                }

                if (!valido) return@setOnClickListener

                btnGuardar.isEnabled = false
                dialogBinding.tvDialogError.visibility = View.GONE

                lifecycleScope.launch {
                    try {
                        val req = UsuarioRequest(
                            nombre = nombre,
                            correo = correo,
                            password = password,
                            rol = rol
                        )
                        RetrofitClient.apiService.crearUsuario(req)
                        Toast.makeText(this@UsuariosActivity, "Usuario creado exitosamente", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        cargarUsuarios()
                    } catch (e: HttpException) {
                        btnGuardar.isEnabled = true
                        val errorMsg = obtenerMensajeError(e)
                        dialogBinding.tvDialogError.text = errorMsg
                        dialogBinding.tvDialogError.visibility = View.VISIBLE
                    } catch (e: Exception) {
                        btnGuardar.isEnabled = true
                        dialogBinding.tvDialogError.text = "Error de conexión: ${e.message}"
                        dialogBinding.tvDialogError.visibility = View.VISIBLE
                    }
                }
            }
        }

        dialog.show()
    }

    private fun mostrarDialogEditarUsuario(usuario: Usuario) {
        val dialogBinding = DialogUsuarioFormBinding.inflate(LayoutInflater.from(this))
        dialogBinding.tvDialogTitulo.text = "Editar Usuario"
        dialogBinding.etNombre.setText(usuario.nombre)
        dialogBinding.etCorreo.setText(usuario.correo)
        dialogBinding.tilPassword.visibility = View.GONE // La contraseña se gestiona con Resetear

        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, rolesPermitidos)
        dialogBinding.spRol.adapter = spinnerAdapter
        val posRol = rolesPermitidos.indexOfFirst { it.equals(usuario.rol, ignoreCase = true) }
        if (posRol >= 0) dialogBinding.spRol.setSelection(posRol)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setPositiveButton("Guardar", null)
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.setOnShowListener {
            val btnGuardar = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            btnGuardar.setOnClickListener {
                val nombre = dialogBinding.etNombre.text.toString().trim()
                val correo = dialogBinding.etCorreo.text.toString().trim()
                val rol = dialogBinding.spRol.selectedItem.toString()

                var valido = true
                if (nombre.isEmpty()) {
                    dialogBinding.tilNombre.error = "Ingresa el nombre"
                    valido = false
                } else {
                    dialogBinding.tilNombre.error = null
                }

                if (correo.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
                    dialogBinding.tilCorreo.error = "Ingresa un correo válido"
                    valido = false
                } else {
                    dialogBinding.tilCorreo.error = null
                }

                if (!valido) return@setOnClickListener

                btnGuardar.isEnabled = false
                dialogBinding.tvDialogError.visibility = View.GONE

                lifecycleScope.launch {
                    try {
                        val req = UsuarioRequest(
                            id = usuario.id,
                            nombre = nombre,
                            correo = correo,
                            rol = rol
                        )
                        RetrofitClient.apiService.actualizarUsuario(req)
                        Toast.makeText(this@UsuariosActivity, "Usuario actualizado exitosamente", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        cargarUsuarios()
                    } catch (e: HttpException) {
                        btnGuardar.isEnabled = true
                        val errorMsg = obtenerMensajeError(e)
                        dialogBinding.tvDialogError.text = errorMsg
                        dialogBinding.tvDialogError.visibility = View.VISIBLE
                    } catch (e: Exception) {
                        btnGuardar.isEnabled = true
                        dialogBinding.tvDialogError.text = "Error: ${e.message}"
                        dialogBinding.tvDialogError.visibility = View.VISIBLE
                    }
                }
            }
        }

        dialog.show()
    }

    private fun mostrarDialogResetearPassword(usuario: Usuario) {
        val dialogBinding = DialogResetPasswordBinding.inflate(LayoutInflater.from(this))
        dialogBinding.tvResetTitulo.text = "Resetear Contraseña"
        dialogBinding.tvResetDescripcion.text = "Asigna una nueva clave a ${usuario.nombre}. Su sesión se cerrará de inmediato."

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setPositiveButton("Resetear", null)
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.setOnShowListener {
            val btnReset = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            btnReset.setOnClickListener {
                val pass1 = dialogBinding.etNuevaPassword.text.toString()
                val pass2 = dialogBinding.etConfirmarPassword.text.toString()

                var valido = true
                if (pass1.length < 8) {
                    dialogBinding.tilNuevaPassword.error = "Mínimo 8 caracteres"
                    valido = false
                } else {
                    dialogBinding.tilNuevaPassword.error = null
                }

                if (pass1 != pass2) {
                    dialogBinding.tilConfirmarPassword.error = "Las contraseñas no coinciden"
                    valido = false
                } else {
                    dialogBinding.tilConfirmarPassword.error = null
                }

                if (!valido) return@setOnClickListener

                btnReset.isEnabled = false
                dialogBinding.tvResetError.visibility = View.GONE

                lifecycleScope.launch {
                    try {
                        val req = UsuarioRequest(
                            id = usuario.id,
                            accion = "resetear_password",
                            passwordNueva = pass1
                        )
                        RetrofitClient.apiService.actualizarUsuario(req)
                        Toast.makeText(this@UsuariosActivity, "Contraseña reseteada con éxito", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        cargarUsuarios()
                    } catch (e: HttpException) {
                        btnReset.isEnabled = true
                        val errorMsg = obtenerMensajeError(e)
                        dialogBinding.tvResetError.text = errorMsg
                        dialogBinding.tvResetError.visibility = View.VISIBLE
                    } catch (e: Exception) {
                        btnReset.isEnabled = true
                        dialogBinding.tvResetError.text = "Error: ${e.message}"
                        dialogBinding.tvResetError.visibility = View.VISIBLE
                    }
                }
            }
        }

        dialog.show()
    }

    private fun confirmarDesactivacion(usuario: Usuario) {
        AlertDialog.Builder(this)
            .setTitle("Confirmar desactivación")
            .setMessage("¿Estás seguro de desactivar a ${usuario.nombre}? Su sesión se cerrará inmediatamente y no podrá acceder al sistema hasta que sea reactivado.")
            .setPositiveButton("Desactivar") { _, _ ->
                cambiarEstadoUsuario(usuario, "desactivar")
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun cambiarEstadoUsuario(usuario: Usuario, accion: String) {
        mostrarCargando(true)

        lifecycleScope.launch {
            try {
                val req = UsuarioRequest(
                    id = usuario.id,
                    accion = accion
                )
                RetrofitClient.apiService.actualizarUsuario(req)
                val accionMsg = if (accion == "desactivar") "desactivado" else "activado"
                Toast.makeText(this@UsuariosActivity, "Usuario $accionMsg exitosamente", Toast.LENGTH_SHORT).show()
                cargarUsuarios()
            } catch (e: HttpException) {
                mostrarCargando(false)
                val errorMsg = obtenerMensajeError(e)
                AlertDialog.Builder(this@UsuariosActivity)
                    .setTitle("Acción no permitida")
                    .setMessage(errorMsg)
                    .setPositiveButton("Entendido", null)
                    .show()
            } catch (e: Exception) {
                mostrarCargando(false)
                Toast.makeText(this@UsuariosActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
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

    private fun mostrarCargando(cargando: Boolean) {
        binding.progressBar.visibility = if (cargando) View.VISIBLE else View.GONE
    }
}

