function pontoDataHojeISO(){
  const d=new Date();
  return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
}

// BUG-FIX 1: pontoAgoraLocalInput deve incluir offset de timezone BRT (-03:00)
// para que a string enviada à API seja interpretada corretamente como hora local
// e não como UTC, evitando marcações 3h adiantadas.
function pontoAgoraLocalInput(){
  const d=new Date();
  const y=d.getFullYear();
  const m=String(d.getMonth()+1).padStart(2,'0');
  const day=String(d.getDate()).padStart(2,'0');
  const h=String(d.getHours()).padStart(2,'0');
  const mi=String(d.getMinutes()).padStart(2,'0');
  // Incluir offset de timezone para que a API saiba que é hora local (BRT).
  const tz=d.getTimezoneOffset();
  const tzSign=tz<=0?'+':'-';
  const tzH=String(Math.floor(Math.abs(tz)/60)).padStart(2,'0');
  const tzM=String(Math.abs(tz)%60).padStart(2,'0');
  return `${y}-${m}-${day}T${h}:${mi}${tzSign}${tzH}:${tzM}`;
}

function pontoCompetenciaAtual(){
  const d=new Date();
  return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}`;
}

async function pontoSyncFuncionarios(force=false){
  if(force || !Array.isArray(pontoFuncs) || !pontoFuncs.length){
    pontoFuncs=await api('/api/funcionarios');
  }
}

function pontoAtivosFiltrados(){
  const termo=(document.getElementById('ponto-func-busca')?.value||'').trim().toLowerCase();
  const ativos=(pontoFuncs||[])
    .filter(f=>String((f.status||'').toLowerCase())==='ativo')
    // BUG-FIX 10: segunda arg de localeCompare deve ser o locale, não b.nome
    .sort((a,b)=>(a.nome||'').localeCompare(b.nome||'','pt-BR'));
  if(!termo) return ativos;
  const termoDig=termo.replace(/\D/g,'');
  return ativos.filter(f=>{
    const mat=String(f.matricula||'').toLowerCase();
    const nm=String(f.nome||'').toLowerCase();
    const cargo=String(f.cargo||f.funcao||'').toLowerCase();
    const cpf=String(f.cpf||'').replace(/\D/g,'');
    return nm.includes(termo) || mat.includes(termo) || cargo.includes(termo) || (termoDig && cpf.includes(termoDig));
  });
}

function pontoRenderGestaoFuncionarios(){
  const box=document.getElementById('ponto-func-list');
  const qtd=document.getElementById('ponto-func-qtd');
  const sel=document.getElementById('ponto-funcionario');
  if(!box || !qtd || !sel) return;
  const ativos=pontoAtivosFiltrados();
  qtd.textContent=String(ativos.length);
  if(!ativos.length){
    box.innerHTML='<div style="text-align:center;padding:14px;color:#8a99a8;font-size:12px">Nenhum colaborador encontrado.</div>';
    return;
  }
  const atual=String(sel.value||'');
  box.innerHTML=ativos.map(f=>{
    const res=pontoResumoPainelByFunc[String(f.id)]||{};
    const status=res.status ? pontoFmtStatus(res.status) : '<span class="pill p-ci">Sem leitura</span>';
    const emp=emps.find(e=>String(e.id)===String(f.empresa_id));
    return `<button type="button" class="ponto-item ${atual===String(f.id)?'on':''}" onclick="pontoSelecionarFuncionario(${f.id},true)">
      <div class="ponto-item-main">
        <div style="font-size:13px;font-weight:700">${f.nome||'—'}</div>
        <div class="ponto-item-meta">${f.matricula?`Mat ${f.matricula} · `:''}${f.cargo||f.funcao||'Sem cargo'}${emp?` · ${emp.nome}`:''}</div>
      </div>
      <div style="display:flex;align-items:center;justify-content:space-between;gap:8px;margin-top:6px">
        ${status}
        <div style="font-size:11px;color:#6f8192">${res.horas_trabalhadas_fmt||'00:00'} / ${res.horas_esperadas_fmt||'08:00'}</div>
      </div>
    </button>`;
  }).join('');
}

async function pontoSelecionarFuncionario(fid,carregar=false){
  const sel=document.getElementById('ponto-funcionario');
  if(!sel) return;
  if(fid!==undefined && fid!==null && String(fid)!==''){
    sel.value=String(fid);
  }
  const atualId=parseInt(sel.value||'0',10);
  const f=(pontoFuncs||[]).find(x=>String(x.id)===String(atualId));
  const nomeEl=document.getElementById('ponto-func-atual-nome');
  const metaEl=document.getElementById('ponto-func-atual-meta');
  const proxEl=document.getElementById('ponto-proximo-label');
  if(nomeEl){
    nomeEl.textContent=f?.nome||'Selecione um colaborador';
  }
  if(metaEl){
    const base=[f?.matricula?`Matrícula ${f.matricula}`:'',f?.cargo||f?.funcao||'',fmtDoc(f?.cpf||'')].filter(Boolean).join(' · ');
    metaEl.textContent=base||'Escolha um colaborador para lançar e auditar as marcações do dia.';
  }
  if(proxEl) proxEl.textContent='Entrada';
  pontoRenderGestaoFuncionarios();
  if(carregar){
    await pontoCarregarDia();
    await pontoCarregarPainelDia();
  }
}

function pontoPopularFuncionarios(){
  const sel=document.getElementById('ponto-funcionario');
  if(!sel)return;
  const atual=String(sel.value||'');
  const ativos=(pontoFuncs||[])
    .filter(f=>String((f.status||'').toLowerCase())==='ativo')
    .sort((a,b)=>(a.nome||'').localeCompare(b.nome||'','pt-BR'));
  sel.innerHTML=ativos.map(f=>`<option value="${f.id}">${f.nome}${f.matricula?` · Mat ${f.matricula}`:''}</option>`).join('');
  if(!document.getElementById('ponto-data').value){
    document.getElementById('ponto-data').value=pontoDataHojeISO();
  }
  if(document.getElementById('ponto-ajuste-dh') && !document.getElementById('ponto-ajuste-dh').value){
    document.getElementById('ponto-ajuste-dh').value=pontoAgoraLocalInput();
  }
  if(document.getElementById('ponto-competencia') && !document.getElementById('ponto-competencia').value){
    document.getElementById('ponto-competencia').value=pontoCompetenciaAtual();
  }
  if(atual && ativos.some(f=>String(f.id)===atual)) sel.value=atual;
  if(!sel.value && ativos[0]) sel.value=String(ativos[0].id);
  pontoSelecionarFuncionario(sel.value,false);
}

async function pontoCarregarFechamentoDia(){
  const el=document.getElementById('ponto-fechamento-st');
  if(!el)return;
  const fid=parseInt(document.getElementById('ponto-funcionario')?.value||'0',10);
  const data=document.getElementById('ponto-data')?.value||pontoDataHojeISO();
  if(!fid){ el.textContent=''; el.className='st'; return; }
  const r=await api('/api/ponto/fechamentos-dia?data='+encodeURIComponent(data));
  if(r.erro){ el.textContent=''; el.className='st'; return; }
  const itens=(r.itens||[]).filter(x=>String(x.funcionario_id)===String(fid));
  if(!itens.length){
    el.className='st';
    el.textContent='';
    return;
  }
  const it=itens[0];
  const st=(it.status==='fechado')?'Dia fechado':'Dia fechado com ressalvas';
  el.className='st ok';
  el.textContent=`${st} por ${it.fechado_por||'usuário'} em ${it.fechado_em_fmt||''}.`;
}

async function pontoCarregarDia(){
  const sel=document.getElementById('ponto-funcionario');
  const tb=document.getElementById('tb-ponto-dia');
  const resumoEl=document.getElementById('ponto-resumo');
  const proxEl=document.getElementById('ponto-proximo-label');
  if(!sel||!tb||!resumoEl)return;
  const fid=parseInt(sel.value||'0',10);
  const data=document.getElementById('ponto-data')?.value||pontoDataHojeISO();
  if(!fid){
    tb.innerHTML='<tr><td colspan="5" style="text-align:center;padding:18px;color:var(--text-muted)">Selecione um colaborador ativo.</td></tr>';
    resumoEl.innerHTML='';
    pontoMarcacoesDiaAtual=[];
    if(proxEl) proxEl.textContent='Entrada';
    return;
  }
  const r=await api('/api/ponto/dia?funcionario_id='+fid+'&data='+encodeURIComponent(data));
  if(r.erro){
    showSt('ponto-st',r.erro,true);
    tb.innerHTML='<tr><td colspan="5" style="text-align:center;padding:18px;color:var(--text-muted)">Não foi possível carregar o ponto do dia.</td></tr>';
    resumoEl.innerHTML='';
    pontoMarcacoesDiaAtual=[];
    if(proxEl) proxEl.textContent='Entrada';
    return;
  }
  const s=r.resumo||{};
  if(proxEl) proxEl.textContent=s.proximo_tipo_label||'Entrada';
  const selTipo=document.getElementById('ponto-ajuste-tipo');
  if(selTipo && s.proximo_tipo) selTipo.value=s.proximo_tipo;
  const inc=(s.inconsistencias||[]);
  resumoEl.innerHTML=`<div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(170px,1fr));gap:8px">
    <div class="card" style="margin:0;padding:10px"><div style="font-size:11px;color:var(--text-muted)">Horas trabalhadas</div><div style="font-size:19px;font-weight:700">${s.horas_trabalhadas_fmt||'00:00'}</div></div>
    <div class="card" style="margin:0;padding:10px"><div style="font-size:11px;color:var(--text-muted)">Carga esperada</div><div style="font-size:19px;font-weight:700">${s.horas_esperadas_fmt||'08:00'}</div></div>
    <div class="card" style="margin:0;padding:10px"><div style="font-size:11px;color:var(--text-muted)">Saldo</div><div style="font-size:19px;font-weight:700">${s.saldo_fmt||'00:00'}</div></div>
    <div class="card" style="margin:0;padding:10px"><div style="font-size:11px;color:var(--text-muted)">Próxima marcação</div><div style="font-size:19px;font-weight:700">${s.proximo_tipo_label||'Entrada'}</div></div>
  </div>
  ${inc.length?`<div style="margin-top:8px;font-size:12px;color:#8a1c1c;background:#fff3f3;border:1px solid #f2c5c5;border-radius:8px;padding:8px">⚠ ${inc.join(' | ')}</div>`:''}`;
  const marc=s.marcacoes||[];
  pontoMarcacoesDiaAtual=marc;
  if(!marc.length){
    tb.innerHTML='<tr><td colspan="5" style="text-align:center;padding:18px;color:var(--text-muted)">Nenhuma marcação neste dia.</td></tr>';
    return;
  }
  tb.innerHTML=marc.map(m=>`<tr>
    <td>${m.tipo_label||m.tipo||'—'}</td>
    <td style="font-weight:700">${m.hora_fmt||''}</td>
    <td>${m.origem||'web'}</td>
    <td>${m.observacao||'—'}</td>
    <td style="display:flex;gap:4px">
      <button class="btn b-gh b-sm" onclick="pontoEditarMarcacao(${m.id})">Editar</button>
      <button class="btn b-vm b-sm" onclick="pontoExcluirMarcacao(${m.id})">Excluir</button>
    </td>
  </tr>`).join('');
  await pontoCarregarFechamentoDia();
}

async function pontoEditarMarcacao(marcacaoId){
  const m=(pontoMarcacoesDiaAtual||[]).find(x=>String(x.id)===String(marcacaoId));
  if(!m){
    showSt('ponto-st','Não foi possível localizar a marcação selecionada.',true);
    return;
  }
  const fid=parseInt(document.getElementById('ponto-funcionario')?.value||'0',10);
  const f=(pontoFuncs||[]).find(x=>String(x.id)===String(fid));
  document.getElementById('pe-id').value=String(m.id);
  document.getElementById('pe-func').value=f?.nome||'Colaborador';
  document.getElementById('pe-tipo').value=(m.tipo||'entrada').trim();
  document.getElementById('pe-datahora').value=(m.data_hora||'').replace(' ','T').slice(0,16) || pontoAgoraLocalInput();
  document.getElementById('pe-obs').value=(m.observacao||'').trim();
  document.getElementById('pe-motivo').value='';
  showSt('pe-st','',false);
  setModalClean('ponto-edit');
  document.getElementById('mod-ponto-edit').classList.add('on');
  setTimeout(()=>document.getElementById('pe-motivo').focus(),120);
}

async function salvarEdicaoMarcacaoPonto(){
  const marcacaoId=parseInt(document.getElementById('pe-id')?.value||'0',10);
  const tipoNorm=(document.getElementById('pe-tipo')?.value||'').trim().toLowerCase();
  const dh=(document.getElementById('pe-datahora')?.value||'').trim();
  const observacao=(document.getElementById('pe-obs')?.value||'').trim();
  const motivo=(document.getElementById('pe-motivo')?.value||'').trim();
  if(!marcacaoId){
    showSt('pe-st','Marcação inválida para edição.',true);
    return;
  }
  if(!['entrada','saida_intervalo','retorno_intervalo','saida'].includes(tipoNorm)){
    showSt('pe-st','Tipo inválido para edição.',true);
    return;
  }
  if(!dh){
    showSt('pe-st','Data/hora é obrigatória para edição.',true);
    return;
  }
  if(!motivo){
    showSt('pe-st','Informe o motivo da edição.',true);
    return;
  }
  if(!confirm('Confirma salvar a edição desta marcação de ponto?')) return;
  const r=await api('/api/ponto/marcacao/'+marcacaoId,'PUT',{
    tipo:tipoNorm,
    data_hora:dh,
    observacao:observacao,
    motivo:motivo
  });
  if(r.erro){
    showSt('pe-st',r.erro,true);
    return;
  }
  closeModal('ponto-edit',true);
  showSt('ponto-st','Marcação editada com sucesso.',false);
  await pontoCarregarDia();
  await pontoCarregarPainelDia();
}

async function pontoExcluirMarcacao(marcacaoId){
  const m=(pontoMarcacoesDiaAtual||[]).find(x=>String(x.id)===String(marcacaoId));
  const label=m?(m.tipo_label||m.tipo)+' às '+(m.hora_fmt||''):'marcação #'+marcacaoId;
  // BUG-FIX 3: prompt() é bloqueado por popup blockers em browsers modernos.
  // Usar um modal inline criado dinamicamente como fallback seguro.
  const motivo = await new Promise(resolve=>{
    const _overlay=document.createElement('div');
    _overlay.style.cssText='position:fixed;inset:0;background:rgba(0,0,0,.45);z-index:9999;display:flex;align-items:center;justify-content:center;padding:16px';
    _overlay.innerHTML=`<div style="background:#fff;border-radius:12px;padding:24px;max-width:420px;width:100%;box-shadow:0 8px 32px rgba(0,0,0,.2)">
      <div style="font-weight:700;margin-bottom:12px">Excluir "${label}"</div>
      <label style="font-size:13px;display:block;margin-bottom:6px">Motivo da exclusão <span style="color:red">*</span></label>
      <input id="_exc-motivo-inp" style="width:100%;padding:8px 10px;border:1px solid #ccc;border-radius:8px;font-size:14px;box-sizing:border-box" placeholder="Informe o motivo…">
      <div style="margin-top:14px;display:flex;gap:8px;justify-content:flex-end">
        <button id="_exc-cancel" style="padding:8px 16px;border:1px solid #ccc;border-radius:8px;cursor:pointer;background:#f5f5f5">Cancelar</button>
        <button id="_exc-ok" style="padding:8px 16px;background:#c62828;color:#fff;border:none;border-radius:8px;cursor:pointer;font-weight:700">Excluir</button>
      </div>
    </div>`;
    document.body.appendChild(_overlay);
    const inp=_overlay.querySelector('#_exc-motivo-inp');
    setTimeout(()=>inp.focus(),80);
    _overlay.querySelector('#_exc-cancel').onclick=()=>{document.body.removeChild(_overlay);resolve(null);};
    _overlay.querySelector('#_exc-ok').onclick=()=>{const v=inp.value.trim();document.body.removeChild(_overlay);resolve(v||null);};
    inp.addEventListener('keydown',e=>{if(e.key==='Enter'){const v=inp.value.trim();document.body.removeChild(_overlay);resolve(v||null);}if(e.key==='Escape'){document.body.removeChild(_overlay);resolve(null);}});
  });
  if(motivo===null) return; // cancelou
  if(!motivo.trim()){
    showSt('ponto-st','Informe o motivo para excluir a marcação.',true);
    return;
  }
  if(!confirm(`Confirma a EXCLUSÃO permanente de "${label}"?\nMotivo: ${motivo}`)) return;
  const r=await api('/api/ponto/marcacao/'+marcacaoId,'DELETE',{motivo:motivo.trim()});
  if(r.erro){
    showSt('ponto-st',r.erro,true);
    return;
  }
  showSt('ponto-st','Marcação excluída com sucesso.',false);
  await pontoCarregarDia();
  await pontoCarregarPainelDia();
}

async function pontoCarregarPainelDia(){
  const tb=document.getElementById('tb-ponto-painel');
  if(!tb)return;
  const data=document.getElementById('ponto-data')?.value||pontoDataHojeISO();
  const r=await api('/api/ponto/resumo-dia?data='+encodeURIComponent(data));
  if(r.erro){
    showSt('ponto-painel-st',r.erro,true);
    tb.innerHTML='<tr><td colspan="7" style="text-align:center;padding:18px;color:var(--text-muted)">Erro ao carregar painel do dia.</td></tr>';
    pontoResumoPainelByFunc={};
    pontoRenderGestaoFuncionarios();
    return;
  }
  showSt('ponto-painel-st',`Funcionários: ${r.totais?.funcionarios||0} · OK: ${r.totais?.ok||0} · Inconsistentes: ${r.totais?.inconsistentes||0}`,false);
  const itens=r.itens||[];
  pontoResumoPainelByFunc={};
  itens.forEach(it=>{ pontoResumoPainelByFunc[String(it.funcionario_id)]=it; });
  pontoRenderGestaoFuncionarios();
  if(!itens.length){
    tb.innerHTML='<tr><td colspan="7" style="text-align:center;padding:18px;color:var(--text-muted)">Nenhum colaborador ativo encontrado.</td></tr>';
    return;
  }
  tb.innerHTML=itens.map(it=>`<tr class="row-clickable" onclick="pontoSelecionarFuncionario(${it.funcionario_id},true)">
    <td>${it.funcionario_nome||'—'}</td>
    <td>${it.marcacoes_count||0}</td>
    <td>${it.horas_trabalhadas_fmt||'00:00'}</td>
    <td>${it.horas_esperadas_fmt||'08:00'}</td>
    <td>${it.saldo_fmt||'00:00'}</td>
    <td>${it.proximo_tipo_label||'Entrada'}</td>
    <td>${pontoFmtStatus(it.status)}</td>
  </tr>`).join('');
}

async function pontoRegistrarTipo(tipo=''){
  const sel=document.getElementById('ponto-funcionario');
  const fid=parseInt(sel?.value||'0',10);
  if(!fid){
    showSt('ponto-st','Selecione um colaborador ativo.',true);
    return;
  }
  const f=(pontoFuncs||[]).find(x=>String(x.id)===String(fid));
  const rot={entrada:'Entrada',saida_intervalo:'Saída intervalo',retorno_intervalo:'Retorno intervalo',saida:'Saída'};
  const tit=tipo?` (${rot[tipo]||tipo})`:'';
  // BUG-FIX 6: confirm() pode ser bloqueado por popup blockers.
  // Usar inline modal para confirmação segura.
  const confirmado = await new Promise(resolve=>{
    const _ov=document.createElement('div');
    _ov.style.cssText='position:fixed;inset:0;background:rgba(0,0,0,.45);z-index:9999;display:flex;align-items:center;justify-content:center;padding:16px';
    _ov.innerHTML=`<div style="background:#fff;border-radius:12px;padding:24px;max-width:380px;width:100%;box-shadow:0 8px 32px rgba(0,0,0,.2)">
      <div style="font-weight:700;margin-bottom:12px">Confirmar registro de ponto${tit}</div>
      <div style="font-size:14px;margin-bottom:16px">Confirma registrar ponto${tit} para <strong>${f?.nome||'o colaborador selecionado'}</strong>?</div>
      <div style="display:flex;gap:8px;justify-content:flex-end">
        <button id="_rg-cancel" style="padding:8px 16px;border:1px solid #ccc;border-radius:8px;cursor:pointer;background:#f5f5f5">Cancelar</button>
        <button id="_rg-ok" style="padding:8px 16px;background:#2e7d32;color:#fff;border:none;border-radius:8px;cursor:pointer;font-weight:700">Confirmar</button>
      </div>
    </div>`;
    document.body.appendChild(_ov);
    setTimeout(()=>_ov.querySelector('#_rg-ok').focus(),80);
    _ov.querySelector('#_rg-cancel').onclick=()=>{document.body.removeChild(_ov);resolve(false);};
    _ov.querySelector('#_rg-ok').onclick=()=>{document.body.removeChild(_ov);resolve(true);};
  });
  if(!confirmado) return;
  const payload={funcionario_id:fid,origem:'web'};
  if(tipo) payload.tipo=tipo;
  const r=await api('/api/ponto/marcar','POST',payload);
  if(r.erro){
    showSt('ponto-st',r.erro,true);
    return;
  }
  showSt('ponto-st',`Marcação registrada: ${r.marcacao?.tipo_label||'OK'} às ${r.marcacao?.hora_fmt||''}.`,false);
  await pontoCarregarDia();
  await pontoCarregarPainelDia();
}

async function pontoRegistrarAgora(){
  await pontoRegistrarTipo('');
}

async function pontoAplicarAjuste(){
  const fid=parseInt(document.getElementById('ponto-funcionario')?.value||'0',10);
  const tipo=(document.getElementById('ponto-ajuste-tipo')?.value||'').trim();
  const dh=(document.getElementById('ponto-ajuste-dh')?.value||'').trim();
  const motivo=(document.getElementById('ponto-ajuste-motivo')?.value||'').trim();
  if(!fid){showSt('ponto-st','Selecione um colaborador ativo.',true);return;}
  if(!tipo){showSt('ponto-st','Selecione o tipo de ajuste.',true);return;}
  if(!dh){showSt('ponto-st','Informe a data/hora do ajuste.',true);return;}
  if(!motivo){showSt('ponto-st','Informe o motivo do ajuste.',true);return;}
  if(!confirm('Confirma aplicar este ajuste manual no ponto?')) return;
  const r=await api('/api/ponto/ajuste','POST',{
    funcionario_id:fid,
    tipo,
    data_hora:dh,
    motivo
  });
  if(r.erro){showSt('ponto-st',r.erro,true);return;}
  showSt('ponto-st','Ajuste aplicado e auditado com sucesso.',false);
  document.getElementById('ponto-ajuste-motivo').value='';
  await pontoCarregarDia();
  await pontoCarregarPainelDia();
}

async function pontoFecharDia(forcar){
  const fid=parseInt(document.getElementById('ponto-funcionario')?.value||'0',10);
  const data=document.getElementById('ponto-data')?.value||pontoDataHojeISO();
  if(!fid){showSt('ponto-st','Selecione um colaborador ativo.',true);return;}
  const msg=forcar
    ? 'Fechar o dia com ressalvas? Use somente quando houver inconsistências justificadas.'
    : 'Confirma fechar o dia deste colaborador?';
  if(!confirm(msg)) return;
  const r=await api('/api/ponto/fechar-dia','POST',{funcionario_id:fid,data,forcar:!!forcar});
  if(r.erro){
    showSt('ponto-st',r.erro,true);
    return;
  }
  showSt('ponto-st',forcar?'Dia fechado com ressalvas.':'Dia fechado com sucesso.',false);
  await pontoCarregarDia();
  await pontoCarregarPainelDia();
}

function pontoBaixarEspelhoMensal(){
  const fid=parseInt(document.getElementById('ponto-funcionario')?.value||'0',10);
  const comp=(document.getElementById('ponto-competencia')?.value||'').trim();
  if(!fid){showSt('ponto-st','Selecione um colaborador ativo.',true);return;}
  if(!/^\d{4}-\d{2}$/.test(comp)){
    showSt('ponto-st','Competência inválida. Use YYYY-MM.',true);
    return;
  }
  const url='/api/ponto/espelho-mensal?funcionario_id='+fid+'&competencia='+encodeURIComponent(comp);
  window.open(url, '_blank');
}
// ─── EDITAR DIA COMPLETO ───────────────────────────────────────────────────
// Nota: data_hora no banco é BRT naive (utcnow() retorna localnow() = BRT).
// Os inputs exibem e enviam o valor tal como está, sem conversão de fuso.
function _dhParaInput(dh){
  // Converte "YYYY-MM-DD HH:MM:SS" ou "YYYY-MM-DDTHH:MM" para "YYYY-MM-DDTHH:MM"
  return String(dh||'').replace(' ','T').slice(0,16);
}
let _pedCtx=null;
async function pontoAbrirEditDia(){
  const marcacoes=pontoMarcacoesDiaAtual||[];
  const fid=parseInt(document.getElementById('ponto-funcionario')?.value||'0',10);
  const f=(pontoFuncs||[]).find(x=>String(x.id)===String(fid));
  const data=document.getElementById('ponto-data')?.value||pontoDataHojeISO();
  if(!fid){showSt('ponto-st','Selecione um colaborador ativo.',true);return;}
  _pedCtx={fid,data,marcacoes,isGf:false};

  const tiposOpts=`<option value="entrada">Entrada</option><option value="saida_intervalo">Saída intervalo</option><option value="retorno_intervalo">Retorno intervalo</option><option value="saida">Saída</option>`;

  document.getElementById('ped-info').textContent=`Editando marcações de ${f?.nome||'Colaborador'} em ${data}`;

  function buildRow(id,tipo,dh,obs,isNova){
    return `<div class="card" style="margin:0 0 8px;padding:10px;position:relative" data-marc-id="${id}" data-nova="${isNova?'1':''}">
      <div class="g3" style="align-items:flex-end;gap:8px">
        <div class="f" style="margin:0"><label style="font-size:11px">Tipo</label>
          <select class="ped-tipo" data-id="${id}">${tiposOpts.replace(`value="${tipo}"`,`value="${tipo}" selected`)}</select>
        </div>
        <div class="f" style="margin:0"><label style="font-size:11px">Data/hora</label>
          <input class="ped-dh" data-id="${id}" type="datetime-local" value="${dh}">
        </div>
        <div class="f" style="margin:0"><label style="font-size:11px">Observação</label>
          <input class="ped-obs" data-id="${id}" placeholder="Opcional" value="${obs}">
        </div>
        <button type="button" class="btn b-vm b-sm" style="flex-shrink:0;margin-bottom:1px" onclick="pedRemoverRow(this)" title="Excluir esta marcação">🗑</button>
      </div>
    </div>`;
  }

  document.getElementById('ped-marcacoes-wrap').innerHTML=
    marcacoes.map(m=>buildRow(m.id,(m.tipo||'entrada').trim().toLowerCase(),_dhParaInput(m.data_hora),m.observacao||'',false)).join('')+
    `<button type="button" class="btn b-vd b-sm" style="width:100%;margin-top:4px" onclick="pedAdicionarLinha()">＋ Adicionar marcação</button>`;

  document.getElementById('ped-motivo').value='';
  showSt('ped-st','',false);
  setModalClean('ponto-edit-dia');
  document.getElementById('mod-ponto-edit-dia').classList.add('on');
  setTimeout(()=>document.getElementById('ped-motivo').focus(),120);
}

let _pedNovaSeq=0;
function pedAdicionarLinha(){
  const wrap=document.getElementById('ped-marcacoes-wrap');
  const addBtn=wrap.querySelector('button[onclick="pedAdicionarLinha()"]');
  const fid=(_pedCtx?.fid)||parseInt(document.getElementById('ponto-funcionario')?.value||'0',10);
  const data=(_pedCtx?.data)||document.getElementById('ponto-data')?.value||pontoDataHojeISO();
  const seq='new_'+(++_pedNovaSeq);
  const div=document.createElement('div');
  div.innerHTML=`<div class="card" style="margin:0 0 8px;padding:10px;position:relative" data-marc-id="${seq}" data-nova="1">
    <div class="g3" style="align-items:flex-end;gap:8px">
      <div class="f" style="margin:0"><label style="font-size:11px">Tipo</label>
        <select class="ped-tipo" data-id="${seq}">
          <option value="entrada">Entrada</option>
          <option value="saida_intervalo">Saída intervalo</option>
          <option value="retorno_intervalo">Retorno intervalo</option>
          <option value="saida">Saída</option>
        </select>
      </div>
      <div class="f" style="margin:0"><label style="font-size:11px">${_pedCtx?.isGf?'Hora':'Data/hora'}</label>
        <input class="ped-dh" data-id="${seq}" type="${_pedCtx?.isGf?'time':'datetime-local'}" value="${_pedCtx?.isGf?'00:00':data+'T00:00'}">
      </div>
      <div class="f" style="margin:0"><label style="font-size:11px">Observação</label>
        <input class="ped-obs" data-id="${seq}" placeholder="Opcional" value="">
      </div>
      <input type="hidden" class="ped-fid" value="${fid}">
      <button type="button" class="btn b-vm b-sm" style="flex-shrink:0;margin-bottom:1px" onclick="pedRemoverRow(this)" title="Remover">🗑</button>
    </div>
  </div>`;
  wrap.insertBefore(div.firstElementChild, addBtn);
}

function pedRemoverRow(btn){
  const card=btn.closest('[data-marc-id]');
  if(card) card.remove();
}

async function salvarEdicaoDiaCompleto(){
  const motivo=(document.getElementById('ped-motivo')?.value||'').trim();
  if(!motivo){showSt('ped-st','Informe o motivo das edições.',true);return;}

  const fid=(_pedCtx?.fid)||parseInt(document.getElementById('ponto-funcionario')?.value||'0',10);
  const data=(_pedCtx?.data)||document.getElementById('ponto-data')?.value||pontoDataHojeISO();
  const wrap=document.getElementById('ped-marcacoes-wrap');

  // Coletar o que está no DOM agora
  const idsPresentes=new Set(
    Array.from(wrap.querySelectorAll('[data-marc-id]:not([data-nova="1"])')).map(c=>c.dataset.marcId)
  );
  // Marcações que estavam no início e foram removidas do DOM = excluir
  const idsOriginais=(_pedCtx?.marcacoes||pontoMarcacoesDiaAtual||[]).map(m=>String(m.id));
  const idsExcluir=idsOriginais.filter(id=>!idsPresentes.has(id));

  // BUG-FIX 2: helper para montar datetime sem offset de timezone:
  // banco usa BRT naive, utcnow()=localnow()=BRT. Enviar sem TZ para que
  // _ponto_parse_data_hora preserve o valor como naive BRT, igual ao que
  // o app mobile e kiosk enviam.
  function _pedDhComTz(dataRef, dhRaw){
    const base=/^\d{2}:\d{2}$/.test(dhRaw)?dataRef+'T'+dhRaw:dhRaw;
    // Remover qualquer offset existente para enviar como naive
    return base.replace(/[+\-]\d{2}:\d{2}$|Z$/,'');
  }
  }

  // Marcações existentes que continuam = editar
  const itensEditar=[];
  wrap.querySelectorAll('[data-marc-id]:not([data-nova="1"])').forEach(card=>{
    const id=card.dataset.marcId;
    const tipo=(card.querySelector('.ped-tipo')?.value||'').trim().toLowerCase();
    const dhRaw=(card.querySelector('.ped-dh')?.value||'').trim();
    const dh=_pedDhComTz(data,dhRaw);
    const obs=(card.querySelector('.ped-obs')?.value||'').trim();
    if(id && tipo && dh) itensEditar.push({id,tipo,data_hora:dh,observacao:obs});
  });

  // Linhas novas = criar
  const itensNovos=[];
  wrap.querySelectorAll('[data-marc-id][data-nova="1"]').forEach(card=>{
    const tipo=(card.querySelector('.ped-tipo')?.value||'').trim().toLowerCase();
    const dhRaw=(card.querySelector('.ped-dh')?.value||'').trim();
    const dh=_pedDhComTz(data,dhRaw);
    const obs=(card.querySelector('.ped-obs')?.value||'').trim();
    if(tipo && dh) itensNovos.push({tipo,data_hora:dh,observacao:obs,funcionario_id:fid,origem:'admin'});
  });

  const totalOps=idsExcluir.length+itensEditar.length+itensNovos.length;
  if(!totalOps){showSt('ped-st','Nenhuma alteração detectada.',true);return;}
  const resumo=[
    itensEditar.length?`${itensEditar.length} edição(ões)`:'',
    idsExcluir.length?`${idsExcluir.length} exclusão(ões)`:'',
    itensNovos.length?`${itensNovos.length} nova(s) marcação(ões)`:'',
  ].filter(Boolean).join(', ');
  if(!confirm(`Confirma: ${resumo}?`)) return;

  showSt('ped-st','Salvando…',false);
  // BUG-FIX 4: parar imediatamente em caso de erro parcial, sem continuar
  // as operações restantes (evita estado inconsistente irrecuperável).
  let erros=[];

  for(const id of idsExcluir){
    const r=await api('/api/ponto/marcacao/'+id,'DELETE',{motivo});
    if(r.erro){showSt('ped-st','Erro ao excluir #'+id+': '+r.erro,true);return;}
  }
  for(const it of itensEditar){
    const r=await api('/api/ponto/marcacao/'+it.id,'PUT',{tipo:it.tipo,data_hora:it.data_hora,observacao:it.observacao,motivo});
    if(r.erro){showSt('ped-st','Erro ao editar #'+it.id+': '+r.erro,true);return;}
  }
  for(const it of itensNovos){
    const r=await api('/api/ponto/marcacao','POST',{...it,motivo});
    if(r.erro){showSt('ped-st','Erro ao criar marcação: '+r.erro,true);return;}
  }

  closeModal('ponto-edit-dia',true);
  if(_pedCtx?.isGf){
    showSt('gf-st',`${resumo} salva(s) com sucesso.`,false);
    await gfCarregarMes();
  } else {
    showSt('ponto-st',`${resumo} salva(s) com sucesso.`,false);
    await pontoCarregarDia();
    await pontoCarregarPainelDia();
  }
}

// ─── GESTÃO FÁCIL ─────────────────────────────────────────────────────────
let gfFuncId = 0;let gfUltimoResumo = null;
async function gfCarregar(){
  await pontoSyncFuncionarios(false);
  gfRenderFuncs();
  // Pré-preencher competência com mês atual se vazio
  const inp=document.getElementById('gf-competencia');
  if(inp && !inp.value) inp.value=pontoCompetenciaAtual();
}

function gfRenderFuncs(){
  const box=document.getElementById('gf-func-list');
  const qtd=document.getElementById('gf-func-qtd');
  if(!box||!qtd) return;
  const termo=(document.getElementById('gf-busca')?.value||'').toLowerCase();
  const ativos=(pontoFuncs||[])
    .filter(f=>String((f.status||'').toLowerCase())==='ativo')
    .sort((a,b)=>(a.nome||'').localeCompare(b.nome||'','pt-BR'));
  const filtrados=!termo?ativos:ativos.filter(f=>{
    const nm=(f.nome||'').toLowerCase();
    const mat=String(f.matricula||'').toLowerCase();
    const cargo=(f.cargo||f.funcao||'').toLowerCase();
    return nm.includes(termo)||mat.includes(termo)||cargo.includes(termo);
  });
  qtd.textContent=String(filtrados.length);
  box.innerHTML=filtrados.map(f=>`
    <button class="ponto-item${String(f.id)===String(gfFuncId)?' on':''}" onclick="gfSelecionarFunc(${f.id})">
      <div class="ponto-item-main">
        <span style="font-size:13px;font-weight:700">${f.nome||'—'}</span>
        <span class="ponto-item-meta">${f.matricula||''} · ${f.cargo||f.funcao||'—'}</span>
      </div>
    </button>`).join('');
}

async function gfSelecionarFunc(id){
  gfFuncId=id;
  const f=(pontoFuncs||[]).find(x=>String(x.id)===String(id));
  document.getElementById('gf-func-nome').textContent=f?.nome||'Colaborador';
  document.getElementById('gf-func-meta').textContent=(f?.cargo||f?.funcao||'')+' · Matrícula: '+(f?.matricula||'—');
  gfRenderFuncs();
  await gfCarregarMes();
}

async function gfSolicitarAprovacaoHE(resumo){
  const btn=document.getElementById('gf-btn-solicitar-he');
  if(btn){btn.disabled=true;btn.textContent='Enviando...';}
  // BUG-FIX: ID correto do input de competência é 'gf-competencia', não 'gf-comp-sel'.
  const comp=resumo.competencia||document.getElementById('gf-competencia')?.value||(document.getElementById('gf-competencia')?.value)||'';
  try{
    const r=await api('/api/ponto/he/solicitacoes','POST',{
      funcionario_id:resumo.funcionario_id||gfFuncId,
      competencia:comp
    });
    if(r&&r.ok){
      showSt('gf-st','✅ Solicitação enviada para aprovação do gestor.',false);
      // BUG-FIX: recarregar o mês para refletir o novo status da solicitação no botão.
      await gfCarregarMes();
    }else{
      if(btn){btn.disabled=false;btn.textContent='⏱ Solicitar aprovação de HE';}
      showSt('gf-st',r?.erro||'Erro ao enviar solicitação.',true);
    }
  }catch(e){
    if(btn){btn.disabled=false;btn.textContent='⏱ Solicitar aprovação de HE';}
    showSt('gf-st','Erro: '+e.message,true);
  }
}

async function gfCarregarMes(){
  if(!gfFuncId){showSt('gf-st','Selecione um colaborador.',true);return;}
  const comp=(document.getElementById('gf-competencia')?.value||'').trim();
  if(!/^\d{4}-\d{2}$/.test(comp)){showSt('gf-st','Competência inválida. Use YYYY-MM.',true);return;}
  showSt('gf-st','Carregando calendário…',false);
  const r=await api('/api/ponto/gestao-facil/calendario?funcionario_id='+gfFuncId+'&competencia='+encodeURIComponent(comp));
  if(r.erro){showSt('gf-st',r.erro,true);return;}
  showSt('gf-st','',false);
  gfUltimoResumo=r.resumo;
  gfRenderCalendario(r.resumo,comp);
  gfRenderFolha(r.resumo);
}

function gfAbrirPreviaFolha(){
  if(!gfFuncId){showSt('gf-st','Selecione um colaborador.',true);return;}
  const comp=(document.getElementById('gf-competencia')?.value||'').trim();
  if(!/^\d{4}-\d{2}$/.test(comp)){showSt('gf-st','Competência inválida. Use YYYY-MM.',true);return;}
  window.open('/api/ponto/espelho-mensal?funcionario_id='+gfFuncId+'&competencia='+encodeURIComponent(comp),'_blank');
}

function gfRenderCalendario(resumo,comp){
  const wrap=document.getElementById('gf-calendario');
  if(!wrap) return;
  const [ano,mes]=comp.split('-').map(Number);
  const hoje=new Date();
  const primeiroDia=new Date(ano,mes-1,1).getDay();// 0=dom
  const diasNoMes=new Date(ano,mes,0).getDate();
  const diasSemana=['Dom','Seg','Ter','Qua','Qui','Sex','Sáb'];

  // Construir mapa data→resumo
  const mapaStatus={};
  (resumo.dias||[]).forEach(d=>{mapaStatus[d.data_ref]=d;});

  let html=diasSemana.map(d=>`<div class="gf-dia-header">${d}</div>`).join('');
  // Células vazias no início
  for(let i=0;i<primeiroDia;i++) html+=`<div class="gf-dia vazio"></div>`;

  for(let d=1;d<=diasNoMes;d++){
    const dataStr=`${comp}-${String(d).padStart(2,'0')}`;
    const dayData=mapaStatus[dataStr];
    const isHoje=hoje.getFullYear()===ano&&hoje.getMonth()+1===mes&&hoje.getDate()===d;
  // BUG-FIX 5: comparar dia/mês/ano diretamente para evitar problema de timezone
  // no boundary do dia (new Date(ano,mes-1,d)>hoje pode retornar false para "hoje"
  // dependendo do fuso horário local do browser às 00:00h local).
  const isFuturo=(ano>hoje.getFullYear())||(ano===hoje.getFullYear()&&mes-1>hoje.getMonth())||(ano===hoje.getFullYear()&&mes-1===hoje.getMonth()&&d>hoje.getDate());
    let cls='gf-dia';
    if(isFuturo) cls+=' futuro';
    else if(dayData){
      const wday=new Date(ano,mes-1,d).getDay();
      const isWeekend=wday===0||wday===6;
      if(isWeekend&&!dayData.marcacoes_count) cls+=' folga';
      else if(dayData.status==='ok'&&dayData.marcacoes_count>0) cls+=' ok';
      else if(dayData.marcacoes_count>0) cls+=' pendente';
      else if(!isWeekend) cls+=' falta';
      else cls+=' folga';
    }
    if(isHoje) cls+=' hoje';
    const saldo=dayData?.saldo_fmt||'';
    const horas=dayData?.horas_trabalhadas_fmt||'';
    const marc=dayData?.marcacoes||[];
    // data_hora no banco é BRT naive: exibir diretamente sem conversão de fuso.
    const getT=(tipo)=>{const m=marc.find(x=>x.tipo===tipo);if(!m)return null;const s=String(m.data_hora||'');const mt=s.match(/(\d{2}:\d{2})(?::\d{2})?$/);return mt?mt[1]:null;};
    const timesHtml=[['entrada','gf-t-e','E'],['saida_intervalo','gf-t-si','SI'],['retorno_intervalo','gf-t-ri','RI'],['saida','gf-t-s','S']]
      .map(([tipo,cls,lb])=>{const t=getT(tipo);return t?`<span class="gf-t ${cls}">${lb} ${t}</span>`:'';})
      .filter(Boolean).join('');
    html+=`<div class="${cls}" onclick="gfDiaClick('${dataStr}')">
      <span class="gf-dn">${d}</span>
      <div class="gf-times">${timesHtml||`<span class="gf-t">${horas||'—'}</span>`}</div>
    </div>`;
  }
  wrap.innerHTML=html;
}

function gfRenderFolha(resumo){
  const wrap=document.getElementById('gf-folha-wrap');
  const tb=document.getElementById('gf-tb-folha');
  const totDiv=document.getElementById('gf-totais');
  if(!wrap||!tb) return;

  // Aviso de HE não autorizada
  const avisoEl=document.getElementById('gf-he-aviso');
  if(avisoEl){
    const heAutorizada=resumo.he_autorizada===undefined||resumo.he_autorizada===null?true:!!resumo.he_autorizada;
    const temHE=(resumo.totais?.he_50_min||0)+(resumo.totais?.he_100_min||0)>0;
    const heVisible=(!heAutorizada&&temHE);
    avisoEl.style.display=heVisible?'':'none';
    // Botão solicitar aprovação HE
    let btnSolHE=document.getElementById('gf-btn-solicitar-he');
    if(heVisible){
      const sol=resumo.he_solicitacao;
      const btnLabel=!sol?'⏱ Solicitar aprovação de HE':sol.status==='pendente'?'⏳ Aguardando aprovação':sol.status==='aprovado'?'✅ HE aprovada':'🔁 Re-solicitar aprovação';
      const btnDisabled=sol&&sol.status==='pendente'?'disabled':sol&&sol.status==='aprovado'?'disabled':'';
      if(!btnSolHE){
        btnSolHE=document.createElement('button');
        btnSolHE.id='gf-btn-solicitar-he';
        btnSolHE.className='btn b-az b-sm';
        btnSolHE.style.marginTop='8px';
        avisoEl.appendChild(btnSolHE);
      }
      btnSolHE.textContent=btnLabel;
      btnSolHE.disabled=!!btnDisabled;
      btnSolHE.onclick=()=>gfSolicitarAprovacaoHE(resumo);
    }else{
      if(btnSolHE)btnSolHE.remove();
    }
  }

  const tipos_map={entrada:'E',saida_intervalo:'SI',retorno_intervalo:'RI',saida:'S'};

  const linhas=(resumo.dias||[]).map(dia=>{
    const marc=dia.marcacoes||[];
    // data_hora no banco é BRT naive: exibir diretamente sem conversão de fuso.
    const get=(tipo)=>{const m=marc.find(x=>x.tipo===tipo);return m?(m.data_hora||'').slice(11,16)||'—':'—'};
    // BUG-FIX 9: _ponto_fmt_minutos(signed=True) retorna "-HH:MM" para negativo
    // mas sem "+" para positivo, então startsWith('+') nunca é verdadeiro.
    // Usar saldo_min (número) para detectar sinal correto.
    const saldoClass=(dia.saldo_min||0)>0?'color:var(--verde)':((dia.saldo_min||0)<0?'color:var(--verm)':'');
    const statusHtml=dia.status==='ok'?'<span class="pill p-vd" style="font-size:10px">OK</span>':'<span class="pill p-vm" style="font-size:10px">⚠</span>';
    const he50=dia.he_50_fmt||'00:00'; const he100=dia.he_100_fmt||'00:00';
    const not=dia.noturno_fmt||'00:00'; const intra=dia.intrajornada_fmt||'00:00';
    return `<tr>
      <td style="font-size:12px">${dia.data_ref}</td>
      <td>${get('entrada')}</td>
      <td>${get('saida_intervalo')}</td>
      <td>${get('retorno_intervalo')}</td>
      <td>${get('saida')}</td>
      <td>${dia.horas_trabalhadas_fmt||'00:00'}</td>
      <td style="${saldoClass}">${dia.saldo_fmt||'00:00'}</td>
      <td style="${he50!=='00:00'?'color:var(--laranja)':''}">${he50}</td>
      <td style="${he100!=='00:00'?'color:var(--verm)':''}">${he100}</td>
      <td style="${not!=='00:00'?'color:var(--azul)':''}">${not}</td>
      <td>${intra}</td>
      <td>${statusHtml}</td>
    </tr>`;
  }).join('');
  tb.innerHTML=linhas||'<tr><td colspan="8" style="text-align:center;padding:18px;color:var(--text-muted)">Nenhum dado para esta competência.</td></tr>';
  wrap.style.display='block';

  const tot=resumo.totais||{};
  // BUG-FIX 9 (totais): mesma correção de cor de saldo para o total.
  const saldoStyle=(tot.saldo_min||0)>0?'color:var(--verde)':((tot.saldo_min||0)<0?'color:var(--verm)':'');
  totDiv.innerHTML=`
    <div class="ponto-kpi"><div class="l">Total trabalhado</div><div class="v">${tot.horas_trabalhadas_fmt||'00:00'}</div></div>
    <div class="ponto-kpi"><div class="l">Carga esperada</div><div class="v">${tot.horas_esperadas_fmt||'00:00'}</div></div>
    <div class="ponto-kpi"><div class="l">Saldo total</div><div class="v" style="${saldoStyle}">${tot.saldo_fmt||'00:00'}</div></div>
    <div class="ponto-kpi"><div class="l">HE 50%</div><div class="v" style="${tot.he_50_min>0?'color:var(--laranja)':''}">${tot.he_50_fmt||'00:00'}</div></div>
    <div class="ponto-kpi"><div class="l">HE 100%</div><div class="v" style="${tot.he_100_min>0?'color:var(--verm)':''}">${tot.he_100_fmt||'00:00'}</div></div>
    <div class="ponto-kpi"><div class="l">Adicional noturno</div><div class="v" style="${tot.noturno_min>0?'color:var(--azul)':''}">${tot.noturno_fmt||'00:00'}</div></div>
    <div class="ponto-kpi"><div class="l">Intrajornada</div><div class="v">${tot.intrajornada_fmt||'00:00'}</div></div>
    <div class="ponto-kpi"><div class="l">Dias inconsistentes</div><div class="v" style="${tot.inconsistencias>0?'color:var(--verm)':''}">${tot.inconsistencias||0}</div></div>
  `;
}

function gfDiaClick(dataRef){
  gfAbrirEditDia(dataRef);
}

function gfAbrirEditDia(dataRef){
  if(!gfFuncId||!gfUltimoResumo){showSt('gf-st','Selecione um colaborador.',true);return;}
  const diaData=(gfUltimoResumo.dias||[]).find(d=>d.data_ref===dataRef);
  const marcacoes=diaData?.marcacoes||[];
  const f=(pontoFuncs||[]).find(x=>String(x.id)===String(gfFuncId));
  _pedCtx={fid:gfFuncId,data:dataRef,marcacoes,isGf:true};

  const tiposOpts=`<option value="entrada">Entrada</option><option value="saida_intervalo">Saída intervalo</option><option value="retorno_intervalo">Retorno intervalo</option><option value="saida">Saída</option>`;
  document.getElementById('ped-info').textContent=`Editando marcações de ${f?.nome||'Colaborador'} em ${dataRef}`;

  // BUG-FIX: reutilizar _dhParaInput para exibir o horário BRT armazenado.
  function toDtLocal(dh){ return _dhParaInput(dh); }

  function buildRow(id,tipo,dh,obs,isNova){
    return `<div class="card" style="margin:0 0 8px;padding:10px;position:relative" data-marc-id="${id}" data-nova="${isNova?'1':''}">
      <div class="g3" style="align-items:flex-end;gap:8px">
        <div class="f" style="margin:0"><label style="font-size:11px">Tipo</label>
          <select class="ped-tipo" data-id="${id}">${tiposOpts.replace(`value="${tipo}"`,`value="${tipo}" selected`)}</select>
        </div>
        <div class="f" style="margin:0"><label style="font-size:11px">Hora</label>
          <input class="ped-dh" data-id="${id}" type="time" value="${dh}">
        </div>
        <div class="f" style="margin:0"><label style="font-size:11px">Observação</label>
          <input class="ped-obs" data-id="${id}" placeholder="Opcional" value="${obs}">
        </div>
        <button type="button" class="btn b-vm b-sm" style="flex-shrink:0;margin-bottom:1px" onclick="pedRemoverRow(this)" title="Excluir esta marcação">🗑</button>
      </div>
    </div>`;
  }

  document.getElementById('ped-marcacoes-wrap').innerHTML=
    marcacoes.map(m=>buildRow(m.id,(m.tipo||'entrada').trim().toLowerCase(),toDtLocal(m.data_hora).slice(11,16),m.observacao||'',false)).join('')+
    `<button type="button" class="btn b-vd b-sm" style="width:100%;margin-top:4px" onclick="pedAdicionarLinha()">＋ Adicionar marcação</button>`;

  document.getElementById('ped-motivo').value='';
  showSt('ped-st','',false);
  setModalClean('ponto-edit-dia');
  document.getElementById('mod-ponto-edit-dia').classList.add('on');
  setTimeout(()=>document.getElementById('ped-motivo').focus(),120);
}

// ── Correções de Ponto (Solicitações do App) ─────────────────────────────────

let _correcoesPontoTodas=[];

const _CORRECAO_TIPO_LABEL={
  horario_errado:'Horário errado',
  marcacao_faltando:'Marcação faltando',
  marcacao_extra:'Marcação extra',
  outro:'Outro',
};
const _CORRECAO_STATUS_LABEL={
  pendente:'🟡 Pendente',
  resolvido:'✅ Aprovada',
  rejeitado:'❌ Rejeitada',
};

async function carregarCorrecoesPonto(){
  try{
    const data=await api('/api/funcionarios/ponto/solicitacoes-correcao/todas-pendentes');
    _correcoesPontoTodas=Array.isArray(data)?data:[];
  }catch(_){
    _correcoesPontoTodas=[];
  }
  _atualizarBadgeCorrecoes();
  renderCorrecoesPonto();
}

function _atualizarBadgeCorrecoes(){
  const badge=document.getElementById('badge-correcoes-ponto');
  if(!badge)return;
  const pendentes=_correcoesPontoTodas.filter(c=>c.status==='pendente').length;
  if(pendentes>0){
    badge.textContent=pendentes;
    badge.style.display='inline';
  }else{
    badge.style.display='none';
  }
}

function renderCorrecoesPonto(){
  const body=document.getElementById('modal-correcoes-body');
  const filtro=document.getElementById('modal-correcoes-filtro')?.value||'pendente';
  const count=document.getElementById('modal-correcoes-count');
  if(!body)return;
  const lista=filtro==='pendente'?_correcoesPontoTodas.filter(c=>c.status==='pendente'):_correcoesPontoTodas;
  if(count)count.textContent=lista.length+' registro(s)';
  if(!lista.length){
    body.innerHTML=`<div style="text-align:center;padding:32px;opacity:.5;font-size:14px">${filtro==='pendente'?'Nenhuma solicitação pendente 🎉':'Nenhuma solicitação encontrada'}</div>`;
    return;
  }
  body.innerHTML=lista.map(c=>{
    const func=(funcs||[]).find(f=>String(f.id)===String(c.funcionario_id));
    const nomeFunc=func?func.nome:(c.funcionario_nome||`Funcionário #${c.funcionario_id}`);
    const statusLabel=_CORRECAO_STATUS_LABEL[c.status]||c.status;
    const tipoLabel=_CORRECAO_TIPO_LABEL[c.tipo_problema]||c.tipo_problema;
    const dataFmt=c.data_ref?c.data_ref.split('-').reverse().join('/'):'-';
    const criadoFmt=c.criado_em?(new Date(c.criado_em+'Z')).toLocaleString('pt-BR',{day:'2-digit',month:'2-digit',year:'numeric',hour:'2-digit',minute:'2-digit'}):'-';
    const isPendente=c.status==='pendente';
    // Bloco de alteração automática de marcação
    const temMarcacao=c.marcacao_id&&c.horario_correto;
    const temFaltando=!c.marcacao_id&&c.tipo_problema==='marcacao_faltando'&&c.horario_correto;
    const blocoAlteracao=temMarcacao
      ?`<div style="margin-top:10px;padding:10px 12px;border-radius:8px;background:var(--verde-cl,#e8f5e9);border:1px solid var(--verde,#4caf50);display:flex;align-items:center;gap:10px;flex-wrap:wrap">
          <span style="font-size:20px">🔄</span>
          <div style="font-size:13px">
            <div style="font-weight:700;color:var(--verde-esc,#2e7d32)">Alteração automática ao aprovar</div>
            <div style="color:var(--preto);margin-top:2px">Marcação #${c.marcacao_id} &nbsp;·&nbsp; ${c.horario_original?`<span style="text-decoration:line-through;opacity:.6">${c.horario_original}</span> → `:''}
              <strong>${c.horario_correto}</strong></div>
          </div>
        </div>`
      :temFaltando
      ?`<div style="margin-top:10px;padding:10px 12px;border-radius:8px;background:#e3f2fd;border:1px solid #1565C0;display:flex;align-items:center;gap:10px;flex-wrap:wrap">
          <span style="font-size:20px">➕</span>
          <div style="font-size:13px">
            <div style="font-weight:700;color:#1565C0">Nova marcação será criada ao aprovar</div>
            <div style="color:var(--preto);margin-top:2px">Data: <strong>${dataFmt}</strong> &nbsp;·&nbsp; Horário: <strong>${c.horario_correto}</strong></div>
          </div>
        </div>`
      :`<div style="margin-top:8px;font-size:12px;opacity:.55;font-style:italic">ℹ️ Sem marcação vinculada — aprovação apenas registra a decisão, sem alteração automática.</div>`;
    return `<div style="border:1px solid var(--borda);border-radius:var(--r);padding:14px 16px;margin-bottom:10px;background:${isPendente?'var(--cinza-cl)':'var(--branco)'}">
      <div style="display:flex;align-items:flex-start;justify-content:space-between;gap:8px;flex-wrap:wrap">
        <div>
          <div style="font-weight:700;font-size:14px">${nomeFunc}</div>
          <div style="font-size:12px;opacity:.6;margin-top:2px">Enviado em ${criadoFmt}</div>
        </div>
        <span style="font-size:12px;padding:3px 10px;border-radius:8px;background:${isPendente?'var(--laranja-cl)':'var(--cinza-cl)'};color:${isPendente?'var(--laranja-esc)':'var(--text-muted)'};">${statusLabel}</span>
      </div>
      <div style="margin-top:10px;display:grid;grid-template-columns:repeat(auto-fill,minmax(160px,1fr));gap:6px 16px;font-size:13px">
        <div><span style="opacity:.6">Data:</span> <strong>${dataFmt}</strong></div>
        <div><span style="opacity:.6">Tipo:</span> <strong>${tipoLabel}</strong></div>
      </div>
      ${blocoAlteracao}
      <div style="margin-top:10px;font-size:13px;background:var(--branco);border:1px solid var(--borda);border-radius:6px;padding:8px 10px;white-space:pre-wrap">${c.observacao||'-'}</div>
      ${c.motivo_admin?`<div style="margin-top:6px;font-size:12px;opacity:.65">💬 RH: ${c.motivo_admin}</div>`:''}
      ${isPendente?`
      <div style="margin-top:12px;display:flex;gap:8px;flex-wrap:wrap;align-items:center">
        <input id="motivo-${c.id}" placeholder="Motivo (opcional para aprovação)" style="flex:1;min-width:160px;font-size:13px;padding:6px 10px;border:1px solid var(--borda);border-radius:var(--r);background:var(--branco);color:var(--preto)">
        <button class="btn b-vd b-sm" onclick="decidirCorrecaoPonto(${c.id},'aprovar')">✅ Aprovar${temMarcacao?' e alterar':temFaltando?' e criar':''}</button>
        <button class="btn b-vm b-sm" onclick="decidirCorrecaoPonto(${c.id},'rejeitar')">❌ Rejeitar</button>
      </div>`:''}
    </div>`;
  }).join('');
}

