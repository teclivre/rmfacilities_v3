# RM Funcionário

Este é o aplicativo Android dos funcionários. Ele não é o aplicativo separado RMFacilities Ponto.

## Gerar APK

APK de testes/debug:

```bash
./gerar-apk-debug.sh
```

APK release para instalação:

```bash
./build-release.sh
```

AAB para Google Play:

```bash
./gerar-aab.sh
```

Os arquivos gerados aparecem em:

```text
app/build/outputs/apk/
app/build/outputs/bundle/release/
```

Identificador Android: `rm.funcionario`

O projeto separado `RMFacilities Ponto` ainda deverá ser criado em `../mobile-rmfacilities-ponto-android/`, com outro identificador Android, para poder ser instalado junto com este aplicativo.
