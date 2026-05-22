import sqlite3
import os

db_path = os.path.join(os.path.dirname(__file__), "instance", "rmfacilities.db")

conn = sqlite3.connect(db_path)
cur = conn.cursor()
try:
    cur.execute(
        "ALTER TABLE beneficio_mensal ADD COLUMN horas_noturnas_min INTEGER DEFAULT 0;"
    )
    print("Coluna 'horas_noturnas_min' adicionada com sucesso.")
except sqlite3.OperationalError as e:
    if "duplicate column name" in str(e) or "already exists" in str(e):
        print("Coluna já existe.")
    else:
        print("Erro:", e)
conn.commit()
conn.close()
