package br.com.rmfacilities.funcionarioapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MensagemAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val itens = mutableListOf<MensagemItem>()

    companion object {
        private const val TIPO_RH = 0
        private const val TIPO_FUNC = 1
    }

    fun replaceAll(novas: List<MensagemItem>) {
        itens.clear()
        itens.addAll(novas)
        notifyDataSetChanged()
    }

    fun addMensagem(m: MensagemItem) {
        itens.add(m)
        notifyItemInserted(itens.size - 1)
    }

    override fun getItemViewType(position: Int) =
        if (itens[position].de_rh) TIPO_RH else TIPO_FUNC

    override fun getItemCount() = itens.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TIPO_RH) {
            val v = inflater.inflate(R.layout.item_msg_rh, parent, false)
            RhViewHolder(v)
        } else {
            val v = inflater.inflate(R.layout.item_msg_func, parent, false)
            FuncViewHolder(v)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = itens[position]
        if (holder is RhViewHolder) {
            holder.tvConteudo.text = item.conteudo
            holder.tvHora.text = item.enviado_fmt ?: ""
            holder.tvRemetente.text = item.enviado_por ?: "RH"
        } else if (holder is FuncViewHolder) {
            holder.tvConteudo.text = item.conteudo
            holder.tvHora.text = item.enviado_fmt ?: ""
        }
    }

    inner class RhViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val tvRemetente: TextView = v.findViewById(R.id.tvRemetente)
        val tvConteudo: TextView = v.findViewById(R.id.tvConteudo)
        val tvHora: TextView = v.findViewById(R.id.tvHora)
    }

    inner class FuncViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val tvConteudo: TextView = v.findViewById(R.id.tvConteudo)
        val tvHora: TextView = v.findViewById(R.id.tvHora)
    }
}
