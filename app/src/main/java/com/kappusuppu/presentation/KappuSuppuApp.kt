package com.kappusuppu.presentation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kappusuppu.presentation.book.BookModeScreen
import com.kappusuppu.presentation.game.GameScreen
import com.kappusuppu.presentation.game.GameViewModel
import com.kappusuppu.presentation.game.TurnStep
import com.kappusuppu.presentation.home.HomeScreen
import com.kappusuppu.presentation.howtoplay.HowToPlayScreen
import com.kappusuppu.presentation.result.ResultScreen
import com.kappusuppu.presentation.roles.RoleScreen

private object Route {
    const val HOME = "home"
    const val HOW = "howtoplay"
    const val ROLES = "roles"
    const val BOOK = "book"
    const val GAME = "game"
    const val RESULT = "result"
}

@Composable
fun KappuSuppuApp(vm: GameViewModel) {
    val nav = rememberNavController()
    val state by vm.state.collectAsStateWithLifecycle()
    val sound by vm.soundEnabled.collectAsStateWithLifecycle()
    val role by vm.roleChoice.collectAsStateWithLifecycle()

    fun goHome() {
        vm.exitToHome()
        nav.navigate(Route.HOME) { popUpTo(Route.HOME) { inclusive = true } }
    }

    fun startGameScreen() {
        nav.navigate(Route.GAME) { popUpTo(Route.HOME) }
    }

    NavHost(
        navController = nav,
        startDestination = Route.HOME,
        enterTransition = { fadeIn(tween(180)) },
        exitTransition = { fadeOut(tween(180)) },
        popEnterTransition = { fadeIn(tween(180)) },
        popExitTransition = { fadeOut(tween(180)) },
    ) {
        composable(Route.HOME) {
            HomeScreen(
                soundEnabled = sound,
                onToggleSound = vm::toggleSound,
                onNewGame = { nav.navigate(Route.ROLES) },
                onHowToPlay = { nav.navigate(Route.HOW) },
            )
        }
        composable(Route.HOW) { HowToPlayScreen(onBack = { nav.popBackStack() }) }
        composable(Route.ROLES) {
            RoleScreen(
                selected = role,
                onSelect = vm::chooseRole,
                onConfirm = { nav.navigate(Route.BOOK) },
                onBack = { nav.popBackStack() },
            )
        }
        composable(Route.BOOK) {
            BookModeScreen(
                onPick = { mode ->
                    vm.startNewGame(mode)
                    startGameScreen()
                },
                onBack = { nav.popBackStack() },
            )
        }
        composable(Route.GAME) {
            DisposableEffect(Unit) {
                vm.setBoardVisible(true)
                onDispose { vm.setBoardVisible(false) }
            }
            // the last character fell: show the result once the final animations have finished
            LaunchedEffect(state.step) {
                if (state.step == TurnStep.GameOver) nav.navigate(Route.RESULT) { popUpTo(Route.HOME) }
            }
            GameScreen(
                state = state,
                onSubmitPage = vm::submitPhysicalPage,
                onClearError = vm::clearPageError,
                onOpenBook = vm::openBook,
                onTarget = vm::fireAt,
                onStrokeDrawn = vm::onStrokeDrawn,
                onStrokesErased = vm::onStrokesErased,
                onQuit = ::goHome,
            )
        }
        composable(Route.RESULT) {
            LaunchedEffect(Unit) { vm.onResultShown() } // game over: all sound stops
            ResultScreen(
                state = state,
                onPlayAgain = {
                    vm.playAgain()
                    startGameScreen()
                },
                onHome = ::goHome,
            )
        }
    }
}
