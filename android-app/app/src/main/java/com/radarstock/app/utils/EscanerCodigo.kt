package com.radarstock.app.utils

import android.content.Context
import android.widget.Toast
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Utilidad para el escaneo de códigos de barras mediante Google Code Scanner (Play Services).
 * Formatos configurados: EAN-13, EAN-8, UPC-A, UPC-E y Code 128.
 * No requiere permisos manuales de cámara en la aplicación ya que es gestionado por Google Play Services.
 */
object EscanerCodigo {

    // Códigos de estado específicos de GmsBarcodeScanning
    private const val STATUS_CODE_SCANNER_CANCELLED = 1
    private const val STATUS_CODE_SCANNER_UNAVAILABLE = 4

    /**
     * Inicia el escáner de Google Play Services y devuelve el texto del código detectado en el callback.
     *
     * @param context Contexto de la actividad que invoca el escáner.
     * @param onCodigoEscaneado Callback invocado con el texto del código de barras.
     * @param onCancelado Callback opcional invocado si el usuario cierra el escáner sin escanear.
     * @param onError Callback opcional con el mensaje de error en español.
     */
    fun escanear(
        context: Context,
        onCodigoEscaneado: (String) -> Unit,
        onCancelado: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_CODE_128
            )
            .enableAutoZoom()
            .build()

        val scanner = GmsBarcodeScanning.getClient(context, options)

        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val codigo = barcode.rawValue?.trim()
                if (!codigo.isNullOrEmpty()) {
                    onCodigoEscaneado(codigo)
                } else {
                    val msg = "No se pudo interpretar el código de barras escaneado."
                    if (onError != null) onError(msg) else Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
            .addOnCanceledListener {
                onCancelado?.invoke()
            }
            .addOnFailureListener { e ->
                val mensajeError = when {
                    e is ApiException && (e.statusCode == STATUS_CODE_SCANNER_UNAVAILABLE || e.statusCode == CommonStatusCodes.API_NOT_CONNECTED) -> {
                        "Google Play Services no está disponible en este dispositivo o el módulo de escaneo aún se está descargando."
                    }
                    e is ApiException && (e.statusCode == STATUS_CODE_SCANNER_CANCELLED || e.statusCode == CommonStatusCodes.CANCELED) -> {
                        "Escaneo cancelado por el usuario."
                    }
                    e is ApiException && e.statusCode == CommonStatusCodes.NETWORK_ERROR -> {
                        "Error de conexión al inicializar el escáner de Google Play Services."
                    }
                    else -> {
                        "Error al escanear código de barras: ${e.localizedMessage ?: "Fallo desconocido"}"
                    }
                }

                if (onError != null) {
                    onError.invoke(mensajeError)
                } else {
                    Toast.makeText(context, mensajeError, Toast.LENGTH_LONG).show()
                }
            }
    }
}
