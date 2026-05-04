package br.com.rmfacilities.funcionarioapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class HistoricoAssinaturasAdapter : RecyclerView.Adapter<HistoricoAssinaturasAdapter.VH>() {

    private val itens = mutableListOf<AssinaturaHistoricoItem>()

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvNome: TextView = v.findViewById(R.id.tvHistNome)
        val tvCategoria: TextView = v.findViewById(R.id.tvHistCategoria)
        val tvData: TextView = v.findViewById(R.id.tvHistData)
        val tvIp: TextView = v.findViewById(R.id.tvHistIp)
        val tvCodigo: TextView = v.findViewById(R.id.tvHistCodigo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_historico_assinatura, parent, false)
        return VH(v)
    }

    override fun getItemCount() = itens.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = itens[position]
        holder.tvNome.text = item.nome_arquivo ?: "Documento"
        holder.tvCategoria.text = listOf(item.categoria_label, item.competencia)
            .filter { !it.isNullOrBlank() }.joinToString(" • ")
        holder.tvData.text = "Assinado em: ${item.ass_em_fmt ?: "–"}"
        holder.tvIp.text = "IP: ${item.ass_ip_mask ?: "–"}"
        val codigo = item.ass_codigo?.takeIf { it.isNotBlank() }
        holder.tvCodigo.text = if (codigo != null) "Código: $codigo" else ""
        holder.tvCodigo.visibility = if (codigo != null) View.VISIBLE else View.GONE
    }

    fun replaceAll(newItens: List<AssinaturaHistoricoItem>) {
        itens.clear()
        itens.addAll(newItens)
        notifyDataSetChanged()
    }
}
