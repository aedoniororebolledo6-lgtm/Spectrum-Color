package com.nioro.spectrumcolor

import android.app.Activity
import android.app.AlertDialog
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout

class MainActivity : Activity(), HostApi {

    private lateinit var game: GameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Abrir el puerto de la sala (operación local y rapidísima) en el hilo principal no debe cerrar la app.
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().permitAll().build())
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        if (Build.VERSION.SDK_INT >= 28) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }

        game = GameView(this, this)
        setContentView(game)
        hideSystemBars()
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        game.resume()
    }

    override fun onPause() {
        game.pause()
        super.onPause()
    }

    override fun onDestroy() {
        game.destroy()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (!game.onBack()) super.onBackPressed()
    }

    override fun quit() {
        finish()
    }

    override fun prompt(title: String, initial: String, hint: String, onOk: (String) -> Unit) {
        runOnUiThread {
            try {
                val input = EditText(this)
                input.setText(initial)
                input.hint = hint
                input.setSingleLine(true)
                input.setSelection(input.text.length)
                val pad = (20 * resources.displayMetrics.density).toInt()
                val box = FrameLayout(this)
                box.setPadding(pad, pad / 2, pad, 0)
                box.addView(input)
                AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle(title)
                    .setView(box)
                    .setPositiveButton("Aceptar") { _, _ -> onOk(input.text.toString()) }
                    .setNegativeButton("Cancelar", null)
                    .show()
            } catch (e: Exception) {
                // si la ventana ya se cerró, no pasa nada
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
    }
}
