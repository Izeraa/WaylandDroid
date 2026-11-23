package org.wayland.xdgbridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder

class BridgeSessionService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channelId = "wayland_bridge_session"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Sessão Wayland + XDG",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
            val notification = Notification.Builder(this, channelId)
                .setContentTitle("WaylandBridgeWorkstation")
                .setContentText("Compositor Wayland + xdg-shell ativo")
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .build()
            startForeground(101, notification)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
