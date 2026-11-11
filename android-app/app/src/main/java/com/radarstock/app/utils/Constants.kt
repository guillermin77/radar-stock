package com.radarstock.app.utils

import com.radarstock.app.BuildConfig

/**
 * Constantes globales de la aplicación.
 * La dirección de la API depende del tipo de compilación:
 * debug -> XAMPP local (10.0.2.2), release -> Hostinger (HTTPS).
 */
object Constants {
    val BASE_URL: String = BuildConfig.BASE_URL
}