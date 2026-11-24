# WaylandBridgeWorkstation (Wayland + XDG Bridge APK)

Este projeto Android compila automaticamente via GitHub Actions gerando `app-debug.apk` na aba **Releases**.

- **Tela Inicial (Home):** Interface nativa de celular organizada de 1 a 6 (`1. Modelos Baixados e Comando de Executar`, `2. Modelos para Baixar + Empacotar`, `3. Termux`, `4. Container Ubuntu 24.04`, `5. Baixar Aplicativo do GitHub`, `6. Aplicativos Criados`), pasta `/storage/emulated/0/WaylandDroid/`, 13 presets (Qwen 3 / 2.5, Gemma 3, SmolLM2, Llama 3.2 e SD-Turbo q4_0 / q8_0), retomada HTTP `Range`, aviso de espaço livre (`StatFs`), remoção de modelos e `allowBackup="false"`.
- **2º Plano + Encerramento Rápido:** Serviço persistente (`BridgeSessionService`) com WakeLock, WifiLock e botões diretos no topo (logo abaixo de `WakeLock: ATIVO`) para `pkill -f llama-server` e **Fechar Container Ubuntu** (`UbuntuShellActivity.closeIfOpen()` + `pkill` PRoot).
- **Termux (GPLv3) & Container Ubuntu 24.04 (aarch64):** Botões para instalar `llama-server` e compilador `GCC / G++` (`clang`/`clang++` no Termux e `g++`/`gcc` + build `llama-server` no Ubuntu PRoot) além de rodar `.llamafile` e `.gguf`.
- **Compositor Wayland (Mouse Azul):** Abre quando você toca em **Abrir App** dentro de um aplicativo listado.
