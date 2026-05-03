from app import app, db, Usuario, AuthTentativa, FuncionarioAppSessao
from werkzeug.security import generate_password_hash

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
        user.senha = generate_password_hash('naoseinao', method='scrypt')
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
            senha=generate_password_hash('naoseinao', method='scrypt')
        )
        db.session.add(user)
        db.session.commit()
        print('Usuário admin criado e cache zerado com sucesso!')
