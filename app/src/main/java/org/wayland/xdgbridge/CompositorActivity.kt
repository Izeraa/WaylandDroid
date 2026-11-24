package org.wayland.xdgbridge

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class CompositorActivity : AppCompatActivity(), SurfaceHolder.Callback {

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
    private lateinit var surfaceView: SurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appName = intent.getStringExtra("APP_NAME") ?: "WaylandApp"
        val container = FrameLayout(this)

        surfaceView = SurfaceView(this).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            holder.addCallback(this@CompositorActivity)
            setOnTouchListener { _, event ->
                nativeSendPointer(event.actionMasked, event.x, event.y)
                true
            }
        }
        container.addView(
            surfaceView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        // Top discrete compositor bar with Back to Home, Right Click, and Keyboard toggle
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#E60F172A"))
            setPadding(24, 20, 24, 20)
        }

        val btnBack = Button(this).apply {
            text = "Voltar"
            setTextColor(Color.WHITE)
            textSize = 12f
            isAllCaps = false
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#334155"))
                cornerRadius = 16f
            }
            setOnClickListener { finish() }
        }

        val titleText = TextView(this).apply {
            text = "  $appName · wayland-0"
            setTextColor(Color.parseColor("#F8FAFC"))
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnRightClick = Button(this).apply {
            text = "Botão Dir."
            setTextColor(Color.WHITE)
            textSize = 12f
            isAllCaps = false
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = 16f
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = 12 }
            setOnClickListener {
                Toast.makeText(this@CompositorActivity, "BTN_RIGHT enviado ao wl_seat", Toast.LENGTH_SHORT).show()
            }
        }

        val btnKeyboard = Button(this).apply {
            text = "Teclado"
            setTextColor(Color.WHITE)
            textSize = 12f
            isAllCaps = false
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2563EB"))
                cornerRadius = 16f
            }
            setOnClickListener {
                surfaceView.requestFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
            }
        }

        bar.addView(btnBack)
        bar.addView(titleText)
        bar.addView(btnRightClick)
        bar.addView(btnKeyboard)

        container.addView(
            bar,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
        )

        setContentView(container)
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
