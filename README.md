# WaylandBridgeWorkstation (Wayland + XDG Bridge APK)

Este projeto Android compila automaticamente via GitHub Actions gerando `app-debug.apk` na aba **Releases**.

- **Tela Inicial (Home):** Interface nativa de celular com toque normal e rolagem simples (`ScrollView`), pasta de dados `/storage/emulated/0/WaylandDroid/` na raiz do usuário, menu para escolher/remover modelos Qwen (`.gguf` / `.llamafile`), integração **Termux (GPLv3)** via `com.termux.RUN_COMMAND` como 2ª opção do menu, empacotamento com `llamafile-0.10.6` + `zipalign` + `.args`, botão **Abrir Qwen no Navegador**, importador de repositórios GitHub para `WaylandDroid/nome_do_aplicativo` e lista de aplicativos criados.
- **Compositor Wayland (Mouse Azul):** Abre somente quando você toca em **Abrir App** dentro de um aplicativo listado.
- **Termux (GPLv3):** Integração de chamada de comando (`com.termux.permission.RUN_COMMAND`) compatível com Termux oficial (`https://github.com/termux/termux-app`).
