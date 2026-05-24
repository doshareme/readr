package labs.dx.readr.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import labs.dx.readr.ui.home.HomeScreen
import labs.dx.readr.ui.reader.ReaderScreen

private object Routes {
    const val HOME = "home"
    const val READER = "reader/{uri}"

    fun reader(uri: String): String = "reader/${Uri.encode(uri)}"
}

@Composable
fun ReadrNavGraph() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenPdf = { uri -> navController.navigate(Routes.reader(uri)) }
            )
        }

        composable(
            route = Routes.READER,
            arguments = listOf(navArgument("uri") { type = NavType.StringType })
        ) {
            ReaderScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
