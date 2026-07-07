package com.rogger.xcast10.ui.navigation

/*
 * Desenvolvido por Roger de Oliveira
 * Data: 03/07/2026
 * Hora: 12:52
 */
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Ecrãs disponíveis na aplicação. Os que têm ícones aparecem no menu lateral (drawer).
 */
sealed class Screen(
    val route: String,
    val label: String,
    val filledIcon: ImageVector? = null,
    val outlinedIcon: ImageVector? = null
) {
    data object Home : Screen("home", "Início",
        Icons.Filled.Home, Icons.Outlined.Home)
    data object Gallery : Screen("gallery", "Galeria de vídeos",
        Icons.Filled.VideoLibrary, Icons.Outlined.VideoLibrary)
    data object Player : Screen("player", "Controlo")

    companion object {
        val drawerItems = listOf(Home, Gallery)
    }
}
