package br.com.rmfacilities.funcionarioapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

private sealed class DocListItem {
    object PendentesHeader : DocListItem()
    data class CategoryHeader(val label: String, val count: Int, val expanded: Boolean) : DocListItem()
    data class YearHeader(val categoria: String, val year: String, val count: Int, val expanded: Boolean) : DocListItem()
    data class Doc(val item: DocumentoItem) : DocListItem()
}

class DocumentoAdapter(
    private val onBaixar: (DocumentoItem) -> Unit,
    private val onAssinar: (DocumentoItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_PENDENTES_HEADER = 0
        private const val TYPE_CATEGORY_HEADER = 1
        private const val TYPE_YEAR_HEADER = 2
        private const val TYPE_ITEM = 3
    }

    private val listItems = mutableListOf<DocListItem>()
    private var pendentesData: List<DocumentoItem> = emptyList()
    private var rawByCategoria: Map<String, Map<String, List<DocumentoItem>>> = emptyMap()
    private val expandedCategories = mutableSetOf<String>()
    private val expandedYears = mutableSetOf<String>() // key = "cat::year"

    // ── View Holders ──────────────────────────────────────────────────────────

    class PendentesHeaderVH(v: View) : RecyclerView.ViewHolder(v)

    class CategoryHeaderVH(v: View) : RecyclerView.ViewHolder(v) {
        val tvLabel: TextView = v.findViewById(R.id.tvCategoryLabel)
        val tvCount: TextView = v.findViewById(R.id.tvCategoryCount)
        val tvArrow: TextView = v.findViewById(R.id.tvCategoryArrow)
    }

    class YearHeaderVH(v: View) : RecyclerView.ViewHolder(v) {
        val tvYear: TextView = v.findViewById(R.id.tvYearHeader)
        val tvCount: TextView = v.findViewById(R.id.tvYearCount)
        val tvArrow: TextView = v.findViewById(R.id.tvYearArrow)
    }

    class ItemVH(v: View) : RecyclerView.ViewHolder(v) {
        val tvNome: TextView = v.findViewById(R.id.tvNomeArquivo)
        val tvInfo: TextView = v.findViewById(R.id.tvInfo)
        val tvAssStatus: TextView = v.findViewById(R.id.tvAssStatus)
        val btnBaixar: MaterialButton = v.findViewById(R.id.btnBaixar)
        val btnAssinar: MaterialButton = v.findViewById(R.id.btnAssinar)
    }

    // ── Adapter overrides ────────────────────────────────────────────────────

    override fun getItemViewType(position: Int): Int = when (listItems[position]) {
        is DocListItem.PendentesHeader -> TYPE_PENDENTES_HEADER
        is DocListItem.CategoryHeader -> TYPE_CATEGORY_HEADER
        is DocListItem.YearHeader -> TYPE_YEAR_HEADER
        is DocListItem.Doc -> TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_PENDENTES_HEADER -> PendentesHeaderVH(inf.inflate(R.layout.item_doc_pendentes_header, parent, false))
            TYPE_CATEGORY_HEADER -> CategoryHeaderVH(inf.inflate(R.layout.item_doc_category_header, parent, false))
            TYPE_YEAR_HEADER -> YearHeaderVH(inf.inflate(R.layout.item_doc_year_header, parent, false))
            else -> ItemVH(inf.inflate(R.layout.item_documento, parent, false))
        }
    }

    override fun getItemCount(): Int = listItems.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val entry = listItems[position]) {
            is DocListItem.PendentesHeader -> { /* static header */ }
            is DocListItem.CategoryHeader -> {
                val vh = holder as CategoryHeaderVH
                vh.tvLabel.text = entry.label
                vh.tvCount.text = "${entry.count}"
                vh.tvArrow.text = if (entry.expanded) "▼" else "▶"
                vh.itemView.setOnClickListener { toggleCategory(entry.label) }
            }
            is DocListItem.YearHeader -> {
                val vh = holder as YearHeaderVH
                vh.tvYear.text = entry.year
                vh.tvCount.text = "${entry.count} arquivo${if (entry.count != 1) "s" else ""}"
                vh.tvArrow.text = if (entry.expanded) "▼" else "▶"
                vh.itemView.setOnClickListener { toggleYear(entry.categoria, entry.year) }
            }
            is DocListItem.Doc -> bindDoc(holder as ItemVH, entry.item)
        }
    }

    private fun bindDoc(vh: ItemVH, item: DocumentoItem) {
        vh.tvNome.text = item.nome_arquivo ?: "Documento"
        vh.tvInfo.text = listOf(item.competencia, item.criado_fmt)
            .filter { !it.isNullOrBlank() }
            .joinToString(" • ")

        val statusNorm = (item.ass_status ?: "").lowercase().trim()
        val pendente = statusNorm == "pendente"
        val assinado = statusNorm == "concluida"

        when {
            assinado -> {
                vh.tvAssStatus.visibility = View.VISIBLE
                val quando = item.ass_em_fmt?.takeIf { it.isNotBlank() }
                vh.tvAssStatus.text = if (quando != null) "Assinado em $quando" else "Documento assinado"
            }
            pendente -> {
                vh.tvAssStatus.visibility = View.VISIBLE
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

        vh.btnAssinar.visibility = if (pendente) View.VISIBLE else View.GONE
        vh.btnBaixar.setOnClickListener { onBaixar(item) }
        vh.btnAssinar.setOnClickListener { if (pendente) onAssinar(item) }
    }

    // ── Data ─────────────────────────────────────────────────────────────────

    fun replaceAll(pendentes: List<DocumentoItem>?, docs: List<DocumentoItem>?) {
        pendentesData = pendentes ?: emptyList()
        // Build rawByCategoria: categoria -> year -> sorted docs
        rawByCategoria = (docs ?: emptyList())
            .groupBy { categoryLabel(it) }
            .mapValues { (_, items) ->
                items.groupBy { extractYear(it) }
                    .mapValues { (_, ys) ->
                        ys.sortedWith(
                            compareByDescending<DocumentoItem> { it.competencia.orEmpty() }
                                .thenByDescending { it.criado_fmt.orEmpty() }
                        )
                    }
            }
        // Preserve existing expansion state — only add newly-seen categories/years if any
        buildAndNotify()
    }

    private fun toggleCategory(cat: String) {
        if (expandedCategories.contains(cat)) expandedCategories.remove(cat)
        else expandedCategories.add(cat)
        buildAndNotify()
    }

    private fun toggleYear(cat: String, year: String) {
        val key = "$cat::$year"
        if (expandedYears.contains(key)) expandedYears.remove(key)
        else expandedYears.add(key)
        buildAndNotify()
    }

    private fun buildAndNotify() {
        listItems.clear()
        // Pendentes (always visible, flat)
        if (pendentesData.isNotEmpty()) {
            listItems.add(DocListItem.PendentesHeader)
            pendentesData.forEach { listItems.add(DocListItem.Doc(it)) }
        }
        // Categorias (collapsible)
        val sortedCats = rawByCategoria.keys.sortedWith(
            compareBy<String>({ it == "Outros" }, { it.lowercase() })
        )
        for (cat in sortedCats) {
            val byYear = rawByCategoria[cat] ?: continue
            val totalCount = byYear.values.sumOf { it.size }
            val catExpanded = expandedCategories.contains(cat)
            listItems.add(DocListItem.CategoryHeader(cat, totalCount, catExpanded))
            if (catExpanded) {
                val sortedYears = byYear.keys.sortedDescending()
                for (year in sortedYears) {
                    val yearDocs = byYear[year] ?: continue
                    val yearKey = "$cat::$year"
                    val yearExpanded = expandedYears.contains(yearKey)
                    listItems.add(DocListItem.YearHeader(cat, year, yearDocs.size, yearExpanded))
                    if (yearExpanded) {
                        yearDocs.forEach { listItems.add(DocListItem.Doc(it)) }
                    }
                }
            }
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

    fun indexOfArquivoId(arquivoId: Int): Int {
        return listItems.indexOfFirst { it is DocListItem.Doc && it.item.id == arquivoId }
    }
}

