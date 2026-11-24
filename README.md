# WaylandBridgeWorkstation (Wayland + XDG Bridge APK)

Este projeto Android compila automaticamente via GitHub Actions gerando `app-debug.apk` na aba **Releases**.

- **Tela Inicial (Home):** Interface nativa de celular com rolagem simples (`ScrollView`), pasta de dados `/storage/emulated/0/WaylandDroid/`, lista de LLMs já baixados no topo, presets diretos (Qwen 3 / 2.5, Gemma 3, SmolLM2, Llama 3.2 e SD-Turbo q4_0 / q8_0), download com retomada HTTP `Range` e aviso de espaço livre (`StatFs`), remoção de modelos e `allowBackup="false"`.
- **2º Plano + `pkill -f llama-server`:** Serviço persistente (`BridgeSessionService`) com WakeLock, WifiLock e botão direto no topo para encerrar `llama-server` no Termux (`pkill -f llama-server`).
- **Termux (GPLv3) & Container Ubuntu 24.04 (aarch64):** Botões para instalar `llama-server` e compilador `GCC / G++` (`clang`/`clang++` no Termux e `g++`/`gcc` + build `llama-server` no Ubuntu PRoot) além de rodar `.llamafile` e `.gguf`.
- **Compositor Wayland (Mouse Azul):** Abre quando você toca em **Abrir App** dentro de um aplicativo listado.
