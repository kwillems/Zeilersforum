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
import nl.kwillems.zeilersforum.data.PmBox
import nl.kwillems.zeilersforum.ui.screens.PmComposeScreen
import nl.kwillems.zeilersforum.ui.screens.PmComposeViewModel
import nl.kwillems.zeilersforum.ui.screens.PmScreen
import nl.kwillems.zeilersforum.ui.screens.PmViewModel
import nl.kwillems.zeilersforum.ui.screens.ImageViewerScreen
import nl.kwillems.zeilersforum.ui.screens.LoginScreen
import nl.kwillems.zeilersforum.ui.screens.LoginViewModel
import nl.kwillems.zeilersforum.ui.screens.ReplyScreen
import nl.kwillems.zeilersforum.ui.screens.ReplyViewModel
import nl.kwillems.zeilersforum.ui.screens.SettingsScreen
import nl.kwillems.zeilersforum.ui.screens.TopicScreen
import nl.kwillems.zeilersforum.ui.screens.TopicViewModel
import nl.kwillems.zeilersforum.ui.screens.CategoryScreen
import nl.kwillems.zeilersforum.ui.screens.HomeScreen
import nl.kwillems.zeilersforum.ui.screens.TopicListViewModel

@Serializable object TopicsRoute
@Serializable data class TopicRoute(val url: String, val title: String = "", val lastPageUrl: String? = null)
@Serializable data class ImagesRoute(val index: Int)
@Serializable data class ReplyRoute(val replyUrl: String, val topicTitle: String, val quote: String? = null, val newTopic: Boolean = false, val edit: Boolean = false)
@Serializable data class CategoryRoute(val url: String, val title: String)
@Serializable object LoginRoute
@Serializable data class PmRoute(val id: String, val outbox: Boolean, val partner: String)
@Serializable data class PmComposeRoute(val to: String? = null)
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
            HomeScreen(
                app = app,
                onOpenTopic = { url, title, last -> nav.navigate(TopicRoute(url, title, last)) },
                onOpenCategory = { c -> nav.navigate(CategoryRoute(c.url, c.name)) },
                onSettings = { nav.navigate(SettingsRoute) },
                onLogin = { nav.navigate(LoginRoute) },
                onOpenMessage = { m, box -> nav.navigate(PmRoute(m.id, box == PmBox.OUTBOX, m.partner)) },
                onComposeMessage = { nav.navigate(PmComposeRoute()) },
            )
        }
        composable<PmRoute> { entry ->
            val route = entry.toRoute<PmRoute>()
            val box = if (route.outbox) PmBox.OUTBOX else PmBox.INBOX
            val vm = viewModel { PmViewModel(app, route.id, box) }
            PmScreen(
                vm = vm,
                partner = route.partner,
                onBack = { nav.popBackStack() },
                onOpenImages = { list, index -> Gallery.images = list; nav.navigate(ImagesRoute(index)) },
            )
        }
        composable<PmComposeRoute> { entry ->
            val route = entry.toRoute<PmComposeRoute>()
            val vm = viewModel { PmComposeViewModel(app, route.to) }
            PmComposeScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onSent = { nav.popBackStack() },
                onLogin = { nav.navigate(LoginRoute) },
            )
        }
        composable<CategoryRoute> { entry ->
            val route = entry.toRoute<CategoryRoute>()
            val vm = viewModel { TopicListViewModel(app, route.url) }
            CategoryScreen(
                vm = vm,
                title = route.title,
                url = route.url,
                refreshRequest = entry.savedStateHandle.getStateFlow(REFRESH_KEY, false),
                onRefreshHandled = { entry.savedStateHandle[REFRESH_KEY] = false },
                onBack = { nav.popBackStack() },
                onOpenTopic = { t -> nav.navigate(TopicRoute(t.url, t.title, t.lastPostUrl)) },
                onOpenCategory = { c -> nav.navigate(CategoryRoute(c.url, c.name)) },
                onNewTopic = { create, name -> nav.navigate(ReplyRoute(create, name, null, newTopic = true)) },
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
                onEdit = { editUrl, title -> nav.navigate(ReplyRoute(editUrl, title, edit = true)) },
                onLogin = { nav.navigate(LoginRoute) },
                onOpenTopic = { url -> nav.navigate(TopicRoute(url)) },
                onOpenCategory = { url, name -> nav.navigate(CategoryRoute(url, name)) },
            )
        }
        composable<ImagesRoute> { entry ->
            val route = entry.toRoute<ImagesRoute>()
            ImageViewerScreen(images = Gallery.images, startIndex = route.index, onBack = { nav.popBackStack() })
        }
        composable<ReplyRoute> { entry ->
            val route = entry.toRoute<ReplyRoute>()
            val vm = viewModel { ReplyViewModel(app, route.replyUrl, route.topicTitle, route.quote, route.newTopic, route.edit) }
            ReplyScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onPosted = { newTopicUrl ->
                    nav.previousBackStackEntry?.savedStateHandle?.set(REFRESH_KEY, true)
                    nav.popBackStack()
                    if (newTopicUrl != null) nav.navigate(TopicRoute(newTopicUrl))
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
