package br.com.rmfacilities.funcionarioapp

data class LoginResponse(
    val ok: Boolean = false,
    val erro: String? = null,
    val access_token: String? = null,
    val refresh_token: String? = null,
    val funcionario: FuncionarioResumo? = null
)

data class OtpStartResponse(
    val ok: Boolean = false,
    val erro: String? = null,
    val mensagem: String? = null,
    val destino: String? = null
)

data class FuncionarioResumo(
    val id: Int,
    val nome: String? = null,
    val cpf: String? = null,
    val cargo: String? = null,
    val setor: String? = null,
    val status: String? = null
)

data class MeResponse(
    val ok: Boolean = false,
    val erro: String? = null,
    val funcionario: FuncionarioPerfil? = null
)

data class FuncionarioPerfil(
    val id: Int,
    val nome: String? = null,
    val cpf: String? = null,
    val email: String? = null,
    val telefone: String? = null,
    val cargo: String? = null,
    val setor: String? = null,
    val empresa_nome: String? = null,
    val posto_operacional: String? = null,
    val status: String? = null,
    val foto_url: String? = null,
    val data_nascimento: String? = null,
    val ultimo_aso_competencia: String? = null,
    val ultimo_aso_enviado_em: String? = null,
    val canal_otp: String? = null
)

data class FotoUploadResponse(
    val ok: Boolean = false,
    val erro: String? = null,
    val foto_url: String? = null
)

data class ContatoUpdateResponse(
    val ok: Boolean = false,
    val erro: String? = null,
    val funcionario: ContatoInfo? = null
)

data class ContatoInfo(
    val id: Int? = null,
    val email: String? = null,
    val telefone: String? = null
)

data class SolicitacaoResponse(
    val ok: Boolean = false,
    val erro: String? = null,
    val item: SolicitacaoItem? = null,
    val items: List<SolicitacaoItem> = emptyList()
)

data class SolicitacaoItem(
    val id: Int,
    val status: String? = null,
    val observacao: String? = null,
    val motivo_admin: String? = null,
    val solicitado_fmt: String? = null
)

data class DocsResponse(
    val ok: Boolean = false,
    val erro: String? = null,
    val itens: List<DocumentoItem> = emptyList()
)

data class DocumentoItem(
    val id: Int,
    val categoria: String? = null,
    val categoria_label: String? = null,
    val ano: String? = null,
    val nome_arquivo: String? = null,
    val competencia: String? = null,
    val criado_fmt: String? = null,
    val app_download_url: String? = null,
    val ass_status: String? = null,
    val ass_em_fmt: String? = null,
    val can_assinar: Boolean = true,
    val ass_prazo_em: String? = null,
    val ass_prazo_fmt: String? = null
)

data class MensagemItem(
    val id: Int,
    val funcionario_id: Int,
    val de_rh: Boolean = false,
    val conteudo: String = "",
    val enviado_em: String? = null,
    val enviado_fmt: String? = null,
    val lida: Boolean = false,
    val enviado_por: String? = null,
    val tipo: String? = "texto",
    val arquivo_nome: String? = null,
    val arquivo_url: String? = null
)

data class NaoLidasResponse(
    val nao_lidas: Int = 0
)

data class ComunicadoItem(
    val id: Int,
    val titulo: String = "",
    val conteudo: String = "",
    val url: String? = null,
    val criado_fmt: String? = null,
    val lido: Boolean = false,
    val lidos_count: Int = 0,
    val funcionario_id: Int? = null,
    val posto_operacional: String? = null
)

data class ApiSimpleResponse(
    val ok: Boolean = false,
    val erro: String? = null,
    val mensagem: String? = null
)

data class VersaoAppResponse(
    val versao_minima: Int = 0,
    val versao_atual: Int = 0,
    val download_url: String? = null
)

data class AssinaturaHistoricoItem(
    val id: Int,
    val categoria: String? = null,
    val categoria_label: String? = null,
    val ano: String? = null,
    val nome_arquivo: String? = null,
    val competencia: String? = null,
    val ass_em_fmt: String? = null,
    val ass_ip_mask: String? = null,
    val ass_codigo: String? = null,
    val app_download_url: String? = null
)

data class HistoricoAssinaturasResponse(
    val ok: Boolean = false,
    val erro: String? = null,
    val itens: List<AssinaturaHistoricoItem> = emptyList()
)

data class PontoMarcacaoItem(
    val id: Int,
    val tipo: String? = null,
    val tipo_label: String? = null,
    val data_hora: String? = null,
    val hora_fmt: String? = null,
    val origem: String? = null,
    val observacao: String? = null,
    val lat: Double? = null,
    val lon: Double? = null
)

data class PontoResumo(
    val funcionario_id: Int? = null,
    val funcionario_nome: String? = null,
    val data_ref: String? = null,
    val marcacoes: List<PontoMarcacaoItem> = emptyList(),
    val proximo_tipo: String? = null,
    val proximo_tipo_label: String? = null,
    val horas_trabalhadas_fmt: String? = null,
    val horas_trabalhadas_min: Int = 0,
    val horas_esperadas_fmt: String? = null,
    val horas_esperadas_min: Int = 0,
    val saldo_fmt: String? = null,
    val saldo_min: Int = 0,
    val status: String? = null,
    val inconsistencias: List<String> = emptyList(),
    val fechado: Boolean = false,
    val fechado_por: String? = null,
    val max_marcacoes_dia: Int = 4,
    val correcoes_faltando_pendentes: Int = 0
)

