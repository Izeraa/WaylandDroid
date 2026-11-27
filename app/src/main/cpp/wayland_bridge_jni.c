#include <jni.h>
#include <android/native_window_jni.h>
#include <unistd.h>
#include "wayland_wire_server.h"

static BridgeCompositorState g_state = {
    .socket_fd = -1,
    .running = 0,
    .width = 0,
    .height = 0,
    .pointer_x = 240.0f,
    .pointer_y = 240.0f,
    .native_window = NULL
};

JNIEXPORT void JNICALL
Java_org_wayland_xdgbridge_CompositorActivity_nativeStartCompositor(
    JNIEnv *env, jobject thiz, jobject surface, jstring runtime_dir, jint width, jint height) {
    (void)thiz;
    const char *dir = (*env)->GetStringUTFChars(env, runtime_dir, NULL);

    g_state.native_window = ANativeWindow_fromSurface(env, surface);
    g_state.width = width;
    g_state.height = height;
    if (g_state.native_window) {
        ANativeWindow_setBuffersGeometry(g_state.native_window, 0, 0, WINDOW_FORMAT_RGBA_8888);
    }

    bridge_compositor_start(&g_state, dir);
    (*env)->ReleaseStringUTFChars(env, runtime_dir, dir);

    while (g_state.running) {
        bridge_compositor_render_frame(&g_state);
        usleep(16666);
    }
}

JNIEXPORT void JNICALL
Java_org_wayland_xdgbridge_CompositorActivity_nativeStopCompositor(JNIEnv *env, jobject thiz) {
    (void)env;
    (void)thiz;
    bridge_compositor_stop(&g_state);
    if (g_state.native_window) {
        ANativeWindow_release(g_state.native_window);
        g_state.native_window = NULL;
    }
}

JNIEXPORT void JNICALL
Java_org_wayland_xdgbridge_CompositorActivity_nativeSendPointer(JNIEnv *env, jobject thiz, jint action, jfloat x, jfloat y) {
    (void)env;
    (void)thiz;
    (void)action;
    g_state.pointer_x = x;
    g_state.pointer_y = y;
}

JNIEXPORT void JNICALL
Java_org_wayland_xdgbridge_CompositorActivity_nativeSendKey(JNIEnv *env, jobject thiz, jint key_code, jboolean down) {
    (void)env;
    (void)thiz;
    (void)key_code;
    (void)down;
}
