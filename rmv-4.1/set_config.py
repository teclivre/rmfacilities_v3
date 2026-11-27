from app import app, db, Config

def set_config():
    with app.app_context():
        chave = 'app_download_url'
        valor = 'https://teclivre.com.br/downloads/rm-facilities-funcionario.apk' # Exemplo, ajuste conforme necessário
        cfg = Config.query.filter_by(chave=chave).first()
        if cfg:
            cfg.valor = valor
            print(f"Atualizado {chave} para '{valor}'")
        else:
            cfg = Config(chave=chave, valor=valor)
            db.session.add(cfg)
            print(f"Criado {chave} com valor '{valor}'")
        db.session.commit()

if __name__ == "__main__":
    set_config()
