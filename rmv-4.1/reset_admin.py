"""Reset do usuário admin.

Uso:
    ADMIN_DEFAULT_PASSWORD='nova-senha-forte' python reset_admin.py

A senha é OBRIGATORIAMENTE lida da variável de ambiente ADMIN_DEFAULT_PASSWORD
para evitar exposição em código-fonte / histórico Git.
"""

import os
import sys
from app import app, db, Usuario
from werkzeug.security import generate_password_hash

NOVA_SENHA = (os.environ.get("ADMIN_DEFAULT_PASSWORD") or "").strip()
if not NOVA_SENHA:
    print(
        "ERRO: defina ADMIN_DEFAULT_PASSWORD antes de rodar este script.",
        file=sys.stderr,
    )
    sys.exit(1)

with app.app_context():
    email = "admin@rmfacilities.com.br"
    user = Usuario.query.filter_by(email=email).first()
    if not user:
        user = Usuario(
            nome="Administrador",
            email=email,
            telefone="5512999999999",
            perfil="admin",
            ativo=True,
            twofa_ativo=False,
            senha=generate_password_hash(NOVA_SENHA, method="scrypt"),
        )
        db.session.add(user)
        db.session.commit()
        print("Usuário admin criado com sucesso!")
    else:
        user.nome = "Administrador"
        user.telefone = "5512999999999"
        user.perfil = "admin"
        user.ativo = True
        user.twofa_ativo = False
        user.senha = generate_password_hash(NOVA_SENHA, method="scrypt")
        db.session.commit()
        print("Usuário admin redefinido com sucesso!")
