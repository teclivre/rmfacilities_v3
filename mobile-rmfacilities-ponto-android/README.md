# RMFacilities Ponto

Aplicativo Android separado para abrir o portal público de ponto em `/ponto`.

- Nome: RMFacilities Ponto
- `applicationId`: `br.com.rmfacilities.ponto`
- URL: `https://portal.grupormfacilities.com.br/ponto`
- GPS: a página solicita a localização e o app encaminha a permissão do Android.

## Gerar APK debug

```bash
./gerar-apk-debug.sh
```

## Gerar APK release

```bash
./build-release.sh
```

## Gerar AAB para Google Play

Antes do primeiro release, copie `keystore.properties.example` para `keystore.properties` e informe a keystore de assinatura da Play Store.

```bash
./gerar-aab.sh
```

Cada execução de `gerar-apk-debug.sh`, `build-release.sh` ou `gerar-aab.sh` incrementa automaticamente o `versionCode` e a versão em `app/version.properties`.

Os dois comandos devem ser executados dentro desta pasta, nunca dentro de `mobile-funcionario-android`.
