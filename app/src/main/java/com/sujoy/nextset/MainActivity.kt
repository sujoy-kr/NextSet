package com.sujoy.nextset

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.sujoy.nextset.ui.NextSetApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) consumeSessionIntent(intent)
        setContent { NextSetApp(application as NextSetApplication) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeSessionIntent(intent)
    }

    private fun consumeSessionIntent(intent: Intent) {
        intent.getStringExtra("session")?.let {
            (application as NextSetApplication).requestedSession.value = it
        }
        intent.removeExtra("session")
        if (intent.getBooleanExtra("open_home", false)) {
            (application as NextSetApplication).requestedHome.value = true
            intent.removeExtra("open_home")
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            try {
                (application as NextSetApplication).runtime.recover()
                (application as NextSetApplication).reminders.refresh()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("NextSet", "Could not recover workout notifications", e)
            }
        }
    }
}