async function abrirModalCorrecoesPonto(){
  const modal=document.getElementById('modal-correcoes-ponto');
  if(!modal)return;
  modal.style.display='flex';
  document.getElementById('modal-correcoes-body').innerHTML='<div style="text-align:center;padding:32px;opacity:.5">Carregando...</div>';
  // Busca todas para o modal (pendentes + resolvidas recentes)
  try{
    const pendentes=await api('/api/funcionarios/ponto/solicitacoes-correcao/todas-pendentes');
    _correcoesPontoTodas=Array.isArray(pendentes)?pendentes:[];
  }catch(_){}
  _atualizarBadgeCorrecoes();
  renderCorrecoesPonto();
}

async function decidirCorrecaoPonto(id,acao){
  const motivo=document.getElementById('motivo-'+id)?.value?.trim()||'';
  showSt('st-correcoes-ponto','Processando...',false);
  try{
    const r=await api('/api/funcionarios/ponto/solicitacao-correcao/'+id+'/decidir','POST',{acao,motivo});
    if(r&&r.ok){
      showSt('st-correcoes-ponto',acao==='aprovar'?'✅ Solicitação aprovada.':'❌ Solicitação rejeitada.',false);
      // BUG-FIX 8: usar status retornado pelo servidor em vez de hardcodar string
      // ('resolvido'/'rejeitado') — evita badge travado se enum mudar.
      const idx=_correcoesPontoTodas.findIndex(c=>c.id===id);
      if(idx>=0){
        _correcoesPontoTodas[idx].status=r.correcao?.status||(acao==='aprovar'?'resolvido':'rejeitado');
        _correcoesPontoTodas[idx].motivo_admin=motivo;
      }
      _atualizarBadgeCorrecoes();
      renderCorrecoesPonto();
    }else{
      showSt('st-correcoes-ponto',r?.erro||'Erro ao processar.',true);
    }
  }catch(e){
    showSt('st-correcoes-ponto','Erro: '+e.message,true);
  }
}