data class PontoMarcacaoLocalizacao(
    val status: String? = null,
    val distancia_m: Double? = null,
    val raio_m: Double? = null,
    val posto_cliente_id: Int? = null
)

data class PontoMarcacaoConfirmada(
    val id: Int? = null,
    val tipo: String? = null,
    val tipo_label: String? = null,
    val hora_fmt: String? = null,
    val localizacao: PontoMarcacaoLocalizacao? = null
)

data class PontoDiaResponse(
    val ok: Boolean = false,
    val erro: String? = null,
    val resumo: PontoResumo? = null,
    val marcacao: PontoMarcacaoConfirmada? = null
)

data class PontoHistoricoResponse(
    val ok: Boolean = false,
    val erro: String? = null,
    val dias: List<PontoResumo> = emptyList()
)

data class PontoEspelhoCompetencia(
    val competencia: String = "",
    val label: String = "",
    val pode_baixar: Boolean = false,
    val fechamentos_dias: Int = 0
)

data class PontoEspelhoStatusResponse(
    val ok: Boolean = false,
    val erro: String? = null,
    val competencias: List<PontoEspelhoCompetencia> = emptyList()
)

data class PontoEspelhoDia(
    val data: String? = null,
    val data_fmt: String? = null,
    val marcacoes: List<PontoMarcacaoItem> = emptyList(),
    val horas_trabalhadas_fmt: String? = null,
    val horas_trabalhadas_min: Int = 0,
    val horas_esperadas_min: Int = 0,
    val he_50_fmt: String? = null,
    val he_50_min: Int = 0,
    val he_100_fmt: String? = null,
    val he_100_min: Int = 0,
    val noturno_fmt: String? = null,
    val noturno_min: Int = 0,
    val intrajornada_fmt: String? = null,
    val intrajornada_min: Int = 0,
    val inconsistencias: List<String> = emptyList(),
    val status: String? = null,
    val tem_marcacoes: Boolean = false
)

data class PontoEspelhoTotais(
    val horas_trabalhadas_fmt: String? = null,
    val he_50_fmt: String? = null,
    val he_50_min: Int = 0,
    val he_100_fmt: String? = null,
    val he_100_min: Int = 0,
    val noturno_fmt: String? = null,
    val noturno_min: Int = 0,
    val intrajornada_fmt: String? = null,
    val intrajornada_min: Int = 0
)

data class PontoEspelhoDadosResponse(
    val ok: Boolean = false,
    val erro: String? = null,
    val competencia: String? = null,
    val label: String? = null,
    val total_horas: String? = null,
    val funcionario: String? = null,
    val totais: PontoEspelhoTotais? = null,
    val dias: List<PontoEspelhoDia> = emptyList()
)

data class FeriasResponse(
    val ok: Boolean = false,
    val erro: String? = null,
    val ferias_inicio: String? = null,
    val ferias_fim: String? = null,
    val ferias_obs: String? = null,
    val ferias_dias: Int = 30,
    val em_ferias: Boolean = false,
    val dias_restantes: Int? = null,
    val proximas: FeriasProximas? = null
)

data class FeriasProximas(
    val inicio: String? = null,
    val fim: String? = null,
    val dias_para_inicio: Int? = null
)

data class CorrecaoPontoResponse(
    val ok: Boolean = false,
    val erro: String? = null,
    val mensagem: String? = null,
    val id: Int? = null
)

data class CorrecaoPontoItem(
    val id: Int = 0,
    val data_ref: String? = null,
    val tipo_problema: String? = null,
    val horario_esperado: String? = null,
    val horario_correto: String? = null,
    val horario_original: String? = null,
    val marcacao_id: Int? = null,
    val observacao: String? = null,
    val status: String? = null,
    val motivo_admin: String? = null,
    val criado_fmt: String? = null,
    val resolvido_fmt: String? = null
)

data class CorrecaoPontoListResponse(
    val ok: Boolean = false,
    val erro: String? = null,
    val itens: List<CorrecaoPontoItem> = emptyList()
)

data class ResumoMesResponse(
    val ok: Boolean = false,
    val erro: String? = null,
    val total_trabalhado_fmt: String? = null,
    val total_trabalhado_min: Int = 0,
    val total_esperado_min: Int = 0,
    val saldo_min: Int = 0,
    val saldo_fmt: String? = null
)

data class AlteracaoSolicitacaoItem(
    val id: Int = 0,
    val status: String? = null,
    val observacao: String? = null,
    val motivo_admin: String? = null,
    val solicitado_fmt: String? = null,
    val analisado_fmt: String? = null,
    val payload: Map<String, String?> = emptyMap()
)

data class AlteracaoListResponse(
    val ok: Boolean = false,
    val erro: String? = null,
    val items: List<AlteracaoSolicitacaoItem> = emptyList()
)
