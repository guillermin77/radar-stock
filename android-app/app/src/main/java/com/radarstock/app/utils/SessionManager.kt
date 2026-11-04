package com.radarstock.app.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Gestor de sesión basado en SharedPreferences.
 * Almacena de forma persistente el token de autorización, el nombre del usuario y su rol.
 */
object SessionManager {

    private const val PREFS_NAME = "radar_stock_prefs"
    private const val KEY_TOKEN = "auth_token"
    private const val KEY_NOMBRE = "usuario_nombre"
    private const val KEY_ROL = "usuario_rol"

    private var preferences: SharedPreferences? = null

    /**
     * Inicializa el gestor con el contexto de la aplicación.
     */
    fun init(context: Context) {
        if (preferences == null) {
            preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    private fun getPrefs(): SharedPreferences {
        return preferences ?: throw IllegalStateException("SessionManager no inicializado. Llama a SessionManager.init(context).")
    }

    /**
     * Guarda los datos de autenticación obtenidos en el login.
     */
    fun guardarSesion(token: String, nombre: String, rol: String) {
        getPrefs().edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_NOMBRE, nombre)
            .putString(KEY_ROL, rol)
            .apply()
    }

    /**
     * Obtiene el token Bearer activo, o null si no hay sesión.
     */
    fun obtenerToken(): String? {
        return getPrefs().getString(KEY_TOKEN, null)
    }

    /**
     * Obtiene el nombre del usuario logueado.
     */
    fun obtenerNombre(): String? {
        return getPrefs().getString(KEY_NOMBRE, null)
    }

    /**
     * Obtiene el rol del usuario ('Administrador', 'Vendedor', etc.).
     */
    fun obtenerRol(): String? {
        return getPrefs().getString(KEY_ROL, null)
    }

    /**
     * Indica si existe una sesión válida guardada.
     */
    fun haySesion(): Boolean {
        val token = obtenerToken()
        return !token.isNullOrBlank()
    }

    /**
     * Borra todos los datos de la sesión activa (cierre de sesión).
     */
    fun cerrarSesion() {
        getPrefs().edit().clear().apply()
    }
}

