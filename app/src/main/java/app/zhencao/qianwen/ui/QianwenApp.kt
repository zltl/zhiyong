package app.zhencao.qianwen.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.zhencao.qianwen.AppViewModel

private object Dest {
    const val TODAY = "today"
    const val CORPUS = "corpus"
    const val LOOK = "look"
    const val PROFILE = "profile"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Dest.TODAY, "字", Icons.Filled.Home),
    Tab(Dest.CORPUS, "千文", Icons.AutoMirrored.Filled.MenuBook),
    Tab(Dest.PROFILE, "我的", Icons.Filled.Settings),
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
        home.settings.script == null -> ScriptPickerScreen(onPick = vm::setScript)
        else -> QianwenMain(vm)
    }
}

@Composable
private fun QianwenMain(vm: AppViewModel) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: Dest.TODAY
    val selected by vm.selected.collectAsState()
    val showBar = route in tabs.map { it.route }
    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
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
            composable(Dest.TODAY) {
                TodayScreen(vm) { index ->
                    vm.select(index)
                    nav.navigate(Dest.LOOK)
                }
            }
            composable(Dest.CORPUS) {
                CorpusScreen(vm) { index ->
                    vm.select(index)
                    nav.navigate(Dest.LOOK)
                }
            }
            composable(Dest.LOOK) {
                StudioScreen(vm, selected, onBack = { nav.popBackStack() })
            }
            composable(Dest.PROFILE) { ProfileScreen(vm) }
        }
    }
}
