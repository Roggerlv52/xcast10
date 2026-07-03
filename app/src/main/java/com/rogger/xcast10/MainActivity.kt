package com.rogger.xcast10

/*
 * Desenvolvido por Roger de Oliveira
 * Data: 03/07/2026
 * Hora: 12:27
 */
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.rogger.xcast10.ui.navigation.AppRoot
import com.rogger.xcast10.ui.theme.Xcast10Theme

/**
 * Atividade única da aplicação (arquitetura 100% Jetpack Compose).
 * Aloja o NavHost com o menu lateral (drawer) e os três ecrãs principais:
 * Início, Galeria de vídeos e Controlo de reprodução.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            Xcast10Theme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot()
                }
            }
        }
    }
}
