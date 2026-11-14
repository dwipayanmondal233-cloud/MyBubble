package com.example.mybubble

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class BubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubble: View? = null
    private val idleAnimators = mutableListOf<ObjectAnimator>()
    private val particles = mutableListOf<View>()

    private val bubbleSize = 240
    private val teddySize = 200
    private val burstEmojis = listOf("❤️", "💖", "✨", "⭐", "🎀")

       override fun onBind(intent: Intent?): IBinder? = null

    private fun soundOn() =
        getSharedPreferences("mybubble_prefs", MODE_PRIVATE).getBoolean("sound", true)

    override fun onCreate() {
        super.onCreate()
        startForeground(1, buildNotification())
        showBubble()
    }

    private fun buildNotification(): Notification {
        val channelId = "bubble_channel"
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(channelId, "Bubble", NotificationManager.IMPORTANCE_LOW)
        )
        return Notification.Builder(this, channelId)
            .setContentTitle("MyBubble is running")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }

    private fun showBubble() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val teddy = TextView(this)
        teddy.text = "🧸"
        teddy.textSize = 38f
        teddy.gravity = Gravity.CENTER
        teddy.includeFontPadding = false
        val circle = GradientDrawable()
        circle.shape = GradientDrawable.OVAL
        circle.setColor(Color.parseColor("#FFD6E8"))
        circle.setStroke(6, Color.WHITE)
        teddy.background = circle

        val root = FrameLayout(this)
        root.addView(teddy, FrameLayout.LayoutParams(teddySize, teddySize, Gravity.CENTER))

        startIdleAnimation(teddy)

        val params = WindowManager.LayoutParams(
            bubbleSize, bubbleSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 100
        params.y = 300

        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f

        root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    SoundFx.press(soundOn())
                    teddy.animate().scaleX(0.85f).scaleY(0.85f).setDuration(100).start()
                     
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - touchX).toInt()
                    params.y = startY + (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(root, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    teddy.animate().scaleX(1f).scaleY(1f).setDuration(450)
                        .setInterpolator(OvershootInterpolator(3f)).start()
                    val moved = Math.abs(event.rawX - touchX) + Math.abs(event.rawY - touchY)
                    if (moved < 15) {
                     SoundFx.pop(soundOn())
                        burst(params.x + bubbleSize / 2, params.y + bubbleSize / 2)
                        val phone = getSharedPreferences("mybubble_prefs", MODE_PRIVATE).getString("phone", "") ?: ""
                        val service = PhoneAccessibilityService.instance
                        if (phone.isEmpty()) {
                            Toast.makeText(this, "No number saved yet", Toast.LENGTH_SHORT).show()
                        } else if (service == null) {
                            Toast.makeText(this, "Turn on MyBubble in Accessibility settings", Toast.LENGTH_LONG).show()
                        } else if (!service.insertText(phone)) {
                            Toast.makeText(this, "Tap a text box first", Toast.LENGTH_SHORT).show()
                        }
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    teddy.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
                    true
                }
                else -> false
            }
        }

        windowManager.addView(root, params)
        bubble = root
    }

    private fun startIdleAnimation(view: View) {
        val bob = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, -10f, 10f)
        bob.duration = 1200
        bob.repeatCount = ValueAnimator.INFINITE
        bob.repeatMode = ValueAnimator.REVERSE
        bob.interpolator = AccelerateDecelerateInterpolator()
        bob.start()

        val wiggle = ObjectAnimator.ofFloat(view, View.ROTATION, -6f, 6f)
        wiggle.duration = 1600
        wiggle.repeatCount = ValueAnimator.INFINITE
        wiggle.repeatMode = ValueAnimator.REVERSE
        wiggle.interpolator = AccelerateDecelerateInterpolator()
        wiggle.start()

        idleAnimators.add(bob)
        idleAnimators.add(wiggle)
    }

    private fun burst(centerX: Int, centerY: Int) {
        val size = 100
        for (i in 0 until 8) {
            val tv = TextView(this)
            tv.text = burstEmojis.random()
            tv.textSize = 22f
            tv.gravity = Gravity.CENTER

            val p = WindowManager.LayoutParams(
                size, size,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            )
            p.gravity = Gravity.TOP or Gravity.START
            val startX = centerX - size / 2
            val startY = centerY - size / 2
            p.x = startX
            p.y = startY
            windowManager.addView(tv, p)
            particles.add(tv)

            val angle = Math.toRadians((i * 45 + Random.nextInt(-15, 15)).toDouble())
            val distance = Random.nextInt(150, 280)

            val anim = ValueAnimator.ofFloat(0f, 1f)
            anim.duration = Random.nextLong(600, 1000)
            anim.interpolator = DecelerateInterpolator()
            anim.addUpdateListener {
                val t = it.animatedValue as Float
                p.x = startX + (cos(angle) * distance * t).toInt()
                p.y = startY + (sin(angle) * distance * t).toInt() - (t * 60).toInt()
                tv.alpha = 1f - t
                try {
                    windowManager.updateViewLayout(tv, p)
                } catch (e: Exception) {
                }
            }
            anim.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    try {
                        windowManager.removeView(tv)
                    } catch (e: Exception) {
                    }
                    particles.remove(tv)
                }
            })
            anim.start()
        }
    }

    override fun onDestroy() {
        idleAnimators.forEach { it.cancel() }
        idleAnimators.clear()
        for (view in particles.toList()) {
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
            }
        }
        particles.clear()
        bubble?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
            }
        }
        super.onDestroy()
    }
}