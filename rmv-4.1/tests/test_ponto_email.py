from datetime import datetime
from types import SimpleNamespace
from unittest.mock import patch

import app as app_module


def test_email_de_batida_contem_dados_do_colaborador_e_localizacao():
    funcionario = SimpleNamespace(id=12, nome="Maria Silva", posto_operacional="Posto Central")
    marcacao = SimpleNamespace(
        id=34,
        tipo="saida_intervalo",
        data_hora=datetime(2026, 10, 19, 8, 15, 30),
        latitude=-23.550520,
        longitude=-46.633308,
        precisao_gps=8.5,
        origem="app",
    )

    with patch("app.smtp_send_text") as send_email:
        assert app_module._notificar_ponto_email(funcionario, marcacao)

    send_email.assert_called_once()
    destino, assunto, corpo = send_email.call_args.args
    assert destino == "pontos@rmfacilities.com.br"
    assert "Maria Silva" in assunto
    assert "19/10/2026" in assunto
    assert "Saída para intervalo" in corpo
    assert "08:15:30" in corpo
    assert "-23.550520, -46.633308" in corpo
    assert "https://www.google.com/maps?q=-23.550520,-46.633308" in corpo
