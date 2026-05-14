package br.com.rmfacilities.funcionarioapp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Item selado para o RecyclerView: cabeçalho de data ou mensagem. */
sealed class ChatItem {
    data class DataHeader(val rotulo: String) : ChatItem()
    data class Msg(val item: MensagemItem) : ChatItem()
}

class MensagemAdapter(
    private val onAbrirArquivo: (MensagemItem) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    /** Lista visível (inclui cabeçalhos de data inseridos automaticamente). */
    private val itens = mutableListOf<ChatItem>()

    /** Última lista pura de mensagens (sem cabeçalhos), usada para comparação. */
    private var msgsPuras = listOf<MensagemItem>()

    companion object {
        private const val TIPO_RH = 0
        private const val TIPO_FUNC = 1
        private const val TIPO_DATA = 2

        private val SDF_DIA = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        private val SDF_EXIB = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        /** Converte lista de mensagens em lista com cabeçalhos de data intercalados. */
        fun comCabecalhos(msgs: List<MensagemItem>): List<ChatItem> {
            val hoje = SDF_DIA.format(Calendar.getInstance().time)
            val ontem = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
                .time.let { SDF_DIA.format(it) }

            val resultado = mutableListOf<ChatItem>()
            var diaAnterior = ""
            for (m in msgs) {
                val diaMsg = m.enviado_em?.take(10) ?: ""
                if (diaMsg != diaAnterior && diaMsg.isNotBlank()) {
                    val rotulo = when (diaMsg) {
                        hoje  -> "Hoje"
                        ontem -> "Ontem"
                        else  -> try { SDF_EXIB.format(SDF_DIA.parse(diaMsg)!!) } catch (_: Exception) { diaMsg }
                    }
                    resultado.add(ChatItem.DataHeader(rotulo))
                    diaAnterior = diaMsg
                }
                resultado.add(ChatItem.Msg(m))
            }
            return resultado
        }
    }

    fun replaceAll(novas: List<MensagemItem>) {
        val novasComCab = comCabecalhos(novas)
        val old = itens.toList()
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = novasComCab.size
            override fun areItemsTheSame(op: Int, np: Int): Boolean {
                val o = old[op]; val n = novasComCab[np]
                if (o is ChatItem.DataHeader && n is ChatItem.DataHeader) return o.rotulo == n.rotulo
                if (o is ChatItem.Msg && n is ChatItem.Msg) return o.item.id == n.item.id
                return false
            }
            override fun areContentsTheSame(op: Int, np: Int) = old[op] == novasComCab[np]
        })
        itens.clear()
        itens.addAll(novasComCab)
        msgsPuras = novas
        diff.dispatchUpdatesTo(this)
    }

    fun addMensagem(m: MensagemItem) {
        val novas = msgsPuras + m
        replaceAll(novas)
    }

    override fun getItemViewType(position: Int) = when (val item = itens[position]) {
        is ChatItem.DataHeader -> TIPO_DATA
        is ChatItem.Msg -> if (item.item.de_rh) TIPO_RH else TIPO_FUNC
    }

    override fun getItemCount() = itens.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TIPO_RH   -> RhViewHolder(inflater.inflate(R.layout.item_msg_rh, parent, false))
            TIPO_FUNC -> FuncViewHolder(inflater.inflate(R.layout.item_msg_func, parent, false))
            else      -> DataViewHolder(inflater.inflate(R.layout.item_msg_date_header, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val chatItem = itens[position]) {
            is ChatItem.DataHeader -> (holder as DataViewHolder).tvData.text = chatItem.rotulo
            is ChatItem.Msg -> {
                val item = chatItem.item
                val temArquivo = item.tipo == "arquivo" && !item.arquivo_url.isNullOrBlank()
                if (holder is RhViewHolder) {
                    holder.tvConteudo.text = item.conteudo
                    holder.tvHora.text = item.enviado_fmt?.takeLast(5) ?: ""
                    holder.tvRemetente.text = item.enviado_por ?: "RH"
                    bindArquivo(holder.layoutArquivo, holder.tvArquivoNome, item, temArquivo)
                    // Long-press → copiar texto
                    holder.itemView.setOnLongClickListener {
                        copiarTexto(holder.itemView.context, item.conteudo)
                        true
                    }
                } else if (holder is FuncViewHolder) {
                    holder.tvConteudo.text = item.conteudo
                    holder.tvHora.text = item.enviado_fmt?.takeLast(5) ?: ""
                    if (item.lida == true) {
                        holder.tvCheck.text = "✓✓"
                        holder.tvCheck.setTextColor(0xFF4DA6FF.toInt()) // azul = lida
                    } else {
                        holder.tvCheck.text = "✓"
                        holder.tvCheck.setTextColor(0xFF5E7FA0.toInt()) // cinza = entregue
                    }
                    bindArquivo(holder.layoutArquivo, holder.tvArquivoNome, item, temArquivo)
                    // Long-press → copiar texto
                    holder.itemView.setOnLongClickListener {
                        copiarTexto(holder.itemView.context, item.conteudo)
                        true
                    }
                }
            }
        }
    }

    private fun copiarTexto(context: Context, texto: String?) {
        if (texto.isNullOrBlank()) return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("mensagem", texto))
        Toast.makeText(context, "Mensagem copiada", Toast.LENGTH_SHORT).show()
    }

    private fun bindArquivo(layout: LinearLayout, tvNome: TextView, item: MensagemItem, temArquivo: Boolean) {
        if (temArquivo) {
            layout.visibility = View.VISIBLE
            tvNome.text = item.arquivo_nome ?: "arquivo"
            layout.setOnClickListener { onAbrirArquivo(item) }
        } else {
            layout.visibility = View.GONE
        }
    }

    inner class DataViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val tvData: TextView = v.findViewById(R.id.tvDataSeparador)
    }

    inner class RhViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val tvRemetente: TextView = v.findViewById(R.id.tvRemetente)
        val tvConteudo: TextView = v.findViewById(R.id.tvConteudo)
        val tvHora: TextView = v.findViewById(R.id.tvHora)
        val layoutArquivo: LinearLayout = v.findViewById(R.id.layoutArquivo)
        val tvArquivoNome: TextView = v.findViewById(R.id.tvArquivoNome)
    }

    inner class FuncViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val tvConteudo: TextView = v.findViewById(R.id.tvConteudo)
        val tvHora: TextView = v.findViewById(R.id.tvHora)
        val tvCheck: TextView = v.findViewById(R.id.tvCheck)
        val layoutArquivo: LinearLayout = v.findViewById(R.id.layoutArquivo)
        val tvArquivoNome: TextView = v.findViewById(R.id.tvArquivoNome)
    }
}
