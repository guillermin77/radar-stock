package com.radarstock.app.network

import com.radarstock.app.models.Alerta
import com.radarstock.app.models.CambiarPasswordRequest
import com.radarstock.app.models.ConteoItemRequest
import com.radarstock.app.models.ConteoResponse
import com.radarstock.app.models.LoginRequest
import com.radarstock.app.models.LoginResponse
import com.radarstock.app.models.Producto
import com.radarstock.app.models.ProductoRequest
import com.radarstock.app.models.RespuestaApi
import com.radarstock.app.models.ResumenAlertasResponse
import com.radarstock.app.models.ResumenResponse
import com.radarstock.app.models.StockIngresoRequest
import com.radarstock.app.models.StockIngresoResponse
import com.radarstock.app.models.StockRequest
import com.radarstock.app.models.StockResponse
import com.radarstock.app.models.Usuario
import com.radarstock.app.models.UsuarioRequest
import com.radarstock.app.models.CompararVendedoresResponse
import com.radarstock.app.models.VentaRequest
import com.radarstock.app.models.VentaResponse
import com.radarstock.app.models.VentasPorVendedorResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query
import retrofit2.http.Streaming

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
     * Busca un producto por su código de barras exacto (?codigo=XXXX).
     */
    @GET("api/productos.php")
    suspend fun getProductoPorCodigo(@Query("codigo") codigo: String): Producto

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
     * Registra un ingreso de stock atómico con auditoría en movimientos_stock (Solo Administrador).
     */
    @POST("api/stock_ingreso.php")
    suspend fun stockIngreso(@Body request: StockIngresoRequest): StockIngresoResponse

    /**
     * Guarda el conteo físico de inventario de una lista de productos en una sola transacción atómica (Solo Administrador).
     */
    @POST("api/inventario_conteo.php")
    suspend fun guardarConteo(@Body items: List<ConteoItemRequest>): ConteoResponse

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

    /**
     * Obtiene el listado de usuarios (Solo Administrador).
     */
    @GET("api/usuarios.php")
    suspend fun getUsuarios(): List<Usuario>

    /**
     * Crea un nuevo usuario en el sistema (Solo Administrador).
     */
    @POST("api/usuarios.php")
    suspend fun crearUsuario(@Body request: UsuarioRequest): RespuestaApi

    /**
     * Modifica, activa/desactiva o resetea contraseña de un usuario (Solo Administrador).
     */
    @PUT("api/usuarios.php")
    suspend fun actualizarUsuario(@Body request: UsuarioRequest): RespuestaApi

    /**
     * Cambia la contraseña del usuario actualmente autenticado (Cualquier rol).
     */
    @POST("api/cambiar_password.php")
    suspend fun cambiarPassword(@Body request: CambiarPasswordRequest): RespuestaApi

    /**
     * Registra el token FCM del dispositivo en el servidor para notificaciones push remotas.
     */
    @POST("api/fcm_token.php")
    suspend fun registrarFcmToken(@Body request: com.radarstock.app.models.FcmTokenRequest): RespuestaApi

    /**
     * Consulta el historial paginado de movimientos de stock (Solo Administrador).
     */
    @GET("api/movimientos.php")
    suspend fun getMovimientos(
        @Query("producto_id") productoId: Int? = null,
        @Query("q") q: String? = null,
        @Query("tipo") tipo: String? = null,
        @Query("desde") desde: String? = null,
        @Query("hasta") hasta: String? = null,
        @Query("pagina") pagina: Int = 1,
        @Query("por_pagina") porPagina: Int = 30
    ): com.radarstock.app.models.MovimientosResponse

    /**
     * Consulta las ventas detalladas de un vendedor específico (Solo Administrador).
     */
    @GET("api/ventas_vendedor.php")
    suspend fun getVentasPorVendedor(
        @Query("usuario_id") usuarioId: Int,
        @Query("rango") rango: String? = null,
        @Query("desde") desde: String? = null,
        @Query("hasta") hasta: String? = null,
        @Query("pagina") pagina: Int = 1,
        @Query("por_pagina") porPagina: Int = 30
    ): VentasPorVendedorResponse

    /**
     * Consulta el comparativo de ventas de todos los vendedores en un período (Solo Administrador).
     */
    @GET("api/ventas_vendedor.php")
    suspend fun getCompararVendedores(
        @Query("rango") rango: String? = null,
        @Query("desde") desde: String? = null,
        @Query("hasta") hasta: String? = null
    ): CompararVendedoresResponse

    /**
     * Descarga la exportación CSV de ventas por vendedor (Solo Administrador).
     */
    @Streaming
    @GET("api/ventas_vendedor_export.php")
    suspend fun exportarVentasVendedorCsv(
        @Query("usuario_id") usuarioId: Int? = null,
        @Query("rango") rango: String? = null,
        @Query("desde") desde: String? = null,
        @Query("hasta") hasta: String? = null
    ): Response<ResponseBody>
}
