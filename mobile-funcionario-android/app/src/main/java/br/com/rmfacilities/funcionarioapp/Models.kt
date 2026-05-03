package br.com.rmfacilities.funcionarioapp

data class LoginResponse(
    val ok: Boolean = false,
    val erro: String? = null,
    val access_token: String? = null,
    val refresh_token: String? = null,
    val funcionario: FuncionarioResumo? = null
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
    val status: String? = null
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
