import re


def _coletivo_cpf_prefix_digits(valor):
    return re.sub(r"\D+", "", str(valor or ""))[:4]


def _coletivo_tipo_descricao(tipo):
    mapa = {
        "entrada": "entrada",
        "saida_intervalo": "saída para almoço",
        "retorno_intervalo": "entrada do almoço",
        "saida": "saída",
    }
    return mapa.get((tipo or "").strip().lower(), "ponto")


def test_prefixo_cpf_para_quatro_digitos():
    assert _coletivo_cpf_prefix_digits("123.456.789-09") == "1234"
    assert _coletivo_cpf_prefix_digits("12345678909") == "1234"


def test_descricao_do_tipo_para_whatsapp():
    assert _coletivo_tipo_descricao("entrada") == "entrada"
    assert _coletivo_tipo_descricao("saida_intervalo") == "saída para almoço"
    assert _coletivo_tipo_descricao("retorno_intervalo") == "entrada do almoço"
    assert _coletivo_tipo_descricao("saida") == "saída"
