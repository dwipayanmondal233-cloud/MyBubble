package com.example.mybubble

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("mybubble_prefs", MODE_PRIVATE)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(48, 48, 48, 48)

        val title = TextView(this)
        title.text = "MyBubble"
        title.textSize = 24f

        val phoneInput = EditText(this)
        phoneInput.hint = "Your phone number"
        phoneInput.inputType = InputType.TYPE_CLASS_PHONE
        phoneInput.setText(prefs.getString("phone", ""))

        val saveButton = Button(this)
        saveButton.text = "Save number"
        saveButton.setOnClickListener {
            prefs.edit().putString("phone", phoneInput.text.toString().trim()).apply()
            Toast.makeText(this, "Number saved", Toast.LENGTH_SHORT).show()
        }

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

                val accButton = Button(this)
        accButton.text = "Open Accessibility settings"
        accButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        layout.addView(title)
        layout.addView(accButton)
        layout.addView(phoneInput)
        layout.addView(saveButton)
        layout.addView(startButton)
        layout.addView(stopButton)
        setContentView(layout)
    }
}