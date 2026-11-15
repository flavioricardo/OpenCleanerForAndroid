# OpenCleaner para Android v1 — plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** APK que analisa o armazenamento (WhatsApp, cache, apps parados, lixo) e resolve num toque o que for seguro.

**Architecture:** Um módulo `app`. A lógica de análise é Kotlin puro sobre `java.io.File` e dados simples (pacote `scan`), testada na JVM. A camada `system` traduz APIs do Android (`StorageStatsManager`, `UsageStatsManager`, intents) para esses dados. A UI Compose é fina: chama `analyze()` e executa `Finding`s.

**Tech Stack:** Kotlin 2.1.0, AGP 8.7.3, Gradle 8.11.1, Compose BOM 2024.12.01, activity-compose 1.9.3, JUnit 4.13.2. Sem outras dependências.

**Spec:** `docs/superpowers/specs/2026-11-15-opencleaner-android-design.md`

## Global Constraints

- `minSdk 30`, `targetSdk 35`, `compileSdk 35`. applicationId `io.github.flavioricardo.opencleaner`.
- **Sem permissão `INTERNET`** no manifest final (verificado por teste, Task 6).
- Permissões: `MANAGE_EXTERNAL_STORAGE`, `PACKAGE_USAGE_STATS`, `QUERY_ALL_PACKAGES`, `REQUEST_DELETE_PACKAGES`.
- Nada em segundo plano: sem `Service`, `WorkManager`, `BroadcastReceiver` nem notificação.
- Analisar nunca apaga. Só sai arquivo com cópia idêntica byte a byte que fica, nunca por semelhança.
- O backup atual do WhatsApp (`msgstore.db.crypt*` sem data) nunca entra em lista de remoção.
- Idiomas: `values/strings.xml` (EN) e `values-pt-rBR/strings.xml` (PT-BR). Nenhum texto fixo em código de UI.
- Cor principal `#0E7C66`; tema claro e escuro seguindo o sistema.
- Autoria dos commits: `flavioricardo@live.com` (já é o `user.email` local do repo).

## Review Focus

1. **Permissão negada ou revogada:** a seção correspondente vira "precisa de permissão", sem crash (teste em Task 4: `analyze` com `apps = null`).
2. **Arquivo some ou muda entre a análise e o "Resolver":** é pulado, e o liberado conta só o que saiu de fato (teste em Task 5: `deleteFiles` com arquivo inexistente).
3. **WhatsApp ausente (pasta não existe):** nenhum item de WhatsApp, sem crash (teste em Task 3).
4. **Dezenas de milhares de arquivos:** a análise roda fora da thread principal, com indicador de progresso; o hash completo só roda nos candidatos (teste em Task 2: arquivos de tamanhos diferentes nunca são lidos).
5. **Usuário cancela o diálogo do sistema** (cache ou desinstalação): a fila segue para o próximo, e o resultado reflete o que de fato aconteceu (validação manual na Task 7).

---

## Estrutura de arquivos

```
settings.gradle.kts, build.gradle.kts, gradle.properties, gradlew(.bat), gradle/wrapper/*
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/java/io/github/flavioricardo/opencleaner/
  scan/Model.kt         Finding, Action, AppInfo
  scan/Duplicates.kt    findDuplicates()
  scan/WhatsApp.kt      whatsAppRoots(), oldBackups(), statusFiles()
  scan/Junk.kt          downloadApks(), thumbnailFiles(), emptyDirs(), largeVideos()
  scan/Analyzer.kt      analyze()
  system/Apps.kt        loadApps(context): List<AppInfo>?
  system/Access.kt      hasAllFiles(), hasUsage(), intents de concessão
  system/Actions.kt     deleteFiles(), intents de cache/desinstalar/detalhes
  ui/Theme.kt
  ui/App.kt             telas: Boas-vindas, Início/Análise, Detalhe, Resultado
  MainActivity.kt
app/src/main/res/values/strings.xml, values-pt-rBR/strings.xml, mipmap-*/ic_launcher.png
app/src/test/java/io/github/flavioricardo/opencleaner/scan/*Test.kt
.github/workflows/ci.yml
README.md
```

