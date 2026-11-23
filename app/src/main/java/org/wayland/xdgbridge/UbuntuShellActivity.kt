package org.wayland.xdgbridge

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File

class UbuntuShellActivity : AppCompatActivity() {

    private lateinit var container: UbuntuContainerManager
    private lateinit var engine: UbuntuShellEngine

    private lateinit var statusText: TextView
    private lateinit var installProgress: ProgressBar
    private lateinit var terminalLogView: TextView
    private lateinit var terminalScroll: ScrollView
    private lateinit var cmdInput: EditText

    private var selectedGpu: UbuntuContainerManager.GpuBackend =
        UbuntuContainerManager.GpuBackend.OPENGL_VULKAN_HYBRID
    private var selectedLayer: UbuntuContainerManager.RuntimeLayer =
        UbuntuContainerManager.RuntimeLayer.AUTO

    private var brainModelFile: String = "qwen2.5-coder-0.5b-instruct-q5_k_m.gguf"
    private var brainLlamafileName: String = "Qwen-Coder-0.5B.llamafile"
    private var brainModelUrl: String =
        "https://huggingface.co/Qwen/Qwen2.5-Coder-0.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-0.5b-instruct-q5_k_m.gguf"

    private val logBuilder = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        brainModelFile = intent.getStringExtra("BRAIN_MODEL_FILE") ?: brainModelFile
        brainLlamafileName = intent.getStringExtra("BRAIN_LLAMAFILE_NAME") ?: brainLlamafileName
        brainModelUrl = intent.getStringExtra("BRAIN_MODEL_URL") ?: brainModelUrl
        selectedGpu = UbuntuContainerManager.GpuBackend.fromId(intent.getStringExtra("GPU_BACKEND"))
        selectedLayer = UbuntuContainerManager.RuntimeLayer.fromId(intent.getStringExtra("RUNTIME_LAYER"))
        val autoRunBrain = intent.getBooleanExtra("AUTO_RUN_BRAIN", false)

