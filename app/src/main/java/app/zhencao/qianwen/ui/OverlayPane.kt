package app.zhencao.qianwen.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import app.zhencao.qianwen.AppViewModel
import app.zhencao.qianwen.UserSettings
import app.zhencao.qianwen.data.SvgPaths
import app.zhencao.qianwen.model.CharacterEntry
import app.zhencao.qianwen.model.ScriptStyle
import app.zhencao.qianwen.model.inkBox
import app.zhencao.qianwen.model.iou
import app.zhencao.qianwen.model.shiftLabel
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun OverlayPane(vm: AppViewModel, entry: CharacterEntry, settings: UserSettings) {
    var paper by remember(entry.index) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !paper, onClick = { paper = false }, label = { Text("屏上笔迹") })
            FilterChip(selected = paper, onClick = { paper = true }, label = { Text("纸上临作") })
        }
        if (paper) {
            PaperOverlay(vm, entry, settings, Modifier.weight(1f).fillMaxWidth())
        } else {
            ScreenOverlay(vm, entry, settings, Modifier.weight(1f).fillMaxWidth())
        }
    }
}

@Composable
private fun ScreenOverlay(
    vm: AppViewModel,
    entry: CharacterEntry,
    settings: UserSettings,
    modifier: Modifier,
) {
    val inkMap by vm.ink.collectAsState()
    val strokes = inkMap[entry.index].orEmpty()
    var opacity by remember(entry.index) { mutableFloatStateOf(0.55f) }
    val script = settings.script ?: ScriptStyle.CAO
    val palette = glyphPalette(settings.edition, settings.invert)
    val modelPhoto = rememberGlyphBitmap(entry.glyphAsset(settings.edition, script))
    LaunchedEffect(strokes.isNotEmpty()) {
        if (strokes.isNotEmpty()) vm.markOverlay(entry.index)
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth().background(palette.paper)) {
            Canvas(Modifier.fillMaxSize()) {
                drawGrid(settings.grid, palette.grid)
                drawScriptModel(entry, script, settings.edition, palette.ink, modelPhoto, settings.invert)
                drawInk(strokes, emptyList(), palette.ink.copy(alpha = opacity))
            }
        }
        Text("笔迹透明度 ${(opacity * 100).roundToInt()}%")
        Slider(value = opacity, onValueChange = { opacity = it })
        if (script == ScriptStyle.CAO) {
            StructureHint(entry, strokes)
        }
    }
}

