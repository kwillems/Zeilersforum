package nl.kwillems.zeilersforum.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable
import nl.kwillems.zeilersforum.ZfApp
import nl.kwillems.zeilersforum.ui.screens.ImageViewerScreen
import nl.kwillems.zeilersforum.ui.screens.LoginScreen
import nl.kwillems.zeilersforum.ui.screens.LoginViewModel
import nl.kwillems.zeilersforum.ui.screens.ReplyScreen
import nl.kwillems.zeilersforum.ui.screens.ReplyViewModel
import nl.kwillems.zeilersforum.ui.screens.SettingsScreen
import nl.kwillems.zeilersforum.ui.screens.TopicScreen
import nl.kwillems.zeilersforum.ui.screens.TopicViewModel
import nl.kwillems.zeilersforum.ui.screens.TopicsScreen
import nl.kwillems.zeilersforum.ui.screens.TopicsViewModel

@Serializable object TopicsRoute
@Serializable data class TopicRoute(val url: String, val title: String = "", val lastPageUrl: String? = null)
@Serializable data class ImagesRoute(val index: Int)
@Serializable data class ReplyRoute(val replyUrl: String, val topicTitle: String, val quote: String? = null)
@Serializable object LoginRoute
@Serializable object SettingsRoute

/** Lijst met foto's die de fotoviewer toont (te groot om via de route door te geven). */
object Gallery {
    var images: List<String> = emptyList()
}

const val REFRESH_KEY = "refresh"

@Composable
fun ZfNavHost(app: ZfApp, deepLink: String?, onDeepLinkHandled: () -> Unit) {
    val nav = rememberNavController()

    LaunchedEffect(deepLink) {
        if (deepLink != null) {
            nav.navigate(TopicRoute(url = deepLink))
            onDeepLinkHandled()
        }
    }

    NavHost(
        navController = nav,
        startDestination = TopicsRoute,
        enterTransition = { slideInHorizontally { it / 4 } + fadeIn() },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { slideOutHorizontally { it / 4 } + fadeOut() },
    ) {
        composable<TopicsRoute> {
            val vm = viewModel { TopicsViewModel(app) }
            TopicsScreen(
                vm = vm,
                onOpenTopic = { t -> nav.navigate(TopicRoute(t.url, t.title, t.lastPostUrl)) },
                onSettings = { nav.navigate(SettingsRoute) },
                onLogin = { nav.navigate(LoginRoute) },
            )
        }
        composable<TopicRoute> { entry ->
            val route = entry.toRoute<TopicRoute>()
            val vm = viewModel { TopicViewModel(app, route.url, route.title, route.lastPageUrl) }
            val refresh = entry.savedStateHandle.getStateFlow(REFRESH_KEY, false)
            TopicScreen(
                vm = vm,
                refreshRequest = refresh,
                onRefreshHandled = { entry.savedStateHandle[REFRESH_KEY] = false },
                onBack = { if (!nav.popBackStack()) nav.navigate(TopicsRoute) },
                onOpenImages = { list, index ->
                    Gallery.images = list
                    nav.navigate(ImagesRoute(index))
                },
                onReply = { replyUrl, title, quote -> nav.navigate(ReplyRoute(replyUrl, title, quote)) },
                onLogin = { nav.navigate(LoginRoute) },
                onOpenTopic = { url -> nav.navigate(TopicRoute(url)) },
            )
        }
        composable<ImagesRoute> { entry ->
            val route = entry.toRoute<ImagesRoute>()
            ImageViewerScreen(images = Gallery.images, startIndex = route.index, onBack = { nav.popBackStack() })
        }
        composable<ReplyRoute> { entry ->
            val route = entry.toRoute<ReplyRoute>()
            val vm = viewModel { ReplyViewModel(app, route.replyUrl, route.topicTitle, route.quote) }
            ReplyScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onPosted = {
                    nav.previousBackStackEntry?.savedStateHandle?.set(REFRESH_KEY, true)
                    nav.popBackStack()
                },
                onLogin = { nav.navigate(LoginRoute) },
            )
        }
        composable<LoginRoute> {
            val vm = viewModel { LoginViewModel(app) }
            LoginScreen(vm = vm, onBack = { nav.popBackStack() }, onLoggedIn = {
                nav.previousBackStackEntry?.savedStateHandle?.set(REFRESH_KEY, true)
                nav.popBackStack()
            })
        }
        composable<SettingsRoute> {
            SettingsScreen(app = app, onBack = { nav.popBackStack() }, onLogin = { nav.navigate(LoginRoute) })
        }
    }
}
