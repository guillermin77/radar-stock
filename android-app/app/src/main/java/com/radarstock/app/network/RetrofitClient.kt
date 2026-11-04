package com.radarstock.app.network

import android.content.Context
import com.radarstock.app.utils.Constants
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Cliente singleton para instanciar Retrofit con OkHttp, Gson y AuthInterceptor
 */
object RetrofitClient {

    private var appContext: Context? = null
    private var okHttpClient: OkHttpClient? = null
    private var retrofitInstance: Retrofit? = null

    fun init(context: Context) {
        if (appContext == null) {
            val appCtx = context.applicationContext
            appContext = appCtx

            val client = OkHttpClient.Builder()
                .addInterceptor(AuthInterceptor(appCtx))
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            okHttpClient = client

            retrofitInstance = Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
    }

    val apiService: ApiService
        get() {
            val instance = retrofitInstance
                ?: throw IllegalStateException("RetrofitClient no inicializado. Llama a RetrofitClient.init(context).")
            return instance.create(ApiService::class.java)
        }
}
