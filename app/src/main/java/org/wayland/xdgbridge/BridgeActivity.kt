package org.wayland.xdgbridge

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max

class BridgeActivity : AppCompatActivity() {

    companion object {
        private const val LLAMAFILE_VERSION = "0.10.6"
        private const val LLAMAFILE_BASE_URL =
            "https://github.com/mozilla-ai/llamafile/releases/download/$LLAMAFILE_VERSION"
        private const val TERMUX_PACKAGE = "com.termux"
        private const val TERMUX_SERVICE = "com.termux.app.RunCommandService"
        private const val TERMUX_PERMISSION = "com.termux.permission.RUN_COMMAND"
        private const val REQUEST_TERMUX_PERMISSION = 9001
    }

    private data class QwenPreset(
        val label: String,
        val modelFileName: String,
        val outputLlamafileName: String,
        val url: String
    )

    private data class HardwareOption(
        val label: String,
        val backend: String,
        val gpuLayers: Int
    )

    private data class PerfOption(
        val label: String,
        val mode: String,
        val contextSize: Int
    )

    private val qwenPresets = listOf(
        QwenPreset(
            "Qwen 2.5 Coder 0.5B Instruct (Qwen-Coder-0.5B.llamafile)",
            "qwen2.5-coder-0.5b-instruct-q5_k_m.gguf",
            "Qwen-Coder-0.5B.llamafile",
            "https://huggingface.co/Qwen/Qwen2.5-Coder-0.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-0.5b-instruct-q5_k_m.gguf"
        ),
        QwenPreset(
            "Llama 3.2 1B Instruct (Llama-3.2-1B.llamafile)",
            "Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            "Llama-3.2-1B.llamafile",
            "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf"
        ),
        QwenPreset(
            "Qwen 2.5 Coder 1.5B (Qwen2.5-Coder-1.5B.llamafile)",
            "qwen2.5-coder-1.5b-instruct-q4_k_m.gguf",
            "Qwen2.5-Coder-1.5B.llamafile",
            "https://huggingface.co/Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf"
        ),
        QwenPreset(
            "URL Personalizada (.gguf / .llamafile)",
            "custom-model.gguf",
            "custom-model.llamafile",
            ""
        )
    )

    private val hardwareOptions = listOf(
        HardwareOption("Somente CPU (--gpu disable · Padrão Android)", "disable", 0),
        HardwareOption("GPU automática + CPU (--gpu auto)", "auto", 20)
    )

    private val perfOptions = listOf(
        PerfOption("Equilibrado (metade threads · ctx 2048)", "balanced", 2048),
        PerfOption("Econômico (1 thread · ctx 1024)", "eco", 1024),
        PerfOption("Alto Desempenho (todas threads · ctx 4096)", "high", 4096),
        PerfOption("Máximo (todas threads · ctx 8192)", "max", 8192)
    )

    private lateinit var waylandRootDir: File
    private lateinit var brainsDir: File
    private lateinit var modelsDir: File

    private lateinit var brainUrlInput: EditText
    private lateinit var brainWebUrlInput: EditText
    private lateinit var brainStatusText: TextView
    private lateinit var brainProgress: ProgressBar

    private lateinit var githubUrlInput: EditText
    private lateinit var appNameInput: EditText
    private lateinit var instructionsInput: EditText
    private lateinit var importStatusText: TextView
    private lateinit var importProgress: ProgressBar

    private lateinit var appsListContainer: LinearLayout

    private lateinit var termuxStatusText: TextView
    private lateinit var termuxCommandInput: EditText
    private lateinit var termuxOutputText: TextView

    private var selectedPresetIndex = 0
    private var selectedHardwareIndex = 0
    private var selectedPerfIndex = 0
    private var llamafileProcess: Process? = null
    private var pendingTermuxCmd: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        waylandRootDir = File(filesDir, "WaylandAndroid").apply { mkdirs() }
        brainsDir = File(waylandRootDir, ".brains").apply { mkdirs() }
        modelsDir = File(brainsDir, "models").apply { mkdirs() }
        seedStarterAppsIfNeeded()

