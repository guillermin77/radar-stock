package com.radarstock.app.network

import android.content.Context
import android.content.Intent
import com.radarstock.app.utils.SessionManager
import com.radarstock.app.views.LoginActivity
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Interceptor de OkHttp que:
 * 1. Agrega «Authorization: Bearer <token>» a todas las peticiones si existe token.
 * 2. Si la API responde 401 en rutas protegidas (no en login), borra la sesión y redirige a LoginActivity.
 */
class AuthInterceptor(private val context: Context) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val token = SessionManager.obtenerToken()

        val requestBuilder = originalRequest.newBuilder()
        if (!token.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        val response = chain.proceed(requestBuilder.build())

        // Si responde 401 en un endpoint protegido (distinto de login.php)
        val esRutaLogin = originalRequest.url().encodedPath().contains("login.php")
        if (response.code() == 401 && !esRutaLogin) {
            SessionManager.cerrarSesion()
            val intent = Intent(context, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            context.startActivity(intent)
        }

        return response
    }
}

