cmake_minimum_required(VERSION 3.22.1)
project(wayland_bridge C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

add_library(wayland_bridge SHARED
    wayland_bridge_jni.c
    main_compositor.c
)

target_compile_definitions(wayland_bridge PRIVATE
    BRIDGE_WSI_SHIM=1
    BRIDGE_XWAYLAND=0
    BRIDGE_OBOE_AUDIO=1
)

find_library(android-lib android)
find_library(log-lib log)
find_library(vulkan-lib vulkan)

target_link_libraries(wayland_bridge
    ${android-lib}
    ${log-lib}
    ${vulkan-lib}
)
