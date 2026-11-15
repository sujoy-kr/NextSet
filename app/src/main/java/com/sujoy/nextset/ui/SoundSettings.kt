package com.sujoy.nextset.ui

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.IntentCompat
import androidx.core.net.toUri

@Composable
fun SoundSettings(soundUri: String?, onSound: (String?) -> Unit) {
    val context = LocalContext.current
    var error by remember { mutableStateOf(false) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result
            ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri =
                    result.data?.let {
                        IntentCompat.getParcelableExtra(
                            it,
                            RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                            Uri::class.java,
                        )
                    }
                if (uri != null && uri.scheme in listOf("content", "android.resource")) {
                    error = false
                    val flags = result.data?.flags ?: 0
                    if (
                        flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0 &&
                            flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0
                    ) {
                        // System media tones normally need no grant; custom providers can offer
                        // one.
                        runCatching {
                                context.contentResolver.takePersistableUriPermission(
                                    uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                                )
                            }
                            .onFailure { error = true }
                    }
                    onSound(uri.toString())
                } else error = true
            }
        }
    val title =
        remember(soundUri) {
            soundUri?.let { value ->
                runCatching {
                        RingtoneManager.getRingtone(context, value.toUri())?.getTitle(context)
                    }
                    .getOrNull() ?: "Selected tone"
            } ?: "App default"
        }
    Column {
        Text("Notification sound", style = MaterialTheme.typography.titleMedium)
        Text(title)
        Text(
            "Used for rest timers and workout reminders when sound is enabled.",
            style = MaterialTheme.typography.bodySmall,
        )
        fun pick(type: Int) {
            val intent =
                Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, type)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    .putExtra(
                        RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                        RingtoneManager.getDefaultUri(type),
                    )
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, soundUri?.toUri())
            runCatching { launcher.launch(intent) }.onFailure { error = true }
        }
        TextButton(onClick = { pick(RingtoneManager.TYPE_RINGTONE) }) { Text("Choose ringtone") }
        TextButton(onClick = { pick(RingtoneManager.TYPE_NOTIFICATION) }) {
            Text("Choose notification tone")
        }
        if (soundUri != null)
            TextButton(
                onClick = {
                    onSound(null)
                    error = false
                }
            ) {
                Text("Use app default")
            }
        if (error)
            Text(
                "Could not select a tone. Try another sound picker or use the app default.",
                color = MaterialTheme.colorScheme.error,
            )
    }
}
