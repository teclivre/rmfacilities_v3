from app import app, db, Config
import sys

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
    if len(sys.argv) > 1:
        set_min_version(sys.argv[1])
    else:
        print("Uso: python3 set_min_version.py <versao>")
