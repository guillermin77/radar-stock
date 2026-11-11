package com.radarstock.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.radarstock.app.network.RetrofitClient
import com.radarstock.app.utils.SessionManager

/**
 * Clase de aplicación global para inicializar singletons y configuración al arrancar.
 */
class RadarStockApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Forzar tema claro en toda la aplicación (sin modo oscuro automático)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        SessionManager.init(this)
        RetrofitClient.init(this)
    }
}
