# STATE — OpenCleanerForAndroid

> **Convenção deste arquivo:** só fato vivo — o que é verdade hoje e o que está
> aberto. Histórico de como chegamos aqui vive no `git log`. Ao fechar uma
> pendência, apague-a daqui (o commit registra); se ela deixou algo ainda por
> fazer, promova essa linha para Pendências. Nunca seção "Resolvidas"/"Feito"
> (o CI barra). Limite: ~150 linhas.

## Status

- **Fase:** v1 implementada (spec e plano em `docs/superpowers/`), falta validar no S20 FE.
- **No ar:** APK como artefato `OpenCleaner-apk` de cada run do CI (GitHub Actions).

## Decisões

- Build e testes só no CI: a máquina local não tem JDK nem Android SDK (escolha do Flávio). Sem Gradle wrapper no repo; o CI fixa o Gradle 8.11.1.
- APK de release assinado com a chave de debug do runner enquanto não houver keystore (`OC_KEYSTORE*` no ambiente).
- Shizuku fica para a v2, como módulo opcional.

## Pendências

<!-- Formato: - [ ] **Ação exata** → link direto | trava: … | [humano] se só o Flávio fecha -->
- [ ] **Instalar o APK no S20 FE e validar os 3 riscos da spec** (apagar em `Android/media/com.whatsapp`, diálogo de cache no One UI 5.1, quanto do WhatsApp é alcançável) → `docs/superpowers/specs/2026-11-15-opencleaner-android-design.md` | [humano]
- [ ] **Keystore de release estável**: cada run do CI assina com uma chave de debug diferente, então atualizar exige desinstalar antes. Gerar keystore, guardar em secrets do repo e passar `OC_KEYSTORE*` no CI → https://github.com/flavioricardo/OpenCleanerForAndroid/settings/secrets/actions | [humano]
- [ ] **Incluir o repo no rollout automático**: PR no `claude-config` acrescentando `flavioricardo/OpenCleanerForAndroid` em `rollout-projects.txt` e dar acesso ao token `ROLLOUT_TOKEN` → https://github.com/settings/tokens?type=beta | [humano] (token)
