# OpenCleaner Android: aba Opções (design curto)

Data: 2026-11-16. Status: aprovado pelo Flávio a partir de um mockup ("Sim").

## Por quê

No teste da 0.2.0, o Flávio sentiu falta de "um menu de configurações com opções e
informações sobre o app". O app desktop tem a página Opções; o Android segue o mesmo
princípio.

## O que entra

Quarta aba, **Opções**. As mudanças valem na hora, como no desktop.

- **Limpeza:**
  - pedir confirmação antes de limpar (padrão: ligado);
  - lembrar o que eu marco: guarda a escolha por categoria, nunca por app (padrão: ligado);
  - vídeos grandes a partir de 100 / 250 / 500 MB / 1 GB;
  - app parado depois de 30 / 60 / 90 / 180 dias;
  - voltar à seleção padrão.
- **Nunca apagar:** pastas do armazenamento interno escolhidas no seletor do sistema. Nada
  dentro delas sai, por nenhum item. Num grupo de duplicados, a cópia protegida é a que
  fica. Pasta vazia que contém uma protegida não é listada.
- **Permissões e idioma:** estado das duas permissões, com atalho; idioma do app pelo
  seletor do Android 13 (`localeConfig`). Abaixo do 13, o app segue o idioma do sistema.
- **Sobre:** versão, licença MIT, sem internet / anúncio / telemetria, crédito Lucide e link
  para o código no GitHub (abre o navegador; o app continua sem permissão de internet).

## Decisões do builder (minhas)

- `ScanOptions` em `scan/` (limites e pastas protegidas) entra em `analyze()`, com
  padrões iguais aos de antes.
- `SharedPreferences` simples (`ui/Prefs.kt`), sem DataStore: é pouca coisa e não exige
  dependência nova.
- Os rótulos ("Vídeos acima de X", "Sem abrir há N+ dias") usam as opções com que a
  análise mostrada rodou.
