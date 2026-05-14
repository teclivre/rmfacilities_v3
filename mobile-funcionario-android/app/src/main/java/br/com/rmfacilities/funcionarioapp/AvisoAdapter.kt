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
    private val expandedPositions = mutableSetOf<Int>()

    fun replaceAll(novaLista: List<ComunicadoItem>) {
        items.clear()
        items.addAll(novaLista)
        expandedPositions.clear()
        notifyDataSetChanged()
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitulo: TextView = view.findViewById(R.id.tvTitulo)
        val tvConteudo: TextView = view.findViewById(R.id.tvConteudo)
        val tvData: TextView = view.findViewById(R.id.tvData)
        val tvNovo: TextView = view.findViewById(R.id.tvNovo)
        val tvVerMais: TextView = view.findViewById(R.id.tvVerMais)
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

        val expanded = expandedPositions.contains(position)
        val needsExpansion = item.conteudo.length > 120 || item.conteudo.contains('\n')

        if (needsExpansion) {
            holder.tvVerMais.visibility = View.VISIBLE
            if (expanded) {
                holder.tvConteudo.maxLines = Int.MAX_VALUE
                holder.tvConteudo.ellipsize = null
                holder.tvVerMais.text = "Ver menos ↑"
            } else {
                holder.tvConteudo.maxLines = 3
                holder.tvConteudo.ellipsize = android.text.TextUtils.TruncateAt.END
                holder.tvVerMais.text = "Ver mais ↓"
            }
            holder.tvVerMais.setOnClickListener {
                if (expandedPositions.contains(position)) {
                    expandedPositions.remove(position)
                } else {
                    expandedPositions.add(position)
                }
                notifyItemChanged(position)
            }
        } else {
            holder.tvConteudo.maxLines = Int.MAX_VALUE
            holder.tvConteudo.ellipsize = null
            holder.tvVerMais.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            if (!item.lido) {
                items[position] = item.copy(lido = true)
                notifyItemChanged(position)
                onLido(item)
            }
        }
    }

    override fun getItemCount() = items.size
}
