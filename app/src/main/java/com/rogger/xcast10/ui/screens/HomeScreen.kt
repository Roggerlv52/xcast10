package com.rogger.xcast10.ui.screens

/*
 * Desenvolvido por Roger de Oliveira
 * Data: 02/07/2026
 * Hora: 15:26
 */
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.rogger.xcast10.ui.components.ConnectionStatusCard
import com.rogger.xcast10.ui.components.DeviceDiscoverySheet
import com.rogger.xcast10.util.PermissionUtils
import com.rogger.xcast10.viewmodel.CastUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: CastUiState,
    onDiscoverDevices: () -> Unit,
    onDeviceSelected: (com.rogger.xcast10.data.model.DLNADevice) -> Unit,
    onConsumeDiscoveryMessage: () -> Unit,
    onGoToGallery: () -> Unit
) {
    val context = LocalContext.current
    var showSheet by remember {
        mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* resultado tratado de forma silenciosa - a galeria valida novamente */ }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context, PermissionUtils.videoPermission
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) permissionLauncher.launch(PermissionUtils.videoPermission)
    }

    LaunchedEffect(uiState.isDiscovering, uiState.discoveredDevices, uiState.discoveryMessage) {
        if (uiState.isDiscovering || uiState.discoveredDevices.isNotEmpty() || uiState.discoveryMessage != null) {
            showSheet = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Bem-vindo de volta",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = "Conecte-se a uma Smart TV e comece a transmitir os seus vídeos.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        ConnectionStatusCard(device = uiState.selectedDevice)

        Button(
            onClick = onDiscoverDevices,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            if (uiState.isDiscovering) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.padding(start = 8.dp))
                Text("Procurando…")
            } else {
                Icon(Icons.Filled.WifiFind, contentDescription = null)
                Spacer(Modifier.padding(start = 8.dp))
                Text("Procurar dispositivos na rede")
            }
        }

        OutlinedButton(
            onClick = onGoToGallery,
            enabled = uiState.selectedDevice != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Icon(Icons.Filled.PlayCircle, contentDescription = null)
            Spacer(Modifier.padding(start = 8.dp))
            Text("Selecionar vídeo para transmitir")
        }

        uiState.lastVideoTitle?.let { title ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.padding(start = 8.dp))
                        Text("Último vídeo transmitido", style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(title, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    if (showSheet) {
        DeviceDiscoverySheet(
            devices = uiState.discoveredDevices,
            isDiscovering = uiState.isDiscovering,
            emptyMessage = uiState.discoveryMessage,
            onDeviceSelected = {
                onDeviceSelected(it)
                showSheet = false
                onConsumeDiscoveryMessage()
            },
            onDismiss = {
                showSheet = false
                onConsumeDiscoveryMessage()
            }
        )
    }
}
