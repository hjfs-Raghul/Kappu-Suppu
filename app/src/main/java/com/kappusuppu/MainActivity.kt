package com.kappusuppu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.kappusuppu.presentation.KappuSuppuApp
import com.kappusuppu.presentation.game.GameViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { KappuSuppuApp(viewModel) }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onAppForeground() // resumes gameplay music if a game is active and sound is on
    }

    override fun onStop() {
        viewModel.onAppBackground() // pauses everything and releases audio focus
        super.onStop()
    }
}
