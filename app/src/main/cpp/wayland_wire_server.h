#ifndef WAYLAND_WIRE_SERVER_H
#define WAYLAND_WIRE_SERVER_H

#include <android/native_window.h>
#include <stdint.h>

typedef struct {
    int socket_fd;
    int running;
    int width;
    int height;
    float pointer_x;
    float pointer_y;
    ANativeWindow *native_window;
} BridgeCompositorState;

int bridge_compositor_start(BridgeCompositorState *state, const char *runtime_dir);
void bridge_compositor_render_frame(BridgeCompositorState *state);
void bridge_compositor_stop(BridgeCompositorState *state);

#endif
