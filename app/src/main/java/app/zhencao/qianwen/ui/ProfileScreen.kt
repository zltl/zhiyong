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
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("我的", style = MaterialTheme.typography.headlineMedium)
        Text("智永真草千字文", color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionLabel("书体")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScriptStyle.entries.forEach { script ->
                FilterChip(
                    selected = home.script == script,
                    onClick = { vm.setScript(script) },
                    label = { Text(script.bookLabel) },
                )
            }
        }
        SectionLabel("临作")
        Text(
            "已临 ${home.counts.size} 字，共 ${home.counts.values.sum()} 次。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SectionLabel("底帖来源")
        SourceNote(
            "墨迹 · 小川本",
            "智永《真草千字文》墨迹，日本京都小川家藏（国宝）。图像取自书法空间（www.9610.com/zhy）刊布的全册扫描。本应用逐字裁切、缩放。",
        )
        SourceNote(
            "拓本 · 关中本",
            "《関中本真草千字文》，东京大学综合图书馆藏（A005940），据该馆数字档案 IIIF 公开图像，按其再利用条款署名。本应用逐字裁切，保持拓片原色。单字页可打开与小川本对照；小川本残缺的字形默认改用此本，格内标「关中本补」。",
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp))
}

@Composable
private fun SourceNote(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.bodyMedium)
    Text(
        body,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
