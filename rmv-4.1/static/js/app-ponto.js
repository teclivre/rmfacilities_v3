function pontoDataHojeISO(){
  const d=new Date();
  return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
}

function pontoAgoraLocalInput(){
  const d=new Date();
  const y=d.getFullYear();
  const m=String(d.getMonth()+1).padStart(2,'0');
  const day=String(d.getDate()).padStart(2,'0');
  const h=String(d.getHours()).padStart(2,'0');
  const mi=String(d.getMinutes()).padStart(2,'0');
  return `${y}-${m}-${day}T${h}:${mi}`;
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
    .sort((a,b)=>(a.nome||'').localeCompare(b.nome||'pt-BR'));
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
    .sort((a,b)=>(a.nome||'').localeCompare(b.nome||'pt-BR'));
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
    <td><button class="btn b-gh b-sm" onclick="pontoEditarMarcacao(${m.id})">Editar</button></td>
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
  document.getElementById('pe-datahora').value=(m.data_hora||'').slice(0,16) || pontoAgoraLocalInput();
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
  if(!confirm(`Confirma registrar ponto${tit} para ${f?.nome||'o colaborador selecionado'}?`)) return;
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
async function pontoAbrirEditDia(){
  const marcacoes=pontoMarcacoesDiaAtual||[];
  const fid=parseInt(document.getElementById('ponto-funcionario')?.value||'0',10);
  const f=(pontoFuncs||[]).find(x=>String(x.id)===String(fid));
  const data=document.getElementById('ponto-data')?.value||pontoDataHojeISO();
  if(!fid){showSt('ponto-st','Selecione um colaborador ativo.',true);return;}
  if(!marcacoes.length){showSt('ponto-st','Nenhuma marcação neste dia para editar.',true);return;}

  const tiposOpts='<option value="entrada">Entrada</option><option value="saida_intervalo">Saída intervalo</option><option value="retorno_intervalo">Retorno intervalo</option><option value="saida">Saída</option>';

  document.getElementById('ped-info').textContent=`Editando marcações de ${f?.nome||'Colaborador'} em ${data}`;
  document.getElementById('ped-marcacoes-wrap').innerHTML=marcacoes.map(m=>`
    <div class="card" style="margin:0 0 8px;padding:10px" data-marc-id="${m.id}">
      <div class="g3" style="align-items:flex-end;gap:8px">
        <div class="f" style="margin:0"><label style="font-size:11px">Tipo</label>
          <select class="ped-tipo" data-id="${m.id}">${tiposOpts}</select>
        </div>
        <div class="f" style="margin:0"><label style="font-size:11px">Data/hora</label>
          <input class="ped-dh" data-id="${m.id}" type="datetime-local" value="${(m.data_hora||'').slice(0,16)}">
        </div>
        <div class="f" style="margin:0"><label style="font-size:11px">Observação</label>
          <input class="ped-obs" data-id="${m.id}" placeholder="Opcional" value="${m.observacao||''}">
        </div>
      </div>
    </div>
  `).join('');

  // Pre-selecionar tipo em cada select
  marcacoes.forEach(m=>{
    const sel=document.querySelector(`.ped-tipo[data-id="${m.id}"]`);
    if(sel) sel.value=(m.tipo||'entrada').trim().toLowerCase();
  });

  document.getElementById('ped-motivo').value='';
  showSt('ped-st','',false);
  setModalClean('ponto-edit-dia');
  document.getElementById('mod-ponto-edit-dia').classList.add('on');
  setTimeout(()=>document.getElementById('ped-motivo').focus(),120);
}

async function salvarEdicaoDiaCompleto(){
  const motivo=(document.getElementById('ped-motivo')?.value||'').trim();
  if(!motivo){showSt('ped-st','Informe o motivo das edições.',true);return;}

  const wrap=document.getElementById('ped-marcacoes-wrap');
  const itens=[];
  wrap.querySelectorAll('[data-marc-id]').forEach(card=>{
    const id=card.dataset.marcId;
    const tipo=(card.querySelector('.ped-tipo')?.value||'').trim().toLowerCase();
    const dh=(card.querySelector('.ped-dh')?.value||'').trim();
    const obs=(card.querySelector('.ped-obs')?.value||'').trim();
    if(id && tipo && dh) itens.push({id,tipo,data_hora:dh,observacao:obs});
  });

  if(!itens.length){showSt('ped-st','Nenhuma marcação encontrada.',true);return;}
  if(!confirm(`Confirma salvar as edições de ${itens.length} marcação(ões)?`)) return;

  showSt('ped-st','Salvando edições…',false);
  let erros=[];
  for(const it of itens){
    const r=await api('/api/ponto/marcacao/'+it.id,'PUT',{
      tipo:it.tipo,
      data_hora:it.data_hora,
      observacao:it.observacao,
      motivo:motivo
    });
    if(r.erro) erros.push(`#${it.id}: ${r.erro}`);
  }

  if(erros.length){
    showSt('ped-st','Erros: '+erros.join(' | '),true);
    return;
  }

  closeModal('ponto-edit-dia',true);
  showSt('ponto-st',`${itens.length} marcação(ões) editada(s) com sucesso.`,false);
  await pontoCarregarDia();
  await pontoCarregarPainelDia();
}

// ─── GESTÃO FÁCIL ─────────────────────────────────────────────────────────
let gfFuncId = 0;

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
    .sort((a,b)=>(a.nome||'').localeCompare(b.nome||'pt-BR'));
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

async function gfCarregarMes(){
  if(!gfFuncId){showSt('gf-st','Selecione um colaborador.',true);return;}
  const comp=(document.getElementById('gf-competencia')?.value||'').trim();
  if(!/^\d{4}-\d{2}$/.test(comp)){showSt('gf-st','Competência inválida. Use YYYY-MM.',true);return;}
  showSt('gf-st','Carregando calendário…',false);
  const r=await api('/api/ponto/gestao-facil/calendario?funcionario_id='+gfFuncId+'&competencia='+encodeURIComponent(comp));
  if(r.erro){showSt('gf-st',r.erro,true);return;}
  showSt('gf-st','',false);
  gfRenderCalendario(r.resumo,comp);
  gfRenderFolha(r.resumo);
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
    const isFuturo=new Date(ano,mes-1,d)>hoje;
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
    html+=`<div class="${cls}" onclick="gfDiaClick('${dataStr}')">
      <span class="gf-dn">${d}</span>
      <span class="gf-ds">${horas||'—'}</span>
    </div>`;
  }
  wrap.innerHTML=html;
}