// Polling periódico: atualiza badge a cada 60s quando a aba de ponto está visível
setInterval(()=>{
  // BUG-FIX 7: não fazer polling quando a seção de ponto está oculta.
  const secPonto=document.getElementById('pg-ponto');
  if(!secPonto||!secPonto.classList.contains('on')) return;
  api('/api/funcionarios/ponto/solicitacoes-correcao/todas-pendentes')
    .then(d=>{if(Array.isArray(d)){_correcoesPontoTodas=d;_atualizarBadgeCorrecoes();}})
    .catch(()=>{});
},60000);

// ── Aprovação de Hora Extra ───────────────────────────────────────────────────
let _heSolicitacoes=[];

async function abrirModalHEPendentes(){
  const modal=document.getElementById('modal-he-pendentes');
  if(!modal)return;
  modal.style.display='flex';
  document.getElementById('modal-he-body').innerHTML='<div style="text-align:center;padding:32px;opacity:.5">Carregando...</div>';
  await carregarHEPendentes();
}

async function carregarHEPendentes(){
  const filtro=document.getElementById('modal-he-filtro')?.value||'pendente';
  try{
    const r=await api('/api/ponto/he/solicitacoes?status='+filtro);
    _heSolicitacoes=Array.isArray(r?.itens)?r.itens:[];
  }catch(_){_heSolicitacoes=[];}
  renderHESolicitacoes();
}

