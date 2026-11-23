# WaylandBridgeWorkstation (Wayland + XDG Bridge APK)

Este projeto Android compila automaticamente via GitHub Actions gerando `app-debug.apk` na aba **Releases**.

- **Tela Inicial (Home):** Interface nativa de celular com toque normal e rolagem simples (`ScrollView`), menu para escolher modelos Qwen (`.gguf` / `.llamafile`), botão **Abrir Qwen no Navegador**, importador de repositórios GitHub para `WaylandAndroid/nome_do_aplicativo` e lista de aplicativos criados.
- **Compositor Wayland (Mouse Azul):** Abre somente quando você toca em **Abrir App** dentro de um aplicativo listado.
