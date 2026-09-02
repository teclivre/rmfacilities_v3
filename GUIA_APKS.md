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

O app Android separado do ponto ainda não possui uma pasta própria neste repositório. A página `/ponto` é um PWA/web app e pode ser instalada pelo navegador.

Para criar um APK separado do RM Funcionário, ele deverá ficar em:

```text
mobile-rmfacilities-ponto-android/
```

Esse novo projeto deverá usar outro identificador Android, por exemplo `br.com.rmfacilities.ponto`, para não substituir o app `rm.funcionario`.

## Importante

Não use scripts de uma pasta dentro da outra. O nome da pasta determina qual projeto será compilado. Os APKs gerados ficam dentro da própria pasta em `app/build/outputs/`.