function renderHESolicitacoes(){
  const body=document.getElementById('modal-he-body');
  const cntEl=document.getElementById('modal-he-count');
  if(!body)return;
  const items=_heSolicitacoes;
  if(cntEl)cntEl.textContent=items.length+' registro(s)';
  if(!items.length){body.innerHTML='<div style="text-align:center;padding:32px;opacity:.5">Nenhum registro.</div>';return;}
  const rows=items.map(s=>{
    const statusStyle=s.status==='pendente'?'color:#b45309':s.status==='aprovado'?'color:var(--verd)':'color:var(--verm)';
    const btns=s.status==='pendente'?`
      <div style="display:flex;gap:6px;margin-top:8px">
        <textarea id="he-motivo-${s.id}" placeholder="Motivo (opcional)" style="flex:1;font-size:12px;padding:4px 6px;border:1px solid var(--borda);border-radius:var(--r);background:var(--branco);color:var(--preto);resize:vertical;min-height:40px"></textarea>
        <div style="display:flex;flex-direction:column;gap:4px">
          <button class="btn b-vd b-sm" onclick="decidirHE(${s.id},'aprovar')">✅ Aprovar</button>
          <button class="btn b-rm b-sm" onclick="decidirHE(${s.id},'recusar')">❌ Recusar</button>
        </div>
      </div>`:'<div style="font-size:12px;opacity:.6;margin-top:4px">Decidido em: '+(s.decidido_fmt||'—')+(s.decidido_por?' por '+s.decidido_por:'')+'</div>';
    return `<div style="padding:12px 0;border-bottom:1px solid var(--borda)">
      <div style="display:flex;justify-content:space-between;align-items:flex-start">
        <div>
          <div style="font-weight:600">${s.funcionario_nome||'—'} <span style="font-size:12px;opacity:.6">${s.funcionario_matricula?'Mat '+s.funcionario_matricula:''}</span></div>
          <div style="font-size:13px;opacity:.7">Competência: ${s.competencia} · HE 50%: ${s.he_50_fmt||'0h'} · HE 100%: ${s.he_100_fmt||'0h'}</div>
          <div style="font-size:12px;opacity:.5">Solicitado em: ${s.criado_fmt||'—'} · Posto: ${s.posto_label||'—'}</div>
        </div>
        <span style="font-size:12px;font-weight:700;${statusStyle}">${s.status.toUpperCase()}</span>
      </div>
      ${btns}
    </div>`;
  }).join('');
  body.innerHTML=rows;
}

