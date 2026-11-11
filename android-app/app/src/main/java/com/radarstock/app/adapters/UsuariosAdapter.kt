package com.radarstock.app.adapters

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.radarstock.app.databinding.ItemUsuarioBinding
import com.radarstock.app.models.Usuario

/**
 * Adaptador para listar los usuarios en el RecyclerView.
 * Muestra nombre, correo, rol y estado (Activo en verde, Inactivo en rojo).
 */
class UsuariosAdapter(
    private val onUsuarioClick: (Usuario) -> Unit
) : RecyclerView.Adapter<UsuariosAdapter.UsuarioViewHolder>() {

    private val usuarios = mutableListOf<Usuario>()

    fun setUsuarios(nuevosUsuarios: List<Usuario>) {
        usuarios.clear()
        usuarios.addAll(nuevosUsuarios)
        notifyDataSetChanged()
    }

    inner class UsuarioViewHolder(private val binding: ItemUsuarioBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(usuario: Usuario) {
            binding.tvNombre.text = usuario.nombre
            binding.tvCorreo.text = usuario.correo

            // Badge de Rol
            binding.tvRol.text = usuario.rol
            val fondoRol = GradientDrawable().apply {
                cornerRadius = 12f
                if (usuario.esAdmin) {
                    setColor(Color.parseColor("#EFF6FF")) // Azul claro
                } else {
                    setColor(Color.parseColor("#F1F5F9")) // Gris pizarra
                }
            }
            binding.tvRol.background = fondoRol
            binding.tvRol.setTextColor(
                if (usuario.esAdmin) Color.parseColor("#1E40AF") else Color.parseColor("#475569")
            )

            // Badge de Estado: Activo (Verde) / Inactivo (Rojo)
            val fondoEstado = GradientDrawable().apply {
                cornerRadius = 16f
                if (usuario.esActivo) {
                    setColor(Color.parseColor("#DCFCE7")) // Verde suave
                } else {
                    setColor(Color.parseColor("#FEE2E2")) // Rojo suave
                }
            }
            binding.tvEstado.background = fondoEstado
            if (usuario.esActivo) {
                binding.tvEstado.text = "Activo"
                binding.tvEstado.setTextColor(Color.parseColor("#166534"))
            } else {
                binding.tvEstado.text = "Inactivo"
                binding.tvEstado.setTextColor(Color.parseColor("#991B1B"))
            }

            // Click listener
            binding.root.setOnClickListener {
                onUsuarioClick(usuario)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UsuarioViewHolder {
        val binding = ItemUsuarioBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return UsuarioViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UsuarioViewHolder, position: Int) {
        holder.bind(usuarios[position])
    }

    override fun getItemCount(): Int = usuarios.size
}