@Composable
private fun StructureHint(entry: CharacterEntry, strokes: List<app.zhencao.qianwen.model.InkStroke>) {
    val model = SvgPaths.modelBox(entry.caoStrokes)
    val user = inkBox(strokes)
    if (model == null || user == null) {
        Text(
            "先在临写里留下笔迹，再看外框重合和重心偏移。",
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }
    val overlap = (iou(model, user) * 100).roundToInt()
    Text("外框重合 $overlap%", style = MaterialTheme.typography.titleMedium)
    Text(
        "重心${shiftLabel(user.centerX - model.centerX, user.centerY - model.centerY)}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        "这是结构提示，不是评分。牵丝和收笔请自己看。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PaperOverlay(
    vm: AppViewModel,
    entry: CharacterEntry,
    settings: UserSettings,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val photos by vm.photos.collectAsState()
    val uriText = photos[entry.index]
    var bitmap by remember(uriText) { mutableStateOf<ImageBitmap?>(null) }
    var message by remember(entry.index) { mutableStateOf<String?>(null) }
    var opacity by remember(entry.index) { mutableFloatStateOf(0.5f) }
    var quad by remember(entry.index) { mutableStateOf<List<Offset>?>(null) }
    var canvasSize by remember(entry.index) { mutableStateOf(IntSize.Zero) }
    val script = settings.script ?: ScriptStyle.CAO
    val palette = glyphPalette(settings.edition, settings.invert)
    val modelPhoto = rememberGlyphBitmap(entry.glyphAsset(settings.edition, script))
    val pendingUri = remember { arrayOfNulls<Uri>(1) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = pendingUri[0]
        if (saved && uri != null) {
            vm.setPhoto(entry.index, uri.toString())
            message = null
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            message = "没有相机权限，可以从相册选一张临作。"
            return@rememberLauncherForActivityResult
        }
        val uri = captureUri(context)
        pendingUri[0] = uri
        takePicture.launch(uri)
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            vm.setPhoto(entry.index, uri.toString())
            message = null
        }
    }

    LaunchedEffect(uriText) {
        bitmap = null
        quad = null
        if (uriText == null) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) {
            runCatching { decodePracticePhoto(context, Uri.parse(uriText)) }.getOrNull()
        }
        bitmap = loaded
        if (loaded != null) vm.markOverlay(entry.index) else message = "这张图没有读出来。"
    }
    LaunchedEffect(bitmap, canvasSize) {
        if (bitmap != null && canvasSize != IntSize.Zero && quad == null) {
            quad = defaultQuad(canvasSize.width.toFloat(), canvasSize.height.toFloat())
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "把纸上的字拖到格子上，调透明度对照。四角可以单独拉。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(palette.paper)
                .onSizeChanged { canvasSize = it }
                .pointerInput(bitmap, canvasSize) {
                    val image = bitmap ?: return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val current = quad ?: defaultQuad(size.width.toFloat(), size.height.toFloat())
                        val handle = hitHandle(current, down.position, 28.dp.toPx()) ?: return@awaitEachGesture
                        down.consume()
                        var latest = current
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            latest = latest.mapIndexed { index, point ->
                                if (index == handle) {
                                    Offset(
                                        change.position.x.coerceIn(0f, size.width.toFloat()),
                                        change.position.y.coerceIn(0f, size.height.toFloat()),
                                    )
                                } else {
                                    point
                                }
                            }
                            quad = latest
                            change.consume()
                            if (!change.pressed) break
                        }
                    }
                },
        ) {
            val image = bitmap
            Canvas(Modifier.fillMaxSize()) {
                drawGrid(settings.grid, palette.grid)
                drawScriptModel(entry, script, settings.edition, palette.ink, modelPhoto, settings.invert)
                val corners = quad
                if (image != null && corners != null) {
                    drawWarped(image, corners, opacity)
                    corners.forEach { corner ->
                        drawCircle(palette.ink, radius = 14f, center = corner)
                        drawCircle(palette.paper, radius = 8f, center = corner)
                    }
                }
            }
        }
        if (bitmap != null) {
            Text("临作透明度 ${(opacity * 100).roundToInt()}%")
            Slider(value = opacity, onValueChange = { opacity = it })
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) { Text("相册") }
            TextButton(onClick = {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.CAMERA,
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (granted) {
                    val uri = captureUri(context)
                    pendingUri[0] = uri
                    takePicture.launch(uri)
                } else {
                    permission.launch(android.Manifest.permission.CAMERA)
                }
            }) { Text("拍照") }
            TextButton(
                onClick = { quad = defaultQuad(canvasSize.width.toFloat(), canvasSize.height.toFloat()) },
                enabled = bitmap != null && canvasSize != IntSize.Zero,
            ) { Text("重置对齐") }
        }
    }
}

private fun defaultQuad(width: Float, height: Float): List<Offset> {
    val insetX = width * 0.14f
    val insetY = height * 0.14f
    return listOf(
        Offset(insetX, insetY),
        Offset(width - insetX, insetY),
        Offset(width - insetX, height - insetY),
        Offset(insetX, height - insetY),
    )
}

private fun hitHandle(quad: List<Offset>, point: Offset, radius: Float): Int? {
    return quad.indices.minByOrNull { (quad[it] - point).getDistance() }
        ?.takeIf { (quad[it] - point).getDistance() <= radius }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWarped(
    image: ImageBitmap,
    quad: List<Offset>,
    opacity: Float,
) {
    val bitmap = image.asAndroidBitmap()
    val matrix = Matrix()
    val source = floatArrayOf(
        0f, 0f,
        bitmap.width.toFloat(), 0f,
        bitmap.width.toFloat(), bitmap.height.toFloat(),
        0f, bitmap.height.toFloat(),
    )
    val destination = floatArrayOf(
        quad[0].x, quad[0].y,
        quad[1].x, quad[1].y,
        quad[2].x, quad[2].y,
        quad[3].x, quad[3].y,
    )
    if (!matrix.setPolyToPoly(source, 0, destination, 0, 4)) return
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        alpha = (opacity.coerceIn(0.05f, 1f) * 255).roundToInt()
    }
    drawContext.canvas.nativeCanvas.drawBitmap(bitmap, matrix, paint)
}

private fun captureUri(context: android.content.Context): Uri {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(dir, "practice-${System.currentTimeMillis()}.jpg")
    if (!file.exists()) file.createNewFile()
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

private fun decodePracticePhoto(context: android.content.Context, uri: Uri): ImageBitmap? {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
    val decoded = BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sample },
    ) ?: return null
    val orientation = ExifInterface(bytes.inputStream()).getAttributeInt(
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.ORIENTATION_NORMAL,
    )
    val degrees = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> return decoded.asImageBitmap()
    }
    val matrix = Matrix().apply { postRotate(degrees) }
    val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    return rotated.asImageBitmap()
}
