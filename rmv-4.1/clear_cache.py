from app import app, db, Usuario, AuthTentativa, FuncionarioAppSessao

with app.app_context():
    email = 'admin@rmfacilities.com.br'
    user = Usuario.query.filter_by(email=email).first()
    if user:
        AuthTentativa.query.filter_by(identificador=user.email).delete()
        FuncionarioAppSessao.query.filter_by(funcionario_id=user.id).delete()
        db.session.commit()
        print('Cache de autenticação e sessões do admin zerado com sucesso!')
    else:
        print('Usuário admin não encontrado.')