---

### Task 1: Projeto Gradle que compila e roda um teste vazio

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, wrapper, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `MainActivity.kt` (tela com `Text("OpenCleaner")`), `.gitignore` (`.gradle/`, `build/`, `local.properties`, `*.jks`)
- Test: `app/src/test/.../scan/SmokeTest.kt`

- [ ] **Step 1:** Gerar o wrapper com `gradle wrapper --gradle-version 8.11.1`.
- [ ] **Step 2:** `SmokeTest.kt` com `@Test fun runs() = assertEquals(2, 1 + 1)`.
- [ ] **Step 3:** Rodar `./gradlew testDebugUnitTest assembleDebug`. Esperado: `BUILD SUCCESSFUL` e `app/build/outputs/apk/debug/app-debug.apk`.
- [ ] **Step 4:** Commit `chore: scaffold Android project`.

### Task 2: Duplicados por conteúdo

**Files:** Create `scan/Model.kt`, `scan/Duplicates.kt`. Test `scan/DuplicatesTest.kt`.

**Interfaces — Produces:**
```kotlin
data class DuplicateGroup(val keep: File, val remove: List<File>, val size: Long)
fun findDuplicates(files: List<File>, keepScore: (File) -> Long): List<DuplicateGroup>
// keepScore: o MENOR valor fica. Empate: o lastModified mais antigo fica.
```

- [ ] **Step 1: Testes que falham** (arquivos criados em `TemporaryFolder`):
  - `sameContentDifferentNames_grouped`: `a.jpg` e `b.jpg` com bytes iguais, um grupo, `remove.size == 1`.
  - `sameSizeDifferentContent_notGrouped`: dois arquivos de 10 bytes diferentes, zero grupos.
  - `keepsLowestScore`: `keepScore = { if ("WhatsApp" in it.path) 1 else 0 }` e cópias em `DCIM/x.jpg` e `WhatsApp/x.jpg`: fica a de `DCIM`.
  - `tieKeepsOldest`: mesmo score; fica o de menor `lastModified`.
  - `uniqueSizes_neverRead`: arquivos de tamanhos distintos, um sem permissão de leitura (`setReadable(false)`): zero grupos, sem exceção.
- [ ] **Step 2:** `./gradlew testDebugUnitTest --tests '*DuplicatesTest'`. Esperado: FAIL (não compila).
- [ ] **Step 3:** Implementar. Agrupa por `length()`; nos grupos com 2+, hash SHA-256 dos primeiros 64 KB; nos que colidem, SHA-256 completo. Arquivo ilegível (`IOException`) é ignorado.
- [ ] **Step 4:** Rodar de novo. Esperado: PASS.
- [ ] **Step 5:** Commit `feat: find exact duplicate files`.

### Task 3: Alvos do WhatsApp e lixo genérico

**Files:** Create `scan/WhatsApp.kt`, `scan/Junk.kt`. Test `scan/WhatsAppTest.kt`, `scan/JunkTest.kt`.

**Interfaces — Produces:**
```kotlin
fun whatsAppRoots(storage: File): List<File>   // existentes entre Android/media/com.whatsapp/WhatsApp e .../com.whatsapp.w4b/WhatsApp Business
fun oldBackups(root: File): List<File>         // root/Databases: nomes que casam BACKUP_DATED
val BACKUP_DATED = Regex("""msgstore-\d{4}-\d{2}-\d{2}\.\d+\.db\.crypt\d+""")
fun statusFiles(root: File): List<File>        // root/Media/.Statuses, arquivos (sem .nomedia)
fun downloadApks(storage: File): List<File>    // storage/Download/**/*.apk
fun thumbnailFiles(storage: File): List<File>  // storage/DCIM/.thumbnails/**
fun emptyDirs(storage: File): List<File>       // dirs sem nenhum arquivo em toda a subárvore; ignora Android/ e a própria raiz
fun largeVideos(files: List<File>, minBytes: Long = 100L * 1024 * 1024): List<File> // mp4, mkv, mov, 3gp, webm
fun listFilesUnder(storage: File): List<File>  // todos os arquivos, exceto Android/data e Android/obb
```

