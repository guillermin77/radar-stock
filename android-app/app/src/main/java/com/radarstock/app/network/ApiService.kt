package com.radarstock.app.network

import com.radarstock.app.models.LoginRequest
import com.radarstock.app.models.LoginResponse
import com.radarstock.app.models.Producto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Definición de endpoints de la API REST mediante Retrofit
 */
interface ApiService {

    /**
     * Inicio de sesión con correo y contraseña.
     * Retorna token y datos del usuario.
     */
    @POST("api/login.php")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    /**
     * Obtiene el listado completo de productos del almacén.
     * Requiere encabezado Authorization con Bearer token.
     */
    @GET("api/productos.php")
    suspend fun getProductos(): List<Producto>
}
