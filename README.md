# WaylandBridgeWorkstation (Wayland + XDG Bridge APK)

Este pacote contém o projeto Android completo (Kotlin + C11 NDK `arm64-v8a` + Gradle + GitHub Actions) pronto para compilar o APK na nuvem **sem precisar instalar o Android Studio**.

## Como gerar o APK em 3 passos pelo GitHub

1. Crie um repositório vazio no seu GitHub.
2. Extraia este `.zip` e envie todos os arquivos (incluindo a pasta `.github/workflows/build-apk.yml`) para o repositório.
3. No seu repositório do GitHub, abra a aba **Actions**, clique no workflow **Build Android APK (ARM64)** e baixe o arquivo **wayland_bridge-arm64-apk** (contendo o `app-debug.apk`) em **Artifacts**.
