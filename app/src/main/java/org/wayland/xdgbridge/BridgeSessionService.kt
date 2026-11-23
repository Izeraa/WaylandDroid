package org.wayland.xdgbridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat
import java.io.File

class BridgeSessionService : Service() {

    companion object {
        const val CHANNEL_ID = "wayland_bridge_session"
        const val NOTIFICATION_ID = 101

        const val ACTION_START_SERVICE = "org.wayland.xdgbridge.ACTION_START_SERVICE"
        const val ACTION_START_LLAMAFILE = "org.wayland.xdgbridge.ACTION_START_LLAMAFILE"
        const val ACTION_STOP_LLAMAFILE = "org.wayland.xdgbridge.ACTION_STOP_LLAMAFILE"
        const val ACTION_TOGGLE_WAKELOCK = "org.wayland.xdgbridge.ACTION_TOGGLE_WAKELOCK"
        const val ACTION_EXIT = "org.wayland.xdgbridge.ACTION_EXIT"

        const val EXTRA_CMD = "extra_cmd"
        const val EXTRA_WORKDIR = "extra_workdir"
        const val EXTRA_LABEL = "extra_label"
        const val EXTRA_PORT = "extra_port"

        @Volatile var isServiceRunning: Boolean = false
        @Volatile var isWakeLockHeld: Boolean = false
        @Volatile var isServerRunning: Boolean = false
        @Volatile var activeServerLabel: String = ""
        @Volatile var serverPort: Int = 8080
        @Volatile var recentLog: String = ""

        @Volatile private var activeProcess: Process? = null

        // Mata o processo e os filhos (sh -> llamafile -> servidor). ProcessHandle só existe no Android 13+,
        // por isso via reflexão e protegido por try/catch.
        private fun killProcessTree(proc: Process?) {
            if (proc == null) return
            if (Build.VERSION.SDK_INT >= 33) {
                try {
                    val handleCls = Class.forName("java.lang.ProcessHandle")
                    val handle = Process::class.java.getMethod("toHandle").invoke(proc)
                    val stream = handleCls.getMethod("descendants").invoke(handle) as java.util.stream.Stream<*>
                    val kill = handleCls.getMethod("destroyForcibly")
                    stream.iterator().forEach { child -> try { kill.invoke(child) } catch (_: Throwable) {} }
                } catch (_: Throwable) {}
            }
            try { proc.destroyForcibly() } catch (_: Throwable) { try { proc.destroy() } catch (_: Throwable) {} }
        }

        fun ensureRunning(context: Context) {
            val intent = Intent(context, BridgeSessionService::class.java).apply {
                action = ACTION_START_SERVICE
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun startServer(
            context: Context,
            cmd: List<String>,
            workdir: String,
            label: String,
            port: Int = 8080
        ) {
            val intent = Intent(context, BridgeSessionService::class.java).apply {
                action = ACTION_START_LLAMAFILE
                putStringArrayListExtra(EXTRA_CMD, ArrayList(cmd))
                putExtra(EXTRA_WORKDIR, workdir)
                putExtra(EXTRA_LABEL, label)
                putExtra(EXTRA_PORT, port)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopServerOnly(context: Context) {
            killProcessTree(activeProcess)
            activeProcess = null
            isServerRunning = false
            activeServerLabel = ""
            if (isServiceRunning) {
                val intent = Intent(context, BridgeSessionService::class.java).apply {
                    action = ACTION_STOP_LLAMAFILE
                }
                context.startService(intent)
            }
        }

        fun toggleWakeLock(context: Context) {
            val intent = Intent(context, BridgeSessionService::class.java).apply {
                action = ACTION_TOGGLE_WAKELOCK
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun exitBackgroundSession(context: Context) {
            val intent = Intent(context, BridgeSessionService::class.java).apply {
                action = ACTION_EXIT
            }
            context.startService(intent)
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        createNotificationChannel()
        acquireLocksIfNeeded()
        promoteToForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isServiceRunning = true
        createNotificationChannel()

        when (intent?.action) {
            ACTION_EXIT -> {
                stopLlamafileProcess()
                releaseLocks()
                isServiceRunning = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_WAKELOCK -> {
                if (isWakeLockHeld) {
                    releaseLocks()
                } else {
                    acquireLocksIfNeeded()
                }
            }
            ACTION_STOP_LLAMAFILE -> {
                stopLlamafileProcess()
            }
            ACTION_START_LLAMAFILE -> {
                acquireLocksIfNeeded()
                val cmd = intent.getStringArrayListExtra(EXTRA_CMD)
                val workdir = intent.getStringExtra(EXTRA_WORKDIR) ?: filesDir.absolutePath
                val label = intent.getStringExtra(EXTRA_LABEL) ?: "Qwen .llamafile"
                val port = intent.getIntExtra(EXTRA_PORT, 8080)
                if (!cmd.isNullOrEmpty()) {
                    spawnLlamafileProcess(cmd, workdir, label, port)
                }
            }
            else -> {
                acquireLocksIfNeeded()
            }
        }

        promoteToForeground()
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Igual ao Termux: fechar a janela nos Recentes não derruba o serviço nem o .llamafile.
        super.onTaskRemoved(rootIntent)
        promoteToForeground()
    }

    override fun onDestroy() {
        stopLlamafileProcess()
        releaseLocks()
        isServiceRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun acquireLocksIfNeeded() {
        try {
            if (wakeLock == null) {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "org.wayland.xdgbridge:LlamafileWakeLock"
                ).apply {
                    setReferenceCounted(false)
                }
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire()
            }
            isWakeLockHeld = wakeLock?.isHeld == true
        } catch (_: Exception) {
            isWakeLockHeld = false
        }

        try {
            if (wifiLock == null) {
                val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                @Suppress("DEPRECATION")
                wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "org.wayland.xdgbridge:WifiLock").apply {
                    setReferenceCounted(false)
                }
            }
            if (wifiLock?.isHeld == false) {
                wifiLock?.acquire()
            }
        } catch (_: Exception) {}
    }

    private fun releaseLocks() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {}
        try {
            if (wifiLock?.isHeld == true) wifiLock?.release()
        } catch (_: Exception) {}
        isWakeLockHeld = false
    }

    private fun spawnLlamafileProcess(cmd: List<String>, workdir: String, label: String, port: Int) {
        stopLlamafileProcess()
        activeServerLabel = label
        serverPort = port
        recentLog = ""
        isServerRunning = true

        Thread {
            try {
                val dir = File(workdir).apply { mkdirs() }
                val proc = ProcessBuilder(cmd)
                    .directory(dir)
                    .redirectErrorStream(true)
                    .start()
                activeProcess = proc

                val logBuf = StringBuilder()
                try {
                    proc.inputStream.bufferedReader().forEachLine { line ->
                        if (logBuf.length > 2000) {
                            logBuf.delete(0, 800)
                        }
                        logBuf.appendLine(line)
                        recentLog = logBuf.toString()
                    }
                } catch (_: Exception) {}

                val code = try { proc.waitFor() } catch (_: Exception) { -1 }
                if (activeProcess == proc) {
                    activeProcess = null
                    isServerRunning = false
                    if (recentLog.isBlank()) {
                        recentLog = "Processo encerrado (exit $code)"
                    }
                    if (isServiceRunning) promoteToForeground()
                }
            } catch (e: Exception) {
                isServerRunning = false
                recentLog = e.message ?: "Erro ao iniciar processo"
                if (isServiceRunning) promoteToForeground()
            }
        }.start()
    }

    private fun stopLlamafileProcess() {
        killProcessTree(activeProcess)
        activeProcess = null
        isServerRunning = false
        activeServerLabel = ""
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val existing = nm.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Sessão 2º Plano (WaylandDroid + Llamafile)",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Mantém o servidor .llamafile e o container ativos em segundo plano."
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    private fun servicePendingIntent(actionStr: String, reqCode: Int): PendingIntent {
        val intent = Intent(this, BridgeSessionService::class.java).apply { action = actionStr }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(this, reqCode, intent, flags)
        } else {
            PendingIntent.getService(this, reqCode, intent, flags)
        }
    }

    private fun promoteToForeground() {
        val openAppIntent = Intent(this, BridgeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPending = PendingIntent.getActivity(
            this,
            10,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val wakeLabel = if (isWakeLockHeld) "WakeLock OFF" else "WakeLock ON"
        val title = if (isServerRunning) {
            "WaylandBridgeWorkstation · Servidor :$serverPort ativo"
        } else {
            "WaylandBridgeWorkstation · Rodando em 2º plano"
        }
        val content = buildString {
            if (isServerRunning) {
                append(activeServerLabel.ifEmpty { ".llamafile" })
                append(" (:$serverPort) · ")
            } else {
                append("Sessão persistente pronta · ")
            }
            append(if (isWakeLockHeld) "WakeLock ON" else "WakeLock OFF")
        }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        builder
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openPending)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    null,
                    wakeLabel,
                    servicePendingIntent(ACTION_TOGGLE_WAKELOCK, 21)
                ).build()
            )

        if (isServerRunning) {
            builder.addAction(
                Notification.Action.Builder(
                    null,
                    "Parar :$serverPort",
                    servicePendingIntent(ACTION_STOP_LLAMAFILE, 22)
                ).build()
            )
        }

        builder.addAction(
            Notification.Action.Builder(
                null,
                "Sair",
                servicePendingIntent(ACTION_EXIT, 23)
            ).build()
        )

        val notification = builder.build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
}
