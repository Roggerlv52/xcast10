package com.rogger.xcast10.ui.components

/*
 * Desenvolvido por Roger de Oliveira
 * Data: 03/07/2026
 * Hora: 12:39
 */
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rogger.xcast10.util.TimeFormatter


@Composable
fun PlaybackProgressRow(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Int) -> Unit
) {
    val maxSeconds = (durationMs / 1000).toInt().coerceAtLeast(0)
    var sliderPosition by remember(positionMs) {
        mutableFloatStateOf((positionMs / 1000).toFloat())
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = sliderPosition.coerceIn(0f, maxSeconds.toFloat().coerceAtLeast(0f)),
            onValueChange = { sliderPosition = it },
            onValueChangeFinished = { onSeek(sliderPosition.toInt()) },
            valueRange = 0f..(if (maxSeconds > 0) maxSeconds.toFloat() else 1f)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = TimeFormatter.formatDuration((sliderPosition * 1000).toLong()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = TimeFormatter.formatDuration(durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun PlayPauseButton(isPlaying: Boolean, onToggle: () -> Unit) {
    FilledIconButton(
        onClick = onToggle,
        modifier = Modifier.size(72.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (isPlaying) "Pausar" else "Reproduzir",
            modifier = Modifier.size(36.dp)
        )
    }
}

@Composable
fun VolumeControlRow(
    volume: Int,
    onVolumeDown: () -> Unit,
    onVolumeUp: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FilledTonalIconButton(onClick = onVolumeDown) {
            Icon(Icons.Filled.VolumeDown, contentDescription = "Diminuir volume")
        }

        Column(modifier = Modifier.weight(1f)) {
            Text("Volume", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = volume.toFloat(),
                onValueChange = {},
                valueRange = 0f..100f,
                enabled = false
            )
        }

        FilledTonalIconButton(onClick = onVolumeUp) {
            Icon(Icons.Filled.VolumeUp, contentDescription = "Aumentar volume")
        }
    }
}
