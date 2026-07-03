package com.rogger.xcast10.ui.components

/*
 * Desenvolvido por Roger de Oliveira
 * Data: 03/07/2026
 * Hora: 12:35
 */
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rogger.xcast10.data.model.DLNADevice
import com.rogger.xcast10.ui.navigation.Screen

@Composable
fun AppDrawerContent(
    currentRoute: String?,
    connectedDevice: DLNADevice?,
    onNavigate: (Screen) -> Unit,
    onDisconnect: () -> Unit
) {
    ModalDrawerSheet {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Cast,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.padding(start = 8.dp))
                Text("Xcast10", style = MaterialTheme.typography.headlineSmall)
            }
            Text(
                text = "Transmita vídeos para a sua Smart TV",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HorizontalDivider()

        Spacer(Modifier.height(8.dp))

        Screen.drawerItems.forEach { screen ->
            NavigationDrawerItem(
                icon = {
                    Icon(
                        imageVector = if (currentRoute == screen.route) screen.filledIcon!! else screen.outlinedIcon!!,
                        contentDescription = null
                    )
                },
                label = { Text(screen.label) },
                selected = currentRoute == screen.route,
                onClick = { onNavigate(screen) },
                colors = NavigationDrawerItemDefaults.colors(),
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }

        if (connectedDevice != null) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            NavigationDrawerItem(
                icon = { Icon(Icons.Filled.LinkOff, contentDescription = null) },
                label = { Text("Desconectar \"${connectedDevice.name}\"") },
                selected = false,
                onClick = onDisconnect,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }
}
