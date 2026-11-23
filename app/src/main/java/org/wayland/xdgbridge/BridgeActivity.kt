package org.wayland.xdgbridge

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class BridgeActivity : AppCompatActivity() {

    private data class QwenPreset(
        val label: String,
        val fileName: String,
        val url: String
    )

    private val qwenPresets = listOf(
        QwenPreset(
            "Qwen-Coder-0.5B (Básico · ~400 MB)",
            "Qwen-Coder-0.5B.gguf",
            "https://huggingface.co/Qwen/Qwen2.5-Coder-0.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-0.5b-instruct-q4_k_m.gguf"
        ),
        QwenPreset(
            "Qwen2.5-Coder-1.5B (Intermediário · ~1.0 GB)",
            "Qwen2.5-Coder-1.5B.gguf",
            "https://huggingface.co/Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf"
        ),
        QwenPreset(
            "Qwen2.5-Coder-3B (Forte 8-16GB RAM · ~2.1 GB)",
            "Qwen2.5-Coder-3B.gguf",
            "https://huggingface.co/Qwen/Qwen2.5-Coder-3B-Instruct-GGUF/resolve/main/qwen2.5-coder-3b-instruct-q4_k_m.gguf"
        ),
        QwenPreset(
            "URL Personalizada (.gguf / .llamafile)",
            "custom-model.gguf",
            ""
        )
    )

    private lateinit var waylandRootDir: File
    private lateinit var brainsDir: File

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

    private var selectedPresetIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        waylandRootDir = File(filesDir, "WaylandAndroid").apply { mkdirs() }
        brainsDir = File(waylandRootDir, ".brains").apply { mkdirs() }
        seedStarterAppsIfNeeded()

        val serviceIntent = Intent(this, BridgeSessionService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        setContentView(buildHomeUi())
        refreshAppsList()
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

        // 1. MENU PARA ESCOLHER O CÉREBRO QWEN + ABRIR NO NAVEGADOR
        val brainCard = makeCard()
        brainCard.addView(
            makeSectionTitle(
                "1. Escolher Cérebro Qwen (.gguf / .llamafile)",
                "Selecione o modelo ou cole a URL e abra o Qwen no navegador."
            )
        )

        val spinner = Spinner(this).apply {
            background = cardDrawable("#0F172A", "#334155")
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            qwenPresets.map { it.label }
        )
        spinner.adapter = adapter
        brainCard.addView(spinner)

        brainUrlInput = EditText(this).apply {
            styleInput(this, "Cole a URL do .gguf ou .llamafile")
            setText(qwenPresets[0].url)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
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

        val brainActionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val btnDownloadBrain = Button(this).apply {
            text = "Baixar Cérebro"
            setTextColor(Color.WHITE)
            textSize = 12f
            isAllCaps = false
            background = buttonDrawable("#2563EB")
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                marginEnd = dp(8)
            }
            setOnClickListener { startDownloadBrain() }
        }
        brainActionsRow.addView(btnDownloadBrain)

        brainWebUrlInput = EditText(this).apply {
            styleInput(this, "http://127.0.0.1:8080")
            setText("http://127.0.0.1:8080")
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        }

        val btnOpenQwenBrowser = Button(this).apply {
            text = "Abrir Qwen no Navegador"
            setTextColor(Color.WHITE)
            textSize = 12f
            isAllCaps = false
            background = buttonDrawable("#0F766E")
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f)
            setOnClickListener { openQwenInBrowser() }
        }
        brainActionsRow.addView(btnOpenQwenBrowser)
        brainCard.addView(brainActionsRow)
        brainCard.addView(brainWebUrlInput)

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

        scrollView.addView(root)
        return scrollView
    }

    private fun getSavedBrainsSummary(): String {
        val files = brainsDir.listFiles()?.filter { it.isFile } ?: emptyList()
        if (files.isEmpty()) {
            return "Nenhum .gguf/.llamafile baixado localmente ainda."
        }
        return "Cérebros salvos: " + files.joinToString(", ") {
            val mb = it.length() / (1024 * 1024)
            "${it.name} (${mb} MB)"
        }
    }

    private fun openQwenInBrowser() {
        val raw = brainWebUrlInput.text.toString().trim()
        val parsed = try { URL(raw) } catch (_: Exception) { null }
        if (parsed == null || (parsed.protocol != "http" && parsed.protocol != "https")) {
            Toast.makeText(this, "Informe uma URL http:// ou https:// válida.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(parsed.toString())))
        } catch (_: Exception) {
            Toast.makeText(this, "Nenhum navegador encontrado no aparelho.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startDownloadBrain() {
        val rawUrl = brainUrlInput.text.toString().trim()
        val parsed = try { URL(rawUrl) } catch (_: Exception) { null }
        if (parsed == null || parsed.protocol != "https") {
            brainStatusText.text = "Informe uma URL HTTPS válida (.gguf ou .llamafile)."
            return
        }

        val preset = qwenPresets.getOrNull(selectedPresetIndex)
        val filenameFromUrl = File(parsed.path).name.takeIf {
            it.endsWith(".gguf", true) || it.endsWith(".llamafile", true)
        } ?: (preset?.fileName ?: "qwen-brain.gguf")
        val safeFile = filenameFromUrl.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val destFile = File(brainsDir, safeFile)

        brainProgress.visibility = View.VISIBLE
        brainProgress.isIndeterminate = true
        brainStatusText.text = "Baixando $safeFile para WaylandAndroid/.brains/..."

        Thread {
            try {
                val conn = (parsed.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 15000
                    readTimeout = 30000
                }
                conn.connect()
                if (conn.responseCode !in 200..299) {
                    throw Exception("HTTP ${conn.responseCode}")
                }
                val total = conn.contentLengthLong
                runOnUiThread {
                    if (total > 0) {
                        brainProgress.isIndeterminate = false
                        brainProgress.progress = 0
                    }
                }

                conn.inputStream.use { input ->
                    FileOutputStream(destFile).use { output ->
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
                                    brainStatusText.text = "Downloading $safeFile: ${mb} MB (${pct}%)"
                                }
                            }
                        }
                    }
                }
                runOnUiThread {
                    brainProgress.visibility = View.GONE
                    brainStatusText.text = getSavedBrainsSummary()
                    Toast.makeText(this, "Cérebro salvo em WaylandAndroid/.brains/$safeFile", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    brainProgress.visibility = View.GONE
                    brainStatusText.text = "Erro ao baixar cérebro: ${e.message}"
                }
            }
        }.start()
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
        val chosenBrain = qwenPresets.getOrNull(selectedPresetIndex)?.fileName ?: "Qwen-Coder-0.5B"

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
}
