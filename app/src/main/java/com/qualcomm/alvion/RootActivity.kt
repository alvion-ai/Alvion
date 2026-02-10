package com.qualcomm.alvion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import com.qualcomm.alvion.core.ui.theme.ALVIONTheme
import com.qualcomm.alvion.feature.auth.LoginScreen
import com.qualcomm.alvion.feature.intro.IntroScreen
import com.qualcomm.alvion.feature.shell.AppShell

class RootActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ALVIONTheme {
                AppNav()
            }
        }
    }
}

@Composable
private fun AppNav() {
    val nav = rememberNavController()
    val auth = FirebaseAuth.getInstance()

    // Start at login if not authenticated, otherwise go to home.
    val startDest = if (auth.currentUser == null) "login" else "home"

    NavHost(navController = nav, startDestination = startDest) {
        composable("login") {
            LoginScreen(onLoginSuccess = {
                nav.navigate("intro") {
                    popUpTo("login") { inclusive = true }
                }
            })
        }
        composable("intro") {
            IntroScreen(onComplete = {
                nav.navigate("home") {
                    popUpTo("intro") { inclusive = true }
                }
            })
        }
        composable("home") {
            AppShell(
                onSignOut = {
                    auth.signOut()
                    nav.navigate("login") {
                        popUpTo("home") { inclusive = true }
                    }
                },
            )
        }
    }
}
