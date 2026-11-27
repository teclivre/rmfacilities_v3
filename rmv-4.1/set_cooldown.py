from app import app, db, Config
import sys

def set_cooldown(hours):
    with app.app_context():
        chave = 'app_version_notif_cooldown_h'
        cfg = Config.query.filter_by(chave=chave).first()
        if cfg:
            cfg.valor = str(hours)
            print(f"Atualizado {chave} para '{hours}' horas")
        else:
            cfg = Config(chave=chave, valor=str(hours))
            db.session.add(cfg)
            print(f"Criado {chave} com valor '{hours}' horas")
        db.session.commit()

if __name__ == "__main__":
    if len(sys.argv) > 1:
        set_cooldown(sys.argv[1])
    else:
        print("Uso: python3 set_cooldown.py <horas>")
