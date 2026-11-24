package org.wayland.xdgbridge

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.system.Os
import java.io.File
import java.io.FileInputStream
import java.net.NetworkInterface
import java.util.Locale

data class ShellTranslationResult(
    val layerBadge: String,
    val androidInstructions: List<String>,
    val output: String,
    val newCwd: String,
    val artJvmCall: String? = null,
    val ndkCall: String? = null,
    val ubuntuGlibcCall: String? = null
)

class UbuntuShellEngine(
    private val context: Context,
    private val container: UbuntuContainerManager,
    private val waylandRootDir: File
) {
    var currentCwd: String = "/root"
        private set

    var activeRuntimeLayer: UbuntuContainerManager.RuntimeLayer =
        UbuntuContainerManager.RuntimeLayer.AUTO

    var activeGpuBackend: UbuntuContainerManager.GpuBackend =
        UbuntuContainerManager.GpuBackend.OPENGL_VULKAN_HYBRID

    fun execute(rawCommand: String): ShellTranslationResult {
        val cmdLine = rawCommand.trim()
        if (cmdLine.isEmpty()) {
            return ShellTranslationResult("IDLE", emptyList(), "", currentCwd)
        }
        val tokens = tokenize(cmdLine)
        val cmd = tokens.firstOrNull()?.lowercase(Locale.ROOT) ?: ""
        val args = if (tokens.size > 1) tokens.drop(1) else emptyList()

        return when (cmd) {
            "help", "tradutor" -> ShellTranslationResult(
                "TRANSLATOR_SPEC",
                listOf(
                    "Camada ativa: ${activeRuntimeLayer.label}",
                    "GPU ativa: ${activeGpuBackend.label}"
                ),
                buildString {
                    appendLine("Comandos Ubuntu aarch64 traduzidos para Android (ART/JVM vs NDK + OpenGL/Vulkan):")
                    appendLine("• gpuinfo / vulkaninfo / glxinfo / arch       -> Sondagem REAL do hardware aarch64 (EGL14/GLES + Vulkan)")
                    appendLine("• vkcube / glxgears [--run]                   -> VK_KHR_android_surface / EGL ANativeWindow")
                    appendLine("• art-ndk / runtime / jni                     -> Tabela ART/JVM vs NDK Bionic vs Ubuntu aarch64")
                    appendLine("• layer <auto|art|ndk|ubuntu>                 -> Alterna camada de tradução ativa")
                    appendLine("• gpu <hybrid|vulkan|opengl|cpu>              -> Alterna backend gráfico OpenGL ES / Vulkan")
                    appendLine("• run-brain [modelo]                          -> Roda Qwen no container com OpenGL/Vulkan")
                    appendLine("• uname -a / lscpu / free -m / df -h / ip     -> Hardware/Rede real do aparelho")
                    appendLine("• xdg-open / notify-send / wl-copy / wl-paste -> Intents, Notificações e Clipboard Android")
                    appendLine("• gcc <arquivo.c> / apt install <pkg>         -> NDK aarch64 Clang (-lGLESv3 -lvulkan) / PRoot apt")
                },
                currentCwd,
                artJvmCall = "Kotlin Framework (Intent, EGL14, NotificationManager, ClipboardManager)",
                ndkCall = "aarch64-linux-android26-clang + Bionic /system/bin/linker64 + ANativeWindow",
                ubuntuGlibcCall = "Ubuntu 24.04 aarch64 nativo (zero emulação CPU · PRoot --link2symlink)"
            )

            "gpuinfo", "arch", "aarch64", "vulkaninfo", "eglinfo" -> {
                val hw = container.probeAarch64Capabilities()
                val vkSupport = if (hw.vulkanLibPresent || hw.vulkanLevel >= 0) {
                    "Sim (Nível ${hw.vulkanLevel}, Vulkan ${hw.vulkanVersion}, libvulkan=${hw.vulkanLibPresent})"
                } else {
                    "Software / ICD fallback (llvmpipe)"
                }
                ShellTranslationResult(
                    "AARCH64_GPU_PROBE",
                    listOf(
                        "ART/JVM: EGL14.eglGetDisplay() + GLES20.glGetString() + PackageManager.FEATURE_VULKAN_*",
                        "NDK aarch64: /system/lib64/libvulkan.so + libGLESv3.so + ANativeWindow",
                        "Ubuntu RootFS (${activeGpuBackend.id}): ${container.buildGpuInlineEnv(activeGpuBackend)}"
                    ),
                    buildString {
                        appendLine("Arquitetura CPU Real : ${hw.machineArch} (64-bit=${hw.is64Bit} · ${hw.cpuCores} núcleos)")
                        appendLine("ABIs Android         : ${hw.abis}")
                        appendLine("ART / JVM Runtime    : ${hw.artVmName} (v${hw.artVmVersion})")
                        appendLine("NDK Dynamic Linker   : ${if (hw.ndkLinker64Present) "/system/bin/linker64 (Bionic aarch64 OK)" else "não encontrado"}")
                        appendLine("OpenGL ES GPU Real   : ${hw.glRenderer} (${hw.glVendor})")
                        appendLine("OpenGL ES Versão     : ${hw.glVersion}")
                        appendLine("Vulkan Hardware      : $vkSupport")
                        appendLine("Perfil GPU Container : ${activeGpuBackend.label}")
                    },
                    currentCwd,
                    artJvmCall = "EGL14 + GLES20 + PackageManager (Vulkan Feature Flags)",
                    ndkCall = "vkEnumeratePhysicalDevices() + eglChooseConfig() aarch64",
                    ubuntuGlibcCall = "MESA_LOADER_DRIVER_OVERRIDE / GALLIUM_DRIVER -> ${activeGpuBackend.id}"
                )
            }

            "art-ndk", "jni", "runtime" -> {
                val hw = container.probeAarch64Capabilities()
                ShellTranslationResult(
                    "ART_VS_NDK_BRIDGE",
                    listOf(
                        "1) ART / JVM (Kotlin): UI Android, Intent, Clipboard, Notification, AudioTrack, SurfaceView",
                        "2) NDK (Bionic aarch64): C/C++ nativo sem VM -> ANativeWindow, GLESv3, Vulkan, AAudio, JNI",
                        "3) Ubuntu 24.04 aarch64: binários glibc nativos ARM64 sem QEMU -> PRoot + Wayland + Zink/VirGL"
                    ),
                    buildString {
                        appendLine("=== Comparativo no seu aparelho ${hw.machineArch} ===")
                        appendLine("ART/JVM ativo : ${hw.artVmName} v${hw.artVmVersion} (Gerencia ciclo de vida e Binder IPC)")
                        appendLine("NDK Bionic    : /system/bin/linker64 (Execução C/C++ + OpenGL ES + Vulkan a 60fps)")
                        appendLine("Ubuntu PRoot  : Mesma ISA ${hw.machineArch}! Zero tradução de instrução de CPU.")
                        appendLine("Troca rápida  : digite 'layer art', 'layer ndk', 'layer ubuntu' ou 'gpu vulkan' / 'gpu opengl'.")
                    },
                    currentCwd,
                    artJvmCall = "Dalvik/ART Bytecode -> JNI -> System.loadLibrary(\"wayland_bridge\")",
                    ndkCall = "ELF64 aarch64 PIE -> /system/bin/linker64 -> libandroid.so / libvulkan.so",
                    ubuntuGlibcCall = "ELF64 aarch64 glibc -> /lib/ld-linux-aarch64.so.1 (via PRoot)"
                )
            }

            "glxinfo", "glxgears" -> {
                val hw = container.probeAarch64Capabilities()
                if (args.any { it == "--run" }) {
                    val intent = Intent(context, CompositorActivity::class.java).apply {
                        putExtra("APP_NAME", "OpenGL ES / Zink (${hw.glRenderer})")
                        putExtra("APP_DIR", container.rootfsDir.absolutePath)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
                val isZink = activeGpuBackend == UbuntuContainerManager.GpuBackend.VULKAN ||
                    activeGpuBackend == UbuntuContainerManager.GpuBackend.OPENGL_VULKAN_HYBRID
                ShellTranslationResult(
                    "OPENGL_ES_BRIDGE",
                    listOf(
                        if (isZink)
                            "Linux GLX/OpenGL -> Mesa Zink (GALLIUM_DRIVER=zink) -> Vulkan ${hw.vulkanVersion}"
                        else
                            "Linux GLX/OpenGL -> VirGL/EGL -> Android EGL14 + ${hw.glVersion}",
                        "Display Surface: Wayland wl_surface -> Android SurfaceHolder / ANativeWindow"
                    ),
                    "GPU detectada: ${hw.glRenderer} (${hw.glVersion}).\nPipeline ativo: ${activeGpuBackend.label}.\nDica: rode 'glxgears --run' para abrir a superfície Wayland.",
                    currentCwd,
                    artJvmCall = "android.opengl.GLSurfaceView / EGL14.eglSwapBuffers()",
                    ndkCall = "EGLNativeWindowType (ANativeWindow*) + GLES3/gl32.h",
                    ubuntuGlibcCall = if (isZink) "MESA_LOADER_DRIVER_OVERRIDE=zink GALLIUM_DRIVER=zink" else "EGL_PLATFORM=wayland GALLIUM_DRIVER=virpipe"
                )
            }

            "vkcube" -> {
                val hw = container.probeAarch64Capabilities()
                if (args.any { it == "--run" }) {
                    val intent = Intent(context, CompositorActivity::class.java).apply {
                        putExtra("APP_NAME", "Vulkan WSI (${hw.vulkanVersion})")
                        putExtra("APP_DIR", container.rootfsDir.absolutePath)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
                ShellTranslationResult(
                    "VULKAN_WSI_SHIM",
                    listOf(
                        "Linux VK_KHR_wayland_surface -> Android NDK VK_KHR_android_surface",
                        "vkCreateAndroidSurfaceKHR(instance, ANativeWindow*, ...) -> Vulkan ${hw.vulkanVersion}"
                    ),
                    "Vulkan hardware nível ${hw.vulkanLevel} (versão ${hw.vulkanVersion}, libvulkan=${hw.vulkanLibPresent}).\nEnv ativo: ${container.buildGpuInlineEnv(activeGpuBackend)}",
                    currentCwd,
                    artJvmCall = "SurfaceView -> Surface -> ANativeWindow_fromSurface(env, jSurface)",
                    ndkCall = "vkCreateAndroidSurfaceKHR + vkQueuePresentKHR (libvulkan.so)",
                    ubuntuGlibcCall = "VK_ICD_FILENAMES + GGML_VK_VISIBLE_DEVICES=0"
                )
            }

            "layer" -> {
                val mode = args.firstOrNull()?.lowercase(Locale.ROOT) ?: "auto"
                activeRuntimeLayer = when {
                    mode.contains("art") || mode.contains("jvm") -> UbuntuContainerManager.RuntimeLayer.ART_JVM
                    mode.contains("ndk") || mode.contains("bionic") -> UbuntuContainerManager.RuntimeLayer.NDK_BIONIC
                    mode.contains("ubuntu") || mode.contains("glibc") -> UbuntuContainerManager.RuntimeLayer.UBUNTU_GLIBC
                    else -> UbuntuContainerManager.RuntimeLayer.AUTO
                }
                ShellTranslationResult(
                    "LAYER_SWITCH",
                    listOf("Camada de execução aarch64 alterada para: ${activeRuntimeLayer.label}"),
                    "Camada ativa: ${activeRuntimeLayer.label}. Próximos comandos usarão essa prioridade de tradução.",
                    currentCwd
                )
            }

            "gpu" -> {
                val mode = args.firstOrNull()?.lowercase(Locale.ROOT) ?: "hybrid"
                activeGpuBackend = when {
                    mode.contains("cpu") || mode.contains("soft") -> UbuntuContainerManager.GpuBackend.SOFTWARE_CPU
                    mode == "opengl" || mode == "opengl_es" || mode == "gl" -> UbuntuContainerManager.GpuBackend.OPENGL_ES
                    mode == "vulkan" || mode == "vk" -> UbuntuContainerManager.GpuBackend.VULKAN
                    else -> UbuntuContainerManager.GpuBackend.OPENGL_VULKAN_HYBRID
                }
                container.writeProotMetadata(activeGpuBackend)
                ShellTranslationResult(
                    "GPU_BACKEND_SWITCH",
                    listOf(
                        "GPU Container atualizada em /etc/waylanddroid/gpu.env:",
                        container.buildGpuInlineEnv(activeGpuBackend)
                    ),
                    "Backend gráfico alterado para: ${activeGpuBackend.label}",
                    currentCwd
                )
            }

            "pwd" -> {
                val host = resolveVfsToHost(currentCwd)
                ShellTranslationResult(
                    "VFS_CONFINED",
                    listOf("File(rootfsDir, \"${currentCwd.trimStart('/')}\").absolutePath"),
                    "$currentCwd\nHost Android: ${host.absolutePath}",
                    currentCwd
                )
            }

            "cd" -> {
                val target = args.firstOrNull() ?: "/root"
                val nextVfs = normalizeVfs(target)
                val host = resolveVfsToHost(nextVfs)
                if (host.exists() && host.isDirectory) {
                    currentCwd = nextVfs
                    ShellTranslationResult(
                        "VFS_CONFINED",
                        listOf("chdir(\"$nextVfs\") -> Directory(\"${host.absolutePath}\")"),
                        "Diretório atual: $currentCwd",
                        currentCwd
                    )
                } else {
                    ShellTranslationResult(
                        "VFS_CONFINED",
                        listOf("File(\"${host.absolutePath}\").isDirectory == false"),
                        "bash: cd: $target: Diretório não encontrado",
                        currentCwd
                    )
                }
            }

            "ls" -> {
                val targetArg = args.firstOrNull { !it.startsWith("-") } ?: "."
                val vfs = normalizeVfs(targetArg)
                val host = resolveVfsToHost(vfs)
                val entries = host.listFiles()?.sortedBy { it.name }
                if (entries == null) {
                    ShellTranslationResult(
                        "POSIX_GETDENTS64",
                        listOf("File(\"${host.absolutePath}\").listFiles() -> null"),
                        "ls: não foi possível acessar '$targetArg'",
                        currentCwd
                    )
                } else {
                    val out = entries.joinToString("  ") { if (it.isDirectory) "${it.name}/" else it.name }
                    ShellTranslationResult(
                        "POSIX_GETDENTS64",
                        listOf("readdir(\"$vfs\") -> File(\"${host.absolutePath}\").listFiles()"),
                        out.ifEmpty { "(vazio)" },
                        currentCwd
                    )
                }
            }

            "mkdir" -> {
                val targetArg = args.firstOrNull { !it.startsWith("-") }
                if (targetArg == null) {
                    ShellTranslationResult("POSIX_MKDIRAT", listOf("Falta nome da pasta"), "Uso: mkdir <pasta>", currentCwd)
                } else {
                    val vfs = normalizeVfs(targetArg)
                    val host = resolveVfsToHost(vfs)
                    val ok = host.mkdirs() || host.isDirectory
                    ShellTranslationResult(
                        "POSIX_MKDIRAT",
                        listOf("mkdirat(\"$vfs\", 0755) -> File(\"${host.absolutePath}\").mkdirs()"),
                        if (ok) "Pasta criada: $vfs (${host.absolutePath})" else "Falha ao criar $vfs",
                        currentCwd
                    )
                }
            }

            "touch" -> {
                val targetArg = args.firstOrNull()
                if (targetArg == null) {
                    ShellTranslationResult("POSIX_OPENAT", listOf("Falta arquivo"), "Uso: touch <arquivo>", currentCwd)
                } else {
                    val vfs = normalizeVfs(targetArg)
                    val host = resolveVfsToHost(vfs)
                    host.parentFile?.mkdirs()
                    if (!host.exists()) host.createNewFile()
                    ShellTranslationResult(
                        "POSIX_OPENAT",
                        listOf("openat(\"$vfs\", O_CREAT) -> File(\"${host.absolutePath}\").createNewFile()"),
                        "Arquivo pronto: $vfs",
                        currentCwd
                    )
                }
            }

            "cat" -> {
                val targetArg = args.firstOrNull()
                if (targetArg == null) {
                    ShellTranslationResult("POSIX_READ", listOf("Falta arquivo"), "Uso: cat <arquivo>", currentCwd)
                } else {
                    val vfs = normalizeVfs(targetArg)
                    val host = resolveVfsToHost(vfs)
                    if (host.exists() && host.isFile) {
                        ShellTranslationResult(
                            "POSIX_READ",
                            listOf("read(\"$vfs\") -> File(\"${host.absolutePath}\").readText()"),
                            host.readText().take(6000),
                            currentCwd
                        )
                    } else {
                        ShellTranslationResult(
                            "POSIX_READ",
                            listOf("File(\"${host.absolutePath}\").exists() == false"),
                            "cat: $targetArg: Arquivo não encontrado",
                            currentCwd
                        )
                    }
                }
            }

            "chmod" -> {
                val targetArg = args.lastOrNull()
                if (targetArg == null || args.size < 2) {
                    ShellTranslationResult("POSIX_CHMOD", listOf("Uso: chmod +x <arquivo>"), "Uso: chmod +x <arquivo>", currentCwd)
                } else {
                    val vfs = normalizeVfs(targetArg)
                    val host = resolveVfsToHost(vfs)
                    val ok = host.setExecutable(true, false)
                    ShellTranslationResult(
                        "POSIX_CHMOD",
                        listOf("fchmodat(\"$vfs\") -> File(\"${host.absolutePath}\").setExecutable(true, false)"),
                        if (ok) "Permissão +x aplicada em $vfs" else "Não foi possível alterar permissão de $vfs",
                        currentCwd
                    )
                }
            }

            "uname" -> {
                val u = Os.uname()
                ShellTranslationResult(
                    "BIONIC_UNAME",
                    listOf("Syscall uname() -> android.system.Os.uname() + Build.SUPPORTED_ABIS (${Build.SUPPORTED_ABIS.joinToString()})"),
                    "Linux ubuntu-arm64 ${u.release} ${u.version} ${u.machine} (Android ${Build.VERSION.RELEASE} / ${Build.MODEL})",
                    currentCwd
                )
            }

            "lscpu", "nproc" -> {
                val cores = Runtime.getRuntime().availableProcessors()
                ShellTranslationResult(
                    "BIONIC_SYSCONF",
                    listOf("sysconf(_SC_NPROCESSORS_ONLN) -> Runtime.getRuntime().availableProcessors() == $cores"),
                    "CPUs online: $cores · ABI: ${Build.SUPPORTED_ABIS.joinToString()}",
                    currentCwd
                )
            }

            "free" -> {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val mi = ActivityManager.MemoryInfo()
                am.getMemoryInfo(mi)
                val totalMb = mi.totalMem / (1024L * 1024L)
                val availMb = mi.availMem / (1024L * 1024L)
                val usedMb = (totalMb - availMb).coerceAtLeast(0L)
                ShellTranslationResult(
                    "ANDROID_ACTIVITY_MANAGER",
                    listOf("/proc/meminfo -> ActivityManager.getMemoryInfo(MemoryInfo)"),
                    "               total        used        free\nMem:          $totalMb MB   $usedMb MB   $availMb MB",
                    currentCwd
                )
            }

            "df" -> {
                val intStat = StatFs(context.filesDir.absolutePath)
                val extStat = StatFs(waylandRootDir.absolutePath)
                val intFreeMb = intStat.availableBytes / (1024L * 1024L)
                val extFreeMb = extStat.availableBytes / (1024L * 1024L)
                ShellTranslationResult(
                    "ANDROID_STATFS",
                    listOf("statfs(\"/\") -> StatFs(filesDir) + StatFs(WaylandDroid)"),
                    "/ (ubuntu-rootfs ext4)  livre: ${intFreeMb} MB\n/mnt/waylanddroid       livre: ${extFreeMb} MB",
                    currentCwd
                )
            }

            "ip", "ifconfig" -> {
                val outLines = mutableListOf<String>()
                try {
                    val ifaces = NetworkInterface.getNetworkInterfaces()
                    while (ifaces.hasMoreElements()) {
                        val ni = ifaces.nextElement()
                        val addrs = ni.inetAddresses
                        while (addrs.hasMoreElements()) {
                            val addr = addrs.nextElement()
                            if (!addr.isLoopbackAddress) {
                                outLines.add("${ni.name}: ${addr.hostAddress}")
                            }
                        }
                    }
                } catch (_: Exception) {}
                ShellTranslationResult(
                    "ANDROID_NETWORK_INTERFACE",
                    listOf("netlink RTM_GETADDR -> NetworkInterface.getNetworkInterfaces()"),
                    outLines.ifEmpty { listOf("lo: 127.0.0.1") }.joinToString("\n"),
                    currentCwd
                )
            }

            "ps" -> {
                val pid = Process.myPid()
                val threads = Thread.getAllStackTraces().size
                ShellTranslationResult(
                    "ANDROID_PROCESS",
                    listOf("/proc/$pid -> android.os.Process.myPid() + Thread.getAllStackTraces()"),
                    "PID=$pid  THREADS=$threads  USER=${context.packageName}  CMD=UbuntuShellEngine",
                    currentCwd
                )
            }

            "env", "printenv" -> ShellTranslationResult(
                "POSIX_ENVIRON",
                listOf("System.getenv() + Wayland/Container ENV"),
                "WAYLAND_DISPLAY=wayland-0\nXDG_RUNTIME_DIR=${context.filesDir.absolutePath}/wayland-runtime\nUBUNTU_ROOTFS=${container.rootfsDir.absolutePath}\nANDROID_WAYLAND_DATA=${waylandRootDir.absolutePath}",
                currentCwd
            )

            "xdg-open" -> {
                val url = args.firstOrNull() ?: "http://127.0.0.1:8080"
                var launched = false
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    launched = true
                } catch (_: Exception) {}
                ShellTranslationResult(
                    "INTENT_ACTION_VIEW",
                    listOf("xdg-open \"$url\" -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(\"$url\")))"),
                    if (launched) "Intent Android executada! Abrindo $url no aparelho." else "Nenhum app Android encontrado para abrir $url.",
                    currentCwd
                )
            }

            "notify-send" -> {
                val title = args.firstOrNull() ?: "Ubuntu Container"
                val body = if (args.size > 1) args.drop(1).joinToString(" ") else "Instrução executada no Android!"
                val ok = postRealAndroidNotification(title, body)
                ShellTranslationResult(
                    "ANDROID_NOTIFICATION",
                    listOf("D-Bus Notifications.Notify -> NotificationManager.notify(\"ubuntu_bridge_ch\")"),
                    if (ok) "Notificação Android real enviada: [$title] $body" else "Permissão POST_NOTIFICATIONS pendente nas configurações do app.",
                    currentCwd
                )
            }

            "wl-copy", "xclip" -> {
                val txt = args.joinToString(" ")
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("ubuntu_shell", txt))
                ShellTranslationResult(
                    "CLIPBOARD_BINDER",
                    listOf("wl_data_device -> ClipboardManager.setPrimaryClip(\"$txt\")"),
                    "Texto copiado para a área de transferência real do Android: $txt",
                    currentCwd
                )
            }

            "wl-paste" -> {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() ?: "(vazio)"
                ShellTranslationResult(
                    "CLIPBOARD_BINDER",
                    listOf("wl_data_device -> ClipboardManager.getPrimaryClip()"),
                    clip,
                    currentCwd
                )
            }

            "apt", "apt-get" -> {
                val sub = args.firstOrNull() ?: "update"
                val pkgs = args.drop(1).filter { !it.startsWith("-") }.joinToString(" ").ifEmpty { "clang" }
                val termuxPkg = pkgs.replace("build-essential", "clang make cmake").replace("python3", "python")
                ShellTranslationResult(
                    "APT_TO_PROOT_TERMUX",
                    listOf(
                        "Ubuntu PRoot: proot --link2symlink -0 -r filesDir/ubuntu-rootfs /usr/bin/apt $sub $pkgs",
                        "Nativo Android/Termux: pkg $sub -y $termuxPkg"
                    ),
                    "Use o botão 'Entrar via PRoot (Termux)' acima para rodar apt completo no rootfs Ubuntu, ou use 'pkg install -y $termuxPkg' no Termux.",
                    currentCwd
                )
            }

            "gcc", "clang", "g++" -> {
                val src = args.firstOrNull { it.endsWith(".c") || it.endsWith(".cpp") || it.endsWith(".cc") } ?: "app.cpp"
                val isCpp = cmd == "g++" || src.endsWith(".cpp") || src.endsWith(".cc")
                val ndkCompiler = if (isCpp) "aarch64-linux-android26-clang++" else "aarch64-linux-android26-clang"
                val ubuntuCompiler = if (isCpp) "g++" else "gcc"
                val gpuLibs = when (activeGpuBackend) {
                    UbuntuContainerManager.GpuBackend.VULKAN -> "-lvulkan -lEGL"
                    UbuntuContainerManager.GpuBackend.OPENGL_ES -> "-lGLESv3 -lEGL"
                    UbuntuContainerManager.GpuBackend.OPENGL_VULKAN_HYBRID -> "-lGLESv3 -lEGL -lvulkan"
                    UbuntuContainerManager.GpuBackend.SOFTWARE_CPU -> "-lm"
                }
                ShellTranslationResult(
                    "NDK_ART_COMPILER",
                    listOf(
                        "NDK Nativo (Bionic aarch64): $ndkCompiler $src $gpuLibs -landroid -llog -o app_arm64",
                        "ART/JVM JNI (.so compartilhado): $ndkCompiler -shared -fPIC $src $gpuLibs -o libubuntu_jni.so -> System.loadLibrary(\"ubuntu_jni\")",
                        "Ubuntu aarch64 (glibc PRoot): $ubuntuCompiler $src -lwayland-client -lEGL -lGLESv2 -o app_ubuntu_arm64"
                    ),
                    "Alvo compilação C/C++ (${activeRuntimeLayer.label}) + GPU (${activeGpuBackend.label}) configurado para aarch64. Use o botão 'GCC/G++ + llama' acima para instalar no Ubuntu/Termux.",
                    currentCwd,
                    artJvmCall = "System.loadLibrary(\"ubuntu_jni\") via JNIEXPORT JNICALL",
                    ndkCall = "$ndkCompiler $src $gpuLibs -landroid -llog",
                    ubuntuGlibcCall = "aarch64-linux-gnu-$ubuntuCompiler $src (PRoot glibc /lib/ld-linux-aarch64.so.1)"
                )
            }

            "launch", "am" -> {
                val intent = Intent(context, CompositorActivity::class.java).apply {
                    putExtra("APP_NAME", "Ubuntu-Wayland (${activeGpuBackend.id})")
                    putExtra("APP_DIR", container.rootfsDir.absolutePath)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ShellTranslationResult(
                    "ACTIVITY_LAUNCH",
                    listOf("Wayland Session (${activeGpuBackend.label}) -> startActivity(CompositorActivity)"),
                    "CompositorActivity aberta com SurfaceView + cursor azul.",
                    currentCwd
                )
            }

            "run-brain", "qwen" -> {
                val targetName = args.firstOrNull() ?: "Qwen-Coder-0.5B.llamafile"
                val brainsDir = File(waylandRootDir, ".brains")
                val modelsDir = File(brainsDir, "models")
                val foundLlama = File(brainsDir, targetName).exists()
                val foundGguf = modelsDir.listFiles()?.firstOrNull { it.name.endsWith(".gguf", true) }?.name
                val gpuLayers = if (activeGpuBackend == UbuntuContainerManager.GpuBackend.VULKAN ||
                    activeGpuBackend == UbuntuContainerManager.GpuBackend.OPENGL_VULKAN_HYBRID) 20 else 0
                ShellTranslationResult(
                    "UBUNTU_BRAIN_RUNNER",
                    listOf(
                        "Ubuntu PRoot Brain -> sh ./$targetName --server -ngl $gpuLayers --port 8080 (${activeGpuBackend.label})",
                        "Fallback Android NDK/Bionic -> llama-server -m /mnt/waylanddroid/.brains/models/... -ngl $gpuLayers"
                    ),
                    buildString {
                        appendLine("Pasta monitorada: ${brainsDir.absolutePath}")
                        appendLine("GPU Runner ativo: ${activeGpuBackend.label} (-ngl $gpuLayers)")
                        appendLine(".llamafile local: " + if (foundLlama) "Encontrado ($targetName)" else "Será empacotado/baixado ao executar")
                        appendLine(".gguf local: " + (foundGguf ?: "Nenhum .gguf salvo em WaylandDroid/.brains/models"))
                        appendLine("Toque em 'Rodar Cérebro' acima para iniciar na porta 8080.")
                    },
                    currentCwd
                )
            }

            "file" -> inspectBinaryTarget(args.firstOrNull())

            else -> {
                if (cmdLine.startsWith("./") || cmdLine.contains("llamafile")) {
                    inspectBinaryTarget(tokens.firstOrNull())
                } else {
                    runRealAndroidShellFallback(cmdLine)
                }
            }
        }
    }

    private fun inspectBinaryTarget(target: String?): ShellTranslationResult {
        if (target == null) {
            return ShellTranslationResult("ELF_INSPECTOR", listOf("Falta arquivo"), "Uso: file <binário>", currentCwd)
        }
        if (target.contains("llamafile", true)) {
            return ShellTranslationResult(
                "APE_PHDR_GUARD",
                listOf(
                    "Cosmopolitan APE detectado (erro 'Could not find a PHDR' no Bionic Android)",
                    "Tradução real Android: pkg install -y llama-cpp && llama-server -m <modelo.gguf> -c 2048 --port 8080 --host 127.0.0.1"
                ),
                "Executável APE redirecionado para llama-server nativo ARM64 na porta 8080.",
                currentCwd
            )
        }
        val host = resolveVfsToHost(normalizeVfs(target))
        if (!host.exists() || !host.isFile) {
            return ShellTranslationResult(
                "ELF_INSPECTOR",
                listOf("File(\"${host.absolutePath}\").exists() == false"),
                "Arquivo não encontrado: $target",
                currentCwd
            )
        }
        val head = ByteArray(16)
        FileInputStream(host).use { it.read(head) }
        val isElf = head[0] == 0x7F.toByte() && head[1] == 'E'.code.toByte() && head[2] == 'L'.code.toByte() && head[3] == 'F'.code.toByte()
        val isApe = head[0] == 'M'.code.toByte() && head[1] == 'Z'.code.toByte()
        val detail = when {
            isElf -> "ELF64 detectado. Se linkado a glibc (ld-linux-aarch64.so.1), rode dentro do container via PRoot; se Bionic (linker64), roda direto no Android."
            isApe -> "Binário Cosmopolitan APE (MZqFpD). No Android/Termux use llama-server nativo em vez de sh ./arquivo.llamafile."
            else -> "Script ou arquivo comum (${host.length()} bytes)."
        }
        return ShellTranslationResult(
            "ELF_ABI_INSPECTOR",
            listOf("Lendo cabeçalho binário real em ${host.absolutePath}"),
            detail,
            currentCwd
        )
    }

    private fun runRealAndroidShellFallback(cmdLine: String): ShellTranslationResult {
        return try {
            val workDir = resolveVfsToHost(currentCwd).takeIf { it.isDirectory } ?: container.rootfsDir
            val proc = ProcessBuilder("/system/bin/sh", "-c", cmdLine)
                .directory(workDir)
                .redirectErrorStream(true)
                .start()
            val out = proc.inputStream.bufferedReader().readText().take(6000)
            val code = proc.waitFor()
            ShellTranslationResult(
                "BIONIC_SH_EXEC",
                listOf("ProcessBuilder(\"/system/bin/sh\", \"-c\", \"$cmdLine\").directory(\"${workDir.absolutePath}\")"),
                out.ifBlank { "(exit $code)" },
                currentCwd
            )
        } catch (e: Exception) {
            ShellTranslationResult(
                "BIONIC_SH_ERROR",
                listOf("Falha ao invocar /system/bin/sh"),
                "Erro: ${e.message}",
                currentCwd
            )
        }
    }

    private fun postRealAndroidNotification(title: String, body: String): Boolean {
        return try {
            val channelId = "ubuntu_bridge_ch"
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(
                    NotificationChannel(channelId, "Ubuntu Shell Tradutor", NotificationManager.IMPORTANCE_DEFAULT)
                )
                val notif = Notification.Builder(context, channelId)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .build()
                nm.notify((System.currentTimeMillis() % 10000).toInt(), notif)
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun normalizeVfs(rawTarget: String): String {
        val base = if (rawTarget.startsWith("/")) "" else currentCwd.trimEnd('/')
        val combined = "$base/$rawTarget"
        val stack = ArrayDeque<String>()
        for (part in combined.split('/')) {
            when (part) {
                "", "." -> {}
                ".." -> if (stack.isNotEmpty()) stack.removeLast()
                else -> stack.addLast(part)
            }
        }
        return "/" + stack.joinToString("/")
    }

    private fun resolveVfsToHost(vfsPath: String): File {
        if (vfsPath == "/mnt/waylanddroid" || vfsPath.startsWith("/mnt/waylanddroid/")) {
            val rel = vfsPath.removePrefix("/mnt/waylanddroid").trimStart('/')
            return if (rel.isEmpty()) waylandRootDir else File(waylandRootDir, rel)
        }
        val rel = vfsPath.trimStart('/')
        return if (rel.isEmpty()) container.rootfsDir else File(container.rootfsDir, rel)
    }

    private fun tokenize(input: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var quote: Char? = null
        for (ch in input) {
            if (quote != null) {
                if (ch == quote) quote = null else cur.append(ch)
            } else if (ch == '"' || ch == '\'') {
                quote = ch
            } else if (ch.isWhitespace()) {
                if (cur.isNotEmpty()) {
                    out.add(cur.toString())
                    cur.clear()
                }
            } else {
                cur.append(ch)
            }
        }
        if (cur.isNotEmpty()) out.add(cur.toString())
        return out
    }
}
