# Aplicativos Android RM Facilities

## 1. RM Funcionário

Pasta do projeto:

```text
mobile-funcionario-android/
```

Gerar APK de testes:

```bash
cd mobile-funcionario-android
./gerar-apk-debug.sh
```

Gerar APK release para instalar nos celulares:

```bash
cd mobile-funcionario-android
./build-release.sh
```

Gerar AAB para Google Play:

```bash
cd mobile-funcionario-android
./gerar-aab.sh
```

Identificador Android atual: `rm.funcionario`

## 2. RM Supervisor

Pasta do projeto:

```text
rm-supervisor-android/
```

Gerar APK de testes:

```bash
cd rm-supervisor-android
./build_apk.sh
```

Gerar AAB para Google Play:

```bash
cd rm-supervisor-android
./build_aab.sh
```

## 3. RMFacilities Ponto

Projeto Android próprio:

```text
mobile-rmfacilities-ponto-android/
```

Gerar APK debug:

```bash
cd mobile-rmfacilities-ponto-android
./gerar-apk-debug.sh
```

Gerar APK release:

```bash
cd mobile-rmfacilities-ponto-android
./build-release.sh
```

O aplicativo abre o portal `/ponto`, preservando as funcionalidades da página, incluindo captura de localização e instalação PWA. O Android encaminha a permissão de GPS para o navegador interno.

Identificador Android separado: `br.com.rmfacilities.ponto`

O portal web continua disponível em:

```text
https://portal.grupormfacilities.com.br/ponto
```

O novo projeto não substitui o app `rm.funcionario`.

## Importante

Não use scripts de uma pasta dentro da outra. O nome da pasta determina qual projeto será compilado. Os APKs gerados ficam dentro da própria pasta em `app/build/outputs/`.
