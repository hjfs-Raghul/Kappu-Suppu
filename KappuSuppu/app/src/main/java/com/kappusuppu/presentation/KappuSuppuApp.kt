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
import com.kappusuppu.presentation.game.HandoffScreen
import com.kappusuppu.presentation.game.NextStep
import com.kappusuppu.presentation.home.HomeScreen
import com.kappusuppu.presentation.howtoplay.HowToPlayScreen
import com.kappusuppu.presentation.result.ResultScreen
import com.kappusuppu.presentation.roles.RoleScreen
import com.kappusuppu.presentation.target.TargetScreen

private object Route {
    const val HOME = "home"
    const val HOW = "howtoplay"
    const val ROLES = "roles"
    const val BOOK = "book"
    const val HANDOFF = "handoff"
    const val GAME = "game"
    const val TARGET = "target"
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

    fun handOff() {
        nav.navigate(Route.HANDOFF) { popUpTo(Route.HOME) }
    }

    fun route(step: NextStep) = when (step) {
        NextStep.TARGET -> nav.navigate(Route.TARGET)
        NextStep.HANDOFF -> handOff()
        NextStep.RESULT -> nav.navigate(Route.RESULT) { popUpTo(Route.HOME) }
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
                    handOff()
                },
                onBack = { nav.popBackStack() },
            )
        }
        composable(Route.HANDOFF) {
            HandoffScreen(
                state = state,
                onContinue = { nav.navigate(Route.GAME) { popUpTo(Route.HANDOFF) { inclusive = true } } },
            )
        }
        composable(Route.GAME) {
            DisposableEffect(Unit) {
                vm.setBoardVisible(true)
                onDispose { vm.setBoardVisible(false) }
            }
            GameScreen(
                state = state,
                onSubmitPage = vm::submitPhysicalPage,
                onClearError = vm::clearPageError,
                onOpenBook = vm::openBook,
                onConfirmSpread = vm::confirmSpread,
                onContinue = { route(vm.continueFromFeedback()) },
                onStrokeDrawn = vm::onStrokeDrawn,
                onStrokesErased = vm::onStrokesErased,
                onQuit = ::goHome,
            )
        }
        composable(Route.TARGET) {
            DisposableEffect(Unit) {
                vm.setBoardVisible(true)
                onDispose { vm.setBoardVisible(false) }
            }
            TargetScreen(
                state = state,
                onSelect = vm::selectTarget,
                onCancel = {
                    vm.cancelTarget()
                    nav.popBackStack()
                },
                onFire = vm::confirmFire,
                onContinue = { route(vm.continueFromTarget()) },
                onStrokeDrawn = vm::onStrokeDrawn,
                onStrokesErased = vm::onStrokesErased,
            )
        }
        composable(Route.RESULT) {
            LaunchedEffect(Unit) { vm.onResultShown() } // game over: all sound stops
            ResultScreen(
                state = state,
                onPlayAgain = {
                    vm.playAgain()
                    handOff()
                },
                onHome = ::goHome,
            )
        }
    }
}
