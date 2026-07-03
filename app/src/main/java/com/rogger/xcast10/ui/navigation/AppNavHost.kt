package com.rogger.xcast10.ui.navigation

/*
 * Desenvolvido por Roger de Oliveira
 * Data: 03/07/2026
 * Hora: 12:50
 */
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rogger.xcast10.ui.components.AppDrawerContent
import com.rogger.xcast10.ui.screens.GalleryScreen
import com.rogger.xcast10.ui.screens.HomeScreen
import com.rogger.xcast10.ui.screens.PlayerScreen
import com.rogger.xcast10.viewmodel.CastEvent
import com.rogger.xcast10.viewmodel.CastViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    val viewModel: CastViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()

    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CastEvent.NavigateToPlayer -> {
                    navController.navigate(Screen.Player.route)
                }

                is CastEvent.StreamingStopped -> {
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                }
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = currentRoute != Screen.Player.route,
        drawerContent = {
            AppDrawerContent(
                currentRoute = currentRoute,
                connectedDevice = uiState.selectedDevice,
                onNavigate = { screen ->
                    scope.launch { drawerState.close() }
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onDisconnect = {
                    scope.launch { drawerState.close() }
                    viewModel.disconnectDevice()
                }
            )
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = when (currentRoute) {
                                Screen.Gallery.route -> Screen.Gallery.label
                                Screen.Player.route -> Screen.Player.label
                                else -> Screen.Home.label
                            }
                        )
                    },
                    navigationIcon = {
                        if (currentRoute != Screen.Player.route) {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Filled.Menu, contentDescription = "Menu")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors()
                )
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(
                        uiState = uiState,
                        onDiscoverDevices = { viewModel.discoverDevices() },
                        onDeviceSelected = { viewModel.selectDevice(it) },
                        onConsumeDiscoveryMessage = { viewModel.consumeDiscoveryMessage() },
                        onGoToGallery = { navController.navigate(Screen.Gallery.route) }
                    )
                }

                composable(Screen.Gallery.route) {
                    GalleryScreen(
                        uiState = uiState,
                        onLoadVideos = { viewModel.loadVideos() },
                        onVideoSelected = { viewModel.playVideo(it) }
                    )
                }

                composable(Screen.Player.route) {
                    PlayerScreen(
                        uiState = uiState,
                        onTogglePlayPause = { viewModel.togglePlayPause() },
                        onSeek = { viewModel.seekTo(it) },
                        onVolumeUp = { viewModel.volumeUp() },
                        onVolumeDown = { viewModel.volumeDown() },
                        onCancelStreaming = { viewModel.cancelStreaming() }
                    )
                }
            }
        }
    }
}
