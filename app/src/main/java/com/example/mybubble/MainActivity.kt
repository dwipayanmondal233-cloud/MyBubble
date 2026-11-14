package com.example.mybubble

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(48, 48, 48, 48)

        val title = TextView(this)
        title.text = "MyBubble"
        title.textSize = 24f

        val startButton = Button(this)
        startButton.text = "Start bubble"
        startButton.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Allow 'Display over other apps', then come back", Toast.LENGTH_LONG).show()
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                startForegroundService(Intent(this, BubbleService::class.java))
            }
        }

        val stopButton = Button(this)
        stopButton.text = "Stop bubble"
        stopButton.setOnClickListener {
            stopService(Intent(this, BubbleService::class.java))
        }

        layout.addView(title)
        layout.addView(startButton)
        layout.addView(stopButton)
        setContentView(layout)
    }
}