- [ ] **Step 1: Testes que falham:**
  - `noWhatsApp_emptyRoots`: storage vazio, `whatsAppRoots == []`.
  - `oldBackups_keepsCurrent`: `Databases/` com `msgstore.db.crypt14`, `msgstore-2026-11-10.1.db.crypt14`, `msgstore-2026-11-11.1.db.crypt15` e `wa.db`: retorna só os dois datados.
  - `statuses_skipsNomedia`.
  - `emptyDirs_nested`: `a/b/c` vazio e `d/` com um arquivo: retorna `a` (a raiz da subárvore vazia, não cada filho) e não `d`.
  - `listFilesUnder_skipsAndroidData`: arquivo em `Android/data/x/f` não aparece; em `Android/media/x/f`, aparece.
  - `largeVideos_threshold`: um `.mp4` com `length` 100 MB + 1 entra; um `.jpg` do mesmo tamanho, não (usar `RandomAccessFile.setLength`).
- [ ] **Step 2:** Rodar. Esperado: FAIL.
- [ ] **Step 3:** Implementar com `File.walkTopDown().onEnter { it.path !in bloqueados }`.
- [ ] **Step 4:** Rodar. Esperado: PASS.
- [ ] **Step 5:** Commit `feat: locate WhatsApp and junk targets`.

### Task 4: `analyze()` monta a lista com os padrões da spec

**Files:** Create `scan/Analyzer.kt`; Modify `scan/Model.kt`. Test `scan/AnalyzerTest.kt`.

**Interfaces — Produces:**
```kotlin
data class AppInfo(val pkg: String, val label: String, val appBytes: Long, val dataBytes: Long,
                   val cacheBytes: Long, val lastUsed: Long /* 0 = nunca */, val system: Boolean)
sealed interface Action {
  data class DeleteFiles(val files: List<File>) : Action
  data object ClearAllCache : Action
  data class Uninstall(val pkg: String) : Action
  data class OpenAppSettings(val pkg: String) : Action   // "só você pode fazer"
}
enum class Kind { WA_DUPLICATES, WA_BACKUPS, WA_STATUSES, CACHE, APKS, THUMBNAILS, EMPTY_DIRS,
                  DUPLICATES, LARGE_VIDEOS, UNUSED_APP, MANUAL_APP, NEEDS_FILES_ACCESS, NEEDS_USAGE_ACCESS }
data class Finding(val kind: Kind, val label: String? /* nome do app, quando há */, val bytes: Long,
                   val action: Action?, val checked: Boolean)
fun analyze(storage: File?, apps: List<AppInfo>?, now: Long): List<Finding>
// storage == null: sem acesso a arquivos. apps == null: sem acesso de uso.
```

Regras:
- Duplicados: um só `findDuplicates` sobre `listFilesUnder(storage)` com arquivos ≥ 100 KB (`// ponytail: piso de 100 KB evita milhares de ícones; baixar se faltar ganho`), `keepScore = 1` se o arquivo está sob uma raiz do WhatsApp, senão `0`. Cópias a remover sob o WhatsApp entram em `WA_DUPLICATES`, as demais em `DUPLICATES`.
- `UNUSED_APP`: `!system && pkg != own && (lastUsed == 0 || now - lastUsed > 30 dias)`, um `Finding` por app, ordenado por `appBytes + dataBytes` decrescente.
- `MANUAL_APP`: `!system && dataBytes >= 1 GB`, fora os do WhatsApp e os já listados como parados.
- `CACHE`: soma de `cacheBytes` de todos os apps.
- Marcados: `WA_DUPLICATES, WA_BACKUPS, WA_STATUSES, CACHE, APKS, THUMBNAILS, EMPTY_DIRS, DUPLICATES`. Desmarcados: `LARGE_VIDEOS, UNUSED_APP`. `MANUAL_APP` e `NEEDS_*` não têm checkbox (`checked = false`).
- Itens com `bytes == 0` (exceto `EMPTY_DIRS` e `NEEDS_*`) não aparecem.

