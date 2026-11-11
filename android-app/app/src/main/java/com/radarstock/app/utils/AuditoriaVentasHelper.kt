package com.radarstock.app.utils

import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.LifecycleCoroutineScope
import com.google.gson.Gson
import com.radarstock.app.models.RespuestaApi
import com.radarstock.app.network.RetrofitClient
import com.radarstock.app.views.LoginActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Currency
import java.util.Locale
import java.util.TimeZone

/**
 * Utilidades compartidas para las pantallas de auditoría de ventas por vendedor:
 * - Formateo de moneda CLP con separador de miles
 * - Formateo de fechas en hora de Chile (dd-MM-yyyy HH:mm)
 * - Manejo centralizado de 401 y errores HTTP
 * - Descarga y compartición de exportación CSV mediante FileProvider
 */
object AuditoriaVentasHelper {

    private val zonaChile: TimeZone = TimeZone.getTimeZone("America/Santiago")

    fun formatearMonedaClp(monto: Double): String {
        val formato = NumberFormat.getCurrencyInstance(Locale("es", "CL")).apply {
            maximumFractionDigits = 0
            currency = Currency.getInstance("CLP")
        }
        return formato.format(monto)
    }

    fun formatearFechaChile(fechaIso: String): String {
        return try {
            val entrada = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).apply {
                timeZone = zonaChile
            }
            val salida = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).apply {
                timeZone = zonaChile
            }
            val date = entrada.parse(fechaIso)
            if (date != null) salida.format(date) else fechaIso
        } catch (_: Exception) {
            fechaIso
        }
    }

    fun validarAccesoAdministrador(activity: AppCompatActivity): Boolean {
        val rol = SessionManager.obtenerRol() ?: ""
        if (!rol.equals("Administrador", ignoreCase = true)) {
            Toast.makeText(activity, "Acceso denegado: solo Administrador", Toast.LENGTH_SHORT).show()
            activity.finish()
            return false
        }
        return true
    }

    fun redirigirALoginPorSesionExpirada(activity: AppCompatActivity) {
        SessionManager.cerrarSesion()
        val intent = Intent(activity, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("SESION_EXPIRADA", true)
            putExtra("MENSAJE_EXPIRADA", "Tu sesión ha expirado. Inicia sesión nuevamente.")
        }
        activity.startActivity(intent)
        activity.finish()
    }

    fun extraerMensajeErrorHttp(e: HttpException): String {
        val raw = try {
            e.response()?.errorBody()?.string()
        } catch (_: Exception) {
            null
        }
        return extraerMensajeDeJson(raw, e.code())
    }

    fun extraerMensajeDeJson(rawJson: String?, httpCode: Int): String {
        if (!rawJson.isNullOrBlank()) {
            try {
                val parsed = Gson().fromJson(rawJson, RespuestaApi::class.java)
                if (!parsed?.mensaje.isNullOrBlank()) {
                    return parsed.mensaje!!
                }
            } catch (_: Exception) {
                // Ignorar si no es JSON válido
            }
        }
        return when (httpCode) {
            401 -> "Tu sesión ha expirado."
            403 -> "No tienes permisos para acceder a esta función."
            404 -> "Vendedor no encontrado."
            else -> "Error del servidor (HTTP $httpCode)"
        }
    }

    fun descargarYCompartirCsv(
        activity: AppCompatActivity,
        scope: LifecycleCoroutineScope,
        usuarioId: Int?,
        rango: String?,
        desde: String?,
        hasta: String?,
        onEstadoCarga: (Boolean) -> Unit
    ) {
        onEstadoCarga(true)
        scope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.apiService.exportarVentasVendedorCsv(
                        usuarioId = usuarioId,
                        rango = rango,
                        desde = desde,
                        hasta = hasta
                    )
                }

                if (response.code() == 401) {
                    onEstadoCarga(false)
                    redirigirALoginPorSesionExpirada(activity)
                    return@launch
                }

                if (!response.isSuccessful) {
                    val errorJson = withContext(Dispatchers.IO) {
                        response.errorBody()?.string()
                    }
                    onEstadoCarga(false)
                    Toast.makeText(
                        activity,
                        extraerMensajeDeJson(errorJson, response.code()),
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }

                val body = response.body()
                if (body == null) {
                    onEstadoCarga(false)
                    Toast.makeText(activity, "El archivo CSV recibido está vacío.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Determinar nombre del archivo desde Content-Disposition o generar uno por defecto
                val disposition = response.headers()["Content-Disposition"]
                val nombreArchivo = Regex("filename=\"?([^\";]+)\"?")
                    .find(disposition ?: "")
                    ?.groupValues
                    ?.getOrNull(1)
                    ?: "ventas_vendedor_export.csv"

                val archivo = withContext(Dispatchers.IO) {
                    val carpeta = File(activity.cacheDir, "exports").apply { mkdirs() }
                    val destino = File(carpeta, nombreArchivo)
                    body.byteStream().use { input ->
                        destino.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    destino
                }

                onEstadoCarga(false)

                val uri = FileProvider.getUriForFile(
                    activity,
                    "${activity.packageName}.fileprovider",
                    archivo
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Reporte de Ventas - Radar Stock")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                activity.startActivity(
                    Intent.createChooser(shareIntent, "Exportar o compartir CSV")
                )
            } catch (e: IOException) {
                onEstadoCarga(false)
                Toast.makeText(
                    activity,
                    "Error de red al descargar el CSV. Verifica tu conexión.",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                onEstadoCarga(false)
                Toast.makeText(
                    activity,
                    "No se pudo exportar el archivo: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}

