(function(){
  function digits(v){
    return String(v || '').replace(/\D/g, '');
  }

  function formatCpfValue(v){
    var d = digits(v).slice(0, 11);
    if(d.length > 9) return d.replace(/^(\d{3})(\d{3})(\d{3})(\d{0,2}).*/, '$1.$2.$3-$4');
    if(d.length > 6) return d.replace(/^(\d{3})(\d{3})(\d{0,3}).*/, '$1.$2.$3');
    if(d.length > 3) return d.replace(/^(\d{3})(\d{0,3}).*/, '$1.$2');
    return d;
  }

  function bindCpfFormatter(inputId){
    var el = document.getElementById(inputId);
    if(!el) return;
    el.addEventListener('input', function(){
      el.value = formatCpfValue(el.value);
    });
  }

  function setStep(stepNumber, sec1Id, sec2Id, p1Id, p2Id){
    var s1=document.getElementById(sec1Id||'sec-1');
    var s2=document.getElementById(sec2Id||'sec-2');
    var p1=document.getElementById(p1Id||'stp-1');
    var p2=document.getElementById(p2Id||'stp-2');
    if(!s1 || !s2 || !p1 || !p2) return;
    s1.className='sec'+(stepNumber===1?' on':'');
    s2.className='sec'+(stepNumber===2?' on':'');
    p1.className='step'+(stepNumber===1?' on':'');
    p2.className='step'+(stepNumber===2?' on':'');
  }

  function friendlyError(txt){
    var t=String(txt||'').toLowerCase();
    if(t.indexOf('cpf válido')>=0 || t.indexOf('cpf inval')>=0 || t.indexOf('não confere')>=0){
      return 'CPF inválido. Confira se digitou os 11 números corretamente.';
    }
    if(t.indexOf('otp expirado')>=0) return 'O código expirou. Clique em "Reenviar código" para receber um novo OTP.';
    if(t.indexOf('otp inválido')>=0 || t.indexOf('otp inval')>=0) return 'Código OTP inválido. Digite os 6 dígitos recebidos.';
    if(t.indexOf('solicite um novo código')>=0) return 'Solicite um novo código OTP para continuar.';
    if(t.indexOf('aceite')>=0) return 'Marque o aceite para continuar com a assinatura.';
    if(t.indexOf('link expirado')>=0 || t.indexOf('prazo para assinatura')>=0) return 'Este link de assinatura expirou. Solicite um novo link ao responsável.';
    return txt || 'Não foi possível concluir sua solicitação.';
  }

  function buttonState(btn, text, disabled){
    if(!btn) return;
    btn.textContent = text;
    btn.disabled = !!disabled;
  }

  function makeCooldown(buttonId, seconds){
    var btn=document.getElementById(buttonId);
    var state={ remaining:0, timer:null };
    state.start=function(seg){
      if(!btn) return;
      state.remaining=Math.max(0, parseInt(seg || seconds || 60, 10));
      if(state.timer){ clearInterval(state.timer); state.timer=null; }
      btn.disabled=state.remaining>0;
      btn.textContent=state.remaining>0?('Reenviar em '+state.remaining+'s'):'Reenviar código';
      state.timer=setInterval(function(){
        state.remaining--;
        if(state.remaining<=0){
          clearInterval(state.timer); state.timer=null;
          btn.disabled=false;
          btn.textContent='Reenviar código';
          return;
        }
        btn.textContent='Reenviar em '+state.remaining+'s';
      }, 1000);
    };
    return state;
  }

  window.SignatureCommon = {
    digits: digits,
    formatCpfValue: formatCpfValue,
    bindCpfFormatter: bindCpfFormatter,
    setStep: setStep,
    friendlyError: friendlyError,
    buttonState: buttonState,
    makeCooldown: makeCooldown
  };
})();
