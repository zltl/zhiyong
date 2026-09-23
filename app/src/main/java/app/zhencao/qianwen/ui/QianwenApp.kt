package app.zhencao.qianwen.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.zhencao.qianwen.AppViewModel
import app.zhencao.qianwen.R

private object Dest {
    const val TODAY = "today"
    const val CORPUS = "corpus"
    const val PROFILE = "profile"
    const val LOOK = "look"
    const val CROP = "crop"
    const val COMPARE = "compare/{id}"

    fun compare(id: Long) = "compare/$id"
}

private data class Tab(val route: String, val label: String, val icon: Int)

private val tabs = listOf(
    Tab(Dest.TODAY, "字", R.drawable.ic_nav_brush),
    Tab(Dest.CORPUS, "千文", R.drawable.ic_nav_scroll),
    Tab(Dest.PROFILE, "我的", R.drawable.ic_nav_seal),
)

@Composable
fun QianwenApp(vm: AppViewModel) {
    val home by vm.home.collectAsState()
    when {
        !home.ready -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("正在准备。")
            }
        }
        home.script == null -> ScriptPickerScreen(onPick = vm::setScript)
        else -> QianwenMain(vm)
    }
}

@Composable
private fun QianwenMain(vm: AppViewModel) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: Dest.TODAY
    val showBar = route in tabs.map { it.route }
    val open: (Int) -> Unit = { index ->
        vm.select(index)
        nav.navigate(Dest.LOOK)
    }
    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = { nav.switchTab(tab.route) },
                            icon = { Icon(painterResource(tab.icon), contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Dest.TODAY,
            modifier = Modifier.padding(padding),
        ) {
            composable(Dest.TODAY) { TodayScreen(vm, onOpen = open) }
            composable(Dest.CORPUS) {
                CorpusScreen(
                    vm,
                    onOpen = open,
                    onPickVerse = { verse ->
                        vm.setVerse(verse)
                        nav.switchTab(Dest.TODAY)
                    },
                )
            }
            composable(Dest.PROFILE) { ProfileScreen(vm) }
            composable(Dest.LOOK) {
                StudioScreen(
                    vm,
                    onBack = { nav.popBackStack() },
                    onFrame = { nav.navigate(Dest.CROP) },
                    onOpenPractice = { id -> nav.navigate(Dest.compare(id)) },
                )
            }
            composable(Dest.CROP) {
                CropScreen(
                    vm,
                    onBack = { nav.popBackStack() },
                    onSaved = { id ->
                        nav.navigate(Dest.compare(id)) {
                            popUpTo(Dest.CROP) { inclusive = true }
                        }
                    },
                )
            }
            composable(
                Dest.COMPARE,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                CompareScreen(
                    vm,
                    id = entry.arguments?.getLong("id") ?: 0L,
                    onBack = { nav.popBackStack() },
                    onNext = { charIndex, framing ->
                        vm.select((charIndex + 1).coerceAtMost(vm.corpus.characters.lastIndex))
                        nav.popBackStack(Dest.LOOK, inclusive = false)
                        if (framing) nav.navigate(Dest.CROP)
                    },
                )
            }
        }
    }
}

private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
