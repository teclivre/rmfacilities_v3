# RM Facilities Android (Supervisor)

Aplicativo Android nativo para supervisores e gestores da RM Facilities.

## Tecnologias
- Kotlin
- Jetpack Compose
- Material 3
- Arquitetura MVVM + Repository Pattern
- Dados MOCK com separação para API real

## Requisitos
- JDK 17+
- Android Studio atualizado
- Android SDK com compile SDK 35
- Gradle Wrapper (já incluído no projeto)

## Como abrir no Android Studio
1. Abra o Android Studio.
2. Selecione `Open`.
3. Escolha a pasta `rm-supervisor-android`.
4. Aguarde sincronização do Gradle.

## Estrutura principal
- `app/src/main/java/com/rmfacilities/app/data/model`: modelos de domínio
- `app/src/main/java/com/rmfacilities/app/data/repository`: contratos e repositórios (MOCK/API)
- `app/src/main/java/com/rmfacilities/app/data/network`: configuração de base URL
- `app/src/main/java/com/rmfacilities/app/data/session`: armazenamento seguro de sessão/token
- `app/src/main/java/com/rmfacilities/app/viewmodel`: lógica de UI (MVVM)
- `app/src/main/java/com/rmfacilities/app/ui/screens`: telas
- `app/src/main/java/com/rmfacilities/app/ui/navigation`: rotas e navegação
- `app/src/main/java/com/rmfacilities/app/ui/components`: componentes reutilizáveis
- `app/src/main/java/com/rmfacilities/app/ui/theme`: tema e cores

## Módulos implementados
- Login
- Dashboard do Supervisor (indicadores e resumo)
- Funcionários (lista, busca e detalhes)
- Postos (lista, busca e detalhes)
- Visitas (registro, horário, localização, foto e finalização)
- Ocorrências (cadastro + filtros por status/prioridade)
- Tarefas (lista e concluir)
- Relatórios (filtros e estrutura para exportação)
- Configurações (logout + base para notificações)

## Configuração de API
No arquivo `app/build.gradle.kts`:
- `API_BASE_URL`
- `USE_MOCK_DATA`

Exemplo atual:
- `API_BASE_URL = "https://api.exemplo.rmfacilities.com"`
- `USE_MOCK_DATA = true`

Quando houver backend real:
1. Ajuste `USE_MOCK_DATA` para `false`.
2. Implemente as chamadas em `ApiOperationsRepository.kt`.

## Segurança
- Sem credenciais hardcoded.
- Token de sessão armazenado com `EncryptedSharedPreferences` (`SecureSessionStore`).
- Estrutura pronta para autenticação real.

## Permissões Android usadas
- `ACCESS_FINE_LOCATION` (visitas)
- `CAMERA` (fotos de visita)
- `POST_NOTIFICATIONS` (estrutura de notificações)

## Build APK
Comandos:
```bash
cd rm-supervisor-android
chmod +x build_apk.sh
./build_apk.sh
```

Comando Gradle direto:
```bash
./gradlew assembleDebug
```

APK gerado em:
- `app/build/outputs/apk/debug/app-debug.apk`

## Instalar no celular via ADB
```bash
cd rm-supervisor-android
chmod +x install_apk.sh
./install_apk.sh
```

Ou manual:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Logo
- Placeholder criado em `app/src/main/res/drawable/rm_facilities_logo.png`.
- Para usar a logo oficial, substitua esse arquivo pelo PNG da marca:
  `https://rmfacilities.com.br/wp-content/uploads/2023/08/logo-rm-facilities-1.png`

## Notificações / FCM
- Arquitetura preparada para integração futura.
- Ainda sem envio real de push (MOCK).

## Desenvolvimento futuro
- Integrar endpoints reais (Flask/Supabase/API externa).
- Implementar upload real de fotos e geolocalização precisa.
- Exportação de relatórios em PDF.
- Testes instrumentados e unitários adicionais.
