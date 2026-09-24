package app.zhencao.qianwen.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import app.zhencao.qianwen.AppViewModel
import kotlinx.coroutines.launch

class SheetPicker(
    val camera: () -> Unit,
    val gallery: () -> Unit,
    val message: String?,
)

/** Camera or gallery into [AppViewModel.openSheet]; calls [onReady] once the sheet is decoded. */
@Composable
fun rememberSheetPicker(vm: AppViewModel, onReady: () -> Unit): SheetPicker {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ready by rememberUpdatedState(onReady)
    var message by remember { mutableStateOf<String?>(null) }
    val pending = remember { arrayOfNulls<Uri>(1) }

    fun open(uri: Uri) {
        scope.launch {
            if (vm.openSheet(uri)) {
                message = null
                ready()
            } else {
                message = "这张照片没有读出来。"
            }
        }
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = pending[0]
        if (saved && uri != null) open(uri)
    }

    fun launchCamera() {
        val uri = vm.captureUri()
        pending[0] = uri
        takePicture.launch(uri)
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else message = "没有相机权限。"
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) open(uri)
    }
    return SheetPicker(
        camera = {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
            if (granted) launchCamera() else permission.launch(Manifest.permission.CAMERA)
        },
        gallery = {
            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        message = message,
    )
}
