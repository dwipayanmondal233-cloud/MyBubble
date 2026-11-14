package com.example.mybubble

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class BubbleService : Service() {

    companion object {
        var instance: BubbleService? = null
        val SKINS = listOf("🧸", "🐰", "🐱", "🐼", "🦊", "🐶", "🐻", "🦄", "🐸", "🐥")
        val COLORS = listOf("#FFD6E8", "#D6EEFF", "#DFF7DC", "#FFF2C2", "#EBDDFF", "#FFFFFF")
    }

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: SharedPreferences
    private lateinit var params: WindowManager.LayoutParams
    private val handler = Handler(Looper.getMainLooper())

    private var root: FrameLayout? = null
    private var teddy: FrameLayout? = null
    private var emoji: TextView? = null
    private var zzz: TextView? = null
    private var attached = false
    private var wantShown = false

    private val idleAnimators = mutableListOf<ObjectAnimator>()
    private val particles = mutableListOf<View>()
    private var jellyAnim: ValueAnimator? = null
    private var dockAnim: ValueAnimator? = null
    private var zzzAnim: ValueAnimator? = null

    private var playMode = false
    private var sleeping = false
    private var flying = false
    private var vx = 0f
    private var vy = 0f
    private var fx = 0f
    private var fy = 0f
    private var lastFrameNanos = 0L
    private var lastBoingMs = 0L

    private var scale = 1f
    private var bubbleSize = 240
    private var teddySize = 200
    private val burstEmojis = listOf("❤️", "💖", "✨", "⭐", "🎀")

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "mode" -> {
                applyMode()
                updateNotification()
            }
            "skin", "color", "size" -> rebuildBubble()
        }
    }

    private val peekRunnable = Runnable {
        if (!playMode && attached) {
            val (w, _) = screenSize()
            val hide = (bubbleSize * 0.45f).toInt()
            val onLeft = params.x + bubbleSize / 2 < w / 2
            val peekX = if (onLeft) -hide else w - bubbleSize + hide
            animatePosition(peekX, params.y, 350, DecelerateInterpolator())
            root?.animate()?.alpha(0.6f)?.setDuration(350)?.start()
        }
    }

    private val checkRunnable = Runnable {
        if (prefs.getInt("mode", 0) == 1) {
            val state = PhoneAccessibilityService.instance?.editableFocusState()
            if (state == true) {
                handler.removeCallbacks(hideRunnable)
                setShown(true)
            } else if (state == false) {
                handler.removeCallbacks(hideRunnable)
                handler.postDelayed(hideRunnable, 1200)
            }
        }
    }

    private val hideRunnable = Runnable {
        if (prefs.getInt("mode", 0) == 1) setShown(false)
    }

    private val sleepRunnable = Runnable {
        if (attached && !playMode && !flying) fallAsleep() else scheduleSleep()
    }

    private val blinkRunnable = Runnable {
        if (attached && !sleeping && !flying) {
            emoji?.let { e ->
                e.animate().scaleY(0.8f).scaleX(1.06f).setDuration(90).withEndAction {
                    e.animate().scaleY(1f).scaleX(1f).setDuration(120).start()
                }.start()
            }
        }
        scheduleBlink()
    }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!flying || !attached) return
            val dt = if (lastFrameNanos == 0L) 0.016f
            else min(0.033f, (frameTimeNanos - lastFrameNanos) / 1_000_000_000f)
            lastFrameNanos = frameTimeNanos
            stepPhysics(dt)
            if (flying) Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun soundOn() = prefs.getBoolean("sound", true)

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = getSharedPreferences("mybubble_prefs", MODE_PRIVATE)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForeground(1, buildNotification())
        buildBubble()
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        applyMode()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "TOGGLE_HIDE") {
            val mode = prefs.getInt("mode", 0)
            if (mode == 2) {
                prefs.edit().putInt("mode", prefs.getInt("mode_before", 0)).apply()
            } else {
                prefs.edit().putInt("mode_before", mode).putInt("mode", 2).apply()
            }
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(): Notification {
        val channelId = "bubble_channel"
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(channelId, "Bubble", NotificationManager.IMPORTANCE_LOW)
        )
        val hidden = prefs.getInt("mode", 0) == 2
        val toggleIntent = Intent(this, BubbleService::class.java).setAction("TOGGLE_HIDE")
        val pi = PendingIntent.getService(
            this, 0, toggleIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val action = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_menu_view),
            if (hidden) "Show teddy 🧸" else "Hide teddy 🙈",
            pi
        ).build()
        return Notification.Builder(this, channelId)
            .setContentTitle(if (hidden) "Teddy is hidden 🙈" else "Teddy is here 🧸")
            .setContentText("Tap the button to hide or show")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .addAction(action)
            .build()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(1, buildNotification())
    }

    @Suppress("DEPRECATION")
    private fun screenSize(): Pair<Int, Int> {
        val dm = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(dm)
        return Pair(dm.widthPixels, dm.heightPixels)
    }

    private fun buildBubble(startX: Int = 8, startY: Int = 400) {
        val sc = prefs.getInt("size", 100) / 100f
        scale = sc
        bubbleSize = (240 * sc).toInt()
        teddySize = (200 * sc).toInt()

        val colorIdx = prefs.getInt("color", 0).coerceIn(0, COLORS.size - 1)
        val skin = prefs.getString("skin", "🧸") ?: "🧸"

        val t = FrameLayout(this)
        val circle = GradientDrawable()
        circle.shape = GradientDrawable.OVAL
        circle.setColor(Color.parseColor(COLORS[colorIdx]))
        circle.setStroke(6, Color.WHITE)
        t.background = circle

        val e = TextView(this)
        e.text = skin
        e.textSize = 38f * sc
        e.gravity = Gravity.CENTER
        e.includeFontPadding = false
        t.addView(
            e,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        teddy = t
        emoji = e

        val r = FrameLayout(this)
        r.addView(t, FrameLayout.LayoutParams(teddySize, teddySize, Gravity.CENTER))

        val z = TextView(this)
        z.text = "💤"
        z.textSize = 16f * sc
        z.visibility = View.GONE
        val zlp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END
        )
        zlp.topMargin = (34 * sc).toInt()
        zlp.rightMargin = (8 * sc).toInt()
        r.addView(z, zlp)
        zzz = z
        root = r

        startIdleAnimation(t)

        params = WindowManager.LayoutParams(
            bubbleSize, bubbleSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = startX
        params.y = startY

        setupTouch(r, t)
        scheduleBlink()
        scheduleSleep()
    }

    private fun rebuildBubble() {
        val x = params.x
        val y = params.y
        stopMotion()
        jellyAnim?.cancel()
        zzzAnim?.cancel()
        sleeping = false
        playMode = false
        handler.removeCallbacks(sleepRunnable)
        handler.removeCallbacks(blinkRunnable)
        idleAnimators.forEach { it.cancel() }
        idleAnimators.clear()
        detach()
        buildBubble(x, y)
        applyMode()
    }

    // ---------- Moods ----------

    private fun scheduleBlink() {
        handler.removeCallbacks(blinkRunnable)
        handler.postDelayed(blinkRunnable, Random.nextLong(2500, 6000))
    }

    private fun scheduleSleep() {
        handler.removeCallbacks(sleepRunnable)
        handler.postDelayed(sleepRunnable, 20000)
    }

    private fun fallAsleep() {
        if (sleeping) return
        sleeping = true
        idleAnimators.forEach { it.pause() }
        emoji?.animate()?.alpha(0.75f)?.setDuration(400)?.start()
        val z = zzz ?: return
        z.visibility = View.VISIBLE
        zzzAnim?.cancel()
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = 2000
        a.repeatCount = ValueAnimator.INFINITE
        a.addUpdateListener {
            val p = it.animatedValue as Float
            z.translationY = -28f * scale * p
            z.alpha = if (p < 0.7f) 1f else (1f - p) / 0.3f
            z.scaleX = 0.7f + 0.5f * p
            z.scaleY = 0.7f + 0.5f * p
        }
        zzzAnim = a
        a.start()
    }

    private fun wakeUp() {
        scheduleSleep()
        if (!sleeping) return
        sleeping = false
        zzzAnim?.cancel()
        zzz?.visibility = View.GONE
        idleAnimators.forEach { it.resume() }
        emoji?.animate()?.alpha(1f)?.setDuration(200)?.start()
        burst(params.x + bubbleSize / 2, params.y + bubbleSize / 2, 4)
    }

    // ---------- Show / hide ----------

    private fun applyMode() {
        handler.removeCallbacks(hideRunnable)
        handler.removeCallbacks(checkRunnable)
        when (prefs.getInt("mode", 0)) {
            0 -> setShown(true)
            1 -> handler.postDelayed(checkRunnable, 100)
            else -> setShown(false)
        }
    }

    fun onInputFocusMaybeChanged() {
        if (prefs.getInt("mode", 0) == 1) {
            handler.removeCallbacks(checkRunnable)
            handler.postDelayed(checkRunnable, 250)
        }
    }

    private fun setShown(show: Boolean) {
        val r = root ?: return
        wantShown = show
        if (show) {
            if (!attached) {
                r.alpha = 0f
                try {
                    windowManager.addView(r, params)
                } catch (e: Exception) {
                    Toast.makeText(this, "Allow 'Display over other apps' first", Toast.LENGTH_LONG).show()
                    return
                }
                attached = true
                r.animate().alpha(1f).setDuration(250).start()
                dockToEdge()
            } else {
                r.animate().alpha(1f).setDuration(200).start()
            }
        } else if (attached) {
            stopMotion()
            playMode = false
            r.animate().alpha(0f).setDuration(200).withEndAction {
                if (!wantShown) detach()
            }.start()
        }
    }

    private fun detach() {
        val r = root ?: return
        if (attached) {
            try {
                windowManager.removeView(r)
            } catch (e: Exception) {
            }
            attached = false
        }
    }

    private fun updateLayoutSafe() {
        val r = root ?: return
        if (attached) {
            try {
                windowManager.updateViewLayout(r, params)
            } catch (e: Exception) {
            }
        }
    }

    // ---------- Touch ----------

    private fun setupTouch(r: FrameLayout, t: View) {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var lastX = 0f
        var lastY = 0f
        var lastTime = 0L
        var ivx = 0f
        var ivy = 0f
        var maxX = 0
        var maxY = 0
        var longPressed = false

        val longPress = Runnable {
            longPressed = true
            togglePlayMode()
        }

        r.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    stopMotion()
                    jellyAnim?.cancel()
                    longPressed = false
                    val size = screenSize()
                    maxX = max(0, size.first - bubbleSize)
                    maxY = max(0, size.second - bubbleSize)
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    lastX = touchX
                    lastY = touchY
                    lastTime = event.eventTime
                    ivx = 0f
                    ivy = 0f
                    r.animate().cancel()
                    r.alpha = 1f
                    wakeUp()
                    SoundFx.press(soundOn())
                    t.animate().scaleX(0.85f).scaleY(0.85f).setDuration(100).start()
                    handler.postDelayed(longPress, 700)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchX
                    val dy = event.rawY - touchY
                    if (abs(dx) + abs(dy) > 15f) handler.removeCallbacks(longPress)
                    params.x = (startX + dx).toInt().coerceIn(0, maxX)
                    params.y = (startY + dy).toInt().coerceIn(0, maxY)
                    updateLayoutSafe()

                    val dt = (event.eventTime - lastTime) / 1000f
                    if (dt > 0.001f) {
                        ivx = ivx * 0.6f + 0.4f * (event.rawX - lastX) / dt
                        ivy = ivy * 0.6f + 0.4f * (event.rawY - lastY) / dt
                        lastX = event.rawX
                        lastY = event.rawY
                        lastTime = event.eventTime
                    }
                    t.animate().cancel()
                    val s = min(0.3f, sqrt(ivx * ivx + ivy * ivy) / 9000f)
                    if (abs(ivx) > abs(ivy)) {
                        t.scaleX = 1f + s
                        t.scaleY = 1f - s * 0.6f
                    } else {
                        t.scaleX = 1f - s * 0.6f
                        t.scaleY = 1f + s
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPress)
                    t.animate().cancel()
                    t.animate().scaleX(1f).scaleY(1f).setDuration(450)
                        .setInterpolator(OvershootInterpolator(3f)).start()
                    val moved = abs(event.rawX - touchX) + abs(event.rawY - touchY)
                    val isUp = event.action == MotionEvent.ACTION_UP
                    if (longPressed) {
                        if (!playMode) dockToEdge()
                    } else if (isUp && moved < 15f) {
                        if (playMode) {
                            poke()
                        } else {
                            onTeddyTapped()
                            dockToEdge()
                        }
                    } else {
                        if (playMode) {
                            if (event.eventTime - lastTime > 80) {
                                ivx = 0f
                                ivy = 0f
                            }
                            fling(ivx, ivy)
                        } else {
                            dockToEdge()
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun onTeddyTapped() {
        SoundFx.pop(soundOn())
        burst(params.x + bubbleSize / 2, params.y + bubbleSize / 2, 8)
        val phone = prefs.getString("phone", "") ?: ""
        val service = PhoneAccessibilityService.instance
        if (phone.isEmpty()) {
            Toast.makeText(this, "No number saved yet", Toast.LENGTH_SHORT).show()
        } else if (service == null) {
            Toast.makeText(this, "Turn on MyBubble in Accessibility settings", Toast.LENGTH_LONG).show()
        } else if (!service.insertText(phone)) {
            Toast.makeText(this, "Tap a text box first", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- Docking ----------

    private fun stopMotion() {
        handler.removeCallbacks(peekRunnable)
        dockAnim?.cancel()
        flying = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    private fun schedulePeek() {
        handler.removeCallbacks(peekRunnable)
        handler.postDelayed(peekRunnable, 2500)
    }

    private fun animatePosition(
        toX: Int,
        toY: Int,
        duration: Long,
        interp: TimeInterpolator,
        onEnd: () -> Unit = {}
    ) {
        dockAnim?.cancel()
        val fromX = params.x
        val fromY = params.y
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = duration
        a.interpolator = interp
        a.addUpdateListener {
            val f = it.animatedValue as Float
            params.x = (fromX + (toX - fromX) * f).toInt()
            params.y = (fromY + (toY - fromY) * f).toInt()
            updateLayoutSafe()
        }
        a.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationCancel(animation: Animator) {
                cancelled = true
            }

            override fun onAnimationEnd(animation: Animator) {
                if (!cancelled) onEnd()
            }
        })
        dockAnim = a
        a.start()
    }

    private fun dockToEdge() {
        if (!attached) return
        val (w, h) = screenSize()
        val toLeft = params.x + bubbleSize / 2 < w / 2
        val targetX = if (toLeft) 0 else w - bubbleSize
        val targetY = params.y.coerceIn(0, max(0, h - bubbleSize))
        root?.animate()?.alpha(1f)?.setDuration(150)?.start()
        animatePosition(targetX, targetY, 450, OvershootInterpolator(2.5f)) {
            teddy?.let { t ->
                t.pivotX = if (toLeft) 0f else teddySize.toFloat()
                t.pivotY = teddySize / 2f
                jelly(t, true, 0.22f)
            }
            schedulePeek()
        }
    }

    // ---------- Jelly play ----------

    private fun togglePlayMode() {
        playMode = !playMode
        SoundFx.pop(soundOn())
        teddy?.let { jelly(it, true, 0.3f) }
        Toast.makeText(
            this,
            if (playMode) "Play mode ON 🎮 Fling me! Hold to stop" else "Play mode off 😴",
            Toast.LENGTH_SHORT
        ).show()
        if (!playMode) stopMotion()
    }

    private fun poke() {
        SoundFx.pop(soundOn())
        burst(params.x + bubbleSize / 2, params.y + bubbleSize / 2, 6)
        val angle = Random.nextDouble(0.0, 2 * PI)
        fling((cos(angle) * 1800).toFloat(), (sin(angle) * 1800).toFloat())
        teddy?.let { jelly(it, Random.nextBoolean(), 0.3f) }
    }

    private fun fling(ivx: Float, ivy: Float) {
        var sx = ivx
        var sy = ivy
        val speed = sqrt(sx * sx + sy * sy)
        if (speed < 200f) return
        if (speed > 5000f) {
            sx *= 5000f / speed
            sy *= 5000f / speed
        }
        vx = sx
        vy = sy
        fx = params.x.toFloat()
        fy = params.y.toFloat()
        flying = true
        lastFrameNanos = 0L
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun stepPhysics(dt: Float) {
        val (w, h) = screenSize()
        val maxX = (w - bubbleSize).toFloat()
        val maxY = (h - bubbleSize).toFloat()
        var x = fx + vx * dt
        var y = fy + vy * dt
        val friction = 1f - 0.45f * dt
        vx *= friction
        vy *= friction
        val rest = 0.88f

        if (x < 0f) {
            x = 0f
            hitWall(abs(vx), 0)
            vx = abs(vx) * rest
        } else if (x > maxX) {
            x = maxX
            hitWall(abs(vx), 1)
            vx = -abs(vx) * rest
        }
        if (y < 0f) {
            y = 0f
            hitWall(abs(vy), 2)
            vy = abs(vy) * rest
        } else if (y > maxY) {
            y = maxY
            hitWall(abs(vy), 3)
            vy = -abs(vy) * rest
        }

        fx = x
        fy = y
        params.x = x.toInt()
        params.y = y.toInt()
        updateLayoutSafe()

        if (sqrt(vx * vx + vy * vy) < 60f) flying = false
    }

    // side: 0 left, 1 right, 2 top, 3 bottom
    private fun hitWall(speed: Float, side: Int) {
        if (speed < 250f) return
        val t = teddy ?: return
        val amount = min(0.35f, speed / 5000f)
        when (side) {
            0 -> { t.pivotX = 0f; t.pivotY = teddySize / 2f }
            1 -> { t.pivotX = teddySize.toFloat(); t.pivotY = teddySize / 2f }
            2 -> { t.pivotX = teddySize / 2f; t.pivotY = 0f }
            else -> { t.pivotX = teddySize / 2f; t.pivotY = teddySize.toFloat() }
        }
        jelly(t, side < 2, amount)
        val now = System.currentTimeMillis()
        if (now - lastBoingMs > 150) {
            lastBoingMs = now
            SoundFx.boing(soundOn())
            if (speed > 1200f) {
                burst(params.x + bubbleSize / 2, params.y + bubbleSize / 2, 4)
            }
        }
    }

    private fun jelly(t: View, horizontal: Boolean, amount: Float) {
        jellyAnim?.cancel()
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = 800
        a.addUpdateListener {
            val p = it.animatedValue as Float
            val s = amount * exp(-5f * p) * cos(16f * p)
            val main = 1f - s
            val other = 1f + s * 0.7f
            if (horizontal) {
                t.scaleX = main
                t.scaleY = other
            } else {
                t.scaleY = main
                t.scaleX = other
            }
        }
        a.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                t.scaleX = 1f
                t.scaleY = 1f
                t.pivotX = teddySize / 2f
                t.pivotY = teddySize / 2f
            }
        })
        jellyAnim = a
        a.start()
    }

    // ---------- Idle + burst ----------

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

    private fun burst(centerX: Int, centerY: Int, count: Int) {
        val size = 100
        for (i in 0 until count) {
            val tv = TextView(this)
            tv.text = burstEmojis.random()
            tv.textSize = 22f
            tv.gravity = Gravity.CENTER

            val p = WindowManager.LayoutParams(
                size, size,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            p.gravity = Gravity.TOP or Gravity.START
            val startX = centerX - size / 2
            val startY = centerY - size / 2
            p.x = startX
            p.y = startY
            try {
                windowManager.addView(tv, p)
            } catch (e: Exception) {
                return
            }
            particles.add(tv)

            val angle = Math.toRadians(i * 360.0 / count + Random.nextInt(-15, 15))
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
        instance = null
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        handler.removeCallbacksAndMessages(null)
        stopMotion()
        jellyAnim?.cancel()
        zzzAnim?.cancel()
        idleAnimators.forEach { it.cancel() }
        idleAnimators.clear()
        for (view in particles.toList()) {
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
            }
        }
        particles.clear()
        detach()
        super.onDestroy()
    }
}