package app.zhencao.qianwen.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.zhencao.qianwen.AppViewModel
import kotlinx.coroutines.launch

@Composable
fun CorpusScreen(
    vm: AppViewModel,
    modifier: Modifier = Modifier,
    onOpen: (Int) -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val home by vm.home.collectAsState()
    val groups = remember { (0 until 250).toList() }
    Column(modifier.fillMaxSize()) {
        Text(
            "千文",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 24.dp, top = 28.dp, end = 24.dp),
        )
        Text(
            home.settings.script?.let { "二百五十句，点字即看${it.bookLabel}。" } ?: "二百五十句，点字即看。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 6.dp, bottom = 8.dp),
        )
        BoxWithSnackbar(snackbar, Modifier.weight(1f)) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(groups, key = { it }) { group ->
                    val chars = vm.corpus.characters.subList(group * 4, group * 4 + 4)
                    val open = chars.all { it.available }
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "%03d".format(group + 1),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(end = 12.dp),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            chars.forEach { entry ->
                                Text(
                                    entry.char,
                                    fontFamily = FontFamily.Serif,
                                    fontSize = 28.sp,
                                    color = if (entry.available) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                    },
                                    modifier = Modifier
                                        .clickable {
                                            if (entry.available) {
                                                onOpen(entry.index)
                                            } else {
                                                scope.launch { snackbar.showSnackbar("这个字还不能看") }
                                            }
                                        }
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                )
                            }
                        }
                        Text(
                            if (open) "" else "未就绪",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxWithSnackbar(
    snackbar: SnackbarHostState,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    androidx.compose.foundation.layout.Box(modifier) {
        content()
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}
