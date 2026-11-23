package org.wayland.xdgbridge

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.appcompat.app.AppCompatActivity

class BridgeActivity : AppCompatActivity(), SurfaceHolder.Callback {

    companion object {
        init {
            System.loadLibrary("wayland_bridge")
        }
    }

    private external fun nativeStartCompositor(surface: Surface, runtimeDir: String, width: Int, height: Int)
    private external fun nativeStopCompositor()
    private external fun nativeSendPointer(action: Int, x: Float, y: Float)
    private external fun nativeSendKey(keyCode: Int, down: Boolean)

    private var compositorStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val serviceIntent = Intent(this, BridgeSessionService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        val surfaceView = SurfaceView(this)
        surfaceView.isFocusable = true
        surfaceView.isFocusableInTouchMode = true
        surfaceView.holder.addCallback(this)

        surfaceView.setOnTouchListener { _, event ->
            nativeSendPointer(event.actionMasked, event.x, event.y)
            true
        }

        setContentView(surfaceView)
        detectExternalPresentationDisplay()
    }

    private fun detectExternalPresentationDisplay() {
        val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val presentationDisplays = dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        if (presentationDisplays.isNotEmpty()) {
            // Workstation mode available via Presentation display
        }
    }

    fun copyToAndroidClipboard(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("wayland", text))
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        nativeSendKey(keyCode, true)
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        nativeSendKey(keyCode, false)
        return super.onKeyUp(keyCode, event)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (!compositorStarted) {
            compositorStarted = true
            val runtimeDir = filesDir.resolve("wayland-runtime").apply { mkdirs() }.absolutePath
            val frame = holder.surfaceFrame
            Thread {
                nativeStartCompositor(holder.surface, runtimeDir, frame.width(), frame.height())
            }.start()
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        nativeStopCompositor()
        compositorStarted = false
    }
}
