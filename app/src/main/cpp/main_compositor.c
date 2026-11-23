#include "wayland_wire_server.h"
#include <android/log.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>

#define LOG_TAG "WaylandXdgBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

int bridge_compositor_start(BridgeCompositorState *state, const char *runtime_dir) {
    setenv("XDG_RUNTIME_DIR", runtime_dir, 1);
    setenv("WAYLAND_DISPLAY", "wayland-0", 1);

    char sock_path[256];
    snprintf(sock_path, sizeof(sock_path), "%s/wayland-0", runtime_dir);
    unlink(sock_path);

    int fd = socket(AF_UNIX, SOCK_STREAM | SOCK_NONBLOCK, 0);
    if (fd < 0) {
        LOGI("Falha ao criar socket Unix Wayland");
        return -1;
    }

    struct sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    strncpy(addr.sun_path, sock_path, sizeof(addr.sun_path) - 1);

    if (bind(fd, (struct sockaddr *)&addr, sizeof(addr)) < 0) {
        close(fd);
        LOGI("Falha ao vincular socket em %s", sock_path);
        return -1;
    }

    listen(fd, 16);
    state->socket_fd = fd;
    state->running = 1;
    LOGI("Servidor Wayland + xdg-shell escutando em %s", sock_path);
    return 0;
}

void bridge_compositor_render_frame(BridgeCompositorState *state) {
    if (!state->native_window) return;

    ANativeWindow_Buffer buffer;
    if (ANativeWindow_lock(state->native_window, &buffer, NULL) == 0) {
        uint32_t *pixels = (uint32_t *)buffer.bits;
        int px = (int)state->pointer_x;
        int py = (int)state->pointer_y;

        for (int y = 0; y < buffer.height; y++) {
            uint32_t *row = pixels + y * buffer.stride;
            for (int x = 0; x < buffer.width; x++) {
                uint32_t color = 0xFF2A170F;
                if ((x % 48 == 0) || (y % 48 == 0)) {
                    color = 0xFF3B291E;
                }
                int dx = x - px;
                int dy = y - py;
                if (dx * dx + dy * dy <= 100) {
                    color = 0xFFF6823B; /* Ponteiro azul dentro do app aberto */
                }
                row[x] = color;
            }
        }
        ANativeWindow_unlockAndPost(state->native_window);
    }
}

void bridge_compositor_stop(BridgeCompositorState *state) {
    state->running = 0;
    if (state->socket_fd >= 0) {
        close(state->socket_fd);
        state->socket_fd = -1;
    }
}
