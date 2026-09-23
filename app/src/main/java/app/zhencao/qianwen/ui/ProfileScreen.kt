package app.zhencao.qianwen.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.zhencao.qianwen.AppViewModel
import app.zhencao.qianwen.model.ScriptStyle

@Composable
fun ProfileScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val home by vm.home.collectAsState()
    val settings = home.settings
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("我的", style = MaterialTheme.typography.headlineMedium)
        Text("智永真草千字文", color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionLabel("书体")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = settings.script == ScriptStyle.ZHEN,
                onClick = { vm.setScript(ScriptStyle.ZHEN) },
                label = { Text("真书") },
            )
            FilterChip(
                selected = settings.script == ScriptStyle.CAO,
                onClick = { vm.setScript(ScriptStyle.CAO) },
                label = { Text("草书") },
            )
        }
        Text(
            "只显示所选书体。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SectionLabel("几个字")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = settings.dailyCount == 4,
                onClick = { vm.setDailyCount(4) },
                label = { Text("4 字") },
            )
            FilterChip(
                selected = settings.dailyCount == 8,
                onClick = { vm.setDailyCount(8) },
                label = { Text("8 字") },
            )
        }
        Text(
            "底帖用小川本墨迹切图；残缺的十一个字形由关中本拓片补，格内标「关中本补」。草书笔顺仍是示意图。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp))
}