- [ ] **Step 1: Testes que falham:**
  - `defaults_matchSpec`: storage com 1 backup datado, 1 vídeo grande, 1 duplicado; apps com 1 parado: `WA_BACKUPS.checked`, `!LARGE_VIDEOS.checked`, `!UNUSED_APP.checked`.
  - `noUsageAccess_showsNeedsUsage`: `apps = null`, contém `NEEDS_USAGE_ACCESS` e nenhum `CACHE` ou `UNUSED_APP`.
  - `noFilesAccess_showsNeedsFiles`: `storage = null`, contém `NEEDS_FILES_ACCESS`.
  - `unusedApp_rules`: sistema excluído; usado há 29 dias excluído; usado há 31 dias e nunca usado incluídos.
  - `waDuplicate_keepsGalleryCopy`: o mesmo arquivo em `DCIM/` e no WhatsApp: `WA_DUPLICATES` remove o do WhatsApp; `DUPLICATES` ausente.
  - `currentBackup_neverListed`: nenhum `Action.DeleteFiles` em nenhum `Finding` contém `msgstore.db.crypt14`.
- [ ] **Step 2:** Rodar. Esperado: FAIL.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** Rodar. Esperado: PASS.
- [ ] **Step 5:** Commit `feat: assemble findings with safe defaults`.

### Task 5: Camada `system` (dados e ações do Android)

**Files:** Create `system/Apps.kt`, `system/Access.kt`, `system/Actions.kt`; Modify `AndroidManifest.xml` (4 permissões, `<queries>` não precisa com `QUERY_ALL_PACKAGES`). Test `scan/DeleteTest.kt` (só `deleteFiles`, que é JVM puro).

**Interfaces — Produces:**
```kotlin
fun hasAllFiles(): Boolean                       // Environment.isExternalStorageManager()
fun hasUsage(ctx: Context): Boolean              // AppOpsManager OPSTR_GET_USAGE_STATS == MODE_ALLOWED
fun allFilesIntent(ctx: Context): Intent         // ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, package uri
fun usageIntent(): Intent                        // Settings.ACTION_USAGE_ACCESS_SETTINGS
fun loadApps(ctx: Context): List<AppInfo>?       // null se !hasUsage
fun deleteFiles(files: List<File>): Long         // bytes realmente removidos; dirs vazios via delete()
fun clearCacheIntent(): Intent                   // StorageManager.ACTION_CLEAR_APP_CACHE
fun uninstallIntent(pkg: String): Intent         // ACTION_DELETE, "package:$pkg"
fun appSettingsIntent(pkg: String): Intent       // ACTION_APPLICATION_DETAILS_SETTINGS
fun storageUsage(): Pair<Long, Long>             // (usado, total) via StatFs(Environment.getExternalStorageDirectory())
```
`loadApps`: `PackageManager.getInstalledApplications(0)`; `StorageStatsManager.queryStatsForPackage(StorageManager.UUID_DEFAULT, pkg, myUserHandle())`; último uso de `UsageStatsManager.queryAndAggregateUsageStats(now - 365 dias, now)[pkg]?.lastTimeUsed ?: 0`; `system = flags & FLAG_SYSTEM != 0 && flags & FLAG_UPDATED_SYSTEM_APP == 0`. Pacote que lança exceção é pulado.
Depois do `deleteFiles`, chamar `MediaScannerConnection.scanFile` nos caminhos removidos para a galeria esquecer deles.

- [ ] **Step 1: Teste que falha** `deleteFiles_countsOnlyRemoved`: dois arquivos de 10 e 20 bytes, apaga o de 10 antes de chamar; retorno `== 20` e sem exceção.
- [ ] **Step 2:** Rodar. Esperado: FAIL.
- [ ] **Step 3:** Implementar os arquivos.
- [ ] **Step 4:** `./gradlew testDebugUnitTest assembleDebug`. Esperado: PASS e build ok.
- [ ] **Step 5:** Commit `feat: read app stats and run system actions`.

