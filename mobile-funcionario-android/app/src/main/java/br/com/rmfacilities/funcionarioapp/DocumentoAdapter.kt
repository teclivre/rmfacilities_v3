package br.com.rmfacilities.funcionarioapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

private sealed class DocListItem {
    data class Header(val year: String) : DocListItem()
    data class Doc(val item: DocumentoItem) : DocListItem()
}

class DocumentoAdapter(
    private val onBaixar: (DocumentoItem) -> Unit,
    private val onAssinar: (DocumentoItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }

    private val listItems = mutableListOf<DocListItem>()

    class HeaderVH(v: View) : RecyclerView.ViewHolder(v) {
        val tvYear: TextView = v.findViewById(R.id.tvYearHeader)
    }

    class ItemVH(v: View) : RecyclerView.ViewHolder(v) {
        val tvNome: TextView = v.findViewById(R.id.tvNomeArquivo)
        val tvInfo: TextView = v.findViewById(R.id.tvInfo)
        val tvAssStatus: TextView = v.findViewById(R.id.tvAssStatus)
        val btnBaixar: MaterialButton = v.findViewById(R.id.btnBaixar)
        val btnAssinar: MaterialButton = v.findViewById(R.id.btnAssinar)
    }

    override fun getItemViewType(position: Int): Int =
        if (listItems[position] is DocListItem.Header) TYPE_HEADER else TYPE_ITEM

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderVH(inflater.inflate(R.layout.item_doc_year_header, parent, false))
        } else {
            ItemVH(inflater.inflate(R.layout.item_documento, parent, false))
        }
    }

    override fun getItemCount(): Int = listItems.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val entry = listItems[position]) {
            is DocListItem.Header -> (holder as HeaderVH).tvYear.text = entry.year
            is DocListItem.Doc -> {
                val vh = holder as ItemVH
                val item = entry.item
                vh.tvNome.text = item.nome_arquivo ?: "Documento"
                vh.tvInfo.text = listOf(item.categoria_label, item.competencia, item.criado_fmt)
                    .filter { !it.isNullOrBlank() }
                    .joinToString(" • ")

                val assinado = (item.ass_status ?: "").equals("concluida", ignoreCase = true)
                vh.tvAssStatus.text = if (assinado) {
                    val quando = item.ass_em_fmt?.takeIf { it.isNotBlank() }
                    if (quando != null) "Assinado em $quando" else "Documento assinado"
                } else {
                    "Assinatura pendente"
                }

                vh.btnBaixar.setOnClickListener { onBaixar(item) }
                vh.btnAssinar.isEnabled = !assinado && item.can_assinar
                vh.btnAssinar.text = if (assinado) "Assinado" else "Assinar"
                vh.btnAssinar.setOnClickListener {
                    if (!assinado && item.can_assinar) onAssinar(item)
                }
            }
        }
    }

    fun replaceAll(newItems: List<DocumentoItem>) {
        listItems.clear()
        val grouped = newItems.groupBy { extractYear(it) }
        val sortedYears = grouped.keys.sortedDescending()
        for (year in sortedYears) {
            listItems.add(DocListItem.Header(year))
            grouped[year]?.forEach { listItems.add(DocListItem.Doc(it)) }
        }
        notifyDataSetChanged()
    }

    private fun extractYear(item: DocumentoItem): String {
        val ano = item.ano?.trim()
        if (!ano.isNullOrBlank()) return ano

        val comp = item.competencia?.trim()
        if (!comp.isNullOrBlank() && comp.length >= 4) return comp.substring(0, 4)

        val criado = item.criado_fmt?.trim()
        if (!criado.isNullOrBlank()) {
            val parts = criado.split("/")
            if (parts.size == 3 && parts[2].length == 4) return parts[2]
        }
        return "Outros"
    }
}