        val rootDir = resolveWaylandRootDir()
        container = UbuntuContainerManager(this, rootDir)
        container.initSkeletonIfNeeded()
        container.writeProotMetadata(selectedGpu)
        engine = UbuntuShellEngine(this, container, rootDir).apply {
            activeGpuBackend = selectedGpu
            activeRuntimeLayer = selectedLayer
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 9101)
        }

        val hw = container.probeAarch64Capabilities()
        setContentView(buildUi())
        appendLog(
            "[UBUNTU AARCH64 KOTLIN] WaylandBridgeWorkstation inicializado.\n" +
                "[HARDWARE REAL] ${hw.shortBadge()} | ART=${hw.artVmName} | NDK linker64=${hw.ndkLinker64Present}\n" +
                "[CAMADA ATIVA] ${selectedLayer.label}\n" +
                "[GPU CONTAINER] ${selectedGpu.label}\n" +
                "[ANDROID BRIDGE] RootFS ext4: ${container.rootfsDir.absolutePath}\n" +
                "Digite um comando Ubuntu ou toque nos atalhos abaixo (ex: gpuinfo, art-ndk, vulkaninfo, glxinfo).\n"
        )
        if (autoRunBrain) {
            runCommand("run-brain $brainLlamafileName")
            launchBrainInUbuntuContainer()
        }
    }

    private fun resolveWaylandRootDir(): File {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            val candidate = File(Environment.getExternalStorageDirectory(), "WaylandDroid")
            if ((candidate.exists() || candidate.mkdirs()) && candidate.canWrite()) return candidate
        }
        val appExt = getExternalFilesDir(null)
        if (appExt != null) return File(appExt, "WaylandDroid").apply { mkdirs() }
        return File(filesDir, "WaylandDroid").apply { mkdirs() }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun btnBg(hex: String): GradientDrawable = GradientDrawable().apply {
        setColor(Color.parseColor(hex))
        cornerRadius = dp(8).toFloat()
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0F172A"))
            setPadding(dp(12), dp(16), dp(12), dp(12))
        }

        // Top Bar
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        }
        topBar.addView(Button(this).apply {
            text = "Voltar"
            setTextColor(Color.WHITE)
            textSize = 12f
            isAllCaps = false
            background = btnBg("#334155")
            setOnClickListener { finish() }
        })
        topBar.addView(TextView(this).apply {
            text = "  Ubuntu 24.04 · Tradutor Android"
            setTextColor(Color.parseColor("#F8FAFC"))
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        root.addView(topBar)

        // Container Status + ART/NDK & OpenGL/Vulkan Selectors
        statusText = TextView(this).apply {
            text = container.getStatusSummary()
            setTextColor(Color.parseColor("#FDBA74"))
            textSize = 11f
            setPadding(0, 0, 0, dp(6))
        }
        root.addView(statusText)

        val layerOptions = UbuntuContainerManager.RuntimeLayer.values().toList()
        val layerSpinner = Spinner(this).apply {
            setBackgroundColor(Color.parseColor("#1E293B"))
            setPadding(dp(8), dp(4), dp(8), dp(4))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6) }
        }
        layerSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            layerOptions.map { "Camada: ${it.label}" }
        )
        layerSpinner.setSelection(layerOptions.indexOf(selectedLayer).coerceAtLeast(0))
        layerSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                selectedLayer = layerOptions[pos]
                engine.activeRuntimeLayer = selectedLayer
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        root.addView(layerSpinner)

        val gpuOptions = UbuntuContainerManager.GpuBackend.values().toList()
        val gpuSpinner = Spinner(this).apply {
            setBackgroundColor(Color.parseColor("#1E293B"))
            setPadding(dp(8), dp(4), dp(8), dp(4))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6) }
        }
        gpuSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            gpuOptions.map { "GPU: ${it.label}" }
        )
        gpuSpinner.setSelection(gpuOptions.indexOf(selectedGpu).coerceAtLeast(0))
        gpuSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                selectedGpu = gpuOptions[pos]
                engine.activeGpuBackend = selectedGpu
                container.writeProotMetadata(selectedGpu)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        root.addView(gpuSpinner)

        installProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6) }
        }
        root.addView(installProgress)

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }
        actionRow.addView(Button(this).apply {
            text = "Instalar Ubuntu"
            setTextColor(Color.WHITE)
            textSize = 11f
            isAllCaps = false
            background = btnBg("#EA580C")
            layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginEnd = dp(4) }
            setOnClickListener { startFullRootfsInstall() }
        })
        actionRow.addView(Button(this).apply {
            text = "PRoot Termux"
            setTextColor(Color.WHITE)
            textSize = 11f
            isAllCaps = false
            background = btnBg("#059669")
            layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginEnd = dp(4) }
            setOnClickListener { launchUbuntuViaTermuxProot() }
        })
        actionRow.addView(Button(this).apply {
            text = "Rodar .llamafile"
            setTextColor(Color.WHITE)
            textSize = 11f
            isAllCaps = false
            background = btnBg("#7C3AED")
            layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f)
            setOnClickListener { launchBrainInUbuntuContainer() }
        })
        root.addView(actionRow)

        // Quick Ubuntu Command Chips
        val chipScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }
        val chipRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val quickCmds = listOf(
            "gpuinfo",
            "vulkaninfo",
            "glxinfo",
            "vkcube",
            "art-ndk",
            "run-brain",
            "gcc app.c",
            "uname -a",
            "free -m",
            "df -h",
            "notify-send Ubuntu GPUAtiva",
            "xdg-open http://127.0.0.1:8080",
            "apt install clang",
            "help"
        )
        for (q in quickCmds) {
            chipRow.addView(Button(this).apply {
                text = q
                setTextColor(Color.parseColor("#E2E8F0"))
                textSize = 11f
                isAllCaps = false
                background = btnBg("#1E293B")
                setPadding(dp(10), dp(4), dp(10), dp(4))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(32)
                ).apply { marginEnd = dp(6) }
                setOnClickListener { runCommand(q) }
            })
        }
        chipScroll.addView(chipRow)
        root.addView(chipScroll)

        // Terminal Log
        terminalScroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#020617"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply { bottomMargin = dp(8) }
        }
        terminalLogView = TextView(this).apply {
            setTextColor(Color.parseColor("#E2E8F0"))
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setTextIsSelectable(true)
        }
        terminalScroll.addView(terminalLogView)
        root.addView(terminalScroll)

        // Input Row
        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        cmdInput = EditText(this).apply {
            hint = "root@ubuntu-arm64:~# comando..."
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.WHITE)
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setBackgroundColor(Color.parseColor("#1E293B"))
            setPadding(dp(10), dp(10), dp(10), dp(10))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6)
            }
        }
        inputRow.addView(cmdInput)
        inputRow.addView(Button(this).apply {
            text = "Traduzir"
            setTextColor(Color.WHITE)
            textSize = 12f
            isAllCaps = false
            background = btnBg("#2563EB")
            setOnClickListener {
                val txt = cmdInput.text.toString().trim()
                if (txt.isNotEmpty()) {
                    cmdInput.setText("")
                    runCommand(txt)
                }
            }
        })
        root.addView(inputRow)

        return root
    }

    private fun runCommand(cmd: String) {
        val cwdBefore = engine.currentCwd
        Thread {
            val res = engine.execute(cmd)
            runOnUiThread {
                val block = buildString {
                    appendLine("root@ubuntu-aarch64:$cwdBefore# $cmd")
                    appendLine("[${res.layerBadge}] TRADUÇÃO AARCH64 (ART/JVM vs NDK):")
                    for (line in res.androidInstructions) {
                        appendLine("  → $line")
                    }
                    if (res.artJvmCall != null) appendLine("  [ART/JVM] ${res.artJvmCall}")
                    if (res.ndkCall != null) appendLine("  [NDK    ] ${res.ndkCall}")
                    if (res.ubuntuGlibcCall != null) appendLine("  [UBUNTU ] ${res.ubuntuGlibcCall}")
                    if (res.output.isNotEmpty()) {
                        appendLine(res.output)
                    }
                    appendLine("----------------------------------------")
                }
                appendLog(block)
            }
        }.start()
    }

    private fun appendLog(text: String) {
        logBuilder.append(text)
        if (logBuilder.length > 16000) {
            logBuilder.delete(0, logBuilder.length - 12000)
        }
        terminalLogView.text = logBuilder.toString()
        terminalScroll.post { terminalScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun startFullRootfsInstall() {
        installProgress.visibility = View.VISIBLE
        installProgress.progress = 0
        Thread {
            try {
                container.installFullUbuntuBaseArm64 { pct, msg ->
                    runOnUiThread {
                        installProgress.progress = pct
                        statusText.text = msg
                    }
                }
                runOnUiThread {
                    installProgress.visibility = View.GONE
                    statusText.text = container.getStatusSummary()
                    appendLog("[UBUNTU ROOTFS] Ubuntu 24.04 aarch64 instalado com sucesso!\n")
                }
            } catch (e: Exception) {
                runOnUiThread {
                    installProgress.visibility = View.GONE
                    statusText.text = "Erro ao instalar Ubuntu Base: ${e.message}"
                    appendLog("[ERRO UBUNTU] ${e.message}\n")
                }
            }
        }.start()
    }

    private fun launchUbuntuViaTermuxProot() {
        val script = container.buildProotLaunchScript(selectedGpu)
        val hasPerm = ContextCompat.checkSelfPermission(this, "com.termux.permission.RUN_COMMAND") == PackageManager.PERMISSION_GRANTED
        if (hasPerm) {
            try {
                val intent = Intent("com.termux.RUN_COMMAND").apply {
                    setClassName("com.termux", "com.termux.app.RunCommandService")
                    putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
                    putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", script))
                    putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
                    putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
                    putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0")
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
                Toast.makeText(this, "Abrindo Ubuntu aarch64 (${selectedGpu.id}) via PRoot!", Toast.LENGTH_SHORT).show()
                return
            } catch (_: Exception) {}
        }
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("ubuntu_proot", script))
        packageManager.getLaunchIntentForPackage("com.termux")?.let { startActivity(it) }
        Toast.makeText(this, "Script PRoot Ubuntu copiado! Cole no Termux.", Toast.LENGTH_LONG).show()
    }

    private fun launchBrainInUbuntuContainer() {
        val script = container.buildProotRunBrainScript(brainModelFile, brainLlamafileName, brainModelUrl, selectedGpu)
        val hasPerm = ContextCompat.checkSelfPermission(this, "com.termux.permission.RUN_COMMAND") == PackageManager.PERMISSION_GRANTED
        if (hasPerm) {
            try {
                val intent = Intent("com.termux.RUN_COMMAND").apply {
                    setClassName("com.termux", "com.termux.app.RunCommandService")
                    putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
                    putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", script))
                    putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
                    putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
                    putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0")
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
                appendLog("[UBUNTU CÉREBRO] Iniciando $brainLlamafileName via Ubuntu/PRoot (${selectedGpu.label}) (:8080)...\n")
                Toast.makeText(this, "Iniciando cérebro no Container Ubuntu!", Toast.LENGTH_SHORT).show()
                return
            } catch (_: Exception) {}
        }
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("ubuntu_brain", script))
        packageManager.getLaunchIntentForPackage("com.termux")?.let { startActivity(it) }
        appendLog("[UBUNTU CÉREBRO] Comando de execução copiado para a área de transferência.\n")
        Toast.makeText(this, "Comando do Cérebro no Ubuntu copiado! Cole no Termux.", Toast.LENGTH_LONG).show()
    }
}
