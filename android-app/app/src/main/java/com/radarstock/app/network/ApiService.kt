package com.radarstock.app.network

import com.radarstock.app.models.Producto
import retrofit2.http.GET

/**
 * Definición de endpoints de la API REST mediante Retrofit
 */
interface ApiService {

    /**
     * Obtiene el listado completo de productos del almacén
     * URL resultante: http://10.0.2.2/backend-api/api/productos.php
     */
    @GET("api/productos.php")
    suspend fun getProductos(): List<Producto>
}

