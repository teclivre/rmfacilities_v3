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

Os dois comandos devem ser executados dentro desta pasta, nunca dentro de `mobile-funcionario-android`.
