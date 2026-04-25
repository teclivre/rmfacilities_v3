from app import app, db, Usuario
from werkzeug.security import generate_password_hash

with app.app_context():
    email = 'admin@rmfacilities.com.br'
    user = Usuario.query.filter_by(email=email).first()
    if not user:
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
        print('Usuário admin criado com sucesso!')
    else:
        user.nome = 'Administrador'
        user.telefone = '5512999999999'
        user.perfil = 'admin'
        user.ativo = True
        user.twofa_ativo = False
        user.senha = generate_password_hash('naoseinao', method='scrypt')
        db.session.commit()
        print('Usuário admin redefinido com sucesso!')
