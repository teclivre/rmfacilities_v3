# RM Facilities v3

Sistema web de gestão operacional, RH e comunicação da RM Facilities, construído em Flask com banco SQLite e interface web única.

## Principais recursos

- Dashboard com indicadores operacionais.
- Gestão de clientes, medições e histórico.
- Gestão de RH com cadastro de funcionários e documentos por categoria/competência.
- Comunicação integrada via WhatsApp (Evolution API), com envio de mensagens e documentos.
- Integração de IA para respostas automáticas no WhatsApp.
- Backup e restauração de dados e arquivos.
- Controle de usuários com áreas de acesso.

## Stack

- Python 3.12
- Flask 3
- Flask-SQLAlchemy
- SQLite
- Gunicorn
- HTML/CSS/JS (templates server-side)

## Estrutura do projeto

```text
rmv3/
  app.py
  requirements.txt
  Procfile
  Dockerfile
  nixpacks.toml
  templates/
  static/
  instance/
```

## Requisitos

- Python 3.12+
- pip

## Rodando localmente

1. Entre na pasta da aplicação:

```bash
cd rmv3
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

Na pasta `rmv3`:

```bash
docker build -t rmfacilities-v3 .
docker run --rm -p 5000:5000 \
  -e SECRET_KEY="troque-esta-chave" \
  -e DATA_DIR=/data \
  -v rmfacilities_data:/data \
  rmfacilities-v3
```

## Deploy (Nixpacks / Procfile)

O projeto já inclui:

- `nixpacks.toml` para build e start em plataformas compatíveis.
- `Procfile` com comando web para Gunicorn.

Comando de start definido:

```bash
gunicorn --bind 0.0.0.0:${PORT:-5000} app:app
```

## Banco e arquivos

- Banco padrão: SQLite em `instance/rmfacilities.db`.
- Uploads e anexos: armazenados em `instance/uploads/`.

Com `DATA_DIR` definido em produção, os caminhos ficam:

- Banco SQLite: `${DATA_DIR}/rmfacilities.db`
- Uploads: `${DATA_DIR}/uploads/`
- Backups auxiliares: `${DATA_DIR}/wa_backups/`, `${DATA_DIR}/bancos_br.json`

Na primeira inicialização sem `DATABASE_URL`, o app tenta migrar automaticamente dados legados de `instance/rmfacilities.db`, `app.db` e `instance/uploads/` para o `DATA_DIR`.

Importante para produção:

- Use volume persistente para a pasta `instance/`.
- Em container, monte um volume persistente no caminho definido em `DATA_DIR` (exemplo: `/data`).
- Faça backup periódico da pasta `instance/`.

## Configurações importantes

A aplicação usa configurações por variáveis de ambiente e tabela `Config`.

### Variáveis de ambiente

- `SECRET_KEY`: chave de sessão Flask (obrigatória em produção).
- `PORT`: porta de execução.
- `DATA_DIR`: diretório persistente para banco SQLite, uploads e arquivos auxiliares. Padrão local: `instance/`.

### Integrações (via tela de configuração)

- WhatsApp Evolution API (`wa_url`, `wa_instancia`, `wa_token`).
- Provedor de IA para respostas automáticas (OpenAI/Gemini, conforme configuração interna).

## Segurança (obrigatório em produção)

- Nunca use `SECRET_KEY` padrão.
- Restrinja acesso ao painel administrativo.
- Utilize HTTPS no ambiente público.
- Limite permissões por área para usuários não administradores.
- Revise periodicamente logs e trilhas de operação.

## Backup e restauração

- O sistema possui recursos de backup/restauração via interface.
- Em caso de erro na restauração, valide o arquivo ZIP e os logs do servidor.
- Mantenha cópias externas dos backups críticos.

## Dependências

Arquivo `requirements.txt`:

- flask==3.0.3
- flask-sqlalchemy==3.1.1
- reportlab==4.2.2
- gunicorn==22.0.0
- pypdf==4.3.1
- openpyxl==3.1.5

## Troubleshooting rápido

### A aplicação não sobe

- Verifique se o ambiente virtual está ativo.
- Confirme instalação das dependências.
- Valide sintaxe do backend:

```bash
python3 -m py_compile app.py
```

### Erro 500 em API

- Verifique logs do Gunicorn/servidor.
- Confira variáveis de ambiente e credenciais de integração.

### Problemas com WhatsApp/IA

- Revise URL/token/instância da Evolution API.
- Valide configuração do provedor de IA na tela de configurações.

## Licença

Uso interno RM Facilities, salvo definição diferente pelo mantenedor do repositório.
