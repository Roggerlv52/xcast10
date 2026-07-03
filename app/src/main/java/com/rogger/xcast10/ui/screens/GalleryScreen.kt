package com.rogger.xcast10.ui.screens

/*
 * Desenvolvido por Roger de Oliveira
 * Data: 03/07/2026
 * Hora: 12:53
 */
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rogger.xcast10.data.model.VideoItem
import com.rogger.xcast10.ui.components.VideoCard
import com.rogger.xcast10.viewmodel.CastUiState

@Composable
fun GalleryScreen(
    uiState: CastUiState,
    onLoadVideos: () -> Unit,
    onVideoSelected: (VideoItem) -> Unit
) {
    LaunchedEffect(Unit) { onLoadVideos() }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            uiState.isLoadingVideos -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
                    Text("A carregar os seus vídeos…", style = MaterialTheme.typography.bodyMedium)
                }
            }

            uiState.videos.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.VideoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                    Text(
                        "Nenhum vídeo encontrado neste dispositivo",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(uiState.videos) { video ->
                        VideoCard(item = video, onClick = { onVideoSelected(video) })
                    }
                }
            }
        }

        if (uiState.isStartingStream) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(24.dp)
                ) {
                    CircularProgressIndicator()
                    androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
                    Text("A iniciar a transmissão…", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
