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
    val status: String? = null,
    val ultimo_aso_competencia: String? = null,
    val ultimo_aso_enviado_em: String? = null
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
    val app_download_url: String? = null
)
