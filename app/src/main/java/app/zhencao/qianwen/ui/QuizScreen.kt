package app.zhencao.qianwen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import app.zhencao.qianwen.AppViewModel
import app.zhencao.qianwen.model.QuizKind
import app.zhencao.qianwen.model.ScriptStyle
import app.zhencao.qianwen.ui.theme.Moss
import app.zhencao.qianwen.ui.theme.PaperDeep

@Composable
fun QuizScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val quiz by vm.quiz.collectAsState()
    val home by vm.home.collectAsState()
    val script = home.settings.script ?: ScriptStyle.CAO
    val question = quiz.question
    Column(
        modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("测验", style = MaterialTheme.typography.headlineMedium)
        Text(
            "本轮 ${quiz.sessionCorrect}/${quiz.sessionTotal}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = quiz.kind == QuizKind.RECOGNIZE_CAO,
                onClick = { vm.setQuizKind(QuizKind.RECOGNIZE_CAO) },
                label = { Text("认${script.label}") },
            )
            FilterChip(
                selected = quiz.kind == QuizKind.PICK_CAO,
                onClick = { vm.setQuizKind(QuizKind.PICK_CAO) },
                label = { Text("选${script.label}") },
            )
        }
        if (question == null) {
            Text("字还不够出题。")
            return
        }
        val answer = vm.corpus[question.answerIndex]
        val edition = home.settings.edition
        Text(
            if (quiz.kind == QuizKind.RECOGNIZE_CAO) {
                "这是哪个字的${script.bookLabel}？"
            } else {
                "哪个是「${answer.char}」的${script.bookLabel}？"
            },
            style = MaterialTheme.typography.titleMedium,
        )
        val palette = glyphPalette(edition, invert = false)
        if (quiz.kind == QuizKind.RECOGNIZE_CAO) {
            val photo = rememberGlyphBitmap(answer.glyphAsset(edition, script))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                GlyphFrame(
                    palette,
                    app.zhencao.qianwen.model.GridType.NONE,
                    "",
                    Modifier.size(184.dp).clip(RoundedCornerShape(12.dp)),
                ) {
                    drawScriptModel(answer, script, edition, palette.ink, photo, invert = false)
                }
            }
        } else {
            Box(
                Modifier.fillMaxWidth().height(140.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(answer.char, fontFamily = FontFamily.Serif, fontSize = 72.sp)
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            items(question.options, key = { it }) { optionIndex ->
                val option = vm.corpus[optionIndex]
                val correct = optionIndex == question.answerIndex
                val picked = quiz.pickedIndex == optionIndex
                val border = when {
                    !quiz.revealed -> Color.Transparent
                    correct -> Moss
                    picked -> MaterialTheme.colorScheme.primary
                    else -> Color.Transparent
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.5.dp, border, RoundedCornerShape(12.dp))
                        .background(PaperDeep)
                        .clickable(enabled = !quiz.revealed) { vm.answer(optionIndex) }
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (quiz.kind == QuizKind.RECOGNIZE_CAO) {
                        Text(
                            option.char,
                            fontFamily = FontFamily.Serif,
                            fontSize = 32.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    } else {
                        val photo = rememberGlyphBitmap(option.glyphAsset(edition, script))
                        GlyphFrame(
                            palette,
                            app.zhencao.qianwen.model.GridType.NONE,
                            "",
                            Modifier.fillMaxWidth().aspectRatio(1f),
                        ) {
                            drawScriptModel(option, script, edition, palette.ink, photo, invert = false)
                        }
                    }
                }
            }
        }
        if (quiz.revealed) {
            TextButton(onClick = vm::nextQuestion, modifier = Modifier.align(Alignment.End)) {
                Text("下一题")
            }
        }
    }
}
