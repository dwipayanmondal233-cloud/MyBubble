package com.example.mybubble

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.SeekBar
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private var bobAnimator: ObjectAnimator? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun soundOn() =
        getSharedPreferences("mybubble_prefs", MODE_PRIVATE).getBoolean("sound", true)

    private fun cuteButton(label: String, color: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.isAllCaps = false
        b.textSize = 17f
        b.typeface = Typeface.DEFAULT_BOLD
        b.setTextColor(Color.WHITE)
        val bg = GradientDrawable()
        bg.cornerRadius = dp(28).toFloat()
        bg.setColor(Color.parseColor(color))
        b.background = bg
        b.stateListAnimator = null
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(56)
        )
        lp.topMargin = dp(12)
        b.layoutParams = lp
        b.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN ->
                    v.animate().scaleX(0.93f).scaleY(0.93f).setDuration(100).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.animate().scaleX(1f).scaleY(1f).setDuration(350)
                        .setInterpolator(OvershootInterpolator(3f)).start()
            }
            false
        }
        b.setOnClickListener {
            SoundFx.press(soundOn())
            onClick()
        }
        return b
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
                actionBar?.hide()

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        val prefs = getSharedPreferences("mybubble_prefs", MODE_PRIVATE)

        val scroll = ScrollView(this)
        val bgGradient = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#FFE3F0"), Color.parseColor("#FFF6E5"))
        )
        scroll.background = bgGradient

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.gravity = Gravity.CENTER_HORIZONTAL
        layout.setPadding(dp(24), dp(48), dp(24), dp(32))

        val teddy = TextView(this)
        teddy.text = "🧸"
        teddy.textSize = 72f
        teddy.gravity = Gravity.CENTER
        bobAnimator = ObjectAnimator.ofFloat(teddy, View.TRANSLATION_Y, -12f, 12f).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
        teddy.setOnClickListener {
            SoundFx.pop(soundOn())
            teddy.animate().rotationBy(360f).setDuration(600).start()
        }

        val title = TextView(this)
        title.text = "MyBubble"
        title.textSize = 30f
        title.typeface = Typeface.DEFAULT_BOLD
        title.setTextColor(Color.parseColor("#E0527F"))
        title.gravity = Gravity.CENTER

        val subtitle = TextView(this)
        subtitle.text = "Your little number buddy 💕"
        subtitle.textSize = 15f
        subtitle.setTextColor(Color.parseColor("#B07A90"))
        subtitle.gravity = Gravity.CENTER

        // White rounded card
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(dp(20), dp(20), dp(20), dp(20))
        val cardBg = GradientDrawable()
        cardBg.cornerRadius = dp(28).toFloat()
        cardBg.setColor(Color.WHITE)
        card.background = cardBg
        val cardLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        cardLp.topMargin = dp(24)
        card.layoutParams = cardLp

        val label = TextView(this)
        label.text = "📞 Your phone number"
        label.textSize = 16f
        label.typeface = Typeface.DEFAULT_BOLD
        label.setTextColor(Color.parseColor("#7A4A63"))

        val phoneInput = EditText(this)
        phoneInput.hint = "Type your number"
        phoneInput.inputType = InputType.TYPE_CLASS_PHONE
        phoneInput.textSize = 20f
        phoneInput.setText(prefs.getString("phone", ""))
        phoneInput.setPadding(dp(16), dp(12), dp(16), dp(12))
        val inputBg = GradientDrawable()
        inputBg.cornerRadius = dp(20).toFloat()
        inputBg.setColor(Color.parseColor("#FFF0F6"))
        inputBg.setStroke(dp(2), Color.parseColor("#FFC2DA"))
        phoneInput.background = inputBg
        val inputLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        inputLp.topMargin = dp(10)
        phoneInput.layoutParams = inputLp

        val saveButton = cuteButton("💾  Save number", "#FF7EB6") {
            prefs.edit().putString("phone", phoneInput.text.toString().trim()).apply()
            SoundFx.pop(soundOn())
            Toast.makeText(this, "Saved! 💖", Toast.LENGTH_SHORT).show()
        }

        card.addView(label)
        card.addView(phoneInput)
        card.addView(saveButton)

        // Sound switch row
        val soundSwitch = Switch(this)
        soundSwitch.text = "🔊  Sounds"
        soundSwitch.textSize = 18f
        soundSwitch.typeface = Typeface.DEFAULT_BOLD
        soundSwitch.setTextColor(Color.parseColor("#7A4A63"))
        soundSwitch.isChecked = prefs.getBoolean("sound", true)
        soundSwitch.setPadding(dp(8), dp(16), dp(8), dp(16))
        soundSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("sound", checked).apply()
            soundSwitch.text = if (checked) "🔊  Sounds" else "🔇  Sounds"
            SoundFx.pop(checked)
        }
        soundSwitch.text = if (soundSwitch.isChecked) "🔊  Sounds" else "🔇  Sounds"
        val switchLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        switchLp.topMargin = dp(16)
        soundSwitch.layoutParams = switchLp

        val styleCard = LinearLayout(this)
        styleCard.orientation = LinearLayout.VERTICAL
        styleCard.setPadding(dp(16), dp(16), dp(16), dp(16))
        val styleBg = GradientDrawable()
        styleBg.cornerRadius = dp(28).toFloat()
        styleBg.setColor(Color.WHITE)
        styleCard.background = styleBg
        val styleLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        styleLp.topMargin = dp(16)
        styleCard.layoutParams = styleLp

        val styleTitle = TextView(this)
        styleTitle.text = "🎀 Teddy style"
        styleTitle.textSize = 16f
        styleTitle.typeface = Typeface.DEFAULT_BOLD
        styleTitle.setTextColor(Color.parseColor("#7A4A63"))
        styleCard.addView(styleTitle)

        teddy.text = prefs.getString("skin", "🧸")

        // Skins
        val skinScroll = HorizontalScrollView(this)
        skinScroll.isHorizontalScrollBarEnabled = false
        val skinRow = LinearLayout(this)
        skinRow.orientation = LinearLayout.HORIZONTAL
        skinRow.setPadding(0, dp(12), 0, 0)
        val skinViews = mutableListOf<TextView>()
        fun paintSkins() {
            val cur = prefs.getString("skin", "🧸")
            for ((i, v) in skinViews.withIndex()) {
                val bg = GradientDrawable()
                bg.shape = GradientDrawable.OVAL
                bg.setColor(Color.parseColor(if (BubbleService.SKINS[i] == cur) "#FFC2DA" else "#FFF0F6"))
                v.background = bg
            }
        }
        for (skin in BubbleService.SKINS) {
            val v = TextView(this)
            v.text = skin
            v.textSize = 28f
            v.gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(dp(56), dp(56))
            lp.rightMargin = dp(8)
            v.layoutParams = lp
            v.setOnClickListener {
                prefs.edit().putString("skin", skin).apply()
                teddy.text = skin
                SoundFx.pop(soundOn())
                paintSkins()
            }
            skinViews.add(v)
            skinRow.addView(v)
        }
        paintSkins()
        skinScroll.addView(skinRow)
        styleCard.addView(skinScroll)

        // Colours
        val colorRow = LinearLayout(this)
        colorRow.orientation = LinearLayout.HORIZONTAL
        colorRow.setPadding(0, dp(14), 0, dp(4))
        val colorViews = mutableListOf<View>()
        fun paintColors() {
            val cur = prefs.getInt("color", 0)
            for ((i, v) in colorViews.withIndex()) {
                val bg = GradientDrawable()
                bg.shape = GradientDrawable.OVAL
                bg.setColor(Color.parseColor(BubbleService.COLORS[i]))
                bg.setStroke(dp(3), Color.parseColor(if (i == cur) "#E0527F" else "#E8D5DE"))
                v.background = bg
            }
        }
        for (i in BubbleService.COLORS.indices) {
            val v = View(this)
            val lp = LinearLayout.LayoutParams(dp(36), dp(36))
            lp.rightMargin = dp(8)
            v.layoutParams = lp
            v.setOnClickListener {
                prefs.edit().putInt("color", i).apply()
                SoundFx.press(soundOn())
                paintColors()
            }
            colorViews.add(v)
            colorRow.addView(v)
        }
        paintColors()
        styleCard.addView(colorRow)

        // Size
        val sizeLabel = TextView(this)
        sizeLabel.text = "📏 Size: ${prefs.getInt("size", 100)}%"
        sizeLabel.textSize = 15f
        sizeLabel.setTextColor(Color.parseColor("#7A4A63"))
        sizeLabel.setPadding(0, dp(10), 0, 0)
        val sizeBar = SeekBar(this)
        sizeBar.max = 80
        sizeBar.progress = prefs.getInt("size", 100) - 70
        sizeBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                sizeLabel.text = "📏 Size: ${p + 70}%"
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}

            override fun onStopTrackingTouch(sb: SeekBar?) {
                prefs.edit().putInt("size", (sb?.progress ?: 30) + 70).apply()
                SoundFx.press(soundOn())
            }
        })
        styleCard.addView(sizeLabel)
        styleCard.addView(sizeBar)

        val modeCard = LinearLayout(this)
        modeCard.orientation = LinearLayout.VERTICAL
        modeCard.setPadding(dp(16), dp(16), dp(16), dp(16))
        val modeBg = GradientDrawable()
        modeBg.cornerRadius = dp(28).toFloat()
        modeBg.setColor(Color.WHITE)
        modeCard.background = modeBg
        val modeLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        modeLp.topMargin = dp(8)
        modeCard.layoutParams = modeLp

        val modeTitle = TextView(this)
        modeTitle.text = "👀 When should teddy show?"
        modeTitle.textSize = 16f
        modeTitle.typeface = Typeface.DEFAULT_BOLD
        modeTitle.setTextColor(Color.parseColor("#7A4A63"))
        modeCard.addView(modeTitle)

        val modeNames = listOf("Always", "Only with a text box", "Hidden")
        val modeButtons = mutableListOf<Button>()
        fun paintModes() {
            val current = prefs.getInt("mode", 0)
            for ((i, b) in modeButtons.withIndex()) {
                val bg = GradientDrawable()
                bg.cornerRadius = dp(22).toFloat()
                if (i == current) {
                    bg.setColor(Color.parseColor("#FF7EB6"))
                    b.setTextColor(Color.WHITE)
                } else {
                    bg.setColor(Color.parseColor("#FFF0F6"))
                    b.setTextColor(Color.parseColor("#7A4A63"))
                }
                b.background = bg
            }
        }
        for ((i, name) in modeNames.withIndex()) {
            val b = Button(this)
            b.text = name
            b.isAllCaps = false
            b.textSize = 15f
            b.stateListAnimator = null
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)
            )
            lp.topMargin = dp(8)
            b.layoutParams = lp
            b.setOnClickListener {
                val e = prefs.edit().putInt("mode", i)
                if (i != 2) e.putInt("mode_before", i)
                e.apply()
                SoundFx.press(soundOn())
                paintModes()
            }
            modeButtons.add(b)
            modeCard.addView(b)
        }
        paintModes()

        val tips = TextView(this)
        tips.text = "🎮 Hold the teddy for 1 second to play. Fling it around!\n🙈 To hide it while recording, use the button in the notification."
        tips.textSize = 13f
        tips.setTextColor(Color.parseColor("#B07A90"))
        tips.setPadding(0, dp(12), 0, 0)
        modeCard.addView(tips)

        val startButton = cuteButton("🧸  Start teddy", "#7ED6A8") {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(
                    this, "Allow 'Display over other apps', then come back", Toast.LENGTH_LONG
                ).show()
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } else {
                startForegroundService(Intent(this, BubbleService::class.java))
                Toast.makeText(this, "Teddy is here! 🧸", Toast.LENGTH_SHORT).show()
            }
        }

        val stopButton = cuteButton("😴  Stop teddy", "#FFB36B") {
            stopService(Intent(this, BubbleService::class.java))
            Toast.makeText(this, "Teddy is sleeping 💤", Toast.LENGTH_SHORT).show()
        }

        val accButton = cuteButton("♿  Accessibility settings", "#A78BFA") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        layout.addView(teddy)
        layout.addView(title)
        layout.addView(subtitle)
        layout.addView(card)
        layout.addView(soundSwitch)
        layout.addView(styleCard)
        layout.addView(modeCard)
        layout.addView(startButton)
        layout.addView(stopButton)
        layout.addView(accButton)
        scroll.addView(layout)
        setContentView(scroll)
    }

    override fun onDestroy() {
        bobAnimator?.cancel()
        super.onDestroy()
    }
}