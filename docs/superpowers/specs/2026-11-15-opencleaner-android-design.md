# OpenCleaner para Android — design

Data: 2026-11-15. Status: rascunho para aprovação do Flávio.

## Intenção

Nasceu de uma dor pessoal: o Galaxy S20 FE do Flávio (Android 13 / One UI 5.1, última versão
que o aparelho recebe) está com **126 de 128 GB usados (98%)**, além de lento e com bateria
fraca. O objetivo é o mesmo princípio do OpenCleaner de Windows
(github.com/flavioricardo/OpenCleaner): grátis, open source (MIT), sem telemetria, sem anúncio,
sem versão paga, e **analisar nunca apaga**.

A vantagem sobre as ferramentas que já existem (o "Gerenciar armazenamento" do WhatsApp, a
Assistência do aparelho da Samsung) é **visão geral num lugar só + "resolve pra mim"**: um
toque analisa tudo, mostra o quadro e resolve o que for seguro. É a "Verificação de saúde" do
OpenCleaner de Windows trazida para o celular.

Primeiro usuário: o Flávio, no S20 FE. Depois, quem baixar do GitHub.

### Diagnóstico real do aparelho (base das prioridades)

| Categoria (One UI) | Tamanho |
|---|---|
| Apps | 91,71 GB |
| System | 20,80 GB |
| Videos | 5,46 GB |
| Other files | 5,76 GB |
| Images | 1,72 GB |
| Resto | < 0,5 GB |

Maiores apps: **WhatsApp 33,51 GB**, Pokémon UNITE 8,51 GB (em "Deep sleeping", sem uso),
Netflix 3,85 GB (em "Deep sleeping"), Spotify 3,14 GB, Instagram 1,60 GB.

Conclusão: o centro da v1 é **WhatsApp + tamanho por app + apps parados**. Mídia geral e
duplicados no armazenamento são secundários.

## Objetivos (v1)

1. **Visão geral**: uso total do armazenamento e os maiores consumidores, por app (separando
   app, dados e cache) e por tipo de lixo.
2. **Faxina do WhatsApp** (e do WhatsApp Business, se instalado), na pasta
   `Android/media/com.whatsapp/WhatsApp/` (e `com.whatsapp.w4b`):
   - mídia duplicada (o mesmo arquivo, byte a byte, em vários chats ou também na galeria):
     mantém uma cópia. Se uma das cópias está fora do WhatsApp (DCIM, Pictures, Download),
     **fica a de fora**; senão, fica a mais antiga. Consequência aceita pelo Flávio: a mensagem
     cuja cópia saiu mostra "mídia indisponível" naquela conversa (o banco é criptografado, não
     há como redirecionar), e o detalhe do item avisa isso;
   - não há item separado para `Sent/`: o WhatsApp recomprime o que envia, então a cópia quase
     nunca é idêntica ao original. O que for idêntico de verdade já cai em "duplicados".
     Comparação por semelhança (hash perceptual) está fora, porque um falso positivo apagaria a
     única cópia;
   - backups locais antigos do banco (`Databases/msgstore-AAAA-MM-DD.*.db.crypt*`), mantendo
     sempre o atual (`msgstore.db.crypt*`, sem data);
   - status já vistos (`Media/.Statuses`);
   - vídeos grandes (acima de 100 MB): listados, nunca marcados por padrão.
3. **Cache de todos os apps**: o total calculado por app e a limpeza pelo diálogo do sistema
   (`StorageManager.ACTION_CLEAR_APP_CACHE`), com antes e depois.
4. **Apps parados**: apps instalados pelo usuário sem abrir há 30+ dias, ordenados por tamanho,
   com data do último uso. Ação: desinstalar (diálogo do sistema) ou abrir a página do app nas
   Configurações (onde ficam a suspensão profunda e "limpar dados"). É a resposta da v1 para
   lentidão e bateria.
5. **Lixo genérico no armazenamento compartilhado**: APKs em `Download/`, `DCIM/.thumbnails`,
   pastas vazias, duplicados exatos fora do WhatsApp.
6. **"Resolver"**: um botão executa todos os itens marcados. Depois, uma tela de resultado com
   o espaço liberado (antes e depois).
7. **"Só você pode fazer"**: lista de ações que nenhum app de fora executa (ex.: apagar
   downloads da Netflix, músicas offline do Spotify), cada uma com atalho para a tela certa.

## Não objetivos (v1)

- Nada em segundo plano: sem serviço, sem agendamento, sem notificação. O Flávio abre o app
  quando quiser (decisão dele).
- Shizuku, root ou ADB. Decidido: Shizuku vira **módulo opcional da v2** (bateria por app via
  `batterystats`, restringir segundo plano, bloatware da Samsung com lista curada, recompilar
  apps, `Android/data`). A v1 não depende dele, mas não fecha a porta: se ativo, ele só
  acrescenta itens à mesma lista de "Resolver".
- Limpar o cache app por app, ou limpar dados de outro app (o Android não deixa sem Shizuku).
- Saber de qual conversa do WhatsApp veio cada arquivo (o banco é criptografado).
- Google Play na v1 (a política restringe `MANAGE_EXTERNAL_STORAGE`). A distribuição é APK no
  GitHub Releases; F-Droid depois.
- Formato de regras configurável (o `builtin.ini` do Windows). A lista de alvos fica no código.

## Antiobjetivos (fracasso mesmo que funcione)

- **Apagar a única cópia de uma foto ou vídeo pessoal.** Por isso só sai arquivo com cópia
  idêntica byte a byte que fica, e nunca por semelhança.
- **Apagar o backup local atual do WhatsApp** (`msgstore.db.crypt*` sem data).
- **Qualquer acesso à rede.** O app **não declara a permissão `INTERNET`**: é a garantia
  verificável de que não há telemetria.
- **Teatro de otimização**: "acelerar RAM", "esfriar CPU", matar processos, alerta alarmista
  de "problemas encontrados". No Android isso não ajuda e às vezes piora.
- Ficar pesado: o APK deve ficar na casa de poucos MB.

## Restrições

- **Android mínimo: 11 (API 30)**. É a versão em que existem `ACTION_CLEAR_APP_CACHE` e
  `MANAGE_EXTERNAL_STORAGE`. O S20 FE está no 13. *(Decisão minha.)*
- Permissões especiais, pedidas numa tela de boas-vindas que explica cada uma:
  - **Acesso a todos os arquivos** (`MANAGE_EXTERNAL_STORAGE`): ler e apagar no
    armazenamento compartilhado, incluindo `Android/media`.
  - **Acesso a dados de uso** (`PACKAGE_USAGE_STATS`): tamanho por app
    (`StorageStatsManager`) e último uso (`UsageStatsManager`).
  - Normais: `QUERY_ALL_PACKAGES` (listar apps), `REQUEST_DELETE_PACKAGES` (diálogo de
    desinstalação).
  - Sem alguma delas, a parte correspondente aparece como "precisa de permissão", sem
    quebrar o resto.
- `Android/data` e `Android/obb` são inacessíveis no Android 13 sem Shizuku. O app não tenta
  contornar.
- Toda ação que o Android exige confirmar (cache geral, cada desinstalação) passa pelo diálogo
  do sistema. "Resolver" encadeia os diálogos, sem tentar evitá-los.
- Idiomas: português (Brasil) e inglês, seguindo o idioma do sistema, como no Windows.

## Abordagens consideradas

| Abordagem | Prós | Contras | Veredito |
|---|---|---|---|
| **Kotlin + Jetpack Compose (nativo)** | Acesso direto às APIs de armazenamento, permissões e intents; APK de 2 a 4 MB; F-Droid sem atrito; caminho aberto para Shizuku | Linguagem nova para o Flávio (ele revisa e testa, Claude escreve) | **Escolhida pelo Flávio** |
| React Native (Expo) + TypeScript | Stack do dia a dia do Flávio | Quase tudo é API nativa: metade viraria módulo Kotlin de qualquer jeito; APK de 20 a 30 MB | Descartada |
| .NET MAUI | Mantém o C# do Windows | Nenhum código reaproveitável (o núcleo do Windows é todo registro, winget e Win32); runtime de ~30 MB | Descartada |
| PWA | — | Sem acesso a arquivos nem a dados de apps | Descartada |

## Como funciona (decidido na conversa)

### Fluxo

1. **Primeira abertura**: boas-vindas com as permissões, cada uma explicada em uma linha.
2. **Tela principal**: barra de uso (ex.: "126 de 128 GB usados") e botão **Analisar**.
3. **Análise** (só lê, nunca apaga): calcula todos os itens e mostra a lista com tamanho e
   checkbox, já com os padrões seguros marcados.
4. **Resolver (X GB)**: executa os marcados. Arquivos são apagados direto. Cache geral e
   desinstalações abrem o diálogo do sistema, um por vez.
5. **Resultado**: espaço liberado (antes e depois) e a lista "só você pode fazer".

Rascunho da tela principal depois da análise (números ilustrativos):

```
OpenCleaner
126 de 128 GB usados   ▓▓▓▓▓▓▓▓▓░
Dá pra liberar ~20 GB

[x] WhatsApp: mídia duplicada       4,2 GB
[x] WhatsApp: backups antigos       1,8 GB
[x] Cache de todos os apps          2,5 GB
[ ] Pokémon UNITE (parado há 90d)   8,5 GB
[ ] Vídeos acima de 100 MB          ...

        [ Resolver: 8,5 GB ]

Só você pode fazer:
  Netflix: apagar downloads    [abrir]
```

Cada item abre um detalhe com os arquivos ou apps que entram, como no Windows ("mostra
exatamente quais arquivos vão sair"). Dá para desmarcar arquivos individuais.

### Padrões de marcação

| Item | Padrão | Motivo |
|---|---|---|
| WhatsApp: duplicados (mantém 1 cópia, de preferência a de fora) | marcado | o arquivo continua no celular; a conversa mostra "indisponível" (decisão do Flávio) |
| WhatsApp: backups antigos com data | marcado | o backup atual fica |
| WhatsApp: `.Statuses` | marcado | cache de status vistos |
| Cache de todos os apps | marcado | os apps recriam |
| APKs em `Download/`, `.thumbnails`, pastas vazias | marcado | lixo |
| Duplicados exatos fora do WhatsApp | marcado (mantém 1) | nada se perde |
| Vídeos acima de 100 MB | desmarcado | pessoal |
| Apps parados (desinstalar) | desmarcado | decisão do usuário |

### Identidade visual

A mesma do OpenCleaner de Windows: cor principal `#0E7C66`, ícones Lucide (os SVGs já estão
em `assets/icons` no repo do Windows, convertidos para vector drawables) e o mesmo ícone do
app. Tema claro e escuro seguindo o sistema. *(Decisão minha.)*

## Fica para quem construir

- Estrutura do projeto Gradle, versões de SDK alvo, AGP e Compose (as estáveis atuais).
- Algoritmo de duplicados. Sugestão: agrupar por tamanho, depois hash dos primeiros 64 KB,
  depois SHA-256 completo só nos candidatos.
- Arquitetura interna (ViewModel, corrotinas), com o mínimo que funcione.
- Testes: lógica pura (classificação de backups, regra do `Sent/`, agrupamento de duplicados,
  padrões de marcação) com testes unitários JVM. O resto se valida à mão no S20 FE.
- CI no GitHub Actions: build e testes. Assinatura do APK de release.
- Textos finais em PT-BR e EN.

## Riscos a verificar cedo (primeiro passo da implementação)

1. Confirmar no S20 FE que `MANAGE_EXTERNAL_STORAGE` lê e apaga em `Android/media/com.whatsapp`
   no Android 13. Se não apagar, o maior ganho da v1 cai, e a decisão sobre Shizuku volta.
2. Confirmar que `ACTION_CLEAR_APP_CACHE` funciona no One UI 5.1.
3. Medir quanto dos 33,5 GB do WhatsApp está em `Android/media` (alcançável) e quanto em
   `Android/data` ou no banco (inalcançável).
