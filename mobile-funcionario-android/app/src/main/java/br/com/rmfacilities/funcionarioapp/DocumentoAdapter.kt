package br.com.rmfacilities.funcionarioapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

class DocumentoAdapter(
    private val itens: MutableList<DocumentoItem>,
    private val onBaixar: (DocumentoItem) -> Unit
) : RecyclerView.Adapter<DocumentoAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvNome: TextView = v.findViewById(R.id.tvNomeArquivo)
        val tvInfo: TextView = v.findViewById(R.id.tvInfo)
        val btnBaixar: MaterialButton = v.findViewById(R.id.btnBaixar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_documento, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = itens.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = itens[position]
        holder.tvNome.text = item.nome_arquivo ?: "Documento"
        holder.tvInfo.text = listOf(item.categoria_label, item.competencia, item.criado_fmt)
            .filter { !it.isNullOrBlank() }
            .joinToString(" • ")
        holder.btnBaixar.setOnClickListener { onBaixar(item) }
    }

    fun replaceAll(newItems: List<DocumentoItem>) {
        itens.clear()
        itens.addAll(newItems)
        notifyDataSetChanged()
    }
}
