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

                val statusNorm = (item.ass_status ?: "").lowercase().trim()
                val pendente = statusNorm == "pendente"
                val assinado = statusNorm == "concluida"

                // Status text: só mostrar quando relevante
                when {
                    assinado -> {
                        vh.tvAssStatus.visibility = View.VISIBLE
                        val quando = item.ass_em_fmt?.takeIf { it.isNotBlank() }
                        vh.tvAssStatus.text = if (quando != null) "Assinado em $quando" else "Documento assinado"
                    }
                    pendente -> {
                        vh.tvAssStatus.visibility = View.VISIBLE
                        // Show deadline badge if available
                        val prazoBadge = if (!item.ass_prazo_em.isNullOrBlank()) {
                            try {
                                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
                                sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                val prazoDate = sdf.parse(item.ass_prazo_em.substring(0, 19))
                                val diffMs = (prazoDate?.time ?: 0L) - System.currentTimeMillis()
                                val dias = (diffMs / (1000 * 60 * 60 * 24)).toInt()
                                when {
                                    dias < 0 -> " • 🔴 VENCIDO"
                                    dias == 0 -> " • ⏰ Vence hoje"
                                    else -> " • ⏰ Expira em $dias dia${if (dias > 1) "s" else ""}"
                                }
                            } catch (_: Exception) { "" }
                        } else ""
                        vh.tvAssStatus.text = "Pendente de assinatura$prazoBadge"
                    }
                    else -> vh.tvAssStatus.visibility = View.GONE
                }

                // Botão assinar: só para documentos pendentes
                vh.btnAssinar.visibility = if (pendente) View.VISIBLE else View.GONE
                vh.btnBaixar.setOnClickListener { onBaixar(item) }
                vh.btnAssinar.setOnClickListener { if (pendente) onAssinar(item) }
            }
        }
    }

    fun replaceAll(pendentes: List<DocumentoItem>, docs: List<DocumentoItem>) {
        listItems.clear()
        // Seção de pendentes de assinatura
        if (pendentes.isNotEmpty()) {
            listItems.add(DocListItem.Header("Pendentes de assinatura"))
            pendentes.forEach { listItems.add(DocListItem.Doc(it)) }
        }
        // Documentos agrupados por pasta/categoria
        val groupedByCategoria = docs.groupBy { categoryLabel(it) }
        val sortedCategorias = groupedByCategoria.keys.sortedWith(
            compareBy<String>({ it == "Outros" }, { it.lowercase() })
        )
        for (categoria in sortedCategorias) {
            listItems.add(DocListItem.Header(categoria))
            val docsCategoria = groupedByCategoria[categoria].orEmpty().sortedWith(
                compareByDescending<DocumentoItem> { extractYear(it) }
                    .thenByDescending { it.competencia.orEmpty() }
                    .thenByDescending { it.criado_fmt.orEmpty() }
            )
            docsCategoria.forEach { listItems.add(DocListItem.Doc(it)) }
        }
        notifyDataSetChanged()
    }

    private fun categoryLabel(item: DocumentoItem): String {
        val direct = item.categoria_label?.trim().orEmpty()
        if (direct.isNotBlank()) return direct
        return when (item.categoria?.trim()?.lowercase().orEmpty()) {
            "holerite" -> "Holerites"
            "folha_ponto", "ponto", "espelho_ponto" -> "Folha de Ponto"
            "aso" -> "ASO"
            "contrato" -> "Contratos"
            else -> "Outros"
        }
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

    /** Returns the list position of the item with the given arquivoId, or -1 if not found. */
    fun indexOfArquivoId(arquivoId: Int): Int {
        return listItems.indexOfFirst { it is DocListItem.Doc && it.item.id == arquivoId }
    }
}
