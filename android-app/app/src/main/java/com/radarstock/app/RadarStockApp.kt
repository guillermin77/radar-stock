package com.radarstock.app

import android.app.Application
import com.radarstock.app.network.RetrofitClient
import com.radarstock.app.utils.SessionManager

/**
 * Clase de aplicación global para inicializar singletons y configuración al arrancar.
 */
class RadarStockApp : Application() {

    override fun onCreate() {
        super.onCreate()
        SessionManager.init(this)
        RetrofitClient.init(this)
    }
}

