package com.example.mybubble

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
import android.widget.TextView
import android.widget.Toast

class BubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubble: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

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

        val view = TextView(this)
        view.text = "\u260E"
        view.textSize = 24f
        view.setTextColor(Color.WHITE)
        view.gravity = Gravity.CENTER
        val circle = GradientDrawable()
        circle.shape = GradientDrawable.OVAL
        circle.setColor(Color.parseColor("#2196F3"))
        view.background = circle

        val params = WindowManager.LayoutParams(
            160, 160,
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

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - touchX).toInt()
                    params.y = startY + (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(view, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val moved = Math.abs(event.rawX - touchX) + Math.abs(event.rawY - touchY)
                    if (moved < 15) {
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
                else -> false
            }
        }

        windowManager.addView(view, params)
        bubble = view
    }

    override fun onDestroy() {
        bubble?.let { windowManager.removeView(it) }
        super.onDestroy()
    }
}