package com.si13.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/** Displays the branded launch artwork before handing off to the task UI. */
class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        window.decorView.postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed
            val nextIntent = Intent(this, MainActivity::class.java).apply {
                intent?.action?.let { action = it }
                intent?.data?.let { data = it }
                intent?.extras?.let { putExtras(it) }
            }
            startActivity(nextIntent)
            finish()
        }, SPLASH_DURATION_MS)
    }

    companion object {
        private const val SPLASH_DURATION_MS = 650L
    }
}
