# RM Facilities v3

Sistema web de gestão operacional, RH e comunicação da RM Facilities, construído em Flask com banco SQLite e interface SPA (single-page).

## Principais módulos

### Comercial e Operacional
- Gestão de clientes, contratos e postos de trabalho.
- Propostas comerciais com numeração automática (PC-YYYY-NNNN) e envio via WhatsApp.
- Medições de serviços com controle de numeração, aprovação e assinatura digital.
- Ordens de compra com assinatura eletrônica.
- Documentos operacionais por categoria.

### RH — Funcionários
- Cadastro completo de colaboradores (dados pessoais, contratuais, bancários, escalas e jornadas).
- Gestão de documentos por categoria e competência, com envio via WhatsApp e assinatura digital.
- Benefícios mensais (VT, VR, plano de saúde, etc.) com histórico por competência.
- Folha de pagamento mensal e relatório de benefícios, com assinatura eletrônica pelo colaborador.
- Lançamentos avulsos (adiantamentos, descontos, etc.).
- Horas extras: solicitação, aprovação e histórico por competência.
- Feriados por empresa, com associação a funcionários.
- Gestão de escalas e jornadas de trabalho.

### Ponto Eletrônico
- Registro de marcações (entrada/saída/intervalo) pelo painel web e pelo app mobile.
- Totem de ponto por QR Code rotativo (`/ponto/totem`).
- Resumo diário e calendário mensal por funcionário.
- Ajustes de ponto com trilha de auditoria.
- Fechamento de dia com cálculo automático de horas.
- Espelho de ponto mensal em PDF (ReportLab) com cabeçalho personalizado por empresa.
- Solicitação de correção de ponto pelo colaborador via app.
- Visão "Gestão Fácil" com calendário de marcações.

### Envelopes e Assinaturas Digitais
- Envelopes digitais compostos por múltiplos documentos.
- Assinatura com código OTP via WhatsApp ou e-mail.
- Verificação criptográfica com hash SHA-256 do documento.
- Suporte a certificados digitais (pyHanko).
- Página pública de validação de assinaturas.

### Financeiro
- Controle de despesas com categorias, status e exportação CSV.
- Dashboard de faturamento consolidado com exportação CSV.
- Conciliação bancária: importação de extrato OFX, matching automático e lote de conciliação.

### Comunicação
- Integração com WhatsApp via Evolution API: envio de mensagens texto, documentos e imagens.
- Disparo de notificações push via Firebase FCM para o app Android.
- IA para respostas automáticas no WhatsApp (OpenAI / Gemini, conforme configuração).
- Backups automáticos agendados enviados via WhatsApp.

### Administração
- Gestão de usuários com controle de acesso por área.
- Cadastro de empresas com logotipo.
- Configurações gerais (numeração de documentos, links úteis, backup, logs, WhatsApp).
- Auditoria de eventos críticos (acessos, downloads, assinaturas).
- Backup e restauração via interface (ZIP com banco + uploads).
- Logs estruturados de erros e eventos do sistema.

## App Mobile — Funcionário (Android)

Pasta: `mobile-funcionario-android/`

### Recursos do app

- Registro de ponto (entrada/saída/intervalo) com geolocalização e validação de geofence.
- Totem de ponto por QR Code rotativo (modo quiosque).
- Visualização e assinatura digital de documentos e holerites.
- Chat com o RH.
- Avisos e comunicados com suporte a artigos via link (WebView interno).
- Solicitação de correção de ponto.
- Histórico e espelho de ponto.
- Notificações push via Firebase (FCM).

### Notificações push — Comunicados com artigo

Ao criar um comunicado no painel web, o campo **URL** é opcional.

- **Com URL**: a notificação push abre diretamente o artigo no WebView interno do app.
- **Sem URL**: a notificação abre a aba "Avisos" normalmente.

Na aba Avisos, comunicados com URL exibem o botão **🔗 Abrir artigo**.

### Build

> **Atenção:** nunca executar o build dentro do VS Code (trava o editor). Use um terminal externo.

```bash
cd mobile-funcionario-android
./gradlew assembleRelease   # APK
./gradlew bundleRelease     # AAB para Play Store
```

A versão é gerenciada em `app/version.properties` (VERSION_CODE, MAJOR, MINOR, PATCH).

## Stack

