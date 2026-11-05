package com.radarstock.app.network

import com.radarstock.app.models.Alerta
import com.radarstock.app.models.LoginRequest
import com.radarstock.app.models.LoginResponse
import com.radarstock.app.models.Producto
import com.radarstock.app.models.ProductoRequest
import com.radarstock.app.models.RespuestaApi
import com.radarstock.app.models.ResumenAlertasResponse
import com.radarstock.app.models.ResumenResponse
import com.radarstock.app.models.StockRequest
import com.radarstock.app.models.StockResponse
import com.radarstock.app.models.VentaRequest
import com.radarstock.app.models.VentaResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query

/**
 * Definición de endpoints de la API REST mediante Retrofit
 */
interface ApiService {

    /**
     * Inicio de sesión con correo y contraseña.
     */
    @POST("api/login.php")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    /**
     * Obtiene el listado de productos del almacén con filtros, orden y paginación.
     */
    @GET("api/productos.php")
    suspend fun getProductos(
        @Query("buscar") buscar: String? = null,
        @Query("categoria") categoria: String? = null,
        @Query("orden") orden: String? = null,
        @Query("pagina") pagina: Int = 1,
        @Query("limite") limite: Int = 50
    ): List<Producto>

    /**
     * Obtiene un producto por su identificador.
     */
    @GET("api/productos.php")
    suspend fun getProductoPorId(@Query("id") id: Int): Producto

    /**
     * Crea un nuevo producto (Solo Administrador).
     */
    @POST("api/productos.php")
    suspend fun crearProducto(@Body request: ProductoRequest): Producto

    /**
     * Modifica un producto existente (Solo Administrador).
     */
    @PUT("api/productos.php")
    suspend fun editarProducto(
        @Query("id") id: Int,
        @Body request: ProductoRequest
    ): Producto

    /**
     * Elimina un producto si no tiene ventas registradas (Solo Administrador).
     */
    @DELETE("api/productos.php")
    suspend fun eliminarProducto(@Query("id") id: Int): RespuestaApi

    /**
     * Suma stock atómicamente a un producto (Solo Administrador).
     */
    @POST("api/stock.php")
    suspend fun agregarStock(@Body request: StockRequest): StockResponse

    /**
     * Obtiene el listado de categorías disponibles (base + base de datos).
     */
    @GET("api/categorias.php")
    suspend fun getCategorias(): List<String>

    /**
     * Registra una venta atómica con control de stock e idempotencia.
     */
    @POST("api/ventas.php")
    suspend fun registrarVenta(@Body request: VentaRequest): VentaResponse

    /**
     * Obtiene el resumen financiero y métricas de ventas (Solo Administrador).
     */
    @GET("api/resumen.php")
    suspend fun getResumen(
        @Query("periodo") periodo: String? = null,
        @Query("desde") desde: String? = null,
        @Query("hasta") hasta: String? = null
    ): ResumenResponse

    /**
     * Obtiene el listado de alertas de stock y vencimiento con filtros y paginación.
     */
    @GET("api/alertas.php")
    suspend fun getAlertas(
        @Query("tipo") tipo: String? = null,
        @Query("pagina") pagina: Int = 1,
        @Query("limite") limite: Int = 50
    ): List<Alerta>

    /**
     * Obtiene el resumen de conteos de alertas {agotados, criticos, bajos, por_vencer, vencidos, total}.
     */
    @GET("api/alertas.php?resumen=1")
    suspend fun getResumenAlertas(): ResumenAlertasResponse
}