async function decidirHE(id,acao){
  const motivo=document.getElementById('he-motivo-'+id)?.value?.trim()||'';
  showSt('st-he','Processando...',false);
  try{
    const r=await api('/api/ponto/he/solicitacoes/'+id+'/decidir','POST',{acao,motivo});
    if(r&&r.ok){
      showSt('st-he',acao==='aprovar'?'✅ HE aprovada.':'❌ HE recusada.',false);
      const idx=_heSolicitacoes.findIndex(s=>s.id===id);
      if(idx>=0)_heSolicitacoes[idx]=r.solicitacao;
      renderHESolicitacoes();
      // Atualizar badge no dashboard
      const elHePend=document.getElementById('d-he-pendentes');
      if(elHePend){
        const atual=parseInt(elHePend.textContent)||0;
        const novo=Math.max(0,atual-1);
        elHePend.textContent=novo;
        elHePend.closest('.metric')?.classList.toggle('vm',novo>0);
        elHePend.closest('.metric')?.style.setProperty('--metric-c',novo>0?'var(--verm)':'#b45309');
      }
    }else{
      showSt('st-he',r?.erro||'Erro ao processar.',true);
    }
  }catch(e){
    showSt('st-he','Erro: '+e.message,true);
  }
}

// ── Gestão Fácil: auto-refresh instantâneo via SSE + fallback 60s ─────────────
let _gfSseSource=null;
let _gfFallbackTimer=null;
let _gfLastRefresh=null;

