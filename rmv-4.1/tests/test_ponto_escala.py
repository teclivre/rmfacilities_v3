from ponto_module import _ponto_6x1_template_dia


def test_escala_6x1_mantem_domingo_como_folga_e_sabado_como_jornada_propria():
    dias = [
        {"tipo": "trabalho", "hora_entrada": "08:00", "hora_saida": "17:00"}
        for _ in range(5)
    ]
    dias.extend(
        [
            {"tipo": "trabalho", "hora_entrada": "08:00", "hora_saida": "12:00"},
            {"tipo": "folga"},
        ]
    )

    assert _ponto_6x1_template_dia(dias, 0) == (0, dias[0])
    assert _ponto_6x1_template_dia(dias, 5) == (5, dias[5])
    assert _ponto_6x1_template_dia(dias, 6) == (6, dias[6])


def test_escala_6x1_nao_cria_folga_em_dia_util_por_falta_de_modelo():
    dias = [{"tipo": "trabalho"}]

    idx, dia = _ponto_6x1_template_dia(dias, 0)
    assert (idx, dia) == (0, dias[0])
    assert _ponto_6x1_template_dia(dias, 6) == (None, {"tipo": "folga"})