# WaylandBridgeWorkstation (Wayland + XDG Bridge APK)

Este projeto Android compila automaticamente via GitHub Actions gerando `app-debug.apk` na aba **Releases**.

- **Tela Inicial (Home):** Interface nativa de celular com toque normal e rolagem simples (`ScrollView`), menu para escolher modelos Qwen (`.gguf` / `.llamafile`), empacotamento com `llamafile-0.10.6` + `zipalign` + `.args`, botão **Abrir Qwen no Navegador**, importador de repositórios GitHub para `WaylandAndroid/nome_do_aplicativo`, lista de aplicativos criados e integração **Termux (GPLv3)** via `com.termux.RUN_COMMAND` como última opção do menu.
- **Compositor Wayland (Mouse Azul):** Abre somente quando você toca em **Abrir App** dentro de um aplicativo listado.
- **Termux (GPLv3):** Integração de chamada de comando (`com.termux.permission.RUN_COMMAND`) compatível com Termux oficial (`https://github.com/termux/termux-app`).
