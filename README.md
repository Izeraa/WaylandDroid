# WaylandBridgeWorkstation (Wayland + XDG Bridge APK)

Este projeto Android compila automaticamente via GitHub Actions gerando `app-debug.apk` na aba **Releases**.

- **Tela Inicial (Home):** Interface nativa de celular com toque normal e rolagem simples (`ScrollView`), pasta de dados `/storage/emulated/0/WaylandDroid/` na raiz do usuário, menu para escolher/remover modelos Qwen (`.gguf` / `.llamafile`), integração **Termux (GPLv3)** via `com.termux.RUN_COMMAND` como 2ª opção do menu, empacotamento com `llamafile-0.10.6` + `zipalign` + `.args`, botão **Abrir Qwen no Navegador**, importador de repositórios GitHub para `WaylandDroid/nome_do_aplicativo` e lista de aplicativos criados.
- **Compositor Wayland (Mouse Azul):** Abre somente quando você toca em **Abrir App** dentro de um aplicativo listado.
- **Termux (GPLv3):** Integração de chamada de comando (`com.termux.permission.RUN_COMMAND`) compatível com Termux oficial (`https://github.com/termux/termux-app`).

## Guia rápido (modelos, libs e imagens)

- **Menu 1 · Modelos Baixados:** lista os `.llamafile`/`.gguf` já salvos em `WaylandDroid/.brains` (e modelos de imagem em `.brains/image-models`). Toque para selecionar e use **Iniciar Modelo Selecionado (:8080)**. O download de modelos novos e os comandos de execução ficam na seção de baixo do mesmo cartão. Baixar um modelo agora retoma downloads interrompidos e avisa se faltar espaço.
- **Modos de execução:**
  - *Termux Nativo (llama-server)*, *.llamafile direto* e *Container Ubuntu (PRoot)*.
  - *libs llama.cpp b12651 · ARM64 CPU / Snapdragon (Termux)*: copie os zips oficiais `llama-*-bin-android-arm64.zip` (e/ou `-snapdragon.zip`) para `WaylandDroid/.brains/libs/`. Na 1ª execução eles são extraídos em `~/llama-cpp/` dentro do Termux. Essas libs são do Android (bionic) e **não** funcionam dentro do container Ubuntu (glibc).
- **Container Ubuntu:** o rootfs fica em `~/ubuntu-rootfs` no Termux (o Termux não acessa a pasta privada do app). Rode `termux-setup-storage` uma vez para o Termux ler `/sdcard/WaylandDroid`.
- **Botão `pkill llama-server`** (ao lado do WakeLock): encerra o servidor iniciado pelo app e envia `pkill -f llama-server` ao Termux.
- **Modelos de imagem (🖼 SD-Turbo):** baixados para `.brains/image-models/`; ao tocar em Iniciar o app pede um prompt e roda o `stable-diffusion.cpp` no Termux (compila na 1ª vez). As imagens saem em `WaylandDroid/images/`.