| Camada | Tecnologia |
|---|---|
| Linguagem | Python 3.12 |
| Framework web | Flask 3 |
| ORM | Flask-SQLAlchemy |
| Banco de dados | SQLite |
| Servidor WSGI | Gunicorn + Gevent |
| PDF | ReportLab, pypdf, pyHanko |
| Planilhas | openpyxl, XlsxWriter, pandas |
| Imagens / QR Code | Pillow, qrcode |
| Push notifications | firebase-admin (FCM) |
| Rate limiting | Flask-Limiter |
| Cache | Flask-Caching |
| Compressão | Flask-Compress |
| Monitoramento | Sentry SDK, structlog |
| Validação | Pydantic v2 |
| Frontend | HTML/CSS/JS (templates server-side, SPA) |

## Estrutura do projeto

```text
rmv-4.1/
  app.py                  # Aplicação principal (modelos, rotas, lógica)
  ponto_module.py         # Módulo de ponto eletrônico
  requirements.txt
  Procfile
  Dockerfile
  nixpacks.toml
  templates/              # Templates Jinja2
  static/
    css/main.css
    js/
    vendor/
  instance/
    rmfacilities.db       # Banco SQLite
    uploads/              # Arquivos enviados

mobile-funcionario-android/
  app/src/main/           # Código-fonte Android (Kotlin)
  app/version.properties  # Versionamento do APK/AAB
```

## Rodando localmente

1. Entre na pasta da aplicação:

```bash
cd rmv-4.1
```

2. Crie e ative um ambiente virtual:

```bash
python3 -m venv .venv
source .venv/bin/activate
```

3. Instale as dependências:

```bash
pip install -r requirements.txt
```

4. Defina variáveis de ambiente (recomendado):

```bash
export SECRET_KEY="troque-esta-chave-em-producao"
export PORT=5000
```

5. Inicie a aplicação:

```bash
python app.py
```

Acesse em `http://localhost:5000`.

## Rodando com Gunicorn

```bash
gunicorn --bind 0.0.0.0:${PORT:-5000} app:app
```

## Rodando com Docker

Na pasta `rmv-4.1`:

```bash
docker build -t rmfacilities-v3 .
docker run --rm -p 5000:5000 \
  -e SECRET_KEY="troque-esta-chave" \
  -e DATA_DIR=/data \
  -v rmfacilities_data:/data \
  rmfacilities-v3
```

## Deploy (Nixpacks / Procfile)

O projeto inclui `nixpacks.toml` e `Procfile` prontos para plataformas como Railway, Render e similares.

Comando de start:

```bash
gunicorn --bind 0.0.0.0:${PORT:-5000} app:app
```

## Banco e arquivos

- Banco padrão: SQLite em `instance/rmfacilities.db`.
- Uploads e anexos: `instance/uploads/`.

Com `DATA_DIR` definido em produção:

| Recurso | Caminho |
|---|---|
| Banco SQLite | `${DATA_DIR}/rmfacilities.db` |
| Uploads | `${DATA_DIR}/uploads/` |
| Backups WA | `${DATA_DIR}/wa_backups/` |
| Bancos BR | `${DATA_DIR}/bancos_br.json` |

Na primeira inicialização sem `DATABASE_URL`, o app tenta migrar automaticamente dados legados de `instance/rmfacilities.db`, `app.db` e `instance/uploads/` para o `DATA_DIR`.

**Importante para produção:** monte um volume persistente no caminho definido em `DATA_DIR` (ex.: `/data`) e faça backup periódico.

## Variáveis de ambiente

| Variável | Descrição |
|---|---|
| `SECRET_KEY` | Chave de sessão Flask (**obrigatória em produção**) |
| `PORT` | Porta de execução (padrão: 5000) |
| `DATA_DIR` | Diretório persistente para banco, uploads e auxiliares |
| `SENTRY_DSN` | DSN do Sentry para monitoramento de erros (opcional) |

## Integrações (configuráveis pela tela de Configurações)

- **WhatsApp Evolution API**: `wa_url`, `wa_instancia`, `wa_token`.
- **Firebase FCM**: configurado via `google-services.json` no app e `serviceAccountKey` no servidor.
- **IA**: provedor OpenAI ou Gemini para respostas automáticas no WhatsApp.

## Segurança (obrigatório em produção)

- Nunca use `SECRET_KEY` padrão.
- Restrinja acesso ao painel administrativo por perfil de usuário.
- Utilize HTTPS no ambiente público.
- Limite permissões por área para usuários não administradores.
- Revise periodicamente logs e trilhas de auditoria.
- Rate limiting ativo via Flask-Limiter (configure limites conforme necessidade).

## Backup e restauração

- Backup via interface: gera ZIP com banco SQLite + pasta de uploads.
- Backups automáticos podem ser agendados e enviados via WhatsApp.
- Em caso de erro na restauração, valide o arquivo ZIP e os logs do servidor.
- Mantenha cópias externas dos backups críticos.
