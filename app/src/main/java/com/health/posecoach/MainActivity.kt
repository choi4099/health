package com.health.posecoach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.health.posecoach.data.ExerciseRepository
import com.health.posecoach.ui.CameraScreen
import com.health.posecoach.ui.ExerciseDetailScreen
import com.health.posecoach.ui.ExerciseListScreen
import com.health.posecoach.ui.HealthTheme
import com.health.posecoach.ui.ResultScreen
import com.health.posecoach.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HealthTheme { AppNav() } }
    }
}

@Composable
private fun AppNav() {
    val nav = rememberNavController()
    val idArg = listOf(navArgument("id") { type = NavType.IntType })

    NavHost(navController = nav, startDestination = "list") {
        composable("list") {
            ExerciseListScreen(
                onOpen = { id -> nav.navigate("detail/$id") },
                onSettings = { nav.navigate("settings") }
            )
        }
        composable("detail/{id}", arguments = idArg) { entry ->
            val id = entry.arguments!!.getInt("id")
            ExerciseDetailScreen(
                ExerciseRepository.byId(id),
                onBack = { nav.popBackStack() },
                onStart = { nav.navigate("camera/$id") }
            )
        }
        composable("camera/{id}", arguments = idArg) { entry ->
            val id = entry.arguments!!.getInt("id")
            CameraScreen(
                ExerciseRepository.byId(id),
                onBack = { nav.popBackStack() },
                onFinished = {
                    nav.navigate("result/$id") { popUpTo("camera/{id}") { inclusive = true } }
                }
            )
        }
        composable("result/{id}", arguments = idArg) { entry ->
            val id = entry.arguments!!.getInt("id")
            ResultScreen(
                ExerciseRepository.byId(id),
                onRetry = { nav.navigate("camera/$id") { popUpTo("result/{id}") { inclusive = true } } },
                onList = { nav.popBackStack("list", inclusive = false) }
            )
        }
        composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }) }
    }
}