        val serviceIntent = Intent(this, BridgeSessionService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        setContentView(buildHomeUi())
        refreshAppsList()
        refreshTermuxStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::termuxStatusText.isInitialized) {
            refreshTermuxStatus()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_TERMUX_PERMISSION) {
            refreshTermuxStatus()
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                val cmd = pendingTermuxCmd
                pendingTermuxCmd = null
                if (!cmd.isNullOrBlank()) {
                    dispatchTermuxRunCommand(cmd)
                }
            } else {
                showTermuxPermissionHelpDialog(pendingTermuxCmd ?: buildTermuxSetupAndRunCommand())
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            llamafileProcess?.destroy()
        } catch (_: Exception) {}
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun cardDrawable(bgHex: String = "#1E293B", borderHex: String = "#334155"): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor(bgHex))
            setStroke(dp(1), Color.parseColor(borderHex))
            cornerRadius = dp(12).toFloat()
        }
    }

    private fun buttonDrawable(bgHex: String): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor(bgHex))
            cornerRadius = dp(8).toFloat()
        }
    }

    private fun styleInput(edit: EditText, hintText: String) {
        edit.hint = hintText
        edit.setHintTextColor(Color.parseColor("#64748B"))
        edit.setTextColor(Color.parseColor("#F8FAFC"))
        edit.textSize = 13f
        edit.setPadding(dp(12), dp(10), dp(12), dp(10))
        edit.background = cardDrawable("#0F172A", "#334155")
    }

    private fun makeCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardDrawable()
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(16)
            }
        }
    }

    private fun makeSectionTitle(title: String, subtitle: String? = null): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@BridgeActivity).apply {
                text = title
                setTextColor(Color.parseColor("#F8FAFC"))
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
            })
            if (subtitle != null) {
                addView(TextView(this@BridgeActivity).apply {
                    text = subtitle
                    setTextColor(Color.parseColor("#94A3B8"))
                    textSize = 12f
                    setPadding(0, dp(2), 0, dp(10))
                })
            }
        }
    }

    private fun buildHomeUi(): View {
        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0F172A"))
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(36))
        }

        // Header
        root.addView(TextView(this).apply {
            text = "WaylandBridgeWorkstation"
            setTextColor(Color.parseColor("#F8FAFC"))
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Home · WaylandAndroid/ · Toque normal e rolagem simples"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 12f
            setPadding(0, dp(4), 0, dp(18))
        })

        // 1. MENU PARA ESCOLHER O CÉREBRO QWEN + LLAMAFILE (sh ... --server) + ABRIR NO NAVEGADOR
        val brainCard = makeCard()
        brainCard.addView(
            makeSectionTitle(
                "1. Escolher Cérebro Qwen (.llamafile / .gguf)",
                "Baixa o modelo + motor llamafile-0.10.6, gera .args e inicia com sh ./nome.llamafile --server."
            )
        )

        val spinner = Spinner(this).apply {
            background = cardDrawable("#0F172A", "#334155")
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }
        spinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            qwenPresets.map { it.label }
        )
        brainCard.addView(spinner)

        val hwSpinner = Spinner(this).apply {
            background = cardDrawable("#0F172A", "#334155")
            setPadding(dp(8), dp(6), dp(8), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        }
        hwSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            hardwareOptions.map { it.label }
        )
        hwSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                selectedHardwareIndex = pos
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        brainCard.addView(hwSpinner)

        val perfSpinner = Spinner(this).apply {
            background = cardDrawable("#0F172A", "#334155")
            setPadding(dp(8), dp(6), dp(8), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        }
        perfSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            perfOptions.map { it.label }
        )
        perfSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                selectedPerfIndex = pos
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        brainCard.addView(perfSpinner)

        brainUrlInput = EditText(this).apply {
            styleInput(this, "Cole a URL do .gguf ou .llamafile")
            setText(qwenPresets[0].url)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        }
        brainCard.addView(brainUrlInput)

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                selectedPresetIndex = pos
                val preset = qwenPresets[pos]
                if (preset.url.isNotEmpty()) {
                    brainUrlInput.setText(preset.url)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        brainProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        }
        brainCard.addView(brainProgress)

        brainStatusText = TextView(this).apply {
            text = getSavedBrainsSummary()
            setTextColor(Color.parseColor("#CBD5E1"))
            textSize = 12f
            setPadding(0, dp(8), 0, dp(10))
        }
        brainCard.addView(brainStatusText)

        val brainRow1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }
        val btnDownloadBrain = Button(this).apply {
            text = "Baixar + Empacotar"
            setTextColor(Color.WHITE)
            textSize = 12f
            isAllCaps = false
            background = buttonDrawable("#2563EB")
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                marginEnd = dp(8)
            }
            setOnClickListener { startDownloadBrain() }
        }
        val btnStartServer = Button(this).apply {
            text = "Iniciar sh --server"
            setTextColor(Color.WHITE)
            textSize = 12f
            isAllCaps = false
            background = buttonDrawable("#7C3AED")
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f)
            setOnClickListener { startLlamafileServer() }
        }
        brainRow1.addView(btnDownloadBrain)
        brainRow1.addView(btnStartServer)
        brainCard.addView(brainRow1)

        brainWebUrlInput = EditText(this).apply {
            styleInput(this, "http://127.0.0.1:8080")
            setText("http://127.0.0.1:8080")
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }
        brainCard.addView(brainWebUrlInput)

        val brainRow2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val btnOpenQwenBrowser = Button(this).apply {
            text = "Abrir Qwen no Navegador"
            setTextColor(Color.WHITE)
            textSize = 12f
            isAllCaps = false
            background = buttonDrawable("#0F766E")
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                marginEnd = dp(8)
            }
            setOnClickListener { openQwenInBrowser() }
        }
        val btnTermuxCmd = Button(this).apply {
            text = "Comando sh (Termux)"
            setTextColor(Color.parseColor("#E2E8F0"))
            textSize = 12f
            isAllCaps = false
            background = buttonDrawable("#334155")
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f)
            setOnClickListener { showTermuxShDialog(null) }
        }
        brainRow2.addView(btnOpenQwenBrowser)
        brainRow2.addView(btnTermuxCmd)
        brainCard.addView(brainRow2)

        root.addView(brainCard)

        // 2. BAIXAR DO GITHUB PARA WaylandAndroid/nome_do_aplicativo
        val githubCard = makeCard()
        githubCard.addView(
            makeSectionTitle(
                "2. Baixar Aplicativo do GitHub",
                "Baixa os arquivos direto para WaylandAndroid/nome_do_aplicativo."
            )
        )

        githubUrlInput = EditText(this).apply {
            styleInput(this, "https://github.com/usuario/projeto ou usuario/projeto")
        }
        githubCard.addView(githubUrlInput)

        appNameInput = EditText(this).apply {
            styleInput(this, "Nome do aplicativo (opcional)")
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        }
        githubCard.addView(appNameInput)

        instructionsInput = EditText(this).apply {
            styleInput(this, "Instruções para traduzir para o Android")
            setText("Mapear janelas para wl_surface + xdg_toplevel e compilar ARM64.")
            minLines = 2
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        }
        githubCard.addView(instructionsInput)

        importProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        }
        githubCard.addView(importProgress)

        importStatusText = TextView(this).apply {
            text = ""
            setTextColor(Color.parseColor("#93C5FD"))
            textSize = 12f
            setPadding(0, dp(6), 0, dp(8))
        }
        githubCard.addView(importStatusText)

        val btnImportGithub = Button(this).apply {
            text = "Baixar e Preparar Aplicativo"
            setTextColor(Color.WHITE)
            textSize = 13f
            isAllCaps = false
            background = buttonDrawable("#2563EB")
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(44)
            )
            setOnClickListener { startGithubDownload() }
        }
        githubCard.addView(btnImportGithub)
        root.addView(githubCard)

        // 3. LISTA DE APLICATIVOS CRIADOS (WaylandAndroid/nome_do_aplicativo)
        val appsHeaderCard = makeCard()
        appsHeaderCard.addView(
            makeSectionTitle(
                "3. Aplicativos Criados (WaylandAndroid/)",
                "Toque em Abrir App para iniciar o compositor com o mouse azul."
            )
        )
        appsListContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        appsHeaderCard.addView(appsListContainer)
        root.addView(appsHeaderCard)

        // 4. ÚLTIMA OPÇÃO: INTEGRAÇÃO TERMUX (GPLv3 · github.com/termux) + EXECUTOR LLAMAFILE
        val termuxCard = makeCard()
        termuxCard.addView(
            makeSectionTitle(
                "4. Termux (GPLv3) · Executor Llamafile",
                "Última opção do menu: chama o Termux diretamente via RUN_COMMAND para rodar sh ./Qwen-Coder-0.5B.llamafile --server."
            )
        )

        termuxStatusText = TextView(this).apply {
            text = "Verificando Termux (com.termux)..."
            setTextColor(Color.parseColor("#CBD5E1"))
            textSize = 12f
            setPadding(0, 0, 0, dp(10))
        }
        termuxCard.addView(termuxStatusText)

        val btnCallTermux = Button(this).apply {
            text = "Chamar Termux e Rodar Llamafile (sh --server)"
            setTextColor(Color.WHITE)
            textSize = 13f
            isAllCaps = false
            background = buttonDrawable("#059669")
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(44)
            ).apply { bottomMargin = dp(8) }
            setOnClickListener { runLlamafileInTermux() }
        }
        termuxCard.addView(btnCallTermux)

        val termuxRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        }
        val btnUnlockTermux = Button(this).apply {
            text = "Liberar / Instalar Termux"
            setTextColor(Color.WHITE)
            textSize = 12f
            isAllCaps = false
            background = buttonDrawable("#334155")
            layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(8) }
            setOnClickListener { openOrUnlockTermux() }
        }
        val btnTermuxGithub = Button(this).apply {
            text = "GitHub Termux (GPLv3)"
            setTextColor(Color.parseColor("#93C5FD"))
            textSize = 12f
            isAllCaps = false
            background = buttonDrawable("#1E293B")
            layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f)
            setOnClickListener {
                launchBrowserIntent("https://github.com/termux/termux-app/releases")
            }
        }
        termuxRow.addView(btnUnlockTermux)
        termuxRow.addView(btnTermuxGithub)
        termuxCard.addView(termuxRow)

        termuxCommandInput = EditText(this).apply {
            styleInput(this, "Comando shell (ex: ls -la WaylandAndroid/.brains)")
            setText("ls -la .brains")
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }
        termuxCard.addView(termuxCommandInput)

        val btnRunLocalShell = Button(this).apply {
            text = "Executar no Shell Local (WaylandAndroid/)"
            setTextColor(Color.WHITE)
            textSize = 12f
            isAllCaps = false
            background = buttonDrawable("#2563EB")
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(40)
            ).apply { bottomMargin = dp(8) }
            setOnClickListener { runLocalShellCommand() }
        }
        termuxCard.addView(btnRunLocalShell)

        termuxOutputText = TextView(this).apply {
            text = "Saída do terminal integrado aparecerá aqui."
            setTextColor(Color.parseColor("#6EE7B7"))
            setBackgroundColor(Color.parseColor("#020617"))
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        termuxCard.addView(termuxOutputText)

        root.addView(termuxCard)

        scrollView.addView(root)
        return scrollView
    }

    private fun resolveThreadsCount(): Int {
        val total = max(2, Runtime.getRuntime().availableProcessors())
        return when (perfOptions.getOrNull(selectedPerfIndex)?.mode) {
            "eco" -> 1
            "balanced" -> max(1, total / 2)
            else -> total
        }
    }

    private fun resolveTargetNames(): Pair<String, String> {
        val rawUrl = brainUrlInput.text.toString().trim()
        val preset = qwenPresets.getOrNull(selectedPresetIndex)
        val pathName = try {
            File(URL(rawUrl).path).name.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
        val modelName = (pathName ?: preset?.modelFileName ?: "qwen2.5-coder-0.5b-instruct-q5_k_m.gguf")
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val outputName = if (modelName.endsWith(".llamafile", true)) {
            modelName
        } else if (preset != null && preset.url == rawUrl) {
            preset.outputLlamafileName
        } else {
            modelName.removeSuffix(".gguf").removeSuffix(".GGUF") + ".llamafile"
        }
        return modelName to outputName
    }

    private fun buildArgsText(modelName: String): String {
        val hw = hardwareOptions.getOrNull(selectedHardwareIndex) ?: hardwareOptions[0]
        val perf = perfOptions.getOrNull(selectedPerfIndex) ?: perfOptions[0]
        val threads = resolveThreadsCount()
        return listOf(
            "-m",
            "/zip/$modelName",
            "-c",
            perf.contextSize.toString(),
            "-n",
            "512",
            "--threads",
            threads.toString(),
            "--gpu",
            hw.backend,
            "-ngl",
            hw.gpuLayers.toString(),
            "--load-mode",
            "mmap",
            "..."
        ).joinToString("\n") + "\n"
    }

    private fun buildTermuxSetupAndRunCommand(): String {
        val rawUrl = brainUrlInput.text.toString().trim().ifEmpty { qwenPresets[0].url }
        val (modelName, outputName) = resolveTargetNames()
        val argsText = buildArgsText(modelName).trimEnd()
        if (modelName.endsWith(".llamafile", true)) {
            return buildString {
                appendLine("mkdir -p ~/WaylandAndroid/.brains && cd ~/WaylandAndroid/.brains")
                appendLine("if [ ! -f ./$outputName ]; then curl -L -o ./$outputName '$rawUrl' && chmod 755 ./$outputName; fi")
                append("sh ./$outputName --server")
            }
        }
        return buildString {
            appendLine("mkdir -p ~/WaylandAndroid/.brains/models && cd ~/WaylandAndroid/.brains")
            appendLine("if [ ! -f ./$outputName ]; then")
            appendLine("  if [ ! -f ./llamafile ]; then curl -L -o ./llamafile '$LLAMAFILE_BASE_URL/llamafile-$LLAMAFILE_VERSION' && chmod 755 ./llamafile; fi")
            appendLine("  if [ ! -f ./zipalign ]; then curl -L -o ./zipalign '$LLAMAFILE_BASE_URL/zipalign-$LLAMAFILE_VERSION' && chmod 755 ./zipalign; fi")
            appendLine("  if [ ! -f ./models/$modelName ]; then curl -L -o ./models/$modelName '$rawUrl'; fi")
            appendLine("  cat << 'EOF' > ./models/.args")
            appendLine(argsText)
            appendLine("EOF")
            appendLine("  cp ./llamafile ./$outputName && (cd ./models && sh ../zipalign -j0 ../$outputName $modelName .args)")
            appendLine("fi")
            append("sh ./$outputName --server")
        }
    }

    private fun getSavedBrainsSummary(): String {
        val llamafiles = brainsDir.listFiles()?.filter { it.isFile && it.name.endsWith(".llamafile", true) } ?: emptyList()
        val ggufs = modelsDir.listFiles()?.filter { it.isFile && it.name.endsWith(".gguf", true) } ?: emptyList()
        if (llamafiles.isEmpty() && ggufs.isEmpty()) {
            return "Nenhum .llamafile/.gguf salvo ainda. Toque em Baixar + Empacotar ou use Comando sh."
        }
        val parts = mutableListOf<String>()
        llamafiles.forEach { parts.add("${it.name} (${it.length() / (1024 * 1024)} MB)") }
        ggufs.forEach { parts.add("models/${it.name} (${it.length() / (1024 * 1024)} MB)") }
        return "Salvos: " + parts.joinToString(", ")
    }

    private fun checkServerHealth(baseUrl: String): Boolean {
        return try {
            val clean = baseUrl.trimEnd('/')
            val conn = (URL("$clean/health").openConnection() as HttpURLConnection).apply {
                connectTimeout = 900
                readTimeout = 900
            }
            conn.responseCode in 200..299
        } catch (_: Exception) {
            false
        }
    }

    private fun downloadToFile(urlStr: String, destFile: File, label: String) {
        val partFile = File(destFile.parentFile, destFile.name + ".download-part")
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15000
            readTimeout = 30000
        }
        conn.connect()
        if (conn.responseCode !in 200..299) {
            throw Exception("$label: HTTP ${conn.responseCode}")
        }
        val total = conn.contentLengthLong
        runOnUiThread {
            if (total > 0) {
                brainProgress.isIndeterminate = false
                brainProgress.progress = 0
            } else {
                brainProgress.isIndeterminate = true
            }
        }
        conn.inputStream.use { input ->
            FileOutputStream(partFile).use { output ->
                val buf = ByteArray(16384)
                var downloaded = 0L
                var lastUi = 0L
                while (true) {
                    val read = input.read(buf)
                    if (read <= 0) break
                    output.write(buf, 0, read)
                    downloaded += read
                    val now = System.currentTimeMillis()
                    if (now - lastUi > 300) {
                        lastUi = now
                        val mb = downloaded / (1024 * 1024)
                        val pct = if (total > 0) ((downloaded * 100) / total).toInt() else 0
                        runOnUiThread {
                            if (total > 0) brainProgress.progress = pct
                            brainStatusText.text = "Baixando $label: ${mb} MB (${pct}%)"
                        }
                    }
                }
            }
        }
        if (destFile.exists()) destFile.delete()
        if (!partFile.renameTo(destFile)) {
            partFile.copyTo(destFile, overwrite = true)
            partFile.delete()
        }
    }

    private fun startDownloadBrain() {
        val rawUrl = brainUrlInput.text.toString().trim()
        val parsed = try { URL(rawUrl) } catch (_: Exception) { null }
        if (parsed == null || parsed.protocol != "https") {
            brainStatusText.text = "Informe uma URL HTTPS válida (.gguf ou .llamafile)."
            return
        }

        val (modelName, outputName) = resolveTargetNames()
        val isDirectLlamafile = modelName.endsWith(".llamafile", true)

        brainProgress.visibility = View.VISIBLE
        brainProgress.isIndeterminate = true
        brainStatusText.text = "Preparando motor llamafile-$LLAMAFILE_VERSION + $outputName..."

        Thread {
            try {
                if (isDirectLlamafile) {
                    val outFile = File(brainsDir, outputName)
                    downloadToFile(rawUrl, outFile, outputName)
                    outFile.setExecutable(true, false)
                } else {
                    val llamafileBin = File(brainsDir, "llamafile")
                    if (!llamafileBin.exists()) {
                        downloadToFile("$LLAMAFILE_BASE_URL/llamafile-$LLAMAFILE_VERSION", llamafileBin, "llamafile")
                    }
                    llamafileBin.setExecutable(true, false)

                    val zipalignBin = File(brainsDir, "zipalign")
                    if (!zipalignBin.exists()) {
                        downloadToFile("$LLAMAFILE_BASE_URL/zipalign-$LLAMAFILE_VERSION", zipalignBin, "zipalign")
                    }
                    zipalignBin.setExecutable(true, false)

                    val ggufFile = File(modelsDir, modelName)
                    if (!ggufFile.exists()) {
                        downloadToFile(rawUrl, ggufFile, modelName)
                    }

                    val argsFile = File(modelsDir, ".args")
                    argsFile.writeText(buildArgsText(modelName))

                    runOnUiThread {
                        brainProgress.isIndeterminate = true
                        brainStatusText.text = "Empacotando $outputName com zipalign + .args..."
                    }

                    val outputFile = File(brainsDir, outputName)
                    llamafileBin.copyTo(outputFile, overwrite = true)
                    outputFile.setExecutable(true, false)

                    try {
                        val proc = ProcessBuilder(
                            "sh",
                            zipalignBin.absolutePath,
                            "-j0",
                            outputFile.absolutePath,
                            modelName,
                            ".args"
                        ).directory(modelsDir).redirectErrorStream(true).start()
                        val exit = proc.waitFor()
                        if (exit != 0) {
                            outputFile.delete()
                        } else {
                            argsFile.delete()
                        }
                    } catch (_: Exception) {
                        outputFile.delete()
                    }
                }

                // Write convenient sh launcher script inside .brains/
                File(brainsDir, "run_server.sh").writeText(
                    "#!/system/bin/sh\n" + buildTermuxSetupAndRunCommand() + "\n"
                )

                runOnUiThread {
                    brainProgress.visibility = View.GONE
                    brainStatusText.text = getSavedBrainsSummary() + "\nToque em 'Iniciar sh --server' para subir na porta 8080."
                    Toast.makeText(this, "Modelo pronto em WaylandAndroid/.brains/", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    brainProgress.visibility = View.GONE
                    brainStatusText.text = "Erro ao baixar/empacotar: ${e.message}"
                }
            }
        }.start()
    }

    private fun startLlamafileServer() {
        val (modelName, outputName) = resolveTargetNames()
        val packagedLlamafile = File(brainsDir, outputName)
        val engineBin = File(brainsDir, "llamafile")
        val ggufFile = File(modelsDir, modelName)
        val baseUrl = brainWebUrlInput.text.toString().trim().ifEmpty { "http://127.0.0.1:8080" }

        brainProgress.visibility = View.VISIBLE
        brainProgress.isIndeterminate = true
        brainStatusText.text = "Verificando/iniciando: sh ./$outputName --server ..."

        Thread {
            if (checkServerHealth(baseUrl)) {
                runOnUiThread {
                    brainProgress.visibility = View.GONE
                    brainStatusText.text = "Servidor Qwen já está ativo em $baseUrl!"
                }
                return@Thread
            }

            if (isTermuxInstalled() &&
                ContextCompat.checkSelfPermission(this, TERMUX_PERMISSION) == PackageManager.PERMISSION_GRANTED
            ) {
                runOnUiThread {
                    brainProgress.visibility = View.GONE
                    brainStatusText.text = "Chamando Termux para executar sh ./$outputName --server..."
                    dispatchTermuxRunCommand(buildTermuxSetupAndRunCommand())
                }
                return@Thread
            }

            val hw = hardwareOptions.getOrNull(selectedHardwareIndex) ?: hardwareOptions[0]
            val perf = perfOptions.getOrNull(selectedPerfIndex) ?: perfOptions[0]
            val threads = resolveThreadsCount()

            val cmd: List<String>? = when {
                packagedLlamafile.exists() -> listOf(
                    "sh",
                    packagedLlamafile.absolutePath,
                    "--server",
                    "--nobrowser",
                    "--port",
                    "8080"
                )
                engineBin.exists() && ggufFile.exists() -> listOf(
                    "sh",
                    engineBin.absolutePath,
                    "-m",
                    ggufFile.absolutePath,
                    "-c",
                    perf.contextSize.toString(),
                    "-n",
                    "512",
                    "--threads",
                    threads.toString(),
                    "--gpu",
                    hw.backend,
                    "-ngl",
                    hw.gpuLayers.toString(),
                    "--load-mode",
                    "mmap",
                    "--server",
                    "--nobrowser",
                    "--port",
                    "8080"
                )
                else -> null
            }

            if (cmd == null) {
                runOnUiThread {
                    brainProgress.visibility = View.GONE
                    brainStatusText.text = "Baixe primeiro (botão Baixar + Empacotar) ou execute via Termux."
                    showTermuxShDialog("Nenhum .llamafile/.gguf encontrado localmente ainda.")
                }
                return@Thread
            }

            try {
                try { llamafileProcess?.destroy() } catch (_: Exception) {}
                val proc = ProcessBuilder(cmd)
                    .directory(brainsDir)
                    .redirectErrorStream(true)
                    .start()
                llamafileProcess = proc

                val logBuf = StringBuilder()
                Thread {
                    try {
                        proc.inputStream.bufferedReader().forEachLine { line ->
                            if (logBuf.length < 1500) logBuf.appendLine(line)
                        }
                    } catch (_: Exception) {}
                }.start()

                var online = false
                for (i in 0 until 24) {
                    Thread.sleep(500)
                    if (checkServerHealth(baseUrl)) {
                        online = true
                        break
                    }
                    if (!proc.isAlive) break
                }

                runOnUiThread {
                    brainProgress.visibility = View.GONE
                    if (online) {
                        brainStatusText.text = "Servidor Qwen ativo em $baseUrl! Toque em 'Abrir Qwen no Navegador'."
                        Toast.makeText(this, "Qwen ativo na porta 8080!", Toast.LENGTH_SHORT).show()
                    } else {
                        val detail = logBuf.toString().trim().ifEmpty { "O Android bloqueou execução direta na pasta privada (SELinux noexec)." }
                        brainStatusText.text = "Não foi possível abrir direto pelo APK: $detail"
                        showTermuxShDialog(detail)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    brainProgress.visibility = View.GONE
                    val msg = e.message ?: "Permission denied"
                    brainStatusText.text = "Execução local bloqueada ($msg). Use sh no Termux."
                    showTermuxShDialog(msg)
                }
            }
        }.start()
    }

    private fun openQwenInBrowser() {
        val raw = brainWebUrlInput.text.toString().trim()
        val parsed = try { URL(raw) } catch (_: Exception) { null }
        if (parsed == null || (parsed.protocol != "http" && parsed.protocol != "https")) {
            Toast.makeText(this, "Informe uma URL http:// ou https:// válida.", Toast.LENGTH_SHORT).show()
            return
        }
        val targetUrl = parsed.toString()
        Thread {
            val isOnline = checkServerHealth(targetUrl)
            runOnUiThread {
                if (isOnline) {
                    launchBrowserIntent(targetUrl)
                } else {
                    val (_, outputName) = resolveTargetNames()
                    AlertDialog.Builder(this)
                        .setTitle("Servidor Qwen offline em 8080")
                        .setMessage(
                            "Ainda não há resposta em $targetUrl.\n\n" +
                            "Recomendado iniciar com:\nsh ./$outputName --server\n\n" +
                            "O que deseja fazer?"
                        )
                        .setPositiveButton("Iniciar sh --server") { _, _ ->
                            startLlamafileServer()
                        }
                        .setNeutralButton("Comando Termux") { _, _ ->
                            showTermuxShDialog(null)
                        }
                        .setNegativeButton("Abrir Mesmo Assim") { _, _ ->
                            launchBrowserIntent(targetUrl)
                        }
                        .show()
                }
            }
        }.start()
    }

    private fun launchBrowserIntent(urlStr: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(urlStr)))
        } catch (_: Exception) {
            Toast.makeText(this, "Nenhum navegador encontrado no aparelho.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showTermuxShDialog(reason: String?) {
        val (_, outputName) = resolveTargetNames()
        val fullCmd = buildTermuxSetupAndRunCommand()
        val quickCmd = "cd ~/WaylandAndroid/.brains && sh ./$outputName --server"
        val message = buildString {
            if (!reason.isNullOrBlank()) {
                appendLine("Motivo: $reason")
                appendLine()
            }
            appendLine("Comando direto (quando já baixou no Termux):")
            appendLine(quickCmd)
            appendLine()
            appendLine("Se for a 1ª vez no Termux, toque em 'Copiar 1ª Instalação'.")
        }

        AlertDialog.Builder(this)
            .setTitle("Iniciar Llamafile via sh")
            .setMessage(message)
            .setPositiveButton("Copiar Só sh --server") { _, _ ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("llamafile_quick", quickCmd))
                val launch = packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)
                if (launch != null) startActivity(launch)
                Toast.makeText(this, "Copiado: sh ./$outputName --server", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Copiar 1ª Instalação") { _, _ ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("llamafile_full", fullCmd))
                val launch = packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)
                if (launch != null) startActivity(launch)
                Toast.makeText(this, "Script de instalação copiado! Cole no Termux.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Fechar", null)
            .show()
    }

    private fun parseGithubOwnerRepo(raw: String): Pair<String, String>? {
        val cleaned = raw.trim().removeSuffix("/").removeSuffix(".git")
        val shortMatch = Regex("^([a-zA-Z0-9_.-]+)/([a-zA-Z0-9_.-]+)$").find(cleaned)
        if (shortMatch != null) {
            return shortMatch.groupValues[1] to shortMatch.groupValues[2]
        }
        return try {
            val u = URL(cleaned)
            if (u.protocol != "https" || u.host.lowercase() != "github.com") return null
            val parts = u.path.split("/").filter { it.isNotEmpty() }
            if (parts.size < 2) null else parts[0] to parts[1]
        } catch (_: Exception) {
            null
        }
    }

    private fun startGithubDownload() {
        val parsed = parseGithubOwnerRepo(githubUrlInput.text.toString())
        if (parsed == null) {
            importStatusText.text = "Informe um link válido do GitHub (ex: https://github.com/dono/repo)."
            return
        }
        val (owner, repo) = parsed
        val rawName = appNameInput.text.toString().trim().ifEmpty { repo }
        val safeApp = rawName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val appDir = File(waylandRootDir, safeApp)
        val srcDir = File(appDir, "src").apply { mkdirs() }
        val instructions = instructionsInput.text.toString().trim()
        val chosenBrain = qwenPresets.getOrNull(selectedPresetIndex)?.outputLlamafileName ?: "Qwen-Coder-0.5B.llamafile"

        importProgress.visibility = View.VISIBLE
        importProgress.isIndeterminate = false
        importProgress.progress = 15
        importStatusText.text = "Downloading repositório $owner/$repo..."

        Thread {
            try {
                val repoConn = (URL("https://api.github.com/repos/$owner/$repo").openConnection() as HttpURLConnection).apply {
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "WaylandDroid-APK")
                }
                if (repoConn.responseCode !in 200..299) {
                    throw Exception("Repositório não encontrado (HTTP ${repoConn.responseCode})")
                }
                val repoJson = JSONObject(repoConn.inputStream.bufferedReader().readText())
                val branch = repoJson.optString("default_branch", "main")

                val treeConn = (URL("https://api.github.com/repos/$owner/$repo/git/trees/$branch?recursive=1").openConnection() as HttpURLConnection).apply {
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "WaylandDroid-APK")
                }
                if (treeConn.responseCode !in 200..299) {
                    throw Exception("Erro ao listar arquivos (HTTP ${treeConn.responseCode})")
                }
                val treeJson = JSONObject(treeConn.inputStream.bufferedReader().readText())
                val treeArr = treeJson.optJSONArray("tree") ?: throw Exception("Árvore vazia")

                val allowedExts = setOf(".c", ".h", ".cpp", ".hpp", ".rs", ".py", ".sh", ".md", ".txt", ".cmake", ".mk", ".json", ".kt", ".xml")
                val targets = mutableListOf<String>()
                for (i in 0 until treeArr.length()) {
                    val item = treeArr.getJSONObject(i)
                    if (item.optString("type") != "blob") continue
                    if (item.optLong("size", 0L) > 180_000L) continue
                    val p = item.optString("path")
                    val name = File(p).name
                    if (allowedExts.any { p.endsWith(it, true) } || name == "Makefile" || name == "CMakeLists.txt") {
                        targets.add(p)
                    }
                    if (targets.size >= 24) break
                }

                runOnUiThread {
                    importProgress.progress = 55
                    importStatusText.text = "Downloading ${targets.size} arquivos para WaylandAndroid/$safeApp..."
                }

                var savedCount = 0
                for (relPath in targets) {
                    val outFile = File(srcDir, relPath)
                    if (!outFile.canonicalPath.startsWith(srcDir.canonicalPath + File.separator)) continue
                    outFile.parentFile?.mkdirs()
                    val rawUrl = "https://raw.githubusercontent.com/$owner/$repo/$branch/$relPath"
                    try {
                        val fConn = URL(rawUrl).openConnection() as HttpURLConnection
                        if (fConn.responseCode in 200..299) {
                            fConn.inputStream.use { inp ->
                                FileOutputStream(outFile).use { out -> inp.copyTo(out) }
                            }
                            savedCount++
                        }
                    } catch (_: Exception) {}
                }

                runOnUiThread {
                    importProgress.progress = 85
                    importStatusText.text = "Translating instruções com $chosenBrain..."
                }

                val prompt = buildString {
                    appendLine("CÉREBRO: $chosenBrain")
                    appendLine("PASTA: WaylandAndroid/$safeApp")
                    appendLine("ORIGEM: https://github.com/$owner/$repo")
                    appendLine("INSTRUÇÕES: $instructions")
                    appendLine("ARQUIVOS:")
                    targets.forEach { appendLine("- src/$it") }
                }
                File(appDir, "brain_prompt.txt").writeText(prompt)
                File(appDir, "bridge.meta.json").writeText(
                    JSONObject().apply {
                        put("name", safeApp)
                        put("github", "$owner/$repo")
                        put("instructions", instructions)
                    }.toString(2)
                )

                runOnUiThread {
                    importProgress.progress = 100
                    importProgress.visibility = View.GONE
                    importStatusText.text = "Pronto! $savedCount arquivos salvos em WaylandAndroid/$safeApp"
                    githubUrlInput.setText("")
                    appNameInput.setText("")
                    refreshAppsList()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    importProgress.visibility = View.GONE
                    importStatusText.text = "Erro: ${e.message}"
                }
            }
        }.start()
    }

    private fun seedStarterAppsIfNeeded() {
        val existing = waylandRootDir.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") } ?: emptyList()
        if (existing.isNotEmpty()) return

        val demo1 = File(waylandRootDir, "compositor-c11-demo")
        File(demo1, "src").mkdirs()
        File(demo1, "src/main.c").writeText("#include <wayland-server.h>\nint main(void) { return 0; }\n")
        File(demo1, "brain_prompt.txt").writeText("Mapear janela principal para wl_surface + xdg_toplevel maximizada.")

        val demo2 = File(waylandRootDir, "editor-vetorial-arm64")
        File(demo2, "src").mkdirs()
        File(demo2, "src/canvas_shm.c").writeText("#include <stdint.h>\n// Canvas 2D ARM64 via wl_shm\n")
        File(demo2, "brain_prompt.txt").writeText("Ativar lupa de precisão e arrasto travado para edição vetorial.")
    }

    private fun refreshAppsList() {
        appsListContainer.removeAllViews()
        val appDirs = waylandRootDir.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

        if (appDirs.isEmpty()) {
            appsListContainer.addView(TextView(this).apply {
                text = "Nenhum aplicativo criado ainda."
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 13f
            })
            return
        }

        for (dir in appDirs) {
            val fileCount = dir.walkTopDown().count { it.isFile }
            val itemBox = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = cardDrawable("#0F172A", "#334155")
                setPadding(dp(12), dp(12), dp(12), dp(12))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(10) }
            }

            itemBox.addView(TextView(this).apply {
                text = dir.name
                setTextColor(Color.parseColor("#F8FAFC"))
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
            })
            itemBox.addView(TextView(this).apply {
                text = "WaylandAndroid/${dir.name} · $fileCount arquivo(s)"
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 12f
                setPadding(0, dp(2), 0, dp(10))
            })

            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val btnOpen = Button(this).apply {
                text = "Abrir App"
                setTextColor(Color.WHITE)
                textSize = 12f
                isAllCaps = false
                background = buttonDrawable("#2563EB")
                layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginEnd = dp(8) }
                setOnClickListener {
                    val intent = Intent(this@BridgeActivity, CompositorActivity::class.java).apply {
                        putExtra("APP_NAME", dir.name)
                        putExtra("APP_DIR", dir.absolutePath)
                    }
                    startActivity(intent)
                }
            }
            val btnFiles = Button(this).apply {
                text = "Ver Pasta / Prompt"
                setTextColor(Color.parseColor("#E2E8F0"))
                textSize = 12f
                isAllCaps = false
                background = buttonDrawable("#334155")
                layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f)
                setOnClickListener { showAppFolderDialog(dir) }
            }
            row.addView(btnOpen)
            row.addView(btnFiles)
            itemBox.addView(row)
            appsListContainer.addView(itemBox)
        }
    }

    private fun showAppFolderDialog(appDir: File) {
        val files = appDir.walkTopDown().filter { it.isFile }.map {
            it.relativeTo(appDir).path
        }.take(25).toList()

        val promptFile = File(appDir, "brain_prompt.txt")
        val promptContent = if (promptFile.exists()) promptFile.readText() else "(Sem instruções salvas)"

        val message = buildString {
            appendLine("Pasta: WaylandAndroid/${appDir.name}")
            appendLine()
            appendLine("Arquivos (${files.size}):")
            files.forEach { appendLine("• $it") }
            appendLine()
            appendLine("Prompt para o Cérebro Qwen:")
            appendLine(promptContent)
        }

        AlertDialog.Builder(this)
            .setTitle(appDir.name)
            .setMessage(message)
            .setPositiveButton("Copiar Prompt") { _, _ ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("qwen_prompt", promptContent))
                Toast.makeText(this, "Prompt copiado! Cole no Qwen.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Fechar", null)
            .show()
    }

    private fun isTermuxInstalled(): Boolean {
        return try {
            packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun refreshTermuxStatus() {
        val installed = isTermuxInstalled()
        val hasPerm = ContextCompat.checkSelfPermission(this, TERMUX_PERMISSION) == PackageManager.PERMISSION_GRANTED
        termuxStatusText.text = when {
            !installed -> "Termux (com.termux) não encontrado. Instale pelo GitHub oficial (GPLv3) ou use o shell abaixo."
            hasPerm -> "Termux detectado · Permissão RUN_COMMAND ativa! Pronto para executar sh ./Qwen-Coder-0.5B.llamafile --server."
            else -> "Termux detectado · Permissão RUN_COMMAND será solicitada ao chamar."
        }
    }

    private fun runLlamafileInTermux() {
        if (!isTermuxInstalled()) {
            AlertDialog.Builder(this)
                .setTitle("Termux não instalado")
                .setMessage(
                    "O Termux (GPLv3 · github.com/termux) não está instalado neste aparelho.\n\n" +
                    "Deseja abrir os lançamentos oficiais do Termux no GitHub ou copiar o comando sh?"
                )
                .setPositiveButton("Baixar Termux (GitHub)") { _, _ ->
                    launchBrowserIntent("https://github.com/termux/termux-app/releases")
                }
                .setNeutralButton("Copiar Comando sh") { _, _ ->
                    showTermuxShDialog(null)
                }
                .setNegativeButton("Fechar", null)
                .show()
            return
        }

        val fullCmd = buildTermuxSetupAndRunCommand()
        if (ContextCompat.checkSelfPermission(this, TERMUX_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            pendingTermuxCmd = fullCmd
            ActivityCompat.requestPermissions(this, arrayOf(TERMUX_PERMISSION), REQUEST_TERMUX_PERMISSION)
            return
        }
        dispatchTermuxRunCommand(fullCmd)
    }

    private fun showTermuxPermissionHelpDialog(scriptText: String) {
        val (_, outputName) = resolveTargetNames()
        val quickCmd = "cd ~/WaylandAndroid/.brains && sh ./$outputName --server"
        termuxOutputText.text = "Permissão RUN_COMMAND pendente nas configurações do Android."
        AlertDialog.Builder(this)
            .setTitle("Liberar Permissão do Termux")
            .setMessage(
                "O Android bloqueou a permissão automática do Termux.\n\n" +
                "Para o app chamar o Termux direto:\n" +
                "1) Toque em 'Abrir Permissões' abaixo\n" +
                "2) Entre em Permissões -> Permissões adicionais\n" +
                "3) Ative 'Run commands in Termux environment'\n\n" +
                "Ou toque em 'Abrir Termux' para rodar manualmente."
            )
            .setPositiveButton("Abrir Permissões") { _, _ ->
                try {
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                } catch (_: Exception) {
                    Toast.makeText(this, "Abra Configurações > Apps > WaylandDroid > Permissões.", Toast.LENGTH_LONG).show()
                }
            }
            .setNeutralButton("Abrir Termux (sh)") { _, _ ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("llamafile_cmd", quickCmd))
                val launch = packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)
                if (launch != null) startActivity(launch)
                Toast.makeText(this, "Copiado: sh ./$outputName --server", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Copiar 1ª Instalação") { _, _ ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("llamafile_full", scriptText))
                val launch = packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)
                if (launch != null) startActivity(launch)
                Toast.makeText(this, "Script completo copiado!", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun dispatchTermuxRunCommand(scriptText: String) {
        try {
            val intent = Intent("com.termux.RUN_COMMAND").apply {
                setClassName(TERMUX_PACKAGE, TERMUX_SERVICE)
                putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", scriptText))
                putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
                putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            termuxOutputText.text = "Comando enviado ao Termux (RUN_COMMAND)!\nAguarde alguns segundos e toque em 'Abrir Qwen no Navegador'."
            Toast.makeText(this, "Executando llamafile no Termux!", Toast.LENGTH_SHORT).show()
        } catch (se: SecurityException) {
            val unlockCmd = "mkdir -p ~/.termux && echo 'allow-external-apps=true' >> ~/.termux/termux.properties && termux-reload-settings"
            termuxOutputText.text = "Termux bloqueou apps externos. Cole isto no Termux uma única vez:\n$unlockCmd"
            AlertDialog.Builder(this)
                .setTitle("Liberar apps externos no Termux")
                .setMessage(
                    "O Termux exige 'allow-external-apps=true' no arquivo ~/.termux/termux.properties (configuração única).\n\n" +
                    "Toque em 'Copiar e Abrir Termux', cole o comando lá uma vez e depois volte aqui!"
                )
                .setPositiveButton("Copiar e Abrir Termux") { _, _ ->
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("termux_unlock", unlockCmd))
                    val launch = packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)
                    if (launch != null) startActivity(launch)
                    Toast.makeText(this, "Comando copiado! Cole no Termux.", Toast.LENGTH_LONG).show()
                }
                .setNeutralButton("Copiar Só sh --server") { _, _ ->
                    val (_, outputName) = resolveTargetNames()
                    val quickCmd = "cd ~/WaylandAndroid/.brains && sh ./$outputName --server"
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("llamafile_quick", quickCmd))
                    val launch = packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)
                    if (launch != null) startActivity(launch)
                    Toast.makeText(this, "Copiado: sh ./$outputName --server", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Fechar", null)
                .show()
        } catch (e: Exception) {
            termuxOutputText.text = "Falha ao chamar Termux: ${e.message}"
            showTermuxShDialog(e.message)
        }
    }

    private fun openOrUnlockTermux() {
        val unlockCmd = "mkdir -p ~/.termux && echo 'allow-external-apps=true' >> ~/.termux/termux.properties && termux-reload-settings"
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("termux_unlock", unlockCmd))
        val launch = packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)
        if (launch != null) {
            startActivity(launch)
            Toast.makeText(this, "Comando allow-external-apps copiado! Cole no Termux.", Toast.LENGTH_LONG).show()
        } else {
            launchBrowserIntent("https://github.com/termux/termux-app/releases")
        }
    }

    private fun runLocalShellCommand() {
        val cmd = termuxCommandInput.text.toString().trim()
        if (cmd.isEmpty()) return
        termuxOutputText.text = "$ $cmd\nExecutando..."
        Thread {
            try {
                val proc = ProcessBuilder("sh", "-c", cmd)
                    .directory(waylandRootDir)
                    .redirectErrorStream(true)
                    .start()
                val out = proc.inputStream.bufferedReader().readText()
                val exit = proc.waitFor()
                runOnUiThread {
                    termuxOutputText.text = "$ $cmd (exit $exit)\n" + out.ifBlank { "(sem saída)" }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    termuxOutputText.text = "Erro shell: ${e.message}"
                }
            }
        }.start()
    }
}
