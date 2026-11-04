package com.radarstock.app.views

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.radarstock.app.databinding.ActivityCrearProductoPlaceholderBinding

/**
 * Pantalla provisional para creación de producto (abierta desde el botón flotante «+»).
 */
class CrearProductoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCrearProductoPlaceholderBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCrearProductoPlaceholderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        binding.btnVolver.setOnClickListener {
            finish()
        }
    }
}

