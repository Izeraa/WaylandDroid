package org.wayland.xdgbridge

import android.content.Context
import android.content.pm.PackageManager
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.os.Build
import android.os.Process
import android.system.Os
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.EOFException
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

class UbuntuContainerManager(
    private val context: Context,
    private val waylandRootDir: File
) {
    enum class GpuBackend(val id: String, val label: String) {
        OPENGL_VULKAN_HYBRID("opengl_vulkan_hybrid", "Híbrido: OpenGL ES 3.2 + Vulkan (Zink/EGL)"),
        VULKAN("vulkan", "Vulkan Hardware (Adreno/Mali ICD + Zink)"),
        OPENGL_ES("opengl_es", "OpenGL ES 3.2 (EGL / VirGL Wayland)"),
        SOFTWARE_CPU("software_cpu", "Software CPU (Mesa llvmpipe)");

        companion object {
            fun fromId(raw: String?): GpuBackend {
                return values().firstOrNull { it.id == raw } ?: OPENGL_VULKAN_HYBRID
            }
        }
    }

    enum class RuntimeLayer(val id: String, val label: String) {
        AUTO("auto", "Auto (ART/JVM + NDK + Ubuntu aarch64)"),
        ART_JVM("art_jvm", "ART / JVM (Kotlin Framework + JNI)"),
        NDK_BIONIC("ndk_bionic", "NDK Nativo (aarch64-linux-android / Bionic)"),
        UBUNTU_GLIBC("ubuntu_glibc", "Ubuntu aarch64 RootFS (glibc PRoot)");

        companion object {
            fun fromId(raw: String?): RuntimeLayer {
                return values().firstOrNull { it.id == raw } ?: AUTO
            }
        }
    }

    data class Aarch64HardwareReport(
        val machineArch: String,
        val abis: String,
        val is64Bit: Boolean,
        val cpuCores: Int,
        val artVmName: String,
        val artVmVersion: String,
        val ndkLinker64Present: Boolean,
        val vulkanLibPresent: Boolean,
        val vulkanLevel: Int,
        val vulkanVersion: String,
        val glVendor: String,
        val glRenderer: String,
        val glVersion: String
    ) {
        fun shortBadge(): String {
            val vkBadge = if (vulkanVersion != "indisponível") "Vulkan $vulkanVersion" else "Vulkan N/D"
            return "$machineArch · $cpuCores cores · GL: $glRenderer · $vkBadge"
        }
    }

    companion object {
        const val UBUNTU_BASE_ARM64_URL =
            "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.1-base-arm64.tar.gz"
    }

    // Armazenado no ext4/f2fs interno do app para suportar symlinks POSIX e chmod +x reais
    val rootfsDir: File = File(context.filesDir, "ubuntu-rootfs").apply { mkdirs() }
    val containerMetaDir: File = File(waylandRootDir, ".ubuntu-container").apply { mkdirs() }

    fun isFullUbuntuInstalled(): Boolean {
        val osRelease = File(rootfsDir, "etc/os-release")
        val bashBin = File(rootfsDir, "bin/bash")
        return osRelease.exists() && bashBin.exists()
    }

    fun probeAarch64Capabilities(): Aarch64HardwareReport {
        val uname = try { Os.uname().machine } catch (_: Throwable) { "aarch64" }
        val abis = Build.SUPPORTED_ABIS.joinToString(", ")
        val is64 = Process.is64Bit()
        val cores = Runtime.getRuntime().availableProcessors()
        val vmName = System.getProperty("java.vm.name") ?: "Dalvik/ART"
        val vmVer = System.getProperty("java.vm.version") ?: Build.VERSION.SDK_INT.toString()
        val linker64 = File("/system/bin/linker64").exists()
        val vkLib = File("/system/lib64/libvulkan.so").exists() || File("/vendor/lib64/libvulkan.so").exists()

        var vkLevel = -1
        var vkVersion = "indisponível"
        try {
            val feats = context.packageManager.systemAvailableFeatures
            for (f in feats) {
                if (f.name == PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL) {
                    vkLevel = f.version
                } else if (f.name == PackageManager.FEATURE_VULKAN_HARDWARE_VERSION) {
                    val v = f.version
                    val major = (v ushr 22) and 0x3FF
                    val minor = (v ushr 12) and 0x3FF
                    val patch = v and 0xFFF
                    vkVersion = "$major.$minor.$patch"
                }
            }
        } catch (_: Throwable) {}

        var glVendor = "Android EGL"
        var glRenderer = "OpenGL ES GPU"
        var glVersion = "OpenGL ES 3.x"
        var dpy = EGL14.EGL_NO_DISPLAY
        var surf = EGL14.EGL_NO_SURFACE
        var ctx = EGL14.EGL_NO_CONTEXT
        try {
            dpy = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val vers = IntArray(2)
            if (dpy != EGL14.EGL_NO_DISPLAY && EGL14.eglInitialize(dpy, vers, 0, vers, 1)) {
                val cfgAttr = intArrayOf(
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                    EGL14.EGL_RED_SIZE, 8,
                    EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_NONE
                )
                val configs = arrayOfNulls<EGLConfig>(1)
                val numCfg = IntArray(1)
                if (EGL14.eglChooseConfig(dpy, cfgAttr, 0, configs, 0, 1, numCfg, 0) && numCfg[0] > 0) {
                    val ctxAttr = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
                    ctx = EGL14.eglCreateContext(dpy, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttr, 0)
                    val pbufAttr = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
                    surf = EGL14.eglCreatePbufferSurface(dpy, configs[0], pbufAttr, 0)
                    if (ctx != EGL14.EGL_NO_CONTEXT && surf != EGL14.EGL_NO_SURFACE && EGL14.eglMakeCurrent(dpy, surf, surf, ctx)) {
                        glVendor = GLES20.glGetString(GLES20.GL_VENDOR) ?: glVendor
                        glRenderer = GLES20.glGetString(GLES20.GL_RENDERER) ?: glRenderer
                        glVersion = GLES20.glGetString(GLES20.GL_VERSION) ?: glVersion
                    }
                }
            }
        } catch (_: Throwable) {
        } finally {
            try {
                if (dpy != EGL14.EGL_NO_DISPLAY) {
                    EGL14.eglMakeCurrent(dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                    if (surf != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(dpy, surf)
                    if (ctx != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(dpy, ctx)
                    EGL14.eglTerminate(dpy)
                }
            } catch (_: Throwable) {}
        }

        return Aarch64HardwareReport(
            machineArch = uname,
            abis = abis,
            is64Bit = is64,
            cpuCores = cores,
            artVmName = vmName,
            artVmVersion = vmVer,
            ndkLinker64Present = linker64,
            vulkanLibPresent = vkLib,
            vulkanLevel = vkLevel,
            vulkanVersion = vkVersion,
            glVendor = glVendor,
            glRenderer = glRenderer,
            glVersion = glVersion
        )
    }

    fun buildGpuEnvExports(gpu: GpuBackend): String {
        return when (gpu) {
            GpuBackend.OPENGL_VULKAN_HYBRID -> """
                export WAYLAND_GPU_BACKEND="opengl_vulkan_hybrid"
                export LIBGL_ALWAYS_SOFTWARE=0
                export EGL_PLATFORM=wayland
                export MESA_GLES_VERSION_OVERRIDE=3.2
                export MESA_LOADER_DRIVER_OVERRIDE=zink
                export GALLIUM_DRIVER=zink
                export ZINK_DESCRIPTORS=lazy
                export GGML_VK_VISIBLE_DEVICES=0
            """.trimIndent()
            GpuBackend.VULKAN -> """
                export WAYLAND_GPU_BACKEND="vulkan"
                export LIBGL_ALWAYS_SOFTWARE=0
                export MESA_LOADER_DRIVER_OVERRIDE=zink
                export GALLIUM_DRIVER=zink
                export ZINK_DESCRIPTORS=lazy
                export GGML_VK_VISIBLE_DEVICES=0
                export VK_LOADER_DEBUG=error
            """.trimIndent()
            GpuBackend.OPENGL_ES -> """
                export WAYLAND_GPU_BACKEND="opengl_es"
                export LIBGL_ALWAYS_SOFTWARE=0
                export EGL_PLATFORM=wayland
                export MESA_GLES_VERSION_OVERRIDE=3.2
                export GALLIUM_DRIVER=virpipe
            """.trimIndent()
            GpuBackend.SOFTWARE_CPU -> """
                export WAYLAND_GPU_BACKEND="software_cpu"
                export LIBGL_ALWAYS_SOFTWARE=1
                export GALLIUM_DRIVER=llvmpipe
            """.trimIndent()
        }
    }

    fun buildGpuInlineEnv(gpu: GpuBackend): String {
        return when (gpu) {
            GpuBackend.OPENGL_VULKAN_HYBRID -> "LIBGL_ALWAYS_SOFTWARE=0 EGL_PLATFORM=wayland MESA_GLES_VERSION_OVERRIDE=3.2 MESA_LOADER_DRIVER_OVERRIDE=zink GALLIUM_DRIVER=zink ZINK_DESCRIPTORS=lazy GGML_VK_VISIBLE_DEVICES=0"
            GpuBackend.VULKAN -> "LIBGL_ALWAYS_SOFTWARE=0 MESA_LOADER_DRIVER_OVERRIDE=zink GALLIUM_DRIVER=zink ZINK_DESCRIPTORS=lazy GGML_VK_VISIBLE_DEVICES=0"
            GpuBackend.OPENGL_ES -> "LIBGL_ALWAYS_SOFTWARE=0 EGL_PLATFORM=wayland MESA_GLES_VERSION_OVERRIDE=3.2 GALLIUM_DRIVER=virpipe"
            GpuBackend.SOFTWARE_CPU -> "LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe"
        }
    }

    fun initSkeletonIfNeeded() {
        val dirs = listOf(
            "etc", "etc/apt", "etc/waylanddroid", "root", "bin", "usr/bin",
            "tmp", "dev", "proc", "sys", "mnt/waylanddroid", "sdcard"
        )
        for (d in dirs) {
            File(rootfsDir, d).mkdirs()
        }
        val osRelease = File(rootfsDir, "etc/os-release")
        if (!osRelease.exists()) {
            osRelease.writeText(
                "PRETTY_NAME=\"Ubuntu 24.04 LTS (aarch64 Container Bridge)\"\n" +
                    "NAME=\"Ubuntu\"\nVERSION_ID=\"24.04\"\nID=ubuntu\nVERSION_CODENAME=noble\n"
            )
        }
        val resolv = File(rootfsDir, "etc/resolv.conf")
        if (!resolv.exists()) {
            resolv.writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n")
        }
        val hosts = File(rootfsDir, "etc/hosts")
        if (!hosts.exists()) {
            hosts.writeText("127.0.0.1 localhost\n::1 localhost\n")
        }
        val bashrc = File(rootfsDir, "root/.bashrc")
        if (!bashrc.exists()) {
            bashrc.writeText(
                "export WAYLAND_DISPLAY=wayland-0\n" +
                    "export XDG_RUNTIME_DIR=/tmp/wayland-runtime\n" +
                    "[ -f /etc/waylanddroid/gpu.env ] && . /etc/waylanddroid/gpu.env\n" +
                    "export PS1=\"root@ubuntu-aarch64:\\w# \"\n"
            )
        }
        val waylandSample = File(rootfsDir, "root/hello_wayland.c")
        if (!waylandSample.exists()) {
            waylandSample.writeText(
                "#include <stdio.h>\nint main(void) { printf(\"Ubuntu aarch64 -> Android ART/NDK + OpenGL/Vulkan\\n\"); return 0; }\n"
            )
        }
        writeProotMetadata(GpuBackend.OPENGL_VULKAN_HYBRID)
    }

    fun writeProotMetadata(gpu: GpuBackend = GpuBackend.OPENGL_VULKAN_HYBRID) {
        val gpuEnvFile = File(rootfsDir, "etc/waylanddroid/gpu.env")
        gpuEnvFile.parentFile?.mkdirs()
        gpuEnvFile.writeText(buildGpuEnvExports(gpu) + "\n")

        val prootScript = buildProotLaunchScript(gpu)
        File(containerMetaDir, "run_ubuntu_proot.sh").writeText(prootScript)

        val hw = probeAarch64Capabilities()
        val meta = JSONObject().apply {
            put("distro", "Ubuntu 24.04 LTS (noble aarch64)")
            put("arch", hw.machineArch)
            put("abis", hw.abis)
            put("artVm", "${hw.artVmName} ${hw.artVmVersion}")
            put("ndkLinker64", hw.ndkLinker64Present)
            put("gpuBackend", gpu.id)
            put("glRenderer", hw.glRenderer)
            put("glVersion", hw.glVersion)
            put("vulkanVersion", hw.vulkanVersion)
            put("vulkanLevel", hw.vulkanLevel)
            put("rootfsPath", rootfsDir.absolutePath)
            put("sharedBind", waylandRootDir.absolutePath)
            put("fullRootfsReady", isFullUbuntuInstalled())
            put("imageUrl", UBUNTU_BASE_ARM64_URL)
        }
        File(containerMetaDir, "container.json").writeText(meta.toString(2))
    }

    fun buildProotLaunchScript(gpu: GpuBackend = GpuBackend.OPENGL_VULKAN_HYBRID): String {
        val rootPath = "\$HOME/ubuntu-rootfs" // dentro do Termux (o Termux nao acessa /data/user/0/<app>)
        val sharedPath = waylandRootDir.absolutePath
        val gpuInline = buildGpuInlineEnv(gpu)
        return """
            pkg update -y >/dev/null 2>&1 || true
            command -v proot >/dev/null 2>&1 || pkg install -y proot wget tar
            mkdir -p "$rootPath"
            if [ ! -f "$rootPath/bin/bash" ]; then
              cd "$rootPath"
              wget -c "$UBUNTU_BASE_ARM64_URL" -O "${'$'}HOME/ubuntu-base-arm64.tar.gz" || { echo "Falha ao baixar o rootfs Ubuntu (sem internet?)"; exit 1; }
              proot --link2symlink -0 tar -xzf "${'$'}HOME/ubuntu-base-arm64.tar.gz" || true
              rm -f "${'$'}HOME/ubuntu-base-arm64.tar.gz"
            fi
            mkdir -p "$rootPath/etc"
            echo "nameserver 8.8.8.8" > "$rootPath/etc/resolv.conf"
            proot --link2symlink -0 -r "$rootPath" -b /dev -b /proc -b /sys -b /sdcard:/sdcard -b "$sharedPath":/mnt/waylanddroid -w /root /usr/bin/env -i HOME=/root TERM=xterm-256color WAYLAND_DISPLAY=wayland-0 $gpuInline PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin /bin/bash --login
        """.trimIndent()
    }

    fun buildProotRunBrainScript(
        modelFileName: String,
        outputLlamafileName: String,
        modelUrl: String,
        gpu: GpuBackend = GpuBackend.OPENGL_VULKAN_HYBRID
    ): String {
        val rootPath = "\$HOME/ubuntu-rootfs" // dentro do Termux (o Termux nao acessa /data/user/0/<app>)
        val sharedPath = waylandRootDir.absolutePath
        val gpuInline = buildGpuInlineEnv(gpu)
        val ngl = if (gpu == GpuBackend.VULKAN || gpu == GpuBackend.OPENGL_VULKAN_HYBRID) "20" else "0"
        return """
            if curl -s -m 2 http://127.0.0.1:8080/health >/dev/null 2>&1; then echo "Servidor ja esta ativo em http://127.0.0.1:8080 (pkill -f llama-server para reiniciar)"; exit 0; fi
            if ! ls /sdcard/WaylandDroid >/dev/null 2>&1; then echo "AVISO: sem acesso ao armazenamento. Rode 'termux-setup-storage' e aceite a permissao."; fi
            pkg update -y >/dev/null 2>&1 || true
            command -v proot >/dev/null 2>&1 || pkg install -y proot wget curl tar
            mkdir -p "$rootPath/root/.brains/models"
            if [ ! -f "$rootPath/bin/bash" ]; then
              cd "$rootPath"
              wget -c "$UBUNTU_BASE_ARM64_URL" -O "${'$'}HOME/ubuntu-base-arm64.tar.gz" || { echo "Falha ao baixar o rootfs Ubuntu (sem internet?)"; exit 1; }
              proot --link2symlink -0 tar -xzf "${'$'}HOME/ubuntu-base-arm64.tar.gz" || true
              rm -f "${'$'}HOME/ubuntu-base-arm64.tar.gz"
              if [ ! -f "$rootPath/bin/bash" ]; then echo "Rootfs nao foi extraido corretamente."; exit 1; fi
            fi
            mkdir -p "$rootPath/etc" "$rootPath/tmp"
            echo "nameserver 8.8.8.8" > "$rootPath/etc/resolv.conf"
            cd "$rootPath/root/.brains"
            MODEL_FOUND=""
            for p in "$sharedPath/.brains/models/$modelFileName" "/sdcard/WaylandDroid/.brains/models/$modelFileName" "${'$'}HOME/WaylandDroid/.brains/models/$modelFileName" "${'$'}HOME/WaylandAndroid/.brains/models/$modelFileName"; do
              if [ -f "${'$'}p" ]; then MODEL_FOUND="${'$'}p"; break; fi
            done
            LLAMA_FOUND=""
            for l in "$sharedPath/.brains/$outputLlamafileName" "/sdcard/WaylandDroid/.brains/$outputLlamafileName" "${'$'}HOME/WaylandDroid/.brains/$outputLlamafileName" "${'$'}HOME/WaylandAndroid/.brains/$outputLlamafileName"; do
              if [ -f "${'$'}l" ]; then LLAMA_FOUND="${'$'}l"; break; fi
            done
            if [ -n "${'$'}LLAMA_FOUND" ]; then
              cp "${'$'}LLAMA_FOUND" "./$outputLlamafileName" && chmod 755 "./$outputLlamafileName"
            fi
            if [ -n "${'$'}MODEL_FOUND" ] && [ ! -f "./models/$modelFileName" ]; then
              cp "${'$'}MODEL_FOUND" "./models/$modelFileName"
            fi
            if [ ! -f "./$outputLlamafileName" ]; then
              if [ ! -f "./models/$modelFileName" ]; then
                curl -L -C - -o "./models/$modelFileName" "$modelUrl"
              fi
              case "$modelFileName" in
                *.llamafile|*.LLAMAFILE)
                  cp "./models/$modelFileName" "./$outputLlamafileName" && chmod 755 "./$outputLlamafileName"
                  ;;
                *)
                  if [ ! -f ./llamafile ]; then curl -L -o ./llamafile 'https://github.com/mozilla-ai/llamafile/releases/download/0.10.6/llamafile-0.10.6' && chmod 755 ./llamafile; fi
                  if [ ! -f ./zipalign ]; then curl -L -o ./zipalign 'https://github.com/mozilla-ai/llamafile/releases/download/0.10.6/zipalign-0.10.6' && chmod 755 ./zipalign; fi
                  printf '%s\n' '-m' '/zip/$modelFileName' '-c' '2048' '-n' '512' '--threads' '4' '--gpu' 'disable' '-ngl' '$ngl' '--load-mode' 'mmap' '...' > ./models/.args
                  cp ./llamafile "./$outputLlamafileName" && chmod 755 "./$outputLlamafileName"
                  proot --link2symlink -0 -r "$rootPath" -b /dev -b /proc -b /sys -b "$sharedPath":/mnt/waylanddroid -w /root/.brains/models /usr/bin/env -i HOME=/root TMPDIR=/tmp PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin sh ../zipalign -j0 "../$outputLlamafileName" "$modelFileName" .args || (cd ./models && sh ../zipalign -j0 "../$outputLlamafileName" "$modelFileName" .args) || true
                  ;;
              esac
            fi
            LIBS_DIR="$sharedPath/.brains/libs"
            mkdir -p "${'$'}LIBS_DIR"
            LIBS_BIND=""
            if [ -n "${'$'}(ls -A "${'$'}LIBS_DIR" 2>/dev/null)" ]; then LIBS_BIND="-b ${'$'}LIBS_DIR:/opt/llama-libs"; fi
            LIBS_ENV="LD_LIBRARY_PATH=/opt/llama-libs:/opt/llama-libs/lib:/usr/local/lib:/usr/lib/aarch64-linux-gnu:/lib/aarch64-linux-gnu"
            if [ -f "./$outputLlamafileName" ]; then
              proot --link2symlink -0 -r "$rootPath" -b /dev -b /proc -b /sys -b /sdcard:/sdcard -b "$sharedPath":/mnt/waylanddroid ${'$'}LIBS_BIND -w /root/.brains /usr/bin/env -i HOME=/root TMPDIR=/tmp $gpuInline ${'$'}LIBS_ENV PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin /bin/bash -lc "sh ./$outputLlamafileName --server --nobrowser -ngl $ngl --port 8080" && exit 0
            fi
            if [ -n "${'$'}LIBS_BIND" ] && [ -x "${'$'}LIBS_DIR/bin/llama-server" ] && [ -f "./models/$modelFileName" ]; then
              proot --link2symlink -0 -r "$rootPath" -b /dev -b /proc -b /sys -b /sdcard:/sdcard -b "$sharedPath":/mnt/waylanddroid ${'$'}LIBS_BIND -w /root/.brains /usr/bin/env -i HOME=/root TMPDIR=/tmp $gpuInline ${'$'}LIBS_ENV PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin /opt/llama-libs/bin/llama-server -m "./models/$modelFileName" -c 2048 -t 4 -ngl $ngl --port 8080 --host 127.0.0.1 && exit 0
            fi
            command -v llama-server >/dev/null 2>&1 || pkg install -y llama-cpp
            llama-server -m "./models/$modelFileName" -c 2048 -t 4 -ngl $ngl --port 8080 --host 127.0.0.1
        """.trimIndent()
    }

    fun getStatusSummary(): String {
        val count = rootfsDir.listFiles()?.size ?: 0
        val hw = probeAarch64Capabilities()
        return if (isFullUbuntuInstalled()) {
            "Ubuntu 24.04 aarch64 pronto ($count pastas) · ${hw.shortBadge()}"
        } else {
            "Ubuntu Kotlin aarch64 ativo (ART/NDK/PRoot) · ${hw.shortBadge()}"
        }
    }

    fun installFullUbuntuBaseArm64(onProgress: (Int, String) -> Unit) {
        initSkeletonIfNeeded()
        val archiveFile = File(containerMetaDir, "ubuntu-base-24.04.1-base-arm64.tar.gz")
        val partFile = File(containerMetaDir, "ubuntu-base-arm64.part")

        onProgress(5, "Conectando em cdimage.ubuntu.com (Ubuntu 24.04 ARM64)...")
        val conn = (URL(UBUNTU_BASE_ARM64_URL).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 20000
            readTimeout = 45000
        }
        conn.connect()
        if (conn.responseCode !in 200..299) {
            throw Exception("HTTP ${conn.responseCode} ao baixar Ubuntu Base ARM64")
        }
        val total = conn.contentLengthLong
        conn.inputStream.use { inp ->
            FileOutputStream(partFile).use { out ->
                val buf = ByteArray(16384)
                var got = 0L
                var lastTick = 0L
                while (true) {
                    val n = inp.read(buf)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                    got += n
                    val now = System.currentTimeMillis()
                    if (now - lastTick > 250) {
                        lastTick = now
                        val mb = got / (1024 * 1024)
                        val pct = if (total > 0) ((got * 60) / total).toInt().coerceIn(5, 65) else 35
                        onProgress(pct, "Baixando Ubuntu 24.04 ARM64: ${mb} MB...")
                    }
                }
            }
        }
        if (archiveFile.exists()) archiveFile.delete()
        if (!partFile.renameTo(archiveFile)) {
            partFile.copyTo(archiveFile, overwrite = true)
            partFile.delete()
        }

        onProgress(70, "Extraindo rootfs tar.gz no ext4 do Android...")
        var extractedFiles = 0
        archiveFile.inputStream().use { fis ->
            GZIPInputStream(BufferedInputStream(fis, 32768)).use { gzis ->
                extractedFiles = extractTarStream(gzis, rootfsDir) { count ->
                    if (count % 250 == 0) {
                        onProgress((70 + (count / 200)).coerceAtMost(96), "Extraindo arquivos Ubuntu: $count...")
                    }
                }
            }
        }
        initSkeletonIfNeeded()
        writeProotMetadata()
        onProgress(100, "Ubuntu 24.04 ARM64 instalado ($extractedFiles entradas)!")
    }

    private fun extractTarStream(
        input: InputStream,
        destRoot: File,
        onEntry: (Int) -> Unit
    ): Int {
        val header = ByteArray(512)
        val rootCanonical = destRoot.canonicalPath + File.separator
        var pendingLongName: String? = null
        var pendingLongLink: String? = null
        var count = 0

        while (true) {
            val readHdr = readFully(input, header)
            if (readHdr == 0 || isZeroBlock(header)) break

            val rawName = parseTarString(header, 0, 100)
            val mode = parseOctal(header, 100, 8)
            val size = parseOctal(header, 124, 12)
            val typeFlag = header[156].toInt().toChar()
            val linkName = pendingLongLink ?: parseTarString(header, 157, 100)
            val prefix = parseTarString(header, 345, 155)
            val fullName = pendingLongName ?: if (prefix.isNotEmpty()) "$prefix/$rawName" else rawName
            pendingLongName = null
            pendingLongLink = null

            if (typeFlag == 'L' || typeFlag == 'K') {
                val metaBytes = ByteArray(size.toInt().coerceIn(0, 65536))
                readFully(input, metaBytes)
                skipPadding(input, size)
                val str = String(metaBytes).trimEnd('\u0000', '\n', '\r')
                if (typeFlag == 'L') pendingLongName = str else pendingLongLink = str
                continue
            }

            if (typeFlag == 'x' || typeFlag == 'g') {
                val paxBytes = ByteArray(size.toInt().coerceIn(0, 65536))
                readFully(input, paxBytes)
                skipPadding(input, size)
                val paxText = String(paxBytes)
                for (line in paxText.lines()) {
                    val sp = line.indexOf(' ')
                    if (sp > 0) {
                        val kv = line.substring(sp + 1)
                        if (kv.startsWith("path=")) pendingLongName = kv.removePrefix("path=")
                        if (kv.startsWith("linkpath=")) pendingLongLink = kv.removePrefix("linkpath=")
                    }
                }
                continue
            }

            val cleanRel = fullName.replace('\\', '/').trimStart('.', '/')
            if (cleanRel.isEmpty() || cleanRel.contains("..")) {
                skipDataAndPadding(input, size)
                continue
            }

            val targetFile = File(destRoot, cleanRel)
            if (!targetFile.canonicalPath.startsWith(rootCanonical) && targetFile.canonicalPath != destRoot.canonicalPath) {
                skipDataAndPadding(input, size)
                continue
            }

            when (typeFlag) {
                '5' -> targetFile.mkdirs()
                '2' -> {
                    targetFile.parentFile?.mkdirs()
                    if (targetFile.exists()) targetFile.delete()
                    try {
                        Os.symlink(linkName, targetFile.absolutePath)
                    } catch (_: Throwable) {
                        // Alguns sistemas de arquivo ignoram symlink; ignora sem abortar extracao
                    }
                }
                '0', '\u0000' -> {
                    targetFile.parentFile?.mkdirs()
                    FileOutputStream(targetFile).use { out ->
                        val buf = ByteArray(16384)
                        var rem = size
                        while (rem > 0) {
                            val chunk = input.read(buf, 0, minOf(buf.size.toLong(), rem).toInt())
                            if (chunk <= 0) throw EOFException("Tar truncado")
                            out.write(buf, 0, chunk)
                            rem -= chunk
                        }
                    }
                    skipPadding(input, size)
                    if ((mode and 64L) != 0L) {
                        targetFile.setExecutable(true, false)
                    }
                }
                else -> skipDataAndPadding(input, size)
            }
            count++
            onEntry(count)
        }
        return count
    }

    private fun readFully(input: InputStream, buf: ByteArray): Int {
        var offset = 0
        while (offset < buf.size) {
            val n = input.read(buf, offset, buf.size - offset)
            if (n < 0) return if (offset == 0) 0 else throw EOFException("Header tar incompleto")
            offset += n
        }
        return offset
    }

    private fun skipPadding(input: InputStream, size: Long) {
        val mod = (size % 512L).toInt()
        if (mod > 0) {
            var rem = (512 - mod).toLong()
            while (rem > 0) {
                val skipped = input.skip(rem)
                if (skipped <= 0) {
                    if (input.read() < 0) break
                    rem--
                } else {
                    rem -= skipped
                }
            }
        }
    }

    private fun skipDataAndPadding(input: InputStream, size: Long) {
        var rem = size
        val buf = ByteArray(8192)
        while (rem > 0) {
            val n = input.read(buf, 0, minOf(buf.size.toLong(), rem).toInt())
            if (n <= 0) break
            rem -= n
        }
        skipPadding(input, size)
    }

    private fun isZeroBlock(buf: ByteArray): Boolean = buf.all { it == 0.toByte() }

    private fun parseTarString(buf: ByteArray, offset: Int, len: Int): String {
        var end = offset
        val limit = offset + len
        while (end < limit && buf[end] != 0.toByte()) end++
        return String(buf, offset, end - offset)
    }

    private fun parseOctal(buf: ByteArray, offset: Int, len: Int): Long {
        val raw = parseTarString(buf, offset, len).trim()
        if (raw.isEmpty()) return 0L
        return raw.toLongOrNull(8) ?: 0L
    }
}