### Task 6: UI completa

**Files:** Create `ui/Theme.kt`, `ui/App.kt`, `res/values/strings.xml`, `res/values-pt-rBR/strings.xml`, ícones `mipmap-*/ic_launcher.png` (de `assets/OpenCleaner.png` do repo do Windows); Modify `MainActivity.kt`. Test `scan/ManifestTest.kt`.

Telas (estado em `remember`/`rememberSaveable`, sem ViewModel nem navegação de biblioteca):
- **Boas-vindas:** aparece enquanto falta `hasAllFiles()` ou `hasUsage()`. Duas linhas explicativas, cada uma com botão "Conceder", e "Continuar mesmo assim". Reavalia em `ON_RESUME`.
- **Início:** barra de uso (`storageUsage()`), botão **Analisar**. Durante a análise (`Dispatchers.IO`), `CircularProgressIndicator`. Depois, a lista de `Finding`s com checkbox, tamanho (`Formatter.formatShortFileSize`) e o total liberável; botão **Resolver (X)** soma os marcados. `MANUAL_APP` aparece em "Só você pode fazer" com botão "Abrir". `NEEDS_*` aparecem como aviso com "Conceder".
- **Detalhe:** toque num item com `DeleteFiles` lista os arquivos (caminho relativo, tamanho) com checkbox individual. Em `WA_DUPLICATES`, o aviso `wa_duplicates_warning`: "The file stays on your phone, but shows as unavailable in that chat." / "O arquivo continua no celular, mas aparece como indisponível naquela conversa."
- **Resolver:** apaga arquivos em `Dispatchers.IO`; depois encadeia os intents (`ClearAllCache` e cada `Uninstall`) com `rememberLauncherForActivityResult(StartActivityForResult())`, avançando a fila no retorno, seja qual for o resultado.
- **Resultado:** "Liberado: X" (`storageUsage()` antes menos depois, nunca negativo) e "Só você pode fazer". Botão "Analisar de novo".

- [ ] **Step 1: Teste que falha** `ManifestTest.noInternetPermission`: lê `src/main/AndroidManifest.xml` e garante que não contém `android.permission.INTERNET` e contém as 4 permissões da spec.
- [ ] **Step 2:** Rodar. Esperado: PASS para INTERNET e FAIL se faltar alguma das 4 (validar que o teste pega isso comentando uma).
- [ ] **Step 3:** Implementar as telas e os textos nos dois idiomas.
- [ ] **Step 4:** `./gradlew testDebugUnitTest assembleDebug lintDebug`. Esperado: tudo ok.
- [ ] **Step 5:** Verificar no APK que a permissão não entrou por dependência: `aapt2 dump permissions app/build/outputs/apk/debug/app-debug.apk` não lista `INTERNET`.
- [ ] **Step 6:** Commit `feat: analysis and resolve screens`.

### Task 7: CI, release e README

**Files:** Create `.github/workflows/ci.yml`, `README.md`; Modify `app/build.gradle.kts` (release com R8 e `signingConfig` lido de variáveis de ambiente `OC_KEYSTORE`, `OC_KEYSTORE_PASSWORD`, `OC_KEY_ALIAS`, `OC_KEY_PASSWORD`; sem elas, release usa a assinatura de debug).

- [ ] **Step 1:** `ci.yml`: `ubuntu-latest`, `actions/setup-java@v4` (temurin 17), `./gradlew testDebugUnitTest assembleDebug`; job chamado `ci`.
- [ ] **Step 2:** `./gradlew assembleRelease`. Esperado: APK em `app/build/outputs/apk/release/`, menor que 5 MB.
- [ ] **Step 3:** README (EN, como o do Windows): o que faz, permissões e por quê, o que não faz (sem segundo plano, sem rede, sem "acelerar RAM"), como instalar o APK, como compilar.
- [ ] **Step 4:** Commit `ci: build and test on push`.
- [ ] **Step 5 (humano):** instalar o APK no S20 FE e validar os 3 riscos da spec e o item 5 da Review Focus.
