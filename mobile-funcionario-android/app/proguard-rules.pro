# Mantem metadados usados por Gson para tipos genericos.
-keepattributes Signature,*Annotation*

# Mantem os modelos JSON do app para evitar quebra de parsing no build release.
-keep class br.com.rmfacilities.funcionarioapp.LoginResponse { *; }
-keep class br.com.rmfacilities.funcionarioapp.OtpStartResponse { *; }
-keep class br.com.rmfacilities.funcionarioapp.FuncionarioResumo { *; }
-keep class br.com.rmfacilities.funcionarioapp.MeResponse { *; }
-keep class br.com.rmfacilities.funcionarioapp.FuncionarioPerfil { *; }
-keep class br.com.rmfacilities.funcionarioapp.FotoUploadResponse { *; }
-keep class br.com.rmfacilities.funcionarioapp.ContatoUpdateResponse { *; }
-keep class br.com.rmfacilities.funcionarioapp.ContatoInfo { *; }
-keep class br.com.rmfacilities.funcionarioapp.SolicitacaoResponse { *; }
-keep class br.com.rmfacilities.funcionarioapp.SolicitacaoItem { *; }
-keep class br.com.rmfacilities.funcionarioapp.DocsResponse { *; }
-keep class br.com.rmfacilities.funcionarioapp.DocumentoItem { *; }
-keep class br.com.rmfacilities.funcionarioapp.MensagemItem { *; }
-keep class br.com.rmfacilities.funcionarioapp.NaoLidasResponse { *; }
-keep class br.com.rmfacilities.funcionarioapp.ApiSimpleResponse { *; }
-keep class br.com.rmfacilities.funcionarioapp.VersaoAppResponse { *; }
-keep class br.com.rmfacilities.funcionarioapp.AssinaturaHistoricoItem { *; }
-keep class br.com.rmfacilities.funcionarioapp.HistoricoAssinaturasResponse { *; }
-keep class br.com.rmfacilities.funcionarioapp.PontoMarcacaoItem { *; }
-keep class br.com.rmfacilities.funcionarioapp.PontoResumo { *; }
-keep class br.com.rmfacilities.funcionarioapp.PontoDiaResponse { *; }