function gfRenderFolha(resumo){
  const wrap=document.getElementById('gf-folha-wrap');
  const tb=document.getElementById('gf-tb-folha');
  const totDiv=document.getElementById('gf-totais');
  if(!wrap||!tb) return;

  const tipos_map={entrada:'E',saida_intervalo:'SI',retorno_intervalo:'RI',saida:'S'};

  const linhas=(resumo.dias||[]).map(dia=>{
    const marc=dia.marcacoes||[];
    const get=(tipo)=>{const m=marc.find(x=>x.tipo===tipo);return m?(m.data_hora||'').slice(11,16):'—';};
    const saldoClass=dia.saldo_fmt?.startsWith('+')?'color:var(--verde)':(dia.saldo_fmt?.startsWith('-')?'color:var(--verm)':'');
    const statusHtml=dia.status==='ok'?'<span class="pill p-vd" style="font-size:10px">OK</span>':'<span class="pill p-vm" style="font-size:10px">⚠</span>';
    return `<tr>
      <td style="font-size:12px">${dia.data_ref}</td>
      <td>${get('entrada')}</td>
      <td>${get('saida_intervalo')}</td>
      <td>${get('retorno_intervalo')}</td>
      <td>${get('saida')}</td>
      <td>${dia.horas_trabalhadas_fmt||'00:00'}</td>
      <td style="${saldoClass}">${dia.saldo_fmt||'00:00'}</td>
      <td>${statusHtml}</td>
    </tr>`;
  }).join('');
  tb.innerHTML=linhas||'<tr><td colspan="8" style="text-align:center;padding:18px;color:var(--text-muted)">Nenhum dado para esta competência.</td></tr>';
  wrap.style.display='block';

  const tot=resumo.totais||{};
  totDiv.innerHTML=`
    <div class="ponto-kpi"><div class="l">Total trabalhado</div><div class="v">${tot.horas_trabalhadas_fmt||'00:00'}</div></div>
    <div class="ponto-kpi"><div class="l">Carga esperada</div><div class="v">${tot.horas_esperadas_fmt||'00:00'}</div></div>
    <div class="ponto-kpi"><div class="l">Saldo total</div><div class="v" style="${(tot.saldo_fmt||'').startsWith('+')?'color:var(--verde)':(tot.saldo_fmt||'').startsWith('-')?'color:var(--verm)':''}">${tot.saldo_fmt||'00:00'}</div></div>
    <div class="ponto-kpi"><div class="l">Dias inconsistentes</div><div class="v" style="${tot.inconsistencias>0?'color:var(--verm)':''}">${tot.inconsistencias||0}</div></div>
  `;
}

function gfDiaClick(dataRef){
  // Navegar para aba de Ponto e carregar o dia selecionado
  const btnPonto=document.getElementById('rh-subtab-ponto');
  if(btnPonto){btnPonto.click();}
  setTimeout(()=>{
    const dataInp=document.getElementById('ponto-data');
    if(dataInp){dataInp.value=dataRef;dataInp.dispatchEvent(new Event('change'));}
    // Selecionar o funcionário se já estava selecionado
    if(gfFuncId){
      pontoSelecionarFuncionario(gfFuncId,true);
    }
  },150);
}