function _gfPaneVisible(){
  // Verifica (1) que a página RH está ativa e (2) que o painel Gestão Fácil
  // está explicitamente em display:block (não apenas "não-none", pois a div
  // não tem display:none inicial e isso causava false-positives).
  const pg=document.getElementById('pg-rh');
  if(!pg||!pg.classList.contains('on')) return false;
  const pane=document.getElementById('rh-pane-gestao-facil');
  return !!(pane&&pane.style.display==='block');
}

function _gfUpdateTimestamp(){
  const el=document.getElementById('gf-live-ts');
  if(!el) return;
  if(!_gfLastRefresh){el.textContent='ao vivo';return;}
  const hh=String(_gfLastRefresh.getHours()).padStart(2,'0');
  const mm=String(_gfLastRefresh.getMinutes()).padStart(2,'0');
  const ss=String(_gfLastRefresh.getSeconds()).padStart(2,'0');
  el.textContent=`atualizado ${hh}:${mm}:${ss}`;
}

async function _gfPollSilent(){
  if(!_gfPaneVisible()||!gfFuncId) return;
  const comp=(document.getElementById('gf-competencia')?.value||'').trim();
  if(!/^\d{4}-\d{2}$/.test(comp)) return;
  try{
    const r=await api('/api/ponto/gestao-facil/calendario?funcionario_id='+gfFuncId+'&competencia='+encodeURIComponent(comp));
    if(r.erro||!r.resumo) return;
    // Só re-renderiza se os dados mudaram (evita piscar sem motivo)
    const novoHash=JSON.stringify(r.resumo?.dias||[]);
    const velhoHash=JSON.stringify(gfUltimoResumo?.dias||[]);
    if(novoHash!==velhoHash){
      gfUltimoResumo=r.resumo;
      gfRenderCalendario(r.resumo,comp);
      gfRenderFolha(r.resumo);
    }
    _gfLastRefresh=new Date();
    _gfUpdateTimestamp();
  }catch(_){}
}

function _gfSseConnect(){
  if(_gfSseSource){_gfSseSource.close();_gfSseSource=null;}
  const src=new EventSource('/api/eventos');
  _gfSseSource=src;
  // Evento 'ponto': disparado imediatamente quando funcionário bate ponto no app
  src.addEventListener('ponto',function(e){
    try{
      const d=JSON.parse(e.data||'{}');
      // Só recarrega se o funcionário exibido é o que bateu o ponto
      if(gfFuncId&&d.funcionario_id&&Number(d.funcionario_id)===Number(gfFuncId)){
        _gfPollSilent();
      }
    }catch(_){}
  });
  // EventSource reconecta automaticamente em caso de erro (nativo do browser)
}

// Inicia SSE ao carregar o script
_gfSseConnect();
// Fallback: polling a cada 60s para cobrir eventuais gaps do SSE
_gfFallbackTimer=setInterval(_gfPollSilent,60000);

