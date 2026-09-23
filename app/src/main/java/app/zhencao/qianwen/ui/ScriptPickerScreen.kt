package app.zhencao.qianwen.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.zhencao.qianwen.model.ScriptStyle

@Composable
fun ScriptPickerScreen(
    onPick: (ScriptStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text("智永真草千字文", style = MaterialTheme.typography.headlineMedium)
        Text(
            "先选一体。选好之后只看这一体，以后打开也是它。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ScriptChoiceCard(
            title = "真",
            detail = "只看真书底帖。",
            onClick = { onPick(ScriptStyle.ZHEN) },
        )
        ScriptChoiceCard(
            title = "草",
            detail = "只看草书底帖，可看笔顺示意。",
            onClick = { onPick(ScriptStyle.CAO) },
        )
        Text(
            "可在「我的」里改回来。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ScriptChoiceCard(
    title: String,
    detail: String,
    onClick: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(
            Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, fontFamily = FontFamily.Serif, fontSize = 48.sp)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
