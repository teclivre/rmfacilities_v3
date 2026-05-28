from datetime import date, datetime, timedelta, timezone
import io
import json
import os
import re
import urllib.request

from flask import jsonify, request, send_file, session
from zoneinfo import ZoneInfo


def register_ponto_routes(
    app,
    *,
    db,
    utcnow,
    to_num,
    lr,
    audit_event,
    Funcionario,
    PontoMarcacao,
    PontoAjuste,
    PontoFechamentoDia,
    Empresa,
    Cliente=None,
    Feriado=None,
    SolicitacaoHoraExtra=None,
    get_logo,
    EscalaFuncionario=None,
    Escala=None,
    JornadaTrabalho=None,
):
    if "api_ponto_marcar" in app.view_functions:
        return

    # Fallback lazy: se algum model opcional não foi passado, tenta resolver via
    # o registry do SQLAlchemy. Evita regressões quando esquecemos de atualizar
    # a chamada em app.py (ex.: 'register_ponto_routes() missing 1 required
    # keyword-only argument: Feriado'). Mantemos None se o model não existir.
    def _resolve_model(name):
        try:
            reg = getattr(db.Model, "registry", None)
            mappers = getattr(reg, "_class_registry", None) if reg is not None else None
            if mappers and name in mappers:
                return mappers[name]
        except Exception:
            pass
        return None

    if Feriado is None:
        Feriado = _resolve_model("Feriado")
    if Cliente is None:
        Cliente = _resolve_model("Cliente")
    if SolicitacaoHoraExtra is None:
        SolicitacaoHoraExtra = _resolve_model("SolicitacaoHoraExtra")
    if EscalaFuncionario is None:
        EscalaFuncionario = _resolve_model("EscalaFuncionario")
    if Escala is None:
        Escala = _resolve_model("Escala")
    if JornadaTrabalho is None:
        JornadaTrabalho = _resolve_model("JornadaTrabalho")

    ponto_tipos = ["entrada", "saida_intervalo", "retorno_intervalo", "saida"]

    def _ponto_label(tipo):
        return {
            "entrada": "Entrada",
            "saida_intervalo": "Saída intervalo",
            "retorno_intervalo": "Retorno intervalo",
            "saida": "Saída",
        }.get((tipo or "").strip().lower(), (tipo or "").strip())

    def _ponto_next_tipo(tipo):
        tipo = (tipo or "").strip().lower()
        if tipo not in ponto_tipos:
            return "entrada"
        return ponto_tipos[(ponto_tipos.index(tipo) + 1) % len(ponto_tipos)]

    def _ponto_tipo_esperado(marcacoes):
        if not marcacoes:
            return "entrada"
        return _ponto_next_tipo(marcacoes[-1].tipo)

    def _ponto_parse_data_ref(valor):
        texto = (valor or "").strip()
        if not texto:
            return date.today()
        try:
            return datetime.strptime(texto, "%Y-%m-%d").date()
        except Exception:
            return date.today()

    def _ponto_parse_data_hora(valor):
        texto = (valor or "").strip()
        if not texto:
            return None
        candidato = texto.replace("Z", "+00:00")
        try:
            dt = datetime.fromisoformat(candidato)
        except Exception:
            dt = None
            for fmt in (
                "%Y-%m-%d %H:%M",
                "%Y-%m-%dT%H:%M",
                "%Y-%m-%d %H:%M:%S",
                "%Y-%m-%dT%H:%M:%S",
            ):
                try:
                    dt = datetime.strptime(texto, fmt)
                    break
                except Exception:
                    continue
        if not dt:
            return None
        if dt.tzinfo:
            dt = dt.astimezone(timezone.utc).replace(tzinfo=None)
        return dt

    def _ponto_min_esperado_jornada(funcionario):
        jornada = str(funcionario.jornada or "").strip().lower()
        if not jornada:
            return 8 * 60
        match = re.search(r"(\d{1,2})\s*[:h]\s*(\d{1,2})", jornada)
        if match:
            horas = max(0, min(16, int(match.group(1))))
            minutos = max(0, min(59, int(match.group(2))))
            return horas * 60 + minutos
        match = re.search(r"\b(\d{1,2})\b", jornada)
        if match:
            horas = max(0, min(16, int(match.group(1))))
            return horas * 60
        return 8 * 60

    def _ponto_min_esperado_data(funcionario, data_ref):
        """Retorna minutos esperados para funcionario em data_ref.
        Prioridade: 1) Escala rotativa (EscalaFuncionario/Escala ciclo_json)
                    2) JornadaTrabalho por dia da semana
                    3) Campo texto legado (jornada)
        """
        try:
            data_str = data_ref.strftime("%Y-%m-%d") if hasattr(data_ref, "strftime") else str(data_ref)
            data_obj = data_ref if hasattr(data_ref, "weekday") else datetime.strptime(data_str, "%Y-%m-%d").date()

            # 1) Escala rotativa
            if EscalaFuncionario and Escala:
                esc_funcs = EscalaFuncionario.query.filter(
                    EscalaFuncionario.funcionario_id == funcionario.id,
                    EscalaFuncionario.data_inicio <= data_str,
                    EscalaFuncionario.ativo == True,
                ).all()
                for ef in esc_funcs:
                    if ef.data_fim and ef.data_fim < data_str:
                        continue
                    esc = db.session.get(Escala, ef.escala_id)
                    if not esc:
                        continue
                    try:
                        ciclo = json.loads(esc.ciclo_json or "{}")
                        dias_ciclo = len(ciclo.get("dias", []))
                        if dias_ciclo > 0:
                            data_inicio_obj = datetime.strptime(ef.data_inicio, "%Y-%m-%d").date()
                            indice = (data_obj - data_inicio_obj).days % dias_ciclo
                            return esc.carga_horaria_min_dia(indice)
                    except Exception:
                        pass
        except Exception:
            pass

        # 2) JornadaTrabalho por dia da semana
        if JornadaTrabalho and getattr(funcionario, "jornada_id", None):
            try:
                j = db.session.get(JornadaTrabalho, funcionario.jornada_id)
                if j:
                    return j.minutos_esperados_weekday(data_ref.weekday())
            except Exception:
                pass

        # 3) Fallback texto legado + regra fim de semana
        if data_ref.weekday() >= 5:
            return 0
        return _ponto_min_esperado_jornada(funcionario)

    def _ponto_competencia_bounds(competencia):
        comp = (competencia or "").strip()
        if not re.match(r"^\d{4}-\d{2}$", comp):
            return None, None
        ano, mes = comp.split("-")
        ano_i = int(ano)
        mes_i = int(mes)
        if ano_i < 2000 or ano_i > 2100 or mes_i < 1 or mes_i > 12:
            return None, None
        inicio = date(ano_i, mes_i, 1)
        proximo = date(ano_i + 1, 1, 1) if mes_i == 12 else date(ano_i, mes_i + 1, 1)
        fim = proximo - timedelta(days=1)
        return inicio, fim

    def _ponto_fmt_minutos(total, signed=False):
        try:
            minutos = int(total or 0)
        except Exception:
            minutos = 0
        sinal = ""
        if signed and minutos < 0:
            sinal = "-"
        minutos = abs(minutos)
        return f"{sinal}{minutos // 60:02d}:{minutos % 60:02d}"

    def _ponto_marcacoes_dia(funcionario_id, data_ref):
        inicio = datetime.combine(data_ref, datetime.min.time())
        fim = inicio + timedelta(days=1)
        return (
            PontoMarcacao.query.filter(PontoMarcacao.funcionario_id == funcionario_id)
            .filter(PontoMarcacao.data_hora >= inicio)
            .filter(PontoMarcacao.data_hora < fim)
            .order_by(PontoMarcacao.data_hora.asc(), PontoMarcacao.id.asc())
            .all()
        )

    def _calc_intersec_noturno(ini_dt, fim_dt):
        """Minutos trabalhados no período noturno 22:00-05:00 para um trecho de trabalho."""
        if not ini_dt or not fim_dt or fim_dt <= ini_dt:
            return 0
        ini_m = ini_dt.hour * 60 + ini_dt.minute
        fim_m = fim_dt.hour * 60 + fim_dt.minute
        total = 0
        if fim_m <= ini_m:
            fim_m += 1440  # turno cruza meia-noite
        # Segmento B: 22:00-00:00 → [1320, 1440]
        total += max(0, min(fim_m, 1440) - max(ini_m, 1320))
        # Segmento A: 00:00-05:00 → [0, 300]; só se o turno cruzou meia-noite
        if fim_m > 1440:
            total += max(0, min(fim_m - 1440, 300))
        # Turno que começa e termina dentro de 00:00-05:00 sem cruzar meia-noite
        elif ini_m < 300:
            total += max(0, min(fim_m, 300) - ini_m)
        return max(0, total)

    def _calc_noturno_min_marcacoes(marcacoes):
        """Total de minutos de adicional noturno (22:00-05:00) a partir das marcações."""
        total = 0
        aberta_em = None
        for m in marcacoes:
            if not getattr(m, "data_hora", None):
                continue
            if m.tipo in ("entrada", "retorno_intervalo"):
                aberta_em = m.data_hora
            elif m.tipo in ("saida_intervalo", "saida") and aberta_em:
                total += _calc_intersec_noturno(aberta_em, m.data_hora)
                aberta_em = None
        return total

    def _calc_intrajornada_min(marcacoes):
        """Total de minutos de intervalo intrajornada efetivamente gozado."""
        total = 0
        saida_int_em = None
        for m in marcacoes:
            if not getattr(m, "data_hora", None):
                continue
            if m.tipo == "saida_intervalo":
                saida_int_em = m.data_hora
            elif m.tipo == "retorno_intervalo" and saida_int_em:
                total += max(0, int((m.data_hora - saida_int_em).total_seconds() / 60))
                saida_int_em = None
        return total

    def _feriados_para_data(data_ref):
        """Carrega o set de feriados do mês de data_ref para uso nos endpoints por-dia."""
        try:
            inicio_mes = data_ref.replace(day=1)
            if data_ref.month == 12:
                fim_mes = data_ref.replace(day=31)
            else:
                fim_mes = (data_ref.replace(month=data_ref.month + 1, day=1) - timedelta(days=1))
            rows = Feriado.query.filter(
                Feriado.data >= inicio_mes.strftime("%Y-%m-%d"),
                Feriado.data <= fim_mes.strftime("%Y-%m-%d"),
            ).all()
            return {
                datetime.strptime(f.data, "%Y-%m-%d").date()
                for f in rows if f.data
            }
        except Exception:
            return set()

    def _ponto_resumo_func_dia(funcionario, data_ref, _marcacoes=None, _feriados=None):
        marcacoes = (
            _marcacoes
            if _marcacoes is not None
            else _ponto_marcacoes_dia(funcionario.id, data_ref)
        )
        inconsistencias = []
        esperado = "entrada"
        segundos_total = 0
        aberta_em = None
        for marcacao in marcacoes:
            if not getattr(marcacao, "data_hora", None):
                inconsistencias.append(
                    "Marcação sem data/hora válida foi ignorada no cálculo."
                )
                esperado = _ponto_next_tipo(marcacao.tipo)
                continue
            if marcacao.tipo != esperado:
                inconsistencias.append(
                    f"Sequência inesperada: recebido {_ponto_label(marcacao.tipo)}; esperado {_ponto_label(esperado)}."
                )
            if marcacao.tipo == "entrada":
                if aberta_em is not None:
                    inconsistencias.append(
                        "Existe uma entrada sem fechamento antes desta nova entrada."
                    )
                aberta_em = marcacao.data_hora
            elif marcacao.tipo == "saida_intervalo":
                if aberta_em is None:
                    inconsistencias.append("Saída para intervalo sem entrada anterior.")
                else:
                    segundos_total += max(
                        0, int((marcacao.data_hora - aberta_em).total_seconds())
                    )
                    aberta_em = None
            elif marcacao.tipo == "retorno_intervalo":
                if aberta_em is not None:
                    inconsistencias.append("Retorno de intervalo sem saída anterior.")
                aberta_em = marcacao.data_hora
            elif marcacao.tipo == "saida":
                if aberta_em is None:
                    inconsistencias.append("Saída final sem entrada anterior.")
                else:
                    segundos_total += max(
                        0, int((marcacao.data_hora - aberta_em).total_seconds())
                    )
                    aberta_em = None
            esperado = _ponto_next_tipo(marcacao.tipo)
        if aberta_em is not None:
            inconsistencias.append("Jornada em aberto (faltou batida de fechamento).")
        minutos_trabalhados = int(round(segundos_total / 60.0))
        minutos_esperados = _ponto_min_esperado_data(funcionario, data_ref)
        saldo = minutos_trabalhados - minutos_esperados
        # ── Horas extras 50% e 100% ──────────────────────────────────────────
        # Dom (weekday==6) ou feriado → tudo a 100%; demais dias → 50% até 2h, 100% além
        _is_feriado = data_ref in (_feriados or set())
        if saldo > 0:
            if data_ref.weekday() >= 5 or _is_feriado:
                he_50_min = 0
                he_100_min = saldo
            else:
                he_50_min = min(saldo, 120)
                he_100_min = max(0, saldo - 120)
        else:
            he_50_min = 0
            he_100_min = 0
        # ── Adicional noturno e intrajornada ─────────────────────────────────
        noturno_min = _calc_noturno_min_marcacoes(marcacoes)
        intrajornada_min = _calc_intrajornada_min(marcacoes)
        return {
            "funcionario_id": funcionario.id,
            "funcionario_nome": funcionario.nome,
            "data_ref": data_ref.strftime("%Y-%m-%d"),
            "marcacoes": [marcacao.to_dict() for marcacao in marcacoes],
            "proximo_tipo": _ponto_tipo_esperado(marcacoes),
            "proximo_tipo_label": _ponto_label(_ponto_tipo_esperado(marcacoes)),
            "horas_trabalhadas_min": minutos_trabalhados,
            "horas_trabalhadas_fmt": _ponto_fmt_minutos(minutos_trabalhados),
            "horas_esperadas_min": minutos_esperados,
            "horas_esperadas_fmt": _ponto_fmt_minutos(minutos_esperados),
            "saldo_min": saldo,
            "saldo_fmt": _ponto_fmt_minutos(saldo, signed=True),
            "he_50_min": he_50_min,
            "he_50_fmt": _ponto_fmt_minutos(he_50_min),
            "he_100_min": he_100_min,
            "he_100_fmt": _ponto_fmt_minutos(he_100_min),
            "noturno_min": noturno_min,
            "noturno_fmt": _ponto_fmt_minutos(noturno_min),
            "intrajornada_min": intrajornada_min,
            "intrajornada_fmt": _ponto_fmt_minutos(intrajornada_min),
            "status": "ok" if not inconsistencias else "inconsistente",
            "inconsistencias": inconsistencias,
        }

    def _ponto_resumo_competencia(funcionario, competencia):
        inicio, fim = _ponto_competencia_bounds(competencia)
        if not inicio:
            return None
        # Carregar feriados do período para calcular HE 100% correto
        try:
            feriados_rows = Feriado.query.filter(
                Feriado.data >= inicio.strftime("%Y-%m-%d"),
                Feriado.data <= fim.strftime("%Y-%m-%d"),
            ).all()
            _feriados_set = {
                datetime.strptime(f.data, "%Y-%m-%d").date()
                for f in feriados_rows
                if f.data
            }
        except Exception:
            _feriados_set = set()
        dias = []
        total_trabalhado = 0
        total_esperado = 0
        total_saldo = 0
        total_he_50 = 0
        total_he_100 = 0
        total_noturno = 0
        total_intrajornada = 0
        inconsistencias = 0
        # Batch load: 1 query para todo o período da competência
        inicio_dt = datetime.combine(inicio, datetime.min.time())
        fim_dt = datetime.combine(fim + timedelta(days=1), datetime.min.time())
        todas_marc_comp = (
            PontoMarcacao.query.filter(
                PontoMarcacao.funcionario_id == funcionario.id,
                PontoMarcacao.data_hora >= inicio_dt,
                PontoMarcacao.data_hora < fim_dt,
            )
            .order_by(PontoMarcacao.data_hora.asc(), PontoMarcacao.id.asc())
            .all()
        )
        marc_por_data_comp = {}
        for _mc in todas_marc_comp:
            _dc = _mc.data_hora.date()
            marc_por_data_comp.setdefault(_dc, []).append(_mc)
        dia = inicio
        while dia <= fim:
            resumo = _ponto_resumo_func_dia(
                funcionario, dia, _marcacoes=marc_por_data_comp.get(dia, []),
                _feriados=_feriados_set
            )
            dias.append(
                {
                    "data_ref": resumo["data_ref"],
                    "horas_trabalhadas_fmt": resumo["horas_trabalhadas_fmt"],
                    "horas_esperadas_fmt": resumo["horas_esperadas_fmt"],
                    "saldo_fmt": resumo["saldo_fmt"],
                    "he_50_min": resumo["he_50_min"],
                    "he_50_fmt": resumo["he_50_fmt"],
                    "he_100_min": resumo["he_100_min"],
                    "he_100_fmt": resumo["he_100_fmt"],
                    "noturno_min": resumo["noturno_min"],
                    "noturno_fmt": resumo["noturno_fmt"],
                    "intrajornada_min": resumo["intrajornada_min"],
                    "intrajornada_fmt": resumo["intrajornada_fmt"],
                    "status": resumo["status"],
                    "marcacoes_count": len(resumo["marcacoes"]),
                    "inconsistencias": resumo["inconsistencias"],
                }
            )
            total_trabalhado += resumo["horas_trabalhadas_min"]
            total_esperado += resumo["horas_esperadas_min"]
            total_saldo += resumo["saldo_min"]
            total_he_50 += resumo["he_50_min"]
            total_he_100 += resumo["he_100_min"]
            total_noturno += resumo["noturno_min"]
            total_intrajornada += resumo["intrajornada_min"]
            if resumo["status"] != "ok":
                inconsistencias += 1
            dia += timedelta(days=1)
        return {
            "funcionario_id": funcionario.id,
            "funcionario_nome": funcionario.nome,
            "competencia": competencia,
            "dias": dias,
            "totais": {
                "horas_trabalhadas_min": total_trabalhado,
                "horas_trabalhadas_fmt": _ponto_fmt_minutos(total_trabalhado),
                "horas_esperadas_min": total_esperado,
                "horas_esperadas_fmt": _ponto_fmt_minutos(total_esperado),
                "saldo_min": total_saldo,
                "saldo_fmt": _ponto_fmt_minutos(total_saldo, signed=True),
                "he_50_min": total_he_50,
                "he_50_fmt": _ponto_fmt_minutos(total_he_50),
                "he_100_min": total_he_100,
                "he_100_fmt": _ponto_fmt_minutos(total_he_100),
                "noturno_min": total_noturno,
                "noturno_fmt": _ponto_fmt_minutos(total_noturno),
                "intrajornada_min": total_intrajornada,
                "intrajornada_fmt": _ponto_fmt_minutos(total_intrajornada),
                "inconsistencias": inconsistencias,
                "dias": len(dias),
            },
        }

    @app.route("/api/ponto/marcar", methods=["POST"])
    @lr
    def api_ponto_marcar():
        dados = request.json or {}
        funcionario_id = to_num(dados.get("funcionario_id"))
        if not funcionario_id:
            return jsonify(
                {"erro": "Selecione o funcionário para registrar ponto."}
            ), 400
        funcionario = Funcionario.query.get(funcionario_id)
        if not funcionario:
            return jsonify({"erro": "Funcionário não encontrado."}), 404
        if (funcionario.status or "").strip().lower() != "ativo":
            return jsonify(
                {"erro": "Somente funcionários ativos podem registrar ponto."}
            ), 400
        data_hora = _ponto_parse_data_hora(dados.get("data_hora")) or utcnow()
        if data_hora > (utcnow() + timedelta(minutes=1)):
            return jsonify(
                {"erro": "Não é permitido registrar ponto em horário futuro."}
            ), 400
        data_ref = data_hora.date()
        marcacoes_dia = _ponto_marcacoes_dia(funcionario.id, data_ref)
        tipo = (dados.get("tipo") or "").strip().lower() or _ponto_tipo_esperado(
            marcacoes_dia
        )
        if tipo not in ponto_tipos:
            return jsonify({"erro": "Tipo de marcação inválido."}), 400
        esperado = _ponto_tipo_esperado(marcacoes_dia)
        if tipo != esperado:
            return jsonify(
                {
                    "erro": f"Ordem de marcação inválida. Agora é esperado: {_ponto_label(esperado)}."
                }
            ), 400
        if any(
            abs((data_hora - marcacao.data_hora).total_seconds()) < 60
            for marcacao in marcacoes_dia
        ):
            return jsonify(
                {"erro": "Já existe marcação neste minuto para este funcionário."}
            ), 400
        origem = (dados.get("origem") or "web").strip().lower()
        if origem not in ("web", "admin", "importacao"):
            origem = "web"
        observacao = (dados.get("observacao") or "").strip()[:500]
        ip = (
            (request.headers.get("X-Forwarded-For", "") or request.remote_addr or "")
            .split(",")[0]
            .strip()[:60]
        )
        marcacao = PontoMarcacao(
            funcionario_id=funcionario.id,
            tipo=tipo,
            data_hora=data_hora,
            origem=origem,
            observacao=observacao,
            criado_por=session.get("nome", ""),
            ip=ip,
        )
        db.session.add(marcacao)
        db.session.commit()
        audit_event(
            "ponto_marcacao",
            "usuario",
            session.get("uid"),
            "funcionario",
            funcionario.id,
            True,
            {"tipo": tipo, "data_ref": data_ref.strftime("%Y-%m-%d"), "origem": origem},
        )
        return jsonify(
            {
                "ok": True,
                "marcacao": marcacao.to_dict(),
                "resumo": _ponto_resumo_func_dia(
                    funcionario, data_ref,
                    _feriados=_feriados_para_data(data_ref),
                ),
            }
        )

    @app.route("/api/ponto/dia")
    @lr
    def api_ponto_dia():
        funcionario_id = to_num(request.args.get("funcionario_id"))
        if not funcionario_id:
            return jsonify({"erro": "funcionario_id é obrigatório."}), 400
        funcionario = Funcionario.query.get(funcionario_id)
        if not funcionario:
            return jsonify({"erro": "Funcionário não encontrado."}), 404
        data_ref = _ponto_parse_data_ref(request.args.get("data"))
        return jsonify(
            {"ok": True, "resumo": _ponto_resumo_func_dia(
                funcionario, data_ref,
                _feriados=_feriados_para_data(data_ref),
            )}
        )

    @app.route("/api/ponto/resumo-dia")
    @lr
    def api_ponto_resumo_dia():
        try:
            data_ref = _ponto_parse_data_ref(request.args.get("data"))
            empresa_id = to_num(request.args.get("empresa_id"))
            query = Funcionario.query.filter(Funcionario.status == "Ativo")
            if empresa_id:
                query = query.filter(Funcionario.empresa_id == empresa_id)
            funcionarios = query.order_by(Funcionario.nome).all()
            # Batch load: 1 query para TODAS as marcações do dia (elimina N+1)
            ids_ativos = [f.id for f in funcionarios]
            if ids_ativos:
                inicio_dia = datetime.combine(data_ref, datetime.min.time())
                fim_dia = inicio_dia + timedelta(days=1)
                todas_marc_dia = (
                    PontoMarcacao.query.filter(
                        PontoMarcacao.funcionario_id.in_(ids_ativos),
                        PontoMarcacao.data_hora >= inicio_dia,
                        PontoMarcacao.data_hora < fim_dia,
                    )
                    .order_by(PontoMarcacao.data_hora.asc(), PontoMarcacao.id.asc())
                    .all()
                )
            else:
                todas_marc_dia = []
            marc_batch = {}
            for _mb in todas_marc_dia:
                marc_batch.setdefault(_mb.funcionario_id, []).append(_mb)
            _feriados_dia = _feriados_para_data(data_ref)
            itens = []
            total_ok = 0
            total_inconsistente = 0
            erros = []
            for funcionario in funcionarios:
                try:
                    resumo = _ponto_resumo_func_dia(
                        funcionario,
                        data_ref,
                        _marcacoes=marc_batch.get(funcionario.id, []),
                        _feriados=_feriados_dia,
                    )
                    if resumo["status"] == "ok":
                        total_ok += 1
                    else:
                        total_inconsistente += 1
                    itens.append(
                        {
                            "funcionario_id": funcionario.id,
                            "funcionario_nome": funcionario.nome,
                            "empresa_id": funcionario.empresa_id,
                            "proximo_tipo_label": resumo["proximo_tipo_label"],
                            "horas_trabalhadas_fmt": resumo["horas_trabalhadas_fmt"],
                            "horas_esperadas_fmt": resumo["horas_esperadas_fmt"],
                            "saldo_fmt": resumo["saldo_fmt"],
                            "status": resumo["status"],
                            "inconsistencias": resumo["inconsistencias"],
                            "marcacoes_count": len(resumo["marcacoes"]),
                        }
                    )
                except Exception as exc:
                    total_inconsistente += 1
                    erros.append(
                        {
                            "funcionario_id": funcionario.id,
                            "nome": funcionario.nome,
                            "erro": str(exc)[:180],
                        }
                    )
                    itens.append(
                        {
                            "funcionario_id": funcionario.id,
                            "funcionario_nome": funcionario.nome,
                            "empresa_id": funcionario.empresa_id,
                            "proximo_tipo_label": "Entrada",
                            "horas_trabalhadas_fmt": "00:00",
                            "horas_esperadas_fmt": "00:00",
                            "saldo_fmt": "00:00",
                            "status": "inconsistente",
                            "inconsistencias": [
                                "Falha ao processar marcações deste colaborador."
                            ],
                            "marcacoes_count": 0,
                        }
                    )
            return jsonify(
                {
                    "ok": True,
                    "data_ref": data_ref.strftime("%Y-%m-%d"),
                    "itens": itens,
                    "totais": {
                        "funcionarios": len(itens),
                        "ok": total_ok,
                        "inconsistentes": total_inconsistente,
                    },
                    "erros_processamento": erros[:10],
                }
            )
        except Exception as exc:
            app.logger.exception("Falha no resumo diário de ponto")
            return jsonify(
                {
                    "erro": "Falha ao carregar painel de ponto.",
                    "detalhe": str(exc)[:220],
                }
            ), 500

    @app.route("/api/ponto/ajuste", methods=["POST"])
    @lr
    def api_ponto_ajuste():
        dados = request.json or {}
        funcionario_id = to_num(dados.get("funcionario_id"))
        tipo = (dados.get("tipo") or "").strip().lower()
        motivo = (dados.get("motivo") or "").strip()
        data_hora = _ponto_parse_data_hora(dados.get("data_hora"))
        if not funcionario_id:
            return jsonify({"erro": "funcionario_id é obrigatório."}), 400
        if tipo not in ponto_tipos:
            return jsonify({"erro": "Tipo de marcação inválido para ajuste."}), 400
        if not data_hora:
            return jsonify({"erro": "data_hora inválida para ajuste."}), 400
        if data_hora > (utcnow() + timedelta(minutes=1)):
            return jsonify({"erro": "Não é permitido ajuste em horário futuro."}), 400
        if not motivo:
            return jsonify({"erro": "Informe o motivo do ajuste."}), 400

        for tentativa in range(2):
            try:
                funcionario = Funcionario.query.get(funcionario_id)
                if not funcionario:
                    return jsonify({"erro": "Funcionário não encontrado."}), 404
                data_ref = data_hora.date()
                antes = [
                    marcacao.to_dict()
                    for marcacao in _ponto_marcacoes_dia(funcionario_id, data_ref)
                ]
                nova = PontoMarcacao(
                    funcionario_id=funcionario_id,
                    tipo=tipo,
                    data_hora=data_hora,
                    origem="admin",
                    observacao=(dados.get("observacao") or "").strip()[:500],
                    criado_por=session.get("nome", ""),
                    ip=(
                        request.headers.get("X-Forwarded-For", "")
                        or request.remote_addr
                        or ""
                    )
                    .split(",")[0]
                    .strip()[:60],
                )
                db.session.add(nova)
                db.session.flush()
                depois = [
                    marcacao.to_dict()
                    for marcacao in _ponto_marcacoes_dia(funcionario_id, data_ref)
                ]
                ajuste = PontoAjuste(
                    funcionario_id=funcionario_id,
                    data_ref=data_ref.strftime("%Y-%m-%d"),
                    motivo=motivo,
                    antes_json=json.dumps(antes, ensure_ascii=False),
                    depois_json=json.dumps(depois, ensure_ascii=False),
                    criado_por=session.get("nome", ""),
                )
                db.session.add(ajuste)
                db.session.commit()
                audit_event(
                    "ponto_ajuste",
                    "usuario",
                    session.get("uid"),
                    "funcionario",
                    funcionario.id,
                    True,
                    {
                        "data_ref": data_ref.strftime("%Y-%m-%d"),
                        "tipo": tipo,
                        "motivo": motivo[:200],
                    },
                )
                return jsonify(
                    {
                        "ok": True,
                        "ajuste": ajuste.to_dict(),
                        "resumo": _ponto_resumo_func_dia(
                            funcionario, data_ref,
                            _feriados=_feriados_para_data(data_ref),
                        ),
                    }
                )
            except Exception as exc:
                db.session.rollback()
                mensagem = str(exc).lower()
                if (
                    tentativa == 0
                    and "no such table" in mensagem
                    and ("ponto_marcacao" in mensagem or "ponto_ajuste" in mensagem)
                ):
                    db.create_all()
                    continue
                app.logger.exception("Falha ao aplicar ajuste de ponto")
                return jsonify(
                    {
                        "erro": "Falha ao aplicar ajuste de ponto.",
                        "detalhe": str(exc)[:220],
                    }
                ), 500

    @app.route("/api/ponto/marcacao/<int:id>", methods=["DELETE"])
    @lr
    def api_ponto_excluir_marcacao(id):
        dados = request.json or {}
        motivo = (dados.get("motivo") or "").strip()
        if not motivo:
            return jsonify({"erro": "Informe o motivo da exclusão."}), 400
        marcacao = PontoMarcacao.query.get(id)
        if not marcacao:
            return jsonify({"erro": "Marcação não encontrada."}), 404
        funcionario = Funcionario.query.get(marcacao.funcionario_id)
        if not funcionario:
            return jsonify({"erro": "Funcionário da marcação não encontrado."}), 404
        data_ref = marcacao.data_hora.date() if marcacao.data_hora else None
        snap_antes = (
            [m.to_dict() for m in _ponto_marcacoes_dia(funcionario.id, data_ref)]
            if data_ref
            else []
        )
        try:
            antes = {
                "marcacao_id": marcacao.id,
                "marcacao": marcacao.to_dict(),
                "dia": snap_antes,
            }
            db.session.delete(marcacao)
            db.session.flush()
            snap_depois = (
                [m.to_dict() for m in _ponto_marcacoes_dia(funcionario.id, data_ref)]
                if data_ref
                else []
            )
            ajuste = PontoAjuste(
                funcionario_id=funcionario.id,
                data_ref=data_ref.strftime("%Y-%m-%d") if data_ref else "",
                motivo=motivo,
                antes_json=json.dumps(antes, ensure_ascii=False, default=str),
                depois_json=json.dumps(
                    {"dia": snap_depois}, ensure_ascii=False, default=str
                ),
                criado_por=session.get("nome", ""),
            )
            db.session.add(ajuste)
            db.session.commit()
            audit_event(
                "ponto_exclusao_marcacao",
                "usuario",
                session.get("uid"),
                "funcionario",
                funcionario.id,
                True,
                {
                    "marcacao_id": id,
                    "data_ref": data_ref.strftime("%Y-%m-%d") if data_ref else "",
                    "motivo": motivo[:200],
                },
            )
            resumo = _ponto_resumo_func_dia(
                funcionario, data_ref,
                _feriados=_feriados_para_data(data_ref),
            ) if data_ref else {}
            return jsonify({"ok": True, "resumo": resumo})
        except Exception as exc:
            db.session.rollback()
            app.logger.exception("Falha ao excluir marcação de ponto")
            return jsonify(
                {
                    "erro": "Falha ao excluir marcação de ponto.",
                    "detalhe": str(exc)[:220],
                }
            ), 500

    @app.route("/api/ponto/marcacao/<int:id>", methods=["PUT"])
    @lr
    def api_ponto_editar_marcacao(id):
        dados = request.json or {}
        marcacao = PontoMarcacao.query.get(id)
        if not marcacao:
            return jsonify({"erro": "Marcação não encontrada."}), 404
        funcionario = Funcionario.query.get(marcacao.funcionario_id)
        if not funcionario:
            return jsonify({"erro": "Funcionário da marcação não encontrado."}), 404

        tipo = (dados.get("tipo") or marcacao.tipo or "").strip().lower()
        data_hora = _ponto_parse_data_hora(dados.get("data_hora")) or marcacao.data_hora
        motivo = (dados.get("motivo") or "").strip()
        observacao = (
            dados.get("observacao") if "observacao" in dados else marcacao.observacao
        ) or ""
        observacao = observacao.strip()[:500]

        if tipo not in ponto_tipos:
            return jsonify({"erro": "Tipo de marcação inválido para edição."}), 400
        if not data_hora:
            return jsonify({"erro": "Data/hora inválida para edição."}), 400
        if data_hora > (utcnow() + timedelta(minutes=1)):
            return jsonify(
                {"erro": "Não é permitido editar marcação para horário futuro."}
            ), 400
        if not motivo:
            return jsonify({"erro": "Informe o motivo da edição da marcação."}), 400

        data_ant = marcacao.data_hora.date() if marcacao.data_hora else data_hora.date()
        data_nova = data_hora.date()

        def _snap(data_ref):
            return [
                item.to_dict()
                for item in _ponto_marcacoes_dia(funcionario.id, data_ref)
            ]

        antes = {
            "marcacao_id": marcacao.id,
            "marcacao": marcacao.to_dict(),
            "dia_anterior": _snap(data_ant),
        }
        if data_nova != data_ant:
            antes["dia_novo_antes"] = _snap(data_nova)

        inicio = datetime.combine(data_nova, datetime.min.time())
        fim = inicio + timedelta(days=1)
        existentes = (
            PontoMarcacao.query.filter(PontoMarcacao.funcionario_id == funcionario.id)
            .filter(PontoMarcacao.id != marcacao.id)
            .filter(PontoMarcacao.data_hora >= inicio)
            .filter(PontoMarcacao.data_hora < fim)
            .all()
        )
        if any(
            abs((data_hora - item.data_hora).total_seconds()) < 60
            for item in existentes
            if getattr(item, "data_hora", None)
        ):
            return jsonify(
                {"erro": "Já existe outra marcação neste minuto para este funcionário."}
            ), 400

        try:
            marcacao.tipo = tipo
            marcacao.data_hora = data_hora
            marcacao.observacao = observacao
            marcacao.origem = "admin"
            marcacao.criado_por = session.get("nome", "")
            marcacao.ip = (
                (
                    request.headers.get("X-Forwarded-For", "")
                    or request.remote_addr
                    or ""
                )
                .split(",")[0]
                .strip()[:60]
            )
            db.session.flush()

            depois = {
                "marcacao_id": marcacao.id,
                "marcacao": marcacao.to_dict(),
                "dia_novo": _snap(data_nova),
            }
            if data_nova != data_ant:
                depois["dia_anterior_depois"] = _snap(data_ant)

            ajuste = PontoAjuste(
                funcionario_id=funcionario.id,
                data_ref=data_nova.strftime("%Y-%m-%d"),
                motivo=motivo,
                antes_json=json.dumps(antes, ensure_ascii=False, default=str),
                depois_json=json.dumps(depois, ensure_ascii=False, default=str),
                criado_por=session.get("nome", ""),
            )
            db.session.add(ajuste)
            db.session.commit()
            audit_event(
                "ponto_edicao_marcacao",
                "usuario",
                session.get("uid"),
                "funcionario",
                funcionario.id,
                True,
                {
                    "marcacao_id": marcacao.id,
                    "data_ref": data_nova.strftime("%Y-%m-%d"),
                    "tipo": tipo,
                    "motivo": motivo[:200],
                },
            )
            return jsonify(
                {
                    "ok": True,
                    "marcacao": marcacao.to_dict(),
                    "resumo": _ponto_resumo_func_dia(
                        funcionario, data_nova,
                        _feriados=_feriados_para_data(data_nova),
                    ),
                    "resumo_dia_anterior": _ponto_resumo_func_dia(
                        funcionario, data_ant,
                        _feriados=_feriados_para_data(data_ant),
                    )
                    if data_ant != data_nova
                    else None,
                }
            )
        except Exception as exc:
            db.session.rollback()
            app.logger.exception("Falha ao editar marcação de ponto")
            return jsonify(
                {
                    "erro": "Falha ao editar marcação de ponto.",
                    "detalhe": str(exc)[:220],
                }
            ), 500

    @app.route("/api/ponto/marcacao", methods=["POST"])
    @lr
    def api_ponto_criar_marcacao():
        """Criar nova marcação de ponto via painel admin (sem checar ordem esperada)."""
        dados = request.json or {}
        funcionario_id = to_num(dados.get("funcionario_id"))
        if not funcionario_id:
            return jsonify({"erro": "funcionario_id é obrigatório."}), 400
        funcionario = Funcionario.query.get(funcionario_id)
        if not funcionario:
            return jsonify({"erro": "Funcionário não encontrado."}), 404

        tipo = (dados.get("tipo") or "").strip().lower()
        if tipo not in ponto_tipos:
            return jsonify({"erro": "Tipo de marcação inválido."}), 400

        data_hora = _ponto_parse_data_hora(dados.get("data_hora"))
        if not data_hora:
            return jsonify({"erro": "Data/hora inválida."}), 400

        motivo = (dados.get("motivo") or "").strip()
        if not motivo:
            return jsonify({"erro": "Informe o motivo da inclusão da marcação."}), 400

        observacao = (dados.get("observacao") or "").strip()[:500]
        data_ref = data_hora.date()

        marcacoes_dia_antes = [
            m.to_dict() for m in _ponto_marcacoes_dia(funcionario.id, data_ref)
        ]
        conflito = PontoMarcacao.query.filter(
            PontoMarcacao.funcionario_id == funcionario.id,
            PontoMarcacao.data_hora >= datetime.combine(data_ref, datetime.min.time()),
            PontoMarcacao.data_hora
            < datetime.combine(data_ref, datetime.min.time()) + timedelta(days=1),
        ).all()
        if any(
            abs((data_hora - m.data_hora).total_seconds()) < 60
            for m in conflito
            if m.data_hora
        ):
            return jsonify(
                {"erro": "Já existe marcação neste minuto para este funcionário."}
            ), 400

        try:
            ip = (
                (
                    request.headers.get("X-Forwarded-For", "")
                    or request.remote_addr
                    or ""
                )
                .split(",")[0]
                .strip()[:60]
            )
            marcacao = PontoMarcacao(
                funcionario_id=funcionario.id,
                tipo=tipo,
                data_hora=data_hora,
                origem="admin",
                observacao=observacao,
                criado_por=session.get("nome", ""),
                ip=ip,
            )
            db.session.add(marcacao)
            db.session.flush()
            marcacoes_dia_depois = [
                m.to_dict() for m in _ponto_marcacoes_dia(funcionario.id, data_ref)
            ]
            ajuste = PontoAjuste(
                funcionario_id=funcionario.id,
                data_ref=data_ref.strftime("%Y-%m-%d"),
                motivo=motivo,
                antes_json=json.dumps(
                    {"dia": marcacoes_dia_antes}, ensure_ascii=False, default=str
                ),
                depois_json=json.dumps(
                    {"dia": marcacoes_dia_depois}, ensure_ascii=False, default=str
                ),
                criado_por=session.get("nome", ""),
            )
            db.session.add(ajuste)
            db.session.commit()
            audit_event(
                "ponto_nova_marcacao_admin",
                "usuario",
                session.get("uid"),
                "funcionario",
                funcionario.id,
                True,
                {
                    "marcacao_id": marcacao.id,
                    "tipo": tipo,
                    "data_ref": data_ref.strftime("%Y-%m-%d"),
                    "motivo": motivo[:200],
                },
            )
            return jsonify({"ok": True, "marcacao": marcacao.to_dict()})
        except Exception as exc:
            db.session.rollback()
            app.logger.exception("Falha ao criar marcação de ponto via admin")
            return jsonify(
                {"erro": "Falha ao criar marcação.", "detalhe": str(exc)[:220]}
            ), 500

    @app.route("/api/ponto/fechar-dia", methods=["POST"])
    @lr
    def api_ponto_fechar_dia():
        dados = request.json or {}
        funcionario_id = to_num(dados.get("funcionario_id"))
        if not funcionario_id:
            return jsonify({"erro": "funcionario_id é obrigatório."}), 400
        funcionario = Funcionario.query.get(funcionario_id)
        if not funcionario:
            return jsonify({"erro": "Funcionário não encontrado."}), 404
        data_ref = _ponto_parse_data_ref(dados.get("data"))
        forcar = bool(dados.get("forcar"))
        observacao = (dados.get("observacao") or "").strip()[:1000]
        resumo = _ponto_resumo_func_dia(
            funcionario, data_ref,
            _feriados=_feriados_para_data(data_ref),
        )
        if resumo["status"] != "ok" and not forcar:
            return jsonify(
                {
                    "erro": "O dia possui inconsistências. Revise as marcações ou feche com ressalvas (forcar=true).",
                    "resumo": resumo,
                }
            ), 400
        data_ref_str = data_ref.strftime("%Y-%m-%d")
        fechamento = PontoFechamentoDia.query.filter_by(
            funcionario_id=funcionario.id, data_ref=data_ref_str
        ).first()
        if not fechamento:
            fechamento = PontoFechamentoDia(
                funcionario_id=funcionario.id, data_ref=data_ref_str
            )
            db.session.add(fechamento)
        fechamento.status = (
            "fechado" if resumo["status"] == "ok" else "fechado_com_ressalvas"
        )
        fechamento.observacao = observacao
        fechamento.resumo_json = json.dumps(resumo, ensure_ascii=False)
        fechamento.fechado_por = session.get("nome", "")
        fechamento.fechado_em = utcnow()
        db.session.commit()
        audit_event(
            "ponto_fechamento_dia",
            "usuario",
            session.get("uid"),
            "funcionario",
            funcionario.id,
            True,
            {"data_ref": data_ref_str, "status": fechamento.status},
        )
        return jsonify(
            {"ok": True, "fechamento": fechamento.to_dict(), "resumo": resumo}
        )

    @app.route("/api/ponto/fechamentos-dia")
    @lr
    def api_ponto_fechamentos_dia():
        data_ref = _ponto_parse_data_ref(request.args.get("data"))
        data_ref_str = data_ref.strftime("%Y-%m-%d")
        itens = (
            PontoFechamentoDia.query.filter_by(data_ref=data_ref_str)
            .order_by(PontoFechamentoDia.fechado_em.desc())
            .all()
        )
        return jsonify(
            {
                "ok": True,
                "data_ref": data_ref_str,
                "itens": [item.to_dict() for item in itens],
            }
        )

    @app.route("/api/ponto/espelho-mensal")
    @lr
    def api_ponto_espelho_mensal():
        funcionario_id = to_num(request.args.get("funcionario_id"))
        competencia = (request.args.get("competencia") or "").strip()
        if not funcionario_id:
            return jsonify({"erro": "funcionario_id é obrigatório."}), 400
        if not re.match(r"^\d{4}-\d{2}$", competencia):
            return jsonify({"erro": "competencia inválida. Use YYYY-MM."}), 400

        funcionario = Funcionario.query.get(funcionario_id)
        if not funcionario:
            return jsonify({"erro": "Funcionário não encontrado."}), 404

        resumo_comp = _ponto_resumo_competencia(funcionario, competencia)
        if not resumo_comp:
            return jsonify(
                {
                    "erro": "Não foi possível gerar o espelho para a competência informada."
                }
            ), 400

        try:
            from reportlab.lib import colors
            from reportlab.lib.enums import TA_CENTER
            from reportlab.lib.pagesizes import A4
            from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
            from reportlab.lib.units import mm
            from reportlab.platypus import (
                Image,
                Paragraph,
                SimpleDocTemplate,
                Spacer,
                Table,
                TableStyle,
            )
        except Exception:
            return jsonify(
                {"erro": "Dependência ReportLab não disponível para gerar o espelho."}
            ), 500

        def p(txt, sty, html=False):
            texto = str(txt or "")
            if not html:
                texto = (
                    texto.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                )
            return Paragraph(texto, sty)

        def hhmm_from_dt(dt):
            return dt.strftime("%H:%M") if dt else ""

        def fmt_comp_br(valor):
            try:
                ano, mes = valor.split("-")
                inicio = date(int(ano), int(mes), 1)
                proximo = (
                    date(int(ano) + 1, 1, 1)
                    if int(mes) == 12
                    else date(int(ano), int(mes) + 1, 1)
                )
                fim = proximo - timedelta(days=1)
                return inicio.strftime("%d/%m/%Y"), fim.strftime("%d/%m/%Y")
            except Exception:
                return "-", "-"

        def fmt_doc(valor):
            digitos = "".join(ch for ch in str(valor or "") if ch.isdigit())
            if len(digitos) == 11:
                return f"{digitos[0:3]}.{digitos[3:6]}.{digitos[6:9]}-{digitos[9:11]}"
            if len(digitos) == 14:
                return f"{digitos[0:2]}.{digitos[2:5]}.{digitos[5:8]}/{digitos[8:12]}-{digitos[12:14]}"
            return str(valor or "")

        weekdays = ["seg", "ter", "qua", "qui", "sex", "sáb", "dom"]
        ano, mes = [int(x) for x in competencia.split("-")]
        inicio = date(ano, mes, 1)
        proximo = date(ano + 1, 1, 1) if mes == 12 else date(ano, mes + 1, 1)
        fim = proximo - timedelta(days=1)

        linhas = []
        total_previstas = 0
        total_diurnas = 0
        total_intervalo = 0
        total_faltas = 0
        total_extras = 0
        dia = inicio

        # Batch: evita N+1 queries (2 por dia) reutilizando os dados já
        # carregados por _ponto_resumo_competencia.
        _marc_por_data = {}
        for _mc in PontoMarcacao.query.filter(
            PontoMarcacao.funcionario_id == funcionario.id,
            PontoMarcacao.data_hora >= datetime.combine(inicio, datetime.min.time()),
            PontoMarcacao.data_hora < datetime.combine(fim + timedelta(days=1), datetime.min.time()),
        ).order_by(PontoMarcacao.data_hora).all():
            _marc_por_data.setdefault(_mc.data_hora.date(), []).append(_mc)
        _resumo_por_data = {
            d["data_ref"]: d for d in (resumo_comp.get("dias") or [])
        }

        while dia <= fim:
            marcacoes = _marc_por_data.get(dia, [])
            tempos = sorted(
                [
                    item.data_hora
                    for item in marcacoes
                    if getattr(item, "data_hora", None)
                ]
            )

            _dia_str = dia.strftime("%Y-%m-%d")
            resumo = _resumo_por_data.get(_dia_str) or _ponto_resumo_func_dia(
                funcionario, dia, _marcacoes=marcacoes
            )
            previstas = int(resumo.get("horas_esperadas_min", 0) or 0)
            diurnas = int(resumo.get("horas_trabalhadas_min", 0) or 0)
            saldo = int(resumo.get("saldo_min", 0) or 0)
            intervalo = 0
            if len(tempos) >= 3:
                intervalo = max(0, int((tempos[2] - tempos[1]).total_seconds() // 60))

            faltas = ""
            extras = ""
            marcacoes_str = ""
            if previstas > 0 and not tempos:
                marcacoes_str = "Falta"
                faltas = "-" + _ponto_fmt_minutos(previstas)
                total_faltas += previstas
            else:
                marcacoes_str = (
                    "  ".join(hhmm_from_dt(t) for t in tempos) if tempos else ""
                )
                if saldo < 0:
                    faltas = "-" + _ponto_fmt_minutos(abs(saldo))
                    total_faltas += abs(saldo)
                elif saldo > 0:
                    extras = _ponto_fmt_minutos(saldo)
                    total_extras += saldo

            total_previstas += previstas
            total_diurnas += diurnas
            total_intervalo += intervalo

            linhas.append(
                [
                    dia.strftime("%d/%m") + " " + weekdays[dia.weekday()],
                    marcacoes_str,
                    _ponto_fmt_minutos(previstas),
                    _ponto_fmt_minutos(diurnas),
                    _ponto_fmt_minutos(intervalo) if intervalo else "",
                    faltas,
                    extras,
                ]
            )
            dia += timedelta(days=1)

        nome_arquivo = f"espelho_ponto_{funcionario.id}_{competencia}.pdf"
        saida = io.BytesIO()
        doc = SimpleDocTemplate(
            saida,
            pagesize=A4,
            leftMargin=8 * mm,
            rightMargin=8 * mm,
            topMargin=7 * mm,
            bottomMargin=7 * mm,
        )
        largura = A4[0] - (
            doc.leftMargin + doc.rightMargin
        )  # largura útil dentro das margens
        compact_mode = len(linhas) >= 30
        styles = getSampleStyleSheet()
        st_titulo = ParagraphStyle(
            "ptt",
            parent=styles["Normal"],
            fontName="Helvetica-Bold",
            fontSize=(12 if compact_mode else 13),
            alignment=TA_CENTER,
        )
        st_small = ParagraphStyle(
            "pts",
            parent=styles["Normal"],
            fontName="Helvetica",
            fontSize=(6.8 if compact_mode else 7.4),
            leading=(8.0 if compact_mode else 9.2),
        )
        st_sign = ParagraphStyle(
            "ptsig",
            parent=styles["Normal"],
            fontName="Helvetica",
            fontSize=(6.6 if compact_mode else 7.6),
            leading=(8.0 if compact_mode else 9.6),
            alignment=TA_CENTER,
        )

        elementos = []
        empresa = (
            Empresa.query.get(funcionario.empresa_id)
            if funcionario.empresa_id
            else None
        )
        logo_url_padrao = "https://rmfacilities.com.br/wp-content/uploads/2023/08/logo-rm-facilities-1.png"

        def _logo_flowable(emp_item):
            cands = []
            if emp_item and (getattr(emp_item, "logo_url", "") or "").strip():
                cands.append(getattr(emp_item, "logo_url").strip())
            lp = get_logo()
            if lp:
                cands.append(lp)
            cands.append(logo_url_padrao)
            for cand in cands:
                try:
                    if isinstance(cand, str) and cand.startswith(
                        ("http://", "https://")
                    ):
                        req = urllib.request.Request(
                            cand, headers={"User-Agent": "Mozilla/5.0"}
                        )
                        with urllib.request.urlopen(req, timeout=8) as resp:
                            data = resp.read()
                        return Image(io.BytesIO(data), width=20 * mm, height=8 * mm)
                    if os.path.exists(cand):
                        return Image(cand, width=20 * mm, height=8 * mm)
                except Exception:
                    continue
            return p(
                f"<b>{(getattr(emp_item, 'nome', '') or 'RM FACILITIES LTDA')}</b>",
                st_small,
                html=True,
            )

        logo_cell = _logo_flowable(empresa)
        inicio_br, fim_br = fmt_comp_br(competencia)

        cabecalho = [
            [
                logo_cell,
                p(
                    f"<b>Empregador:</b> {(empresa.nome if empresa else 'RM FACILITIES LTDA')}<br/><b>CNPJ / CPF:</b> {(empresa.cnpj if empresa and empresa.cnpj else '-')}<br/><b>Função:</b> {(funcionario.funcao or funcionario.cargo or '-')}<br/><b>Departamento:</b> {(funcionario.posto_operacional or funcionario.setor or '-')}",
                    st_small,
                    html=True,
                ),
                p(f"<b>Período:</b><br/>{inicio_br} à {fim_br}", st_small, html=True),
                p(
                    f"<b>Jornada de trabalho:</b><br/>{(funcionario.jornada or '08:00').replace(';', '<br/>')}",
                    st_small,
                    html=True,
                ),
            ],
            [
                p(
                    f"<b>Colaborador:</b> {funcionario.nome}<br/><b>CPF:</b> {fmt_doc(funcionario.cpf or '')}",
                    st_small,
                    html=True,
                ),
                p(
                    f"<b>Crachá:</b> {funcionario.re or '-'}<br/><b>PIS:</b> {funcionario.pis or '-'}",
                    st_small,
                    html=True,
                ),
                p("<b>Escala:</b> Normal", st_small, html=True),
                p(
                    f"<b>Data de emissão:</b> {datetime.now(ZoneInfo('America/Sao_Paulo')).strftime('%d/%m/%Y')}",
                    st_small,
                    html=True,
                ),
            ],
        ]
        tabela_cab = Table(
            cabecalho,
            colWidths=[largura * 0.12, largura * 0.46, largura * 0.21, largura * 0.21],
        )
        tabela_cab.setStyle(
            TableStyle(
                [
                    ("GRID", (0, 0), (-1, -1), 0.5, colors.HexColor("#777777")),
                    ("SPAN", (0, 0), (0, 1)),
                    ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
                    ("ALIGN", (0, 0), (0, 1), "CENTER"),
                    ("LEFTPADDING", (0, 0), (-1, -1), 4),
                    ("RIGHTPADDING", (0, 0), (-1, -1), 4),
                    ("TOPPADDING", (0, 0), (-1, -1), (3 if compact_mode else 5)),
                    ("BOTTOMPADDING", (0, 0), (-1, -1), (3 if compact_mode else 5)),
                ]
            )
        )

        elementos.append(p("Espelho Ponto", st_titulo))
        elementos.append(Spacer(1, (4 if compact_mode else 6)))
        elementos.append(tabela_cab)
        elementos.append(Spacer(1, (4 if compact_mode else 6)))

        tabela_dias = [
            [
                "Data",
                "Marcações",
                "Previstas",
                "Diurnas",
                "Intervalo",
                "Faltas",
                "Ext. 100",
            ]
        ]
        tabela_dias.extend(linhas)
        tabela_main = Table(
            tabela_dias,
            colWidths=[
                largura * 0.11,
                largura * 0.35,
                largura * 0.09,
                largura * 0.09,
                largura * 0.09,
                largura * 0.09,
                largura * 0.10,
            ],
            repeatRows=1,
        )
        tabela_main.setStyle(
            TableStyle(
                [
                    ("GRID", (0, 0), (-1, -1), 0.35, colors.HexColor("#aaaaaa")),
                    ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#e8e8e8")),
                    ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
                    ("FONTNAME", (0, 1), (0, -1), "Helvetica-Bold"),
                    ("FONTSIZE", (0, 0), (-1, -1), (6.2 if compact_mode else 6.9)),
                    ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
                    ("LEFTPADDING", (0, 0), (-1, -1), 2.0),
                    ("RIGHTPADDING", (0, 0), (-1, -1), 2.0),
                    ("TOPPADDING", (0, 0), (-1, -1), (1.2 if compact_mode else 2.2)),
                    ("BOTTOMPADDING", (0, 0), (-1, -1), (1.2 if compact_mode else 2.2)),
                ]
            )
        )
        elementos.append(tabela_main)
        elementos.append(Spacer(1, (4 if compact_mode else 8)))

        resumo_line = (
            f"<b>Previstas:</b> {_ponto_fmt_minutos(total_previstas)}   "
            f"<b>Diurnas:</b> {_ponto_fmt_minutos(total_diurnas)}   "
            f"<b>Intervalo:</b> {_ponto_fmt_minutos(total_intervalo)}   "
            f"<b>Faltas:</b> -{_ponto_fmt_minutos(total_faltas)}   "
            f"<b>Extras 100%:</b> {_ponto_fmt_minutos(total_extras)}"
        )
        resumo_tbl = Table(
            [[p(resumo_line, st_small, html=True)]], colWidths=[largura * 1.00]
        )
        resumo_tbl.setStyle(
            TableStyle(
                [
                    ("GRID", (0, 0), (-1, -1), 0.4, colors.HexColor("#888888")),
                    ("LEFTPADDING", (0, 0), (-1, -1), 4),
                    ("RIGHTPADDING", (0, 0), (-1, -1), 4),
                    ("TOPPADDING", (0, 0), (-1, -1), (3 if compact_mode else 5)),
                    ("BOTTOMPADDING", (0, 0), (-1, -1), (3 if compact_mode else 5)),
                ]
            )
        )
        elementos.append(resumo_tbl)
        elementos.append(Spacer(1, (6 if compact_mode else 10)))

        assinatura = Table(
            [
                [
                    p(
                        "<b>Legenda:</b> Marcação incluída; Marcação por solicitação; Marcação pré-assinalada.",
                        st_small,
                        html=True,
                    ),
                    p(
                        f"Eu, <b>{funcionario.nome}</b>, concordo com as marcações e cálculos.",
                        st_sign,
                        html=True,
                    ),
                ],
                [
                    p(
                        f"<b>Marcações consideradas:</b> {datetime.now(ZoneInfo('America/Sao_Paulo')).strftime('%d/%m/%Y %H:%M')}",
                        st_small,
                        html=True,
                    ),
                    p("Assinatura do colaborador:", st_sign),
                ],
                [
                    p("", st_small),
                    p(
                        "____________________________________________________________",
                        st_sign,
                    ),
                ],
            ],
            colWidths=[largura * 0.67, largura * 0.33],
        )
        assinatura.setStyle(
            TableStyle(
                [
                    ("GRID", (0, 0), (-1, -1), 0.4, colors.HexColor("#888888")),
                    ("VALIGN", (0, 0), (-1, -1), "TOP"),
                    ("LEFTPADDING", (0, 0), (-1, -1), 4),
                    ("RIGHTPADDING", (0, 0), (-1, -1), 4),
                    ("TOPPADDING", (0, 0), (-1, -1), (4 if compact_mode else 6)),
                    ("BOTTOMPADDING", (0, 0), (-1, -1), (4 if compact_mode else 6)),
                    ("BOTTOMPADDING", (1, 2), (1, 2), (8 if compact_mode else 14)),
                ]
            )
        )
        elementos.append(assinatura)
        elementos.append(Spacer(1, 3))
        elementos.append(p("Assinado eletronicamente por RM Facilities", st_small))

        doc.build(elementos)
        saida.seek(0)
        from flask import make_response
        resp = make_response(saida.read())
        resp.headers['Content-Type'] = 'application/pdf'
        resp.headers['Content-Disposition'] = f'inline; filename="{nome_arquivo}"'
        return resp

    # ── GESTÃO FÁCIL: calendário mensal ──────────────────────────────────────
    @app.route("/api/ponto/gestao-facil/calendario")
    @lr
    def api_ponto_gestao_facil_calendario():
        funcionario_id = to_num(request.args.get("funcionario_id"))
        competencia = (request.args.get("competencia") or "").strip()
        if not funcionario_id:
            return jsonify({"erro": "funcionario_id é obrigatório."}), 400
        if not re.match(r"^\d{4}-\d{2}$", competencia):
            return jsonify({"erro": "competencia inválida. Use YYYY-MM."}), 400
        funcionario = Funcionario.query.get(funcionario_id)
        if not funcionario:
            return jsonify({"erro": "Funcionário não encontrado."}), 404

        resumo_comp = _ponto_resumo_competencia(funcionario, competencia)
        if not resumo_comp:
            return jsonify(
                {
                    "erro": "Não foi possível gerar o calendário para a competência informada."
                }
            ), 400

        # Enriquecer cada dia com os horários de cada marcação para a folha
        inicio, fim = _ponto_competencia_bounds(competencia)
        inicio_dt = datetime.combine(inicio, datetime.min.time())
        fim_dt = datetime.combine(fim + timedelta(days=1), datetime.min.time())
        todas_marc = (
            PontoMarcacao.query.filter(
                PontoMarcacao.funcionario_id == funcionario.id,
                PontoMarcacao.data_hora >= inicio_dt,
                PontoMarcacao.data_hora < fim_dt,
            )
            .order_by(PontoMarcacao.data_hora.asc(), PontoMarcacao.id.asc())
            .all()
        )
        marc_por_data = {}
        for mc in todas_marc:
            dc = mc.data_hora.date().strftime("%Y-%m-%d")
            marc_por_data.setdefault(dc, []).append(mc.to_dict())

        for dia in resumo_comp["dias"]:
            dia["marcacoes"] = marc_por_data.get(dia["data_ref"], [])

        # Incluir flag he_autorizada do posto do funcionário
        cli = None
        if funcionario.posto_cliente_id:
            cli = Cliente.query.get(funcionario.posto_cliente_id)
        resumo_comp["he_autorizada"] = (
            cli.he_autorizada if cli and cli.he_autorizada is not None else True
        )
        resumo_comp["posto_nome"] = (cli.nome or "") if cli else (funcionario.posto_operacional or "")

        # Incluir status de solicitação HE se existir
        comp_str = competencia
        sol = SolicitacaoHoraExtra.query.filter_by(
            funcionario_id=funcionario.id, competencia=comp_str
        ).first()
        resumo_comp["he_solicitacao"] = sol.to_dict() if sol else None

        return jsonify({"ok": True, "resumo": resumo_comp})

    # ── Solicitações de aprovação de hora extra ───────────────────────────────

    @app.route("/api/ponto/he/solicitacoes", methods=["GET"])
    @lr
    def api_ponto_he_solicitacoes_list():
        status_q = request.args.get("status") or "pendente"
        empresa_id = to_num(request.args.get("empresa_id")) or None
        q = SolicitacaoHoraExtra.query
        if status_q != "todos":
            q = q.filter_by(status=status_q)
        if empresa_id:
            q = q.filter_by(empresa_id=empresa_id)
        itens = q.order_by(SolicitacaoHoraExtra.criado_em.desc()).all()
        return jsonify({"ok": True, "itens": [s.to_dict() for s in itens], "total": len(itens)})

    @app.route("/api/ponto/he/solicitacoes", methods=["POST"])
    @lr
    def api_ponto_he_solicitacoes_criar():
        d = request.json or {}
        func_id = to_num(d.get("funcionario_id"))
        competencia = (d.get("competencia") or "").strip()
        if not func_id or not competencia:
            return jsonify({"erro": "funcionario_id e competencia são obrigatórios."}), 400
        func = Funcionario.query.get(func_id)
        if not func:
            return jsonify({"erro": "Funcionário não encontrado."}), 404
        # Calcular HE do período
        resumo = _ponto_resumo_competencia(func, competencia)
        if not resumo:
            return jsonify({"erro": "Competência inválida."}), 400
        he_50 = resumo["totais"]["he_50_min"]
        he_100 = resumo["totais"]["he_100_min"]
        if he_50 == 0 and he_100 == 0:
            return jsonify({"erro": "Não há horas extras registradas nesta competência."}), 400
        # Criar ou atualizar solicitação
        sol = SolicitacaoHoraExtra.query.filter_by(
            funcionario_id=func_id, competencia=competencia
        ).first()
        if sol and sol.status not in ("recusado",):
            # Atualizar valores (HE pode ter mudado)
            sol.he_50_min = he_50
            sol.he_100_min = he_100
            sol.he_50_fmt = resumo["totais"]["he_50_fmt"]
            sol.he_100_fmt = resumo["totais"]["he_100_fmt"]
            sol.status = "pendente"
            sol.criado_em = utcnow()
        else:
            sol = SolicitacaoHoraExtra(
                funcionario_id=func_id,
                empresa_id=func.empresa_id,
                competencia=competencia,
                he_50_min=he_50,
                he_100_min=he_100,
                he_50_fmt=resumo["totais"]["he_50_fmt"],
                he_100_fmt=resumo["totais"]["he_100_fmt"],
                status="pendente",
                motivo=d.get("motivo") or "",
            )
            db.session.add(sol)
        db.session.commit()
        return jsonify({"ok": True, "solicitacao": sol.to_dict()}), 201

    @app.route("/api/ponto/he/solicitacoes/<int:sid>/decidir", methods=["POST"])
    @lr
    def api_ponto_he_solicitacoes_decidir(sid):
        from flask import session as flask_session
        sol = SolicitacaoHoraExtra.query.get_or_404(sid)
        d = request.json or {}
        acao = (d.get("acao") or "").strip()
        if acao not in ("aprovar", "recusar"):
            return jsonify({"erro": "acao deve ser 'aprovar' ou 'recusar'."}), 400
        sol.status = "aprovado" if acao == "aprovar" else "recusado"
        sol.motivo = d.get("motivo") or ""
        sol.decidido_em = utcnow()
        sol.decidido_por = flask_session.get("usuario_nome") or flask_session.get("email") or "gestor"
        db.session.commit()
        audit_event(
            f"he_solicitacao_{sol.status}",
            alvo_tipo="solicitacao_he",
            alvo_id=str(sol.id),
        )
        return jsonify({"ok": True, "solicitacao": sol.to_dict()})
