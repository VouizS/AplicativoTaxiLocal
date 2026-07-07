# Via Prime v0.2.0 — Split Apps Premium Base

Este pacote aplica a próxima versão do projeto Via Prime/TaxiConnect:

- cria dois APKs por productFlavors:
  - Via Prime Cliente
  - Via Prime Motoristas
- separa a experiência de cliente e motorista no mesmo código-base;
- aplica o ícone preto/dourado com coroa, ajustado para launcher/adaptive icon;
- remove os AlertDialogs brancos principais e troca por modais premium preto/dourado;
- mantém mapa real via WebView/OpenStreetMap;
- mantém localização real quando a permissão é concedida.

## Como usar

Extraia o zip na raiz do projeto Android, onde existe a pasta `app/`, e rode:

```bash
bash apply_viaprime_v020_split_apps.sh
```

APKs esperados:

```text
ViaPrime_v020_APKs/ViaPrimeCliente-v0.2.0-debug.apk
ViaPrime_v020_APKs/ViaPrimeMotoristas-v0.2.0-debug.apk
```
