from app import app, db, Config

def set_min_version(version_code):
    with app.app_context():
        chave = 'app_version_minima'
        cfg = Config.query.filter_by(chave=chave).first()
        if cfg:
            cfg.valor = str(version_code)
            print(f"Atualizado {chave} para '{version_code}'")
        else:
            cfg = Config(chave=chave, valor=str(version_code))
            db.session.add(cfg)
            print(f"Criado {chave} com valor '{version_code}'")
        db.session.commit()

if __name__ == "__main__":
    # Definindo para 808 conforme a versão que você acabou de gerar
    set_min_version(808)
