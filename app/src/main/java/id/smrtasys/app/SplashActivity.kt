package id.smrtasys.app

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.webkit.CookieManager
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var animatorSet: AnimatorSet? = null
    private var hasNavigated = false

    private val proceedRunnable = Runnable {
        navigateToMain()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        runEntranceMotion()
        preparePortalSessionAsync()

        handler.postDelayed(proceedRunnable, ServerConfig.SPLASH_MIN_DURATION_MS)
        handler.postDelayed(proceedRunnable, ServerConfig.SPLASH_MAX_DURATION_MS)
    }

    private fun runEntranceMotion() {
        val glow = findViewById<View>(R.id.glowAura)
        val emblem = findViewById<View>(R.id.emblemContainer)
        val brandCode = findViewById<View>(R.id.tvBrandCode)
        val portalName = findViewById<View>(R.id.tvPortalName)
        val systemName = findViewById<View>(R.id.tvSystemName)

        emblem.scaleX = 0.88f
        emblem.scaleY = 0.88f
        brandCode.translationY = 14f
        portalName.translationY = 12f
        systemName.translationY = 10f

        val glowAlpha = ObjectAnimator.ofFloat(glow, View.ALPHA, 0f, 1f).apply {
            duration = 550L
            interpolator = DecelerateInterpolator()
        }
        val emblemAlpha = ObjectAnimator.ofFloat(emblem, View.ALPHA, 0f, 1f).apply {
            duration = 450L
            interpolator = DecelerateInterpolator()
        }
        val emblemScaleX = ObjectAnimator.ofFloat(emblem, View.SCALE_X, 0.88f, 1.0f).apply {
            duration = 520L
            interpolator = AccelerateDecelerateInterpolator()
        }
        val emblemScaleY = ObjectAnimator.ofFloat(emblem, View.SCALE_Y, 0.88f, 1.0f).apply {
            duration = 520L
            interpolator = AccelerateDecelerateInterpolator()
        }
        val codeAlpha = ObjectAnimator.ofFloat(brandCode, View.ALPHA, 0f, 1f).apply {
            duration = 380L
            startDelay = 120L
        }
        val codeTransY = ObjectAnimator.ofFloat(brandCode, View.TRANSLATION_Y, 14f, 0f).apply {
            duration = 420L
            startDelay = 120L
            interpolator = DecelerateInterpolator()
        }
        val portalAlpha = ObjectAnimator.ofFloat(portalName, View.ALPHA, 0f, 1f).apply {
            duration = 360L
            startDelay = 200L
        }
        val portalTransY = ObjectAnimator.ofFloat(portalName, View.TRANSLATION_Y, 12f, 0f).apply {
            duration = 400L
            startDelay = 200L
            interpolator = DecelerateInterpolator()
        }
        val sysAlpha = ObjectAnimator.ofFloat(systemName, View.ALPHA, 0f, 1f).apply {
            duration = 360L
            startDelay = 260L
        }
        val sysTransY = ObjectAnimator.ofFloat(systemName, View.TRANSLATION_Y, 10f, 0f).apply {
            duration = 380L
            startDelay = 260L
            interpolator = DecelerateInterpolator()
        }

        animatorSet = AnimatorSet().apply {
            playTogether(
                glowAlpha,
                emblemAlpha,
                emblemScaleX,
                emblemScaleY,
                codeAlpha,
                codeTransY,
                portalAlpha,
                portalTransY,
                sysAlpha,
                sysTransY
            )
            start()
        }
    }

    private fun preparePortalSessionAsync() {
        Thread {
            runCatching {
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.flush()
            }
        }.start()
    }

    private fun navigateToMain() {
        if (hasNavigated || isFinishing || isDestroyed) return
        hasNavigated = true
        handler.removeCallbacks(proceedRunnable)

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        animatorSet?.cancel()
        animatorSet = null
        super.onDestroy()
    }
}
