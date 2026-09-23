package app.zhencao.qianwen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import app.zhencao.qianwen.ui.QianwenApp
import app.zhencao.qianwen.ui.theme.QianwenTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: AppViewModel = viewModel(
                factory = AppViewModelFactory(application as QianwenApplication),
            )
            QianwenTheme {
                QianwenApp(viewModel)
            }
        }
    }
}
