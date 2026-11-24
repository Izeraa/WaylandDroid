package org.wayland.xdgbridge

import android.Manifest
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
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
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
        private const val REQUEST_STORAGE_PERMISSION = 9002
        private const val REQUEST_NOTIFICATION_PERMISSION = 9003
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

    private data class RunModeOption(
        val label: String,
        val mode: String
    )

    private val qwenPresets = listOf(
        QwenPreset(
            "Qwen 2.5 Coder 0.5B (~420 MB · Código)",
            "qwen2.5-coder-0.5b-instruct-q5_k_m.gguf",
            "Qwen-Coder-0.5B.llamafile",
            "https://huggingface.co/Qwen/Qwen2.5-Coder-0.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-0.5b-instruct-q5_k_m.gguf"
        ),
        QwenPreset(
            "Qwen 3 0.6B (~640 MB · Texto)",
            "Qwen3-0.6B-Q8_0.gguf",
            "Qwen3-0.6B.llamafile",
            "https://huggingface.co/Qwen/Qwen3-0.6B-GGUF/resolve/main/Qwen3-0.6B-Q8_0.gguf"
        ),
        QwenPreset(
            "Qwen 2.5 0.5B Instruct (~400 MB · Texto)",
            "qwen2.5-0.5b-instruct-q4_k_m.gguf",
            "Qwen2.5-0.5B.llamafile",
            "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf"
        ),
        QwenPreset(
            "Llama 3.2 1B Instruct (~770 MB · Texto)",
            "Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            "Llama-3.2-1B.llamafile",
            "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf"
        ),
        QwenPreset(
            "Gemma 3 1B IT (~806 MB · Texto)",
            "gemma-3-1b-it-Q4_K_M.gguf",
            "Gemma-3-1B.llamafile",
            "https://huggingface.co/unsloth/gemma-3-1b-it-GGUF/resolve/main/gemma-3-1b-it-Q4_K_M.gguf"
        ),
        QwenPreset(
            "Qwen 2.5 Coder 1.5B (~1.0 GB · Código)",
            "qwen2.5-coder-1.5b-instruct-q4_k_m.gguf",
            "Qwen2.5-Coder-1.5B.llamafile",
            "https://huggingface.co/Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf"
        ),
        QwenPreset(
            "Qwen 2.5 1.5B Instruct (~1.0 GB · Texto)",
            "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            "Qwen2.5-1.5B.llamafile",
            "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf"
        ),
        QwenPreset(
            "SmolLM2 1.7B Instruct (~1.0 GB · Texto)",
            "smollm2-1.7b-instruct-q4_k_m.gguf",
            "SmolLM2-1.7B.llamafile",
            "https://huggingface.co/HuggingFaceTB/SmolLM2-1.7B-Instruct-GGUF/resolve/main/smollm2-1.7b-instruct-q4_k_m.gguf"
        ),
        QwenPreset(
            "Qwen 3 1.7B (~1.2 GB · Texto)",
            "Qwen_Qwen3-1.7B-Q4_K_M.gguf",
            "Qwen3-1.7B.llamafile",
            "https://huggingface.co/bartowski/Qwen_Qwen3-1.7B-GGUF/resolve/main/Qwen_Qwen3-1.7B-Q4_K_M.gguf"
        ),
        QwenPreset(
            "Qwen 2.5 3B Instruct (~1.9 GB · Texto)",
            "qwen2.5-3b-instruct-q4_k_m.gguf",
            "Qwen2.5-3B.llamafile",
            "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/qwen2.5-3b-instruct-q4_k_m.gguf"
        ),
        QwenPreset(
            "Llama 3.2 3B Instruct (~2.0 GB · Texto)",
            "Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            "Llama-3.2-3B.llamafile",
            "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf"
        ),
        QwenPreset(
            "SD-Turbo Imagem q4_0 (~1.8 GB · Imagem)",
            "sd_turbo_q4_0.gguf",
            "SD-Turbo-q4_0.llamafile",
            "https://huggingface.co/samoudi/sd-turbo-gguf/resolve/main/sd_turbo_q4_0.gguf"
        ),
        QwenPreset(
            "SD-Turbo Imagem q8_0 (~2.1 GB · Imagem)",
            "sd_turbo_q8_0.gguf",
            "SD-Turbo-q8_0.llamafile",
            "https://huggingface.co/samoudi/sd-turbo-gguf/resolve/main/sd_turbo_q8_0.gguf"
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

    private val runModeOptions = listOf(
        RunModeOption("Rodar via Termux Nativo (llama-server ARM64 · Sem PHDR)", "termux_llama_server"),
        RunModeOption("Rodar .llamafile Direto (sh ./nome.llamafile --server)", "llamafile_sh"),
        RunModeOption("Rodar .llamafile pelo Container Ubuntu 24.04 (aarch64 PRoot)", "ubuntu_container")
    )

    private lateinit var waylandRootDir: File
    private lateinit var brainsDir: File
    private lateinit var modelsDir: File

    private lateinit var storagePathText: TextView
    private lateinit var btnGrantStorage: Button
    private lateinit var backgroundStatusText: TextView
    private lateinit var btnToggleWakeLock: Button
    private lateinit var btnBatteryExempt: Button

    private lateinit var downloadedLlmsContainer: LinearLayout
    private lateinit var selectedDownloadedText: TextView

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
    private var selectedRunModeIndex = 0
    private var selectedUbuntuGpuIndex = 0
    private var selectedDownloadedFile: File? = null
    private var pendingTermuxCmd: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initStorageDirectories()
        BridgeSessionService.ensureRunning(this)
        requestBackgroundNotificationIfNeeded()

        setContentView(buildHomeUi())
        refreshStorageBanner()
        refreshBackgroundBanner()
        refreshDownloadedLlms()
        refreshAppsList()
        refreshTermuxStatus()
    }

    override fun onResume() {
        super.onResume()
        initStorageDirectories()
        BridgeSessionService.ensureRunning(this)
        if (::storagePathText.isInitialized) {
            refreshStorageBanner()
        }
        if (::backgroundStatusText.isInitialized) {
            refreshBackgroundBanner()
        }
        if (::downloadedLlmsContainer.isInitialized) {
            refreshDownloadedLlms()
        }
        if (::brainStatusText.isInitialized && brainProgress.visibility != View.VISIBLE) {
            brainStatusText.text = getSavedBrainsSummary()
        }
        if (::appsListContainer.isInitialized) {
            refreshAppsList()
        }
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
        } else if (requestCode == REQUEST_STORAGE_PERMISSION) {
            initStorageDirectories()
            if (::storagePathText.isInitialized) refreshStorageBanner()
            if (::appsListContainer.isInitialized) refreshAppsList()
            if (::brainStatusText.isInitialized) brainStatusText.text = getSavedBrainsSummary()
        }
    }

    private fun hasSharedStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun resolveWaylandRootDir(): File {
        if (hasSharedStoragePermission()) {
            val extRoot = Environment.getExternalStorageDirectory()
            if (extRoot != null) {
                val candidate = File(extRoot, "WaylandDroid")
                if (candidate.exists() || candidate.mkdirs()) {
                    if (candidate.canWrite()) return candidate
                }
            }
        }
        val appExt = getExternalFilesDir(null)
        if (appExt != null) {
            val fallbackExt = File(appExt, "WaylandDroid")
            if (fallbackExt.exists() || fallbackExt.mkdirs()) {
                return fallbackExt
            }
        }
        return File(filesDir, "WaylandDroid").apply { mkdirs() }
    }

    private fun initStorageDirectories() {
        waylandRootDir = resolveWaylandRootDir()
        brainsDir = File(waylandRootDir, ".brains").apply { mkdirs() }
        modelsDir = File(brainsDir, "models").apply { mkdirs() }
        seedStarterAppsIfNeeded()
    }

    private fun refreshStorageBanner() {
        val usingSharedRoot = waylandRootDir.absolutePath.endsWith("/WaylandDroid") &&
            !waylandRootDir.absolutePath.contains("/Android/data/") &&
            !waylandRootDir.absolutePath.contains("/data/user/")
        if (usingSharedRoot) {
            storagePathText.text = "Pasta raiz ativa: ${waylandRootDir.absolutePath}"
            btnGrantStorage.visibility = View.GONE
        } else {
            storagePathText.text =
                "Pasta temporária: ${waylandRootDir.absolutePath}\nLibere acesso para salvar direto em /storage/emulated/0/WaylandDroid"
            btnGrantStorage.visibility = View.VISIBLE
        }
    }

    private fun requestSharedStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } catch (_: Exception) {
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                } catch (_: Exception) {
                    Toast.makeText(
                        this,
                        "Abra Configurações > Apps > WaylandDroid > Acesso a todos os arquivos.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                REQUEST_STORAGE_PERMISSION
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Mantém BridgeSessionService + servidor .llamafile vivos em segundo plano (estilo Termux).
    }

    private fun requestBackgroundNotificationIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_NOTIFICATION_PERMISSION
                )
            }
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        return try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(packageName)
        } catch (_: Exception) {
            false
        }
    }

    private fun requestIgnoreBatteryOptimization() {
        try {
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) {
                Toast.makeText(
                    this,
                    "Abra Configurações > Apps > WaylandDroid > Bateria > Sem restrições.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun refreshBackgroundBanner() {
        val daemon = if (BridgeSessionService.isServiceRunning) "2º Plano ATIVO" else "Iniciando 2º plano..."
        val wl = if (BridgeSessionService.isWakeLockHeld) "WakeLock ON" else "WakeLock OFF"
        val srv = if (BridgeSessionService.isServerRunning) " · .llamafile rodando (:${BridgeSessionService.serverPort})" else " · Pronto para :8080"
        val bat = if (isIgnoringBatteryOptimizations()) "Bateria: Sem restrições ✓" else "Bateria: Toque para liberar"
        backgroundStatusText.text = "$daemon · $wl$srv\n$bat (não desliga ao minimizar ou abrir navegador)"
        btnToggleWakeLock.text = if (BridgeSessionService.isWakeLockHeld) "WakeLock: ATIVO" else "Ativar WakeLock"
        btnBatteryExempt.visibility = if (isIgnoringBatteryOptimizations()) View.GONE else View.VISIBLE
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
            text = "Home · WaylandDroid/ · Armazenamento interno raiz"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 12f
            setPadding(0, dp(4), 0, dp(10))
        })

        // Storage path indicator + 1-tap root permission button
        val storageCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardDrawable("#0F172A", "#334155")
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(14) }
        }
        storagePathText = TextView(this).apply {
            text = "Pasta: ${waylandRootDir.absolutePath}"
            setTextColor(Color.parseColor("#93C5FD"))
            textSize = 11f
            typeface = Typeface.MONOSPACE
        }
        storageCard.addView(storagePathText)
        btnGrantStorage = Button(this).apply {
            text = "Liberar Pasta /WaylandDroid na Raiz"
            setTextColor(Color.WHITE)
            textSize = 12f
            isAllCaps = false
            background = buttonDrawable("#2563EB")
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(38)
            ).apply { topMargin = dp(8) }
            setOnClickListener { requestSharedStoragePermission() }
        }
        storageCard.addView(btnGrantStorage)
        root.addView(storageCard)

        // Background persistence indicator (Termux-style daemon + WakeLock + Battery exemption)
        val bgCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardDrawable("#0F172A", "#059669")
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(14) }
        }
        backgroundStatusText = TextView(this).apply {
            text = "2º Plano Estilo Termux: WakeLock ATIVO · Servidor não desliga ao minimizar"
            setTextColor(Color.parseColor("#6EE7B7"))
            textSize = 11f
            typeface = Typeface.MONOSPACE
        }
        bgCard.addView(backgroundStatusText)

        val bgRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        }
        btnToggleWakeLock = Button(this).apply {
            text = "WakeLock: ATIVO"
            setTextColor(Color.WHITE)
            textSize = 11f
            isAllCaps = false
            background = buttonDrawable("#059669")
            layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f).apply { marginEnd = dp(6) }
            setOnClickListener {
                BridgeSessionService.toggleWakeLock(this@BridgeActivity)
                backgroundStatusText.postDelayed({ refreshBackgroundBanner() }, 180)
            }
        }
        btnBatteryExempt = Button(this).apply {
            text = "Sem Restrição Bateria"
            setTextColor(Color.WHITE)
            textSize = 11f
            isAllCaps = false
            background = buttonDrawable("#D97706")
            layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f).apply { marginEnd = dp(6) }
            setOnClickListener { requestIgnoreBatteryOptimization() }
        }
        val btnExitDaemon = Button(this).apply {
            text = "Parar 2º Plano"
            setTextColor(Color.WHITE)
            textSize = 11f
            isAllCaps = false
            background = buttonDrawable("#475569")
            layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f)
            setOnClickListener {
                BridgeSessionService.exitBackgroundSession(this@BridgeActivity)
                Toast.makeText(this@BridgeActivity, "Sessão em segundo plano encerrada.", Toast.LENGTH_SHORT).show()
                backgroundStatusText.postDelayed({ refreshBackgroundBanner() }, 200)
            }
        }
        bgRow.addView(btnToggleWakeLock)
        bgRow.addView(btnBatteryExempt)
        bgRow.addView(btnExitDaemon)
        bgCard.addView(bgRow)

        val bgRowStop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
        }
        val btnKillTermuxServer = Button(this).apply {
            text = "pkill -f llama-server"
            setTextColor(Color.WHITE)
            textSize = 11f
            isAllCaps = false
            background = buttonDrawable("#DC2626")
            layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f).apply { marginEnd = dp(6) }
            setOnClickListener { killTermuxLlamaServer() }
        }
        val btnCloseUbuntuContainer = Button(this).apply {
            text = "Fechar Container Ubuntu"
            setTextColor(Color.WHITE)
            textSize = 11f
            isAllCaps = false
            background = buttonDrawable("#B91C1C")
            layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f)
            setOnClickListener { closeUbuntuContainer() }
        }
        bgRowStop.addView(btnKillTermuxServer)
        bgRowStop.addView(btnCloseUbuntuContainer)
        bgCard.addView(bgRowStop)

        val bgRowTools = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
        }
        val btnTopInstallTools = Button(this).apply {
            text = "Instalar llama-server + G++ (Termux)"
            setTextColor(Color.WHITE)
            textSize = 11f
            isAllCaps = false
            background = buttonDrawable("#0F766E")
            layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f)
            setOnClickListener { installTermuxToolchain() }
        }
        bgRowTools.addView(btnTopInstallTools)
        bgCard.addView(bgRowTools)

        root.addView(bgCard)

        // 1. 1ª OPÇÃO DO MENU: MODELOS BAIXADOS E COMANDO DE EXECUTAR
        val execCard = makeCard()
        execCard.addView(
            makeSectionTitle(
                "1. Modelos Baixados e Comando de Executar",
                "Escolha um modelo já baixado em WaylandDroid/.brains para rodar em 2º plano (.llamafile / Termux / Container Ubuntu)."
            )
        )

        selectedDownloadedText = TextView(this).apply {
            text = "LLM ativo: verificando WaylandDroid/.brains..."
            setTextColor(Color.parseColor("#93C5FD"))
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, dp(6))
        }
        execCard.addView(selectedDownloadedText)

        downloadedLlmsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        }
        execCard.addView(downloadedLlmsContainer)

        val btnRefreshLlms = Button(this).apply {
            text = "Atualizar Lista de Modelos Baixados"
            setTextColor(Color.parseColor("#CBD5E1"))
            textSize = 11f
            isAllCaps = false
            background = buttonDrawable("#1E293B")
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(36)
            ).apply { bottomMargin = dp(12) }
            setOnClickListener { refreshDownloadedLlms() }
        }
        execCard.addView(btnRefreshLlms)

        // Comando de Executar logo abaixo da lista
        execCard.addView(
            makeSectionTitle(
                "Comando de Executar",
                "Escolha o modo (.llamafile / Termux / Container Ubuntu), hardware e desempenho para iniciar o servidor."
            )
        )

        val runModeSpinner = Spinner(this).apply {
            background = cardDrawable("#0F172A", "#4F46E5")
            setPadding(dp(8), dp(6), dp(8), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }
        runModeSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            runModeOptions.map { it.label }
        )
        runModeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                selectedRunModeIndex = pos
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        execCard.addView(runModeSpinner)

        val hwSpinner = Spinner(this).apply {
            background = cardDrawable("#0F172A", "#334155")
            setPadding(dp(8), dp(6), dp(8), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
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
        execCard.addView(hwSpinner)

        val perfSpinner = Spinner(this).apply {
            background = cardDrawable("#0F172A", "#334155")
            setPadding(dp(8), dp(6), dp(8), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
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
        execCard.addView(perfSpinner)

        val brainRowExec = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }
        val btnStartServer = Button(this).apply {
            text = "Executar Selecionado (:8080)"
            setTextColor(Color.WHITE)
            textSize = 12f
            isAllCaps = false
            background = buttonDrawable("#7C3AED")
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                marginEnd = dp(8)
            }
            setOnClickListener { startLlamafileServer() }
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
        brainRowExec.addView(btnStartServer)
        brainRowExec.addView(btnTermuxCmd)
        execCard.addView(brainRowExec)

        val btnRunLlamafileUbuntuCard1 = Button(this).apply {
            text = "Rodar .llamafile pelo Container Ubuntu (:8080)"
            setTextColor(Color.WHITE)
            textSize = 12f
            isAllCaps = false
            background = buttonDrawable("#6D28D9")
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(40)
            ).apply { bottomMargin = dp(8) }
            setOnClickListener { launchBrainInsideUbuntu() }
        }
        execCard.addView(btnRunLlamafileUbuntuCard1)

        brainWebUrlInput = EditText(this).apply {
            styleInput(this, "http://127.0.0.1:8080")
            setText("http://127.0.0.1:8080")
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }
        execCard.addView(brainWebUrlInput)

        val btnOpenQwenBrowser = Button(this).apply {
            text = "Abrir Qwen no Navegador (:8080)"
            setTextColor(Color.WHITE)
            textSize = 12f
            isAllCaps = false
            background = buttonDrawable("#0F766E")
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(40)
            )
            setOnClickListener { openQwenInBrowser() }
        }
        execCard.addView(btnOpenQwenBrowser)
        root.addView(execCard)

        // 2. 2ª OPÇÃO DO MENU: MODELOS PARA BAIXAR + EMPACOTAR
        val downloadCard = makeCard()
        downloadCard.addView(
            makeSectionTitle(
                "2. Modelos para Baixar + Empacotar",
                "Escolha um dos 13 modelos (Texto, Código ou SD-Turbo) ou cole a URL de qualquer .gguf / .llamafile para baixar e empacotar."
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
        downloadCard.addView(spinner)

        brainUrlInput = EditText(this).apply {
            styleInput(this, "Cole a URL do .gguf ou .llamafile para baixar")
            setText(qwenPresets[0].url)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        }
        downloadCard.addView(brainUrlInput)

        var presetInitSkipped = false
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                selectedPresetIndex = pos
                val preset = qwenPresets[pos]
                if (preset.url.isNotEmpty()) {
                    brainUrlInput.setText(preset.url)
                }
                if (presetInitSkipped) {
                    selectedDownloadedFile = null
                    refreshDownloadedLlms()
                }
                presetInitSkipped = true
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
        downloadCard.addView(brainProgress)

        brainStatusText = TextView(this).apply {
            text = getSavedBrainsSummary()
            setTextColor(Color.parseColor("#CBD5E1"))
            textSize = 12f
            setPadding(0, dp(8), 0, dp(10))
        }
        downloadCard.addView(brainStatusText)

        val brainRowDownload = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val btnDownloadBrain = Button(this).apply {
            text = "Baixar + Empacotar Novo LLM"
            setTextColor(Color.WHITE)
            textSize = 12f
            isAllCaps = false
            background = buttonDrawable("#2563EB")
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                marginEnd = dp(8)
            }
            setOnClickListener { startDownloadBrain() }
        }
        val btnRemoveModels = Button(this).apply {
            text = "Remover Modelos"
            setTextColor(Color.WHITE)
            textSize = 12f
            isAllCaps = false
            background = buttonDrawable("#DC2626")
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f)
            setOnClickListener { showRemoveModelsDialog() }
        }
        brainRowDownload.addView(btnDownloadBrain)
        brainRowDownload.addView(btnRemoveModels)
        downloadCard.addView(brainRowDownload)

        root.addView(downloadCard)

        // 3. 3ª OPÇÃO DO MENU: INTEGRAÇÃO TERMUX (GPLv3 · github.com/termux) + EXECUTOR LLAMAFILE
        val termuxCard = makeCard()
        termuxCard.addView(
            makeSectionTitle(
                "3. Termux (GPLv3) · Executor Llamafile",
                "3ª opção do menu: chama o Termux diretamente via RUN_COMMAND para rodar sh ./Qwen-Coder-0.5B.llamafile --server."
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

        val btnTermuxUbuntuLlamafile = Button(this).apply {
            text = "Rodar .llamafile pelo Container Ubuntu (PRoot)"
            setTextColor(Color.WHITE)
            textSize = 12f
            isAllCaps = false
            background = buttonDrawable("#7C3AED")
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(40)
            ).apply { bottomMargin = dp(8) }
            setOnClickListener { launchBrainInsideUbuntu() }
        }
        termuxCard.addView(btnTermuxUbuntuLlamafile)

        val btnInstallTermuxToolchain = Button(this).apply {
            text = "Instalar llama-server + GCC/G++ (C++) no Termux"
            setTextColor(Color.WHITE)
            textSize = 12f
            isAllCaps = false
            background = buttonDrawable("#0F766E")
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(40)
            ).apply { bottomMargin = dp(8) }
            setOnClickListener { installTermuxToolchain() }
        }
        termuxCard.addView(btnInstallTermuxToolchain)

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
            styleInput(this, "Comando shell (ex: ls -la WaylandDroid/.brains)")
            setText("ls -la .brains")
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }
        termuxCard.addView(termuxCommandInput)

        val btnRunLocalShell = Button(this).apply {
            text = "Executar no Shell Local (WaylandDroid/)"
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

        // 4. CONTAINER UBUNTU 24.04 (AARCH64 · ART/JVM VS NDK · OPENGL ES & VULKAN)
        val ubuntuCard = makeCard()
        ubuntuCard.addView(
            makeSectionTitle(
                "4. Container Ubuntu 24.04 (aarch64 · ART/NDK + GPU)",
                "Tradução dual ART/JVM vs NDK Bionic para seu celular aarch64, seleção OpenGL ES 3.2 / Vulkan e execução Qwen (.llamafile)."
            )
        )
        val ubuntuGpuOptions = UbuntuContainerManager.GpuBackend.values().toList()
        val ubuntuGpuSpinner = Spinner(this).apply {
            background = cardDrawable("#0F172A", "#EA580C")
            setPadding(dp(8), dp(6), dp(8), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }
        ubuntuGpuSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            ubuntuGpuOptions.map { "GPU Ubuntu: ${it.label}" }
        )
        ubuntuGpuSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                selectedUbuntuGpuIndex = pos
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        ubuntuCard.addView(ubuntuGpuSpinner)

        val btnOpenUbuntuShell = Button(this).apply {
            text = "Abrir Container Ubuntu + Tradutor aarch64"
            setTextColor(Color.WHITE)
            textSize = 13f
            isAllCaps = false
            background = buttonDrawable("#EA580C")
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(42)
            ).apply { bottomMargin = dp(8) }
            setOnClickListener {
                val gpu = ubuntuGpuOptions.getOrNull(selectedUbuntuGpuIndex)
                    ?: UbuntuContainerManager.GpuBackend.OPENGL_VULKAN_HYBRID
                val intent = Intent(this@BridgeActivity, UbuntuShellActivity::class.java).apply {
                    putExtra("GPU_BACKEND", gpu.id)
                }
                startActivity(intent)
            }
        }
        val btnRunBrainUbuntu = Button(this).apply {
            text = "Rodar .llamafile pelo Container Ubuntu (:8080)"
            setTextColor(Color.WHITE)
            textSize = 12f
            isAllCaps = false
            background = buttonDrawable("#7C3AED")
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(40)
            ).apply { bottomMargin = dp(8) }
            setOnClickListener { launchBrainInsideUbuntu() }
        }
        val btnInstallUbuntuToolchain = Button(this).apply {
            text = "Instalar GCC/G++ (C++) + llama-server no Ubuntu"
            setTextColor(Color.WHITE)
            textSize = 12f
            isAllCaps = false
            background = buttonDrawable("#0F766E")
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(40)
            )
            setOnClickListener { installUbuntuToolchainViaTermux() }
        }
        ubuntuCard.addView(btnOpenUbuntuShell)
        ubuntuCard.addView(btnRunBrainUbuntu)
        ubuntuCard.addView(btnInstallUbuntuToolchain)
        root.addView(ubuntuCard)

        // 5. BAIXAR DO GITHUB PARA WaylandDroid/nome_do_aplicativo
        val githubCard = makeCard()
        githubCard.addView(
            makeSectionTitle(
                "5. Baixar Aplicativo do GitHub",
                "Baixa os arquivos direto para WaylandDroid/nome_do_aplicativo."
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

        // 6. LISTA DE APLICATIVOS CRIADOS (WaylandDroid/nome_do_aplicativo)
        val appsHeaderCard = makeCard()
        appsHeaderCard.addView(
            makeSectionTitle(
                "6. Aplicativos Criados (WaylandDroid/)",
                "Toque em Abrir App para iniciar o compositor com o mouse azul."
            )
        )
        appsListContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        appsHeaderCard.addView(appsListContainer)
        root.addView(appsHeaderCard)

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

    private fun resolveTargetNames(preferDownloaded: Boolean = true): Pair<String, String> {
        val downloaded = if (preferDownloaded) selectedDownloadedFile?.takeIf { it.exists() } else null
        if (downloaded != null) {
            val dName = downloaded.name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            if (dName.endsWith(".llamafile", true)) {
                val matchedPreset = qwenPresets.firstOrNull { it.outputLlamafileName.equals(dName, true) }
                val baseGguf = matchedPreset?.modelFileName
                    ?: (dName.removeSuffix(".llamafile").removeSuffix(".LLAMAFILE") + ".gguf")
                return baseGguf to dName
            }
            val outLlama = qwenPresets.firstOrNull { it.modelFileName.equals(dName, true) }?.outputLlamafileName
                ?: (dName.removeSuffix(".gguf").removeSuffix(".GGUF") + ".llamafile")
            return dName to outLlama
        }

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

    private fun listDownloadedLlmFiles(): List<File> {
        val result = mutableListOf<File>()
        brainsDir.listFiles()?.filter {
            it.isFile && it.name.endsWith(".llamafile", true)
        }?.sortedByDescending { it.lastModified() }?.let { result.addAll(it) }
        modelsDir.listFiles()?.filter {
            it.isFile && it.name.endsWith(".gguf", true)
        }?.sortedByDescending { it.lastModified() }?.let { result.addAll(it) }
        return result
    }

    private fun refreshDownloadedLlms() {
        if (!::downloadedLlmsContainer.isInitialized) return
        downloadedLlmsContainer.removeAllViews()
        val files = listDownloadedLlmFiles()

        if (selectedDownloadedFile != null && selectedDownloadedFile?.exists() != true) {
            selectedDownloadedFile = null
        }
        if (selectedDownloadedFile == null && files.isNotEmpty()) {
            selectedDownloadedFile = files.first()
        }

        if (files.isEmpty()) {
            selectedDownloadedText.text = "Nenhum LLM baixado em WaylandDroid/.brains ainda."
            downloadedLlmsContainer.addView(TextView(this).apply {
                text = "Baixe um modelo abaixo (Baixar + Empacotar) ou coloque arquivos .llamafile / .gguf em WaylandDroid/.brains/."
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 12f
                background = cardDrawable("#0F172A", "#334155")
                setPadding(dp(12), dp(10), dp(12), dp(10))
            })
            return
        }

        val activeName = selectedDownloadedFile?.name ?: files.first().name
        selectedDownloadedText.text = "LLM selecionado para rodar: $activeName"

        for (file in files) {
            val isSelected = selectedDownloadedFile?.absolutePath == file.absolutePath
            val mb = max(1L, file.length() / (1024L * 1024L))
            val kindTag = if (file.name.endsWith(".llamafile", true)) ".llamafile" else "models/.gguf"

            val itemCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = cardDrawable(
                    if (isSelected) "#172554" else "#0F172A",
                    if (isSelected) "#3B82F6" else "#334155"
                )
                setPadding(dp(10), dp(10), dp(10), dp(10))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(8) }
                setOnClickListener {
                    selectedDownloadedFile = file
                    refreshDownloadedLlms()
                }
            }

            itemCard.addView(TextView(this).apply {
                text = (if (isSelected) "▶ " else "• ") + file.name
                setTextColor(Color.parseColor("#F8FAFC"))
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
            })
            itemCard.addView(TextView(this).apply {
                text = "$kindTag · ${mb} MB" + if (isSelected) " · Selecionado" else " · Toque para selecionar"
                setTextColor(Color.parseColor(if (isSelected) "#93C5FD" else "#94A3B8"))
                textSize = 11f
                setPadding(0, dp(2), 0, dp(8))
            })

            val rowBtns = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowBtns.addView(Button(this).apply {
                text = "Rodar"
                setTextColor(Color.WHITE)
                textSize = 11f
                isAllCaps = false
                background = buttonDrawable("#7C3AED")
                layoutParams = LinearLayout.LayoutParams(0, dp(34), 1f).apply { marginEnd = dp(6) }
                setOnClickListener {
                    selectedDownloadedFile = file
                    refreshDownloadedLlms()
                    startLlamafileServer()
                }
            })
            rowBtns.addView(Button(this).apply {
                text = "Ubuntu"
                setTextColor(Color.WHITE)
                textSize = 11f
                isAllCaps = false
                background = buttonDrawable("#EA580C")
                layoutParams = LinearLayout.LayoutParams(0, dp(34), 1f).apply { marginEnd = dp(6) }
                setOnClickListener {
                    selectedDownloadedFile = file
                    refreshDownloadedLlms()
                    launchBrainInsideUbuntu()
                }
            })
            rowBtns.addView(Button(this).apply {
                text = "Remover"
                setTextColor(Color.WHITE)
                textSize = 11f
                isAllCaps = false
                background = buttonDrawable("#DC2626")
                layoutParams = LinearLayout.LayoutParams(0, dp(34), 1f)
                setOnClickListener {
                    AlertDialog.Builder(this@BridgeActivity)
                        .setTitle("Remover LLM")
                        .setMessage("Deseja apagar '${file.name}' de WaylandDroid/.brains?")
                        .setPositiveButton("Remover") { _, _ ->
                            BridgeSessionService.stopServerOnly(this@BridgeActivity)
                            file.delete()
                            if (selectedDownloadedFile?.absolutePath == file.absolutePath) {
                                selectedDownloadedFile = null
                            }
                            refreshDownloadedLlms()
                            brainStatusText.text = getSavedBrainsSummary()
                        }
                        .setNegativeButton("Cancelar", null)
                        .show()
                }
            })
            itemCard.addView(rowBtns)
            downloadedLlmsContainer.addView(itemCard)
        }
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

    private fun resolveTargetUrl(modelName: String, outputName: String): String {
        val mStem = modelName.substringBeforeLast('.').lowercase()
        val oStem = outputName.substringBeforeLast('.').lowercase()
        val presetMatch = qwenPresets.firstOrNull {
            it.modelFileName.equals(modelName, true) ||
                it.outputLlamafileName.equals(outputName, true) ||
                it.modelFileName.substringBeforeLast('.').lowercase() == mStem ||
                it.outputLlamafileName.substringBeforeLast('.').lowercase() == oStem
        }
        val inputUrl = brainUrlInput.text.toString().trim()
        if (inputUrl.isNotEmpty() &&
            (inputUrl.contains(modelName, ignoreCase = true) || inputUrl.contains(outputName, ignoreCase = true))
        ) {
            return inputUrl
        }
        return presetMatch?.url ?: inputUrl.ifEmpty { qwenPresets[0].url }
    }

    private fun buildTermuxUnlockPrefix(): String {
        return "mkdir -p ~/.termux && (grep -q 'allow-external-apps=true' ~/.termux/termux.properties 2>/dev/null || (echo 'allow-external-apps=true' >> ~/.termux/termux.properties && (termux-reload-settings >/dev/null 2>&1 || true)))"
    }

    private fun resolveQuickShCommand(modelName: String, outputName: String): String {
        val runMode = runModeOptions.getOrNull(selectedRunModeIndex)?.mode ?: "termux_llama_server"
        val hw = hardwareOptions.getOrNull(selectedHardwareIndex) ?: hardwareOptions[0]
        val perf = perfOptions.getOrNull(selectedPerfIndex) ?: perfOptions[0]
        val threads = resolveThreadsCount()
        val rawUrl = resolveTargetUrl(modelName, outputName)
        val appModelsDir = modelsDir.absolutePath
        val appBrainsDir = brainsDir.absolutePath
        val unlockPrefix = buildTermuxUnlockPrefix()

        if (runMode == "llamafile_sh") {
            return "$unlockPrefix; mkdir -p ~/WaylandDroid/.brains/models && cd ~/WaylandDroid/.brains && (for l in \"$appBrainsDir/$outputName\" \"/sdcard/WaylandDroid/.brains/$outputName\" \"/storage/emulated/0/WaylandDroid/.brains/$outputName\" \"\$HOME/storage/shared/WaylandDroid/.brains/$outputName\"; do [ -r \"\$l\" ] && [ -s \"\$l\" ] && [ ! -s ./$outputName ] && cp \"\$l\" ./$outputName 2>/dev/null && chmod 755 ./$outputName && break; done || true); (sh ./$outputName --server --nobrowser --port 8080 || (command -v unzip >/dev/null 2>&1 || pkg install -y unzip; command -v llama-server >/dev/null 2>&1 || pkg install -y llama-cpp; [ -s ./$outputName ] && unzip -o ./$outputName '*.gguf' -d ./models >/dev/null 2>&1 || true; M=\$(ls -S ./models/*.gguf \"$appModelsDir\"/*.gguf /sdcard/WaylandDroid/.brains/models/*.gguf ~/storage/shared/WaylandDroid/.brains/models/*.gguf 2>/dev/null | head -n1); [ -s \"\$M\" ] && llama-server -m \"\$M\" -c ${perf.contextSize} -t $threads -ngl ${hw.gpuLayers} --port 8080 --host 127.0.0.1)) || echo 'ERRO: $outputName não encontrado.'"
        }
        if (runMode == "ubuntu_container") {
            val gpu = UbuntuContainerManager.GpuBackend.values().getOrNull(selectedUbuntuGpuIndex)
                ?: UbuntuContainerManager.GpuBackend.OPENGL_VULKAN_HYBRID
            return UbuntuContainerManager(this, waylandRootDir).buildProotRunBrainScript(modelName, outputName, rawUrl, gpu)
        }
        if (modelName.endsWith(".llamafile", true)) {
            return "$unlockPrefix; command -v llama-server >/dev/null 2>&1 || (pkg update -y && pkg install -y llama-cpp unzip); mkdir -p ~/WaylandDroid/.brains/models && cd ~/WaylandDroid/.brains; for l in \"$appBrainsDir/$outputName\" \"/sdcard/WaylandDroid/.brains/$outputName\" \"\$HOME/storage/shared/WaylandDroid/.brains/$outputName\" \"./$outputName\"; do if [ -r \"\$l\" ] && [ -s \"\$l\" ]; then unzip -o \"\$l\" '*.gguf' -d ./models >/dev/null 2>&1 || true; break; fi; done; M=\$(ls -S ./models/*.gguf \"$appModelsDir\"/*.gguf /sdcard/WaylandDroid/.brains/models/*.gguf ~/storage/shared/WaylandDroid/.brains/models/*.gguf 2>/dev/null | head -n1); if [ ! -s \"\$M\" ]; then curl -fL --retry 3 -C - -o \"./$outputName.part\" '$rawUrl' && mv \"./$outputName.part\" \"./$outputName\" && unzip -o \"./$outputName\" '*.gguf' -d ./models >/dev/null 2>&1 || true; M=\$(ls -S ./models/*.gguf 2>/dev/null | head -n1); fi; [ -s \"\$M\" ] && llama-server -m \"\$M\" -c ${perf.contextSize} -t $threads -ngl ${hw.gpuLayers} --port 8080 --host 127.0.0.1 || echo 'ERRO: Nenhum GGUF válido encontrado para $outputName.'"
        }
        return "$unlockPrefix; command -v llama-server >/dev/null 2>&1 || (pkg update -y && pkg install -y llama-cpp curl); mkdir -p ~/WaylandDroid/.brains/models && cd ~/WaylandDroid/.brains; M=\"\"; for p in \"$appModelsDir/$modelName\" \"/sdcard/WaylandDroid/.brains/models/$modelName\" \"/storage/emulated/0/WaylandDroid/.brains/models/$modelName\" \"\$HOME/storage/shared/WaylandDroid/.brains/models/$modelName\" \"\$HOME/WaylandDroid/.brains/models/$modelName\" \"\$HOME/WaylandAndroid/.brains/models/$modelName\" \"./models/$modelName\"; do if [ -r \"\$p\" ] && [ -s \"\$p\" ]; then M=\"\$p\"; break; fi; done; if [ -z \"\$M\" ]; then for l in \"$appBrainsDir/$outputName\" \"/sdcard/WaylandDroid/.brains/$outputName\" \"\$HOME/storage/shared/WaylandDroid/.brains/$outputName\" \"./$outputName\"; do if [ -r \"\$l\" ] && [ -s \"\$l\" ]; then (command -v unzip >/dev/null 2>&1 || pkg install -y unzip) && unzip -o \"\$l\" '*.gguf' -d ./models >/dev/null 2>&1 || true; break; fi; done; M=\$(ls -S ./models/*.gguf 2>/dev/null | head -n1); fi; if [ ! -s \"\$M\" ]; then echo 'Baixando $modelName...'; curl -fL --retry 3 -C - -o \"./models/$modelName.part\" '$rawUrl' && mv \"./models/$modelName.part\" \"./models/$modelName\" && M=\"./models/$modelName\" || rm -f \"./models/$modelName.part\"; fi; [ -s \"\$M\" ] && llama-server -m \"\$M\" -c ${perf.contextSize} -t $threads -ngl ${hw.gpuLayers} --port 8080 --host 127.0.0.1 || echo 'ERRO: Falha ao localizar ou baixar $modelName.'"
    }

    private fun buildTermuxSetupAndRunCommand(): String {
        val (modelName, outputName) = resolveTargetNames()
        val rawUrl = resolveTargetUrl(modelName, outputName)
        val argsText = buildArgsText(modelName).trimEnd()
        val runMode = runModeOptions.getOrNull(selectedRunModeIndex)?.mode ?: "termux_llama_server"
        val hw = hardwareOptions.getOrNull(selectedHardwareIndex) ?: hardwareOptions[0]
        val perf = perfOptions.getOrNull(selectedPerfIndex) ?: perfOptions[0]
        val threads = resolveThreadsCount()
        val appModelsDir = modelsDir.absolutePath
        val appBrainsDir = brainsDir.absolutePath
        val unlockPrefix = buildTermuxUnlockPrefix()

        if (runMode == "ubuntu_container") {
            val gpu = UbuntuContainerManager.GpuBackend.values().getOrNull(selectedUbuntuGpuIndex)
                ?: UbuntuContainerManager.GpuBackend.OPENGL_VULKAN_HYBRID
            return UbuntuContainerManager(this, waylandRootDir).buildProotRunBrainScript(modelName, outputName, rawUrl, gpu)
        }

        if (runMode == "llamafile_sh") {
            return buildString {
                appendLine(unlockPrefix)
                appendLine("mkdir -p ~/WaylandDroid/.brains/models && cd ~/WaylandDroid/.brains")
                appendLine("if [ ! -s ./$outputName ]; then")
                appendLine("  for l in \"$appBrainsDir/$outputName\" \"/sdcard/WaylandDroid/.brains/$outputName\" \"/storage/emulated/0/WaylandDroid/.brains/$outputName\" \"\$HOME/storage/shared/WaylandDroid/.brains/$outputName\"; do")
                appendLine("    if [ -r \"\$l\" ] && [ -s \"\$l\" ]; then cp \"\$l\" \"./$outputName\" 2>/dev/null && chmod 755 \"./$outputName\"; break; fi")
                appendLine("  done")
                appendLine("fi")
                appendLine("MODEL_PATH=\"\"")
                appendLine("for p in \"$appModelsDir/$modelName\" \"/sdcard/WaylandDroid/.brains/models/$modelName\" \"/storage/emulated/0/WaylandDroid/.brains/models/$modelName\" \"\$HOME/storage/shared/WaylandDroid/.brains/models/$modelName\" \"\$HOME/WaylandDroid/.brains/models/$modelName\" \"\$HOME/WaylandAndroid/.brains/models/$modelName\" \"./models/$modelName\"; do")
                appendLine("  if [ -r \"\$p\" ] && [ -s \"\$p\" ]; then MODEL_PATH=\"\$p\"; break; fi")
                appendLine("done")
                appendLine("if [ ! -s ./$outputName ]; then")
                appendLine("  if [ -z \"\$MODEL_PATH\" ]; then curl -fL --retry 3 -C - -o \"./models/$modelName.part\" '$rawUrl' && mv \"./models/$modelName.part\" \"./models/$modelName\" && MODEL_PATH=\"./models/$modelName\"; else [ \"\$MODEL_PATH\" != \"./models/$modelName\" ] && cp \"\$MODEL_PATH\" \"./models/$modelName\" 2>/dev/null || true; fi")
                appendLine("  if [ ! -s ./llamafile ]; then curl -fL --retry 3 -o ./llamafile.part '$LLAMAFILE_BASE_URL/llamafile-$LLAMAFILE_VERSION' && mv ./llamafile.part ./llamafile && chmod 755 ./llamafile; fi")
                appendLine("  if [ ! -s ./zipalign ]; then curl -fL --retry 3 -o ./zipalign.part '$LLAMAFILE_BASE_URL/zipalign-$LLAMAFILE_VERSION' && mv ./zipalign.part ./zipalign && chmod 755 ./zipalign; fi")
                appendLine("  cat << 'EOF' > ./models/.args")
                appendLine(argsText)
                appendLine("EOF")
                appendLine("  [ -s \"./models/$modelName\" ] && cp ./llamafile ./$outputName && (cd ./models && sh ../zipalign -j0 ../$outputName $modelName .args)")
                appendLine("fi")
                appendLine("LLAMAFILE_OK=0")
                appendLine("if [ -s ./$outputName ]; then sh ./$outputName --server --nobrowser --port 8080 && LLAMAFILE_OK=1 || LLAMAFILE_OK=0; fi")
                appendLine("if [ \"\$LLAMAFILE_OK\" = \"0\" ]; then")
                appendLine("  echo 'Aviso: sh .llamafile falhou neste kernel (PHDR/APE). Iniciando automaticamente via llama-server ARM64...'")
                appendLine("  if [ ! -s \"./models/$modelName\" ] && [ -s ./$outputName ]; then")
                appendLine("    command -v unzip >/dev/null 2>&1 || pkg install -y unzip")
                appendLine("    unzip -o ./$outputName '*.gguf' -d ./models >/dev/null 2>&1 || true")
                appendLine("  fi")
                appendLine("  M=\"\$MODEL_PATH\"")
                appendLine("  [ ! -s \"\$M\" ] && M=\$(ls -S ./models/*.gguf \"$appModelsDir\"/*.gguf /sdcard/WaylandDroid/.brains/models/*.gguf ~/storage/shared/WaylandDroid/.brains/models/*.gguf 2>/dev/null | head -n1)")
                appendLine("  if [ ! -s \"\$M\" ]; then curl -fL --retry 3 -C - -o \"./models/$modelName.part\" '$rawUrl' && mv \"./models/$modelName.part\" \"./models/$modelName\" && M=\"./models/$modelName\"; fi")
                appendLine("  command -v llama-server >/dev/null 2>&1 || (pkg update -y && pkg install -y llama-cpp)")
                appendLine("  [ -s \"\$M\" ] && llama-server -m \"\$M\" -c ${perf.contextSize} -t $threads -ngl ${hw.gpuLayers} --port 8080 --host 127.0.0.1 || echo 'ERRO: Falha ao preparar $outputName / $modelName.'")
                append("fi")
            }
        }

        if (modelName.endsWith(".llamafile", true)) {
            return buildString {
                appendLine(unlockPrefix)
                appendLine("mkdir -p ~/WaylandDroid/.brains/models && cd ~/WaylandDroid/.brains")
                appendLine("if [ ! -s ./$outputName ]; then")
                appendLine("  for l in \"$appBrainsDir/$outputName\" \"/sdcard/WaylandDroid/.brains/$outputName\" \"/storage/emulated/0/WaylandDroid/.brains/$outputName\" \"\$HOME/storage/shared/WaylandDroid/.brains/$outputName\"; do")
                appendLine("    if [ -r \"\$l\" ] && [ -s \"\$l\" ]; then cp \"\$l\" \"./$outputName\" 2>/dev/null && chmod 755 \"./$outputName\"; break; fi")
                appendLine("  done")
                appendLine("fi")
                appendLine("if [ ! -s ./$outputName ]; then curl -fL --retry 3 -C - -o \"./$outputName.part\" '$rawUrl' && mv \"./$outputName.part\" \"./$outputName\" && chmod 755 \"./$outputName\"; fi")
                appendLine("if [ -n \"\$TERMUX_VERSION\" ] || [ -d /data/data/com.termux ]; then")
                appendLine("  command -v llama-server >/dev/null 2>&1 || (pkg update -y && pkg install -y llama-cpp unzip)")
                appendLine("  command -v unzip >/dev/null 2>&1 || pkg install -y unzip")
                appendLine("  [ -s ./$outputName ] && unzip -o ./$outputName '*.gguf' -d ./models >/dev/null 2>&1 || true")
                appendLine("  M=\$(ls -S ./models/*.gguf \"$appModelsDir\"/*.gguf /sdcard/WaylandDroid/.brains/models/*.gguf ~/storage/shared/WaylandDroid/.brains/models/*.gguf 2>/dev/null | head -n1)")
                appendLine("  if [ -s \"\$M\" ]; then llama-server -m \"\$M\" -c ${perf.contextSize} -t $threads -ngl ${hw.gpuLayers} --port 8080 --host 127.0.0.1; else echo 'ERRO: Nenhum arquivo .gguf válido extraído ou baixado.'; fi")
                appendLine("else")
                appendLine("  sh ./$outputName --server")
                append("fi")
            }
        }
        return buildString {
            appendLine(unlockPrefix)
            appendLine("mkdir -p ~/WaylandDroid/.brains/models && cd ~/WaylandDroid/.brains")
            appendLine("MODEL_PATH=\"\"")
            appendLine("for p in \"$appModelsDir/$modelName\" \"/sdcard/WaylandDroid/.brains/models/$modelName\" \"/storage/emulated/0/WaylandDroid/.brains/models/$modelName\" \"\$HOME/storage/shared/WaylandDroid/.brains/models/$modelName\" \"\$HOME/WaylandDroid/.brains/models/$modelName\" \"\$HOME/WaylandAndroid/.brains/models/$modelName\" \"./models/$modelName\"; do")
            appendLine("  if [ -r \"\$p\" ] && [ -s \"\$p\" ]; then MODEL_PATH=\"\$p\"; break; fi")
            appendLine("done")
            appendLine("if [ -z \"\$MODEL_PATH\" ]; then")
            appendLine("  for l in \"$appBrainsDir/$outputName\" \"/sdcard/WaylandDroid/.brains/$outputName\" \"/storage/emulated/0/WaylandDroid/.brains/$outputName\" \"\$HOME/storage/shared/WaylandDroid/.brains/$outputName\" \"./$outputName\"; do")
            appendLine("    if [ -r \"\$l\" ] && [ -s \"\$l\" ]; then")
            appendLine("      command -v unzip >/dev/null 2>&1 || pkg install -y unzip")
            appendLine("      unzip -o \"\$l\" '*.gguf' -d ./models >/dev/null 2>&1 || true")
            appendLine("      [ -s \"./models/$modelName\" ] && MODEL_PATH=\"./models/$modelName\" && break")
            appendLine("    fi")
            appendLine("  done")
            appendLine("fi")
            appendLine("if [ -z \"\$MODEL_PATH\" ]; then")
            appendLine("  echo 'Baixando modelo GGUF ($modelName)...'")
            appendLine("  if curl -fL --retry 3 -C - -o \"./models/$modelName.part\" '$rawUrl'; then")
            appendLine("    mv \"./models/$modelName.part\" \"./models/$modelName\"")
            appendLine("    MODEL_PATH=\"./models/$modelName\"")
            appendLine("  else")
            appendLine("    rm -f \"./models/$modelName.part\"")
            appendLine("  fi")
            appendLine("fi")
            appendLine("if [ -z \"\$MODEL_PATH\" ] || [ ! -s \"\$MODEL_PATH\" ]; then")
            appendLine("  MODEL_PATH=\$(ls -S ./models/*.gguf \"$appModelsDir\"/*.gguf /sdcard/WaylandDroid/.brains/models/*.gguf ~/storage/shared/WaylandDroid/.brains/models/*.gguf 2>/dev/null | head -n1)")
            appendLine("fi")
            appendLine("if [ -z \"\$MODEL_PATH\" ] || [ ! -s \"\$MODEL_PATH\" ]; then")
            appendLine("  echo 'ERRO: Modelo $modelName não encontrado e download falhou ($rawUrl).'")
            appendLine("  echo 'Verifique a internet ou baixe pelo app em 2. Modelos para Baixar + Empacotar.'")
            appendLine("else")
            appendLine("  cat << 'EOF' > ./models/.args")
            appendLine(argsText)
            appendLine("EOF")
            appendLine("  if [ -n \"\$TERMUX_VERSION\" ] || [ -d /data/data/com.termux ]; then")
            appendLine("    command -v llama-server >/dev/null 2>&1 || (pkg update -y && pkg install -y llama-cpp)")
            appendLine("    llama-server -m \"\$MODEL_PATH\" -c ${perf.contextSize} -t $threads -ngl ${hw.gpuLayers} --port 8080 --host 127.0.0.1")
            appendLine("  else")
            appendLine("    if [ ! -s ./$outputName ]; then")
            appendLine("      if [ ! -s ./llamafile ]; then curl -fL --retry 3 -o ./llamafile.part '$LLAMAFILE_BASE_URL/llamafile-$LLAMAFILE_VERSION' && mv ./llamafile.part ./llamafile && chmod 755 ./llamafile; fi")
            appendLine("      if [ ! -s ./zipalign ]; then curl -fL --retry 3 -o ./zipalign.part '$LLAMAFILE_BASE_URL/zipalign-$LLAMAFILE_VERSION' && mv ./zipalign.part ./zipalign && chmod 755 ./zipalign; fi")
            appendLine("      cp ./llamafile ./$outputName && (cd ./models && sh ../zipalign -j0 ../$outputName $modelName .args)")
            appendLine("    fi")
            appendLine("    sh ./$outputName --server")
            appendLine("  fi")
            append("fi")
        }
    }

    private fun showRemoveModelsDialog() {
        val modelFiles = mutableListOf<File>()
        brainsDir.listFiles()?.filter {
            it.isFile && (it.name.endsWith(".llamafile", true) || it.name == "llamafile" || it.name == "zipalign" || it.name == "run_server.sh")
        }?.sortedBy { it.name }?.let { modelFiles.addAll(it) }

        modelsDir.listFiles()?.filter {
            it.isFile && (it.name.endsWith(".gguf", true) || it.name == ".args")
        }?.sortedBy { it.name }?.let { modelFiles.addAll(it) }

        if (modelFiles.isEmpty()) {
            Toast.makeText(this, "Nenhum modelo salvo em WaylandDroid/.brains.", Toast.LENGTH_SHORT).show()
            brainStatusText.text = getSavedBrainsSummary()
            return
        }

        val labels = modelFiles.map { f ->
            val mb = max(1L, f.length() / (1024L * 1024L))
            val prefix = if (f.parentFile?.name == "models") "models/" else ""
            "$prefix${f.name} (${mb} MB)"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Remover Modelos (WaylandDroid/.brains)")
            .setItems(labels) { _, which ->
                val target = modelFiles[which]
                AlertDialog.Builder(this)
                    .setTitle("Confirmar Remoção")
                    .setMessage("Deseja apagar '${target.name}' de WaylandDroid/.brains?")
                    .setPositiveButton("Remover") { _, _ ->
                        BridgeSessionService.stopServerOnly(this)
                        val deleted = target.delete()
                        if (selectedDownloadedFile?.absolutePath == target.absolutePath) {
                            selectedDownloadedFile = null
                        }
                        refreshDownloadedLlms()
                        brainStatusText.text = getSavedBrainsSummary()
                        Toast.makeText(
                            this,
                            if (deleted) "Removido: ${target.name}" else "Não foi possível remover.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
            .setNeutralButton("Apagar Todos") { _, _ ->
                AlertDialog.Builder(this)
                    .setTitle("Apagar Todos os Modelos?")
                    .setMessage("Isso removerá todos os arquivos .llamafile e .gguf de WaylandDroid/.brains.")
                    .setPositiveButton("Apagar Tudo") { _, _ ->
                        BridgeSessionService.stopServerOnly(this)
                        var count = 0
                        modelFiles.forEach { if (it.delete()) count++ }
                        selectedDownloadedFile = null
                        refreshDownloadedLlms()
                        brainStatusText.text = getSavedBrainsSummary()
                        Toast.makeText(this, "$count arquivo(s) removido(s).", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
            .setNegativeButton("Fechar", null)
            .show()
    }

    private fun getAvailableMb(dir: File): Long {
        return try {
            val st = StatFs(dir.absolutePath)
            st.availableBytes / (1024L * 1024L)
        } catch (_: Exception) {
            -1L
        }
    }

    private fun getSavedBrainsSummary(): String {
        val llamafiles = brainsDir.listFiles()?.filter { it.isFile && it.name.endsWith(".llamafile", true) } ?: emptyList()
        val ggufs = modelsDir.listFiles()?.filter { it.isFile && it.name.endsWith(".gguf", true) } ?: emptyList()
        val freeMb = getAvailableMb(brainsDir)
        val freeLabel = if (freeMb >= 0L) " · Espaço livre: ${freeMb} MB" else ""
        if (llamafiles.isEmpty() && ggufs.isEmpty()) {
            return "Nenhum .llamafile/.gguf salvo ainda$freeLabel. Toque em Baixar + Empacotar ou use Comando sh."
        }
        val parts = mutableListOf<String>()
        llamafiles.forEach { parts.add("${it.name} (${it.length() / (1024 * 1024)} MB)") }
        ggufs.forEach { parts.add("models/${it.name} (${it.length() / (1024 * 1024)} MB)") }
        return "Salvos: " + parts.joinToString(", ") + freeLabel
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
        var existingBytes = if (partFile.exists()) partFile.length() else 0L

        fun openDownloadConn(rangeStart: Long): HttpURLConnection {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15000
                readTimeout = 30000
                if (rangeStart > 0L) {
                    setRequestProperty("Range", "bytes=$rangeStart-")
                }
            }
            conn.connect()
            return conn
        }

        var conn = openDownloadConn(existingBytes)
        if (conn.responseCode == 416) {
            conn.disconnect()
            partFile.delete()
            existingBytes = 0L
            conn = openDownloadConn(0L)
        }

        val code = conn.responseCode
        if (code !in 200..299 && code != 206) {
            conn.disconnect()
            throw Exception("$label: HTTP $code")
        }

        val isResuming = (code == 206 && existingBytes > 0L)
        if (!isResuming && existingBytes > 0L) {
            existingBytes = 0L
        }

        val remainingLen = conn.contentLengthLong
        val total = if (remainingLen > 0L) existingBytes + remainingLen else -1L
        val freeBytes = try {
            StatFs(destFile.parentFile?.absolutePath ?: filesDir.absolutePath).availableBytes
        } catch (_: Exception) {
            Long.MAX_VALUE
        }
        if (remainingLen > 0L && freeBytes < remainingLen + (32L * 1024L * 1024L)) {
            conn.disconnect()
            val availMb = freeBytes / (1024L * 1024L)
            val needMb = (remainingLen / (1024L * 1024L)) + 32L
            throw Exception("Espaço insuficiente para $label: livre ${availMb} MB, necessário ${needMb} MB.")
        }

        runOnUiThread {
            if (total > 0) {
                brainProgress.isIndeterminate = false
                brainProgress.progress = ((existingBytes * 100) / total).toInt().coerceIn(0, 100)
            } else {
                brainProgress.isIndeterminate = true
            }
            if (freeBytes < 1024L * 1024L * 1024L) {
                Toast.makeText(
                    this,
                    "Aviso: pouco espaço livre (${freeBytes / (1024L * 1024L)} MB).",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        conn.inputStream.use { input ->
            FileOutputStream(partFile, isResuming).use { output ->
                val buf = ByteArray(16384)
                var downloaded = existingBytes
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
                            val modeTag = if (isResuming) "Retomando" else "Baixando"
                            brainStatusText.text = "$modeTag $label: ${mb} MB (${pct}%)"
                        }
                    }
                }
            }
        }
        conn.disconnect()
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

        val (modelName, outputName) = resolveTargetNames(preferDownloaded = false)
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
                    selectedDownloadedFile = outFile
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
                            selectedDownloadedFile = ggufFile
                        } else {
                            argsFile.delete()
                            selectedDownloadedFile = outputFile
                        }
                    } catch (_: Exception) {
                        outputFile.delete()
                        selectedDownloadedFile = ggufFile
                    }
                }

                // Write convenient sh launcher script inside .brains/
                File(brainsDir, "run_server.sh").writeText(
                    "#!/system/bin/sh\n" + buildTermuxSetupAndRunCommand() + "\n"
                )

                runOnUiThread {
                    brainProgress.visibility = View.GONE
                    refreshDownloadedLlms()
                    brainStatusText.text = getSavedBrainsSummary() + "\nToque em 'Executar Selecionado (:8080)' para subir."
                    Toast.makeText(this, "Modelo pronto em WaylandDroid/.brains/", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    brainProgress.visibility = View.GONE
                    brainStatusText.text = "Erro ao baixar/empacotar: ${e.message}"
                }
            }
        }.start()
    }

    private fun launchBrainInsideUbuntu() {
        val (modelName, outputName) = resolveTargetNames()
        val rawUrl = resolveTargetUrl(modelName, outputName)
        val gpu = UbuntuContainerManager.GpuBackend.values().getOrNull(selectedUbuntuGpuIndex)
            ?: UbuntuContainerManager.GpuBackend.OPENGL_VULKAN_HYBRID
        val intent = Intent(this, UbuntuShellActivity::class.java).apply {
            putExtra("AUTO_RUN_BRAIN", true)
            putExtra("BRAIN_MODEL_FILE", modelName)
            putExtra("BRAIN_LLAMAFILE_NAME", outputName)
            putExtra("BRAIN_MODEL_URL", rawUrl)
            putExtra("GPU_BACKEND", gpu.id)
        }
        startActivity(intent)
    }

    private fun startLlamafileServer() {
        val runMode = runModeOptions.getOrNull(selectedRunModeIndex)?.mode ?: "termux_llama_server"
        if (runMode == "ubuntu_container") {
            launchBrainInsideUbuntu()
            return
        }

        val (modelName, outputName) = resolveTargetNames()
        val packagedLlamafile = File(brainsDir, outputName)
        val engineBin = File(brainsDir, "llamafile")
        val ggufFile = File(modelsDir, modelName)
        val baseUrl = brainWebUrlInput.text.toString().trim().ifEmpty { "http://127.0.0.1:8080" }

        brainProgress.visibility = View.VISIBLE
        brainProgress.isIndeterminate = true
        brainStatusText.text = if (runMode == "llamafile_sh") {
            "Iniciando .llamafile: sh ./$outputName --server ..."
        } else {
            "Verificando/iniciando servidor :8080 ..."
        }

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
                    brainStatusText.text = if (runMode == "llamafile_sh") {
                        "Chamando Termux para executar sh ./$outputName --server..."
                    } else {
                        "Chamando Termux para iniciar llama-server ARM64 (:8080)..."
                    }
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
                BridgeSessionService.startServer(
                    this,
                    cmd,
                    brainsDir.absolutePath,
                    outputName,
                    8080
                )

                var online = false
                for (i in 0 until 24) {
                    Thread.sleep(500)
                    if (checkServerHealth(baseUrl)) {
                        online = true
                        break
                    }
                    if (i >= 4 && !BridgeSessionService.isServerRunning) break
                }

                runOnUiThread {
                    brainProgress.visibility = View.GONE
                    refreshBackgroundBanner()
                    if (online) {
                        brainStatusText.text = "Servidor Qwen ativo em 2º plano ($baseUrl)! Pode minimizar ou abrir o navegador."
                        Toast.makeText(this, "Qwen ativo em 2º plano na porta 8080!", Toast.LENGTH_SHORT).show()
                    } else {
                        val detail = BridgeSessionService.recentLog.trim().ifEmpty {
                            "O Android bloqueou execução direta na pasta privada (SELinux noexec)."
                        }
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
        val (modelName, outputName) = resolveTargetNames()
        val fullCmd = buildTermuxSetupAndRunCommand()
        val quickCmd = resolveQuickShCommand(modelName, outputName)
        runCatching {
            File(brainsDir, "run_server.sh").writeText("#!/system/bin/sh\n" + fullCmd + "\n")
        }
        val message = buildString {
            if (!reason.isNullOrBlank()) {
                appendLine("Motivo: $reason")
                appendLine()
            }
            appendLine("No Termux/Android, o script já destrava allow-external-apps=true, procura seu .gguf em WaylandDroid e baixa automaticamente via curl -fL caso falte.")
            appendLine()
            appendLine("Comando pronto (:8080):")
            appendLine(quickCmd)
        }

        AlertDialog.Builder(this)
            .setTitle("Iniciar Qwen no Termux (:8080)")
            .setMessage(message)
            .setPositiveButton("Copiar + Abrir Termux") { _, _ ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("llamafile_full", fullCmd))
                val launch = packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)
                if (launch != null) startActivity(launch)
                Toast.makeText(this, "Comando completo copiado! Cole no Termux.", Toast.LENGTH_LONG).show()
            }
            .setNeutralButton("Rodar no Ubuntu (:8080)") { _, _ ->
                launchBrainInsideUbuntu()
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
                    importStatusText.text = "Downloading ${targets.size} arquivos para WaylandDroid/$safeApp..."
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
                    appendLine("PASTA: WaylandDroid/$safeApp")
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
                    importStatusText.text = "Pronto! $savedCount arquivos salvos em WaylandDroid/$safeApp"
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
                text = "WaylandDroid/${dir.name} · $fileCount arquivo(s)"
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
            appendLine("Pasta: WaylandDroid/${appDir.name}")
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
        val (_, outputName) = resolveTargetNames()
        termuxStatusText.text = when {
            !installed -> "Termux (com.termux) não encontrado. Instale pelo GitHub oficial (GPLv3) ou rode no Container Ubuntu."
            hasPerm -> "Termux detectado · Permissão RUN_COMMAND ativa! Pronto para iniciar $outputName (:8080)."
            else -> "Termux detectado · Se o Android negar RUN_COMMAND, use 'Copiar + Abrir Termux' ou Container Ubuntu."
        }
    }

    private fun copyCommandToClipboard(label: String, commandText: String) {
        runCatching {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText(label, commandText))
        }
        runCatching {
            File(brainsDir, "run_server.sh").writeText("#!/system/bin/sh\n" + commandText + "\n")
        }
    }

    private fun openAppPermissionsSettings() {
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

    private fun runLlamafileInTermux() {
        val fullCmd = buildTermuxSetupAndRunCommand()
        copyCommandToClipboard("llamafile_full", fullCmd)
        if (!isTermuxInstalled()) {
            AlertDialog.Builder(this)
                .setTitle("Termux não instalado")
                .setMessage(
                    "O Termux (GPLv3 · github.com/termux) não está instalado neste aparelho.\n\n" +
                    "O comando já foi copiado. Você pode baixar o Termux ou rodar direto no Container Ubuntu 24.04!"
                )
                .setPositiveButton("Baixar Termux (GitHub)") { _, _ ->
                    launchBrowserIntent("https://github.com/termux/termux-app/releases")
                }
                .setNeutralButton("Rodar no Ubuntu (:8080)") { _, _ ->
                    launchBrainInsideUbuntu()
                }
                .setNegativeButton("Fechar", null)
                .show()
            return
        }

        if (ContextCompat.checkSelfPermission(this, TERMUX_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            pendingTermuxCmd = fullCmd
            ActivityCompat.requestPermissions(this, arrayOf(TERMUX_PERMISSION), REQUEST_TERMUX_PERMISSION)
            return
        }
        dispatchTermuxRunCommand(fullCmd)
    }

    private fun showTermuxPermissionHelpDialog(scriptText: String) {
        copyCommandToClipboard("llamafile_full", scriptText)
        termuxOutputText.text =
            "Permissão RUN_COMMAND negada pelo Android.\n" +
            "O script completo (já destrava allow-external-apps + baixa o .gguf se faltar) foi COPIADO para sua área de transferência e salvo em WaylandDroid/.brains/run_server.sh."
        AlertDialog.Builder(this)
            .setTitle("Termux: Comando Copiado (Sem Bloqueio)")
            .setMessage(
                "O Android bloqueou a chamada automática RUN_COMMAND, mas não se preocupe:\n\n" +
                "• O comando completo já está COPIADO! Basta tocar em 'Abrir Termux e Colar' (ele já destrava o Termux e baixa/inicia o modelo).\n" +
                "• Ou libere 'Run commands in Termux environment' em Permissões adicionais do app.\n" +
                "• Ou rode direto no Container Ubuntu sem depender do Termux."
            )
            .setPositiveButton("Abrir Termux e Colar") { _, _ ->
                copyCommandToClipboard("llamafile_full", scriptText)
                val launch = packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)
                if (launch != null) startActivity(launch)
                Toast.makeText(this, "Cole no Termux e dê Enter!", Toast.LENGTH_LONG).show()
            }
            .setNeutralButton("Abrir Permissões") { _, _ ->
                openAppPermissionsSettings()
            }
            .setNegativeButton("Rodar no Ubuntu (:8080)") { _, _ ->
                launchBrainInsideUbuntu()
            }
            .show()
    }

    private fun dispatchTermuxRunCommand(scriptText: String) {
        copyCommandToClipboard("llamafile_full", scriptText)
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
            Toast.makeText(this, "Executando servidor Qwen no Termux!", Toast.LENGTH_SHORT).show()
        } catch (se: SecurityException) {
            val unlockCmd = buildTermuxUnlockPrefix()
            termuxOutputText.text =
                "Termux bloqueou apps externos (allow-external-apps).\n" +
                "O script completo (que já destrava isso + baixa/inicia o modelo) foi COPIADO! Cole no Termux uma vez:\n$unlockCmd"
            AlertDialog.Builder(this)
                .setTitle("Liberar + Rodar no Termux")
                .setMessage(
                    "O Termux exige 'allow-external-apps=true' (configuração única) ou permissão do Android.\n\n" +
                    "O comando completo JÁ FOI COPIADO. Toque em 'Abrir Termux e Colar' e aperte Enter lá — ele destrava e já inicia o modelo!"
                )
                .setPositiveButton("Abrir Termux e Colar") { _, _ ->
                    copyCommandToClipboard("llamafile_full", scriptText)
                    val launch = packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)
                    if (launch != null) startActivity(launch)
                    Toast.makeText(this, "Comando completo copiado! Cole no Termux.", Toast.LENGTH_LONG).show()
                }
                .setNeutralButton("Abrir Permissões") { _, _ ->
                    openAppPermissionsSettings()
                }
                .setNegativeButton("Rodar no Ubuntu (:8080)") { _, _ ->
                    launchBrainInsideUbuntu()
                }
                .show()
        } catch (e: Exception) {
            termuxOutputText.text = "Falha ao chamar Termux (${e.message}). Comando copiado para colar manualmente."
            showTermuxShDialog(e.message)
        }
    }

    private fun openOrUnlockTermux() {
        val fullCmd = buildTermuxSetupAndRunCommand()
        copyCommandToClipboard("llamafile_full", fullCmd)
        val launch = packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)
        if (launch != null) {
            startActivity(launch)
            Toast.makeText(this, "Script completo (destravar + rodar :8080) copiado! Cole no Termux.", Toast.LENGTH_LONG).show()
        } else {
            launchBrowserIntent("https://github.com/termux/termux-app/releases")
        }
    }

    private fun killTermuxLlamaServer() {
        BridgeSessionService.stopServerOnly(this)
        val killCmd = "pkill -9 -f 'llama-server|llama_server|llamafile' || pkill -f llama-server || true; echo 'Servidor llama-server encerrado.'"
        runCatching {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("pkill_llama_server", "pkill -f llama-server"))
        }
        val hasPerm = isTermuxInstalled() &&
            ContextCompat.checkSelfPermission(this, TERMUX_PERMISSION) == PackageManager.PERMISSION_GRANTED
        if (hasPerm) {
            try {
                val intent = Intent("com.termux.RUN_COMMAND").apply {
                    setClassName(TERMUX_PACKAGE, TERMUX_SERVICE)
                    putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
                    putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", killCmd))
                    putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
                    putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
                    putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0")
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
                termuxOutputText.text = "Comando pkill -f llama-server enviado ao Termux!"
                Toast.makeText(this, "pkill -f llama-server enviado ao Termux!", Toast.LENGTH_SHORT).show()
                refreshBackgroundBanner()
                return
            } catch (_: Exception) {}
        }
        termuxOutputText.text = "Comando 'pkill -f llama-server' copiado! Cole no Termux se ele estiver rodando."
        AlertDialog.Builder(this)
            .setTitle("Encerrar llama-server (Termux)")
            .setMessage(
                "O servidor local do app foi parado e o comando:\n\npkill -f llama-server\n\nfoi COPIADO. Deseja abrir o Termux para colar?"
            )
            .setPositiveButton("Abrir Termux e Colar") { _, _ ->
                packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)?.let { startActivity(it) }
                Toast.makeText(this, "Cole 'pkill -f llama-server' no Termux.", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Fechar", null)
            .show()
        refreshBackgroundBanner()
    }

    private fun closeUbuntuContainer() {
        UbuntuShellActivity.closeIfOpen()
        BridgeSessionService.stopServerOnly(this)
        runCatching {
            ProcessBuilder("sh", "-c", "pkill -9 -f 'proot|ubuntu-rootfs|ubuntu-arm64' 2>/dev/null || true").start()
        }
        val killUbuntuCmd = "pkill -9 -f 'proot|ubuntu-rootfs|ubuntu-arm64|llama-server|llamafile' || pkill -f proot || true; echo 'Container Ubuntu encerrado.'"
        runCatching {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("close_ubuntu_container", "pkill -9 -f 'proot|ubuntu-rootfs|ubuntu-arm64' || pkill -f proot"))
        }
        val hasPerm = isTermuxInstalled() &&
            ContextCompat.checkSelfPermission(this, TERMUX_PERMISSION) == PackageManager.PERMISSION_GRANTED
        if (hasPerm) {
            try {
                val intent = Intent("com.termux.RUN_COMMAND").apply {
                    setClassName(TERMUX_PACKAGE, TERMUX_SERVICE)
                    putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
                    putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", killUbuntuCmd))
                    putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
                    putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
                    putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0")
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
                termuxOutputText.text = "Container Ubuntu fechado e processos PRoot encerrados no Termux!"
                Toast.makeText(this, "Container Ubuntu fechado e PRoot encerrado!", Toast.LENGTH_SHORT).show()
                refreshBackgroundBanner()
                return
            } catch (_: Exception) {}
        }
        termuxOutputText.text = "Container Ubuntu fechado! Comando pkill (proot) copiado para o Termux."
        Toast.makeText(this, "Container Ubuntu fechado!", Toast.LENGTH_SHORT).show()
        refreshBackgroundBanner()
    }

    private fun installTermuxToolchain() {
        val unlockPrefix = buildTermuxUnlockPrefix()
        val installCmd =
            "$unlockPrefix; pkg update -y && pkg install -y llama-cpp clang make cmake pkg-config git curl unzip && ln -sf \"\$(command -v clang)\" \"\$PREFIX/bin/gcc\" 2>/dev/null || true; ln -sf \"\$(command -v clang++)\" \"\$PREFIX/bin/g++\" 2>/dev/null || true; llama-server --version || true; g++ --version | head -n1 || true"
        copyCommandToClipboard("termux_toolchain", installCmd)
        if (isTermuxInstalled() &&
            ContextCompat.checkSelfPermission(this, TERMUX_PERMISSION) == PackageManager.PERMISSION_GRANTED
        ) {
            dispatchTermuxRunCommand(installCmd)
            return
        }
        packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)?.let { startActivity(it) }
        termuxOutputText.text = "Comando para instalar llama-server + clang/gcc/g++ no Termux foi copiado!"
        Toast.makeText(this, "Comando llama-server + GCC/G++ copiado! Cole no Termux.", Toast.LENGTH_LONG).show()
    }

    private fun installUbuntuToolchainViaTermux() {
        val gpu = UbuntuContainerManager.GpuBackend.values().getOrNull(selectedUbuntuGpuIndex)
            ?: UbuntuContainerManager.GpuBackend.OPENGL_VULKAN_HYBRID
        val script = UbuntuContainerManager(this, waylandRootDir).buildProotInstallToolchainScript(gpu)
        copyCommandToClipboard("ubuntu_toolchain", script)
        if (isTermuxInstalled() &&
            ContextCompat.checkSelfPermission(this, TERMUX_PERMISSION) == PackageManager.PERMISSION_GRANTED
        ) {
            dispatchTermuxRunCommand(script)
            return
        }
        packageManager.getLaunchIntentForPackage("com.termux")?.let { startActivity(it) }
        termuxOutputText.text = "Script para instalar GCC/G++ (C++) + llama-server no Container Ubuntu copiado!"
        Toast.makeText(this, "Script GCC/G++ + llama-server (Ubuntu) copiado! Cole no Termux.", Toast.LENGTH_LONG).show()
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
