# STATE — OpenCleanerForAndroid

> **Convenção deste arquivo:** só fato vivo — o que é verdade hoje e o que está
> aberto. Histórico de como chegamos aqui vive no `git log`. Ao fechar uma
> pendência, apague-a daqui (o commit registra); se ela deixou algo ainda por
> fazer, promova essa linha para Pendências. Nunca seção "Resolvidas"/"Feito"
> (o CI barra). Limite: ~150 linhas.

## Status

- **Fase:** v0.2.1 com a UI v2 no estilo do desktop (abas Saúde/Limpeza/Apps, progresso por etapa, resultado por categoria). Specs e planos em `docs/superpowers/`.
- **Validado no S20 FE (v0.1.0):** apagar em `Android/media/com.whatsapp` funciona no Android 13 (1,7 GB liberados).
- **No ar:** APK como artefato `OpenCleaner-apk` de cada run do CI (GitHub Actions).

## Decisões

- Build e testes só no CI: a máquina local não tem JDK nem Android SDK (escolha do Flávio). Sem Gradle wrapper no repo; o CI fixa o Gradle 8.11.1.
- APK de release assinado com a chave de debug do runner enquanto não houver keystore (`OC_KEYSTORE*` no ambiente).
- Shizuku fica para a v2, como módulo opcional.

## Pendências

<!-- Formato: - [ ] **Ação exata** → link direto | trava: … | [humano] se só o Flávio fecha -->
- [ ] **Validar no S20 FE quanto dos 33,5 GB do WhatsApp é alcançável** (risco 3 da spec). Risco 2 explicado: o diálogo de cache do Android só limpa cache externo (docs do StorageManager); o interno ficou app por app, na aba Apps. → `docs/superpowers/specs/2026-11-15-opencleaner-android-design.md` | [humano]
- [ ] **Keystore de release estável**: cada run do CI assina com uma chave de debug diferente, então atualizar exige desinstalar antes. Gerar keystore, guardar em secrets do repo e passar `OC_KEYSTORE*` no CI → https://github.com/flavioricardo/OpenCleanerForAndroid/settings/secrets/actions | [humano]
- [ ] **Incluir o repo no rollout automático**: PR no `claude-config` acrescentando `flavioricardo/OpenCleanerForAndroid` em `rollout-projects.txt` e dar acesso ao token `ROLLOUT_TOKEN` → https://github.com/settings/tokens?type=beta | [humano] (token)
