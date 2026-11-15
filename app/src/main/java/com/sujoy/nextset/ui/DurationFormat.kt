package com.sujoy.nextset.ui

/** Retains seconds for short sessions instead of reporting a misleading zero minutes. */
fun elapsedLabel(millis: Long): String {
    val seconds = millis.coerceAtLeast(0) / 1000
    val hours = seconds / 3600
    val minutes = seconds % 3600 / 60
    val remainder = seconds % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${remainder}s"
        else -> "${remainder}s"
    }
}
