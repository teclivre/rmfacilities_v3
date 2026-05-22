"""Reset do usuário admin + limpeza de cache de autenticação.

Uso:
    ADMIN_DEFAULT_PASSWORD='nova-senha-forte' python reset_admin_and_cache.py

A senha é OBRIGATORIAMENTE lida da variável de ambiente ADMIN_DEFAULT_PASSWORD.
"""
import os
import sys
from app import app, db, Usuario, AuthTentativa, FuncionarioAppSessao
from werkzeug.security import generate_password_hash

NOVA_SENHA = (os.environ.get('ADMIN_DEFAULT_PASSWORD') or '').strip()
if not NOVA_SENHA:
    print('ERRO: defina ADMIN_DEFAULT_PASSWORD antes de rodar este script.', file=sys.stderr)
    sys.exit(1)

with app.app_context():
    email = 'admin@rmfacilities.com.br'
    user = Usuario.query.filter_by(email=email).first()
    if user:
        # Limpa cache de autenticação e sessões
        AuthTentativa.query.filter_by(identificador=user.email).delete()
        FuncionarioAppSessao.query.filter_by(funcionario_id=user.id).delete()
        # Atualiza dados do usuário
        user.nome = 'Administrador'
        user.telefone = '5512999999999'
        user.perfil = 'admin'
        user.ativo = True
        user.twofa_ativo = False
        user.senha = generate_password_hash(NOVA_SENHA, method='scrypt')
        db.session.commit()
        print('Usuário admin redefinido e cache zerado com sucesso!')
    else:
        user = Usuario(
            nome='Administrador',
            email=email,
            telefone='5512999999999',
            perfil='admin',
            ativo=True,
            twofa_ativo=False,
            senha=generate_password_hash(NOVA_SENHA, method='scrypt')
        )
        db.session.add(user)
        db.session.commit()
        print('Usuário admin criado com sucesso!')
