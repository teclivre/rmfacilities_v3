"""Cria a tabela solicitacao_hora_extra no banco SQLite."""
import sys, os
sys.path.insert(0, os.path.dirname(__file__))

from app import app, db, SolicitacaoHoraExtra  # noqa: F401

with app.app_context():
    db.create_all()
    print("✅ Tabela solicitacao_hora_extra criada/verificada.")
