package br.com.rmfacilities.funcionarioapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AvisoAdapter(
    private val onLido: (ComunicadoItem) -> Unit
) : RecyclerView.Adapter<AvisoAdapter.VH>() {

    private val items = mutableListOf<ComunicadoItem>()

    fun replaceAll(novaLista: List<ComunicadoItem>) {
        items.clear()
        items.addAll(novaLista)
        notifyDataSetChanged()
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitulo: TextView = view.findViewById(R.id.tvTitulo)
        val tvConteudo: TextView = view.findViewById(R.id.tvConteudo)
        val tvData: TextView = view.findViewById(R.id.tvData)
        val tvNovo: TextView = view.findViewById(R.id.tvNovo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_aviso, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tvTitulo.text = item.titulo
        holder.tvConteudo.text = item.conteudo
        holder.tvData.text = item.criado_fmt ?: ""
        holder.tvNovo.visibility = if (!item.lido) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener {
            if (!item.lido) {
                // Atualiza localmente e notifica para marcar no servidor
                items[position] = item.copy(lido = true)
                notifyItemChanged(position)
                onLido(item)
            }
        }
    }

    override fun getItemCount() = items.size
}
