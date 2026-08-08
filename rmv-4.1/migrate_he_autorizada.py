"""Migração: adiciona coluna he_autorizada na tabela cliente.

Execução:
    python migrate_he_autorizada.py
"""
import sqlite3
import os

db_path = os.path.join(os.path.dirname(__file__), "instance", "rmfacilities.db")

conn = sqlite3.connect(db_path)
cur = conn.cursor()
try:
    cur.execute(
        "ALTER TABLE cliente ADD COLUMN he_autorizada INTEGER NOT NULL DEFAULT 1;"
    )
    print("Coluna 'he_autorizada' adicionada com sucesso.")
except sqlite3.OperationalError as e:
    if "duplicate column name" in str(e) or "already exists" in str(e):
        print("Coluna já existe, nada a fazer.")
    else:
        print("Erro:", e)
conn.commit()
conn.close()
