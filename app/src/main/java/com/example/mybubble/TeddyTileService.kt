package com.example.mybubble

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

class TeddyTileService : TileService() {

    override fun onStartListening() {
        refresh()
    }

    override fun onClick() {
        val prefs = getSharedPreferences("mybubble_prefs", MODE_PRIVATE)
        if (BubbleService.instance == null) {
            Toast.makeText(this, "Start the teddy in the app first 🧸", Toast.LENGTH_SHORT).show()
            return
        }
        val mode = prefs.getInt("mode", 0)
        if (mode == 2) {
            prefs.edit().putInt("mode", prefs.getInt("mode_before", 0)).apply()
        } else {
            prefs.edit().putInt("mode_before", mode).putInt("mode", 2).apply()
        }
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val prefs = getSharedPreferences("mybubble_prefs", MODE_PRIVATE)
        val running = BubbleService.instance != null
        val hidden = prefs.getInt("mode", 0) == 2
        tile.label = "Teddy"
        tile.state = if (running && !hidden) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= 29) {
            tile.subtitle = if (!running) "Not running" else if (hidden) "Hidden 🙈" else "Showing 🧸"
        }
        tile.updateTile()
    }
}