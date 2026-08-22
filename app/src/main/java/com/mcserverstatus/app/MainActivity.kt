package com.mcserverstatus.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mcserverstatus.app.ui.MainViewModel
import com.mcserverstatus.app.ui.screens.ServerDetailScreen
import com.mcserverstatus.app.ui.screens.ServerListScreen
import com.mcserverstatus.app.ui.theme.McServerStatusTheme

private const val ROUTE_LIST = "list"
private const val ROUTE_DETAIL = "detail/{serverId}"
private const val ARG_SERVER_ID = "serverId"

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.factory(application as McServerStatusApp)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            McServerStatusTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost(viewModel)
                }
            }
        }
    }
}

@Composable
private fun AppNavHost(viewModel: MainViewModel) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = ROUTE_LIST) {
        composable(ROUTE_LIST) {
            ServerListScreen(
                viewModel = viewModel,
                onOpenServer = { id -> navController.navigate("detail/$id") },
            )
        }
        composable(
            route = ROUTE_DETAIL,
            arguments = listOf(navArgument(ARG_SERVER_ID) { type = NavType.LongType }),
        ) { backStackEntry ->
            val serverId = backStackEntry.arguments?.getLong(ARG_SERVER_ID) ?: -1L
            ServerDetailScreen(
                serverId = serverId,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
