package app.zhencao.qianwen.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import app.zhencao.qianwen.model.CharacterEntry
import app.zhencao.qianwen.model.ScriptStyle
import java.io.File

const val CROP_SIZE = 512

class PracticeStore(private val context: Context) {
    private val modelCache = object : LinkedHashMap<String, InkMask>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, InkMask>?): Boolean = size > 40
    }

    fun modelMask(entry: CharacterEntry, script: ScriptStyle): InkMask? {
        val asset = entry.glyphAsset(script)
        synchronized(modelCache) { modelCache[asset]?.let { return it } }
        val bitmap = runCatching {
            context.assets.open(asset).use { BitmapFactory.decodeStream(it) }
        }.getOrNull() ?: return null
        val mask = maskOf(bitmap, dropBorder = false, lightInk = entry.usesFallback(script))
        synchronized(modelCache) { modelCache[asset] = mask }
        return mask
    }

    fun saveCrop(crop: Bitmap, charIndex: Int, createdAt: Long): String {
        val relative = "practice/${createdAt}_%03d.jpg".format(charIndex)
        val file = File(context.filesDir, relative)
        file.parentFile?.mkdirs()
        file.outputStream().use { crop.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return relative
    }

    fun loadCrop(relative: String): Bitmap? =
        BitmapFactory.decodeFile(File(context.filesDir, relative).path)

    fun deleteCrop(relative: String) {
        File(context.filesDir, relative).delete()
    }

    fun captureUri(): Uri {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "sheet-${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun decodeSheet(uri: Uri): Bitmap? {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        // A whole sheet needs more pixels than a single glyph; 2400px keeps each character sharp.
        while (bounds.outWidth / sample > 2400 || bounds.outHeight / sample > 2400) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return null
        val degrees = when (
            ExifInterface(bytes.inputStream()).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        ) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return decoded
        }
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    }
}

fun maskOf(bitmap: Bitmap, dropBorder: Boolean, lightInk: Boolean = false): InkMask {
    val scaled = Bitmap.createScaledBitmap(bitmap, MASK_SIZE, MASK_SIZE, true)
    val pixels = IntArray(MASK_SIZE * MASK_SIZE)
    scaled.getPixels(pixels, 0, MASK_SIZE, 0, 0, MASK_SIZE, MASK_SIZE)
    return InkAnalysis.extract(pixels, MASK_SIZE, dropBorder, lightInk)
}

fun SheetTransform.toMatrix(left: Float, top: Float, frame: Float): Matrix = Matrix().apply {
    postScale(scale, scale)
    postRotate(rotation)
    postTranslate(tx, ty)
    postScale(frame, frame)
    postTranslate(left, top)
}

fun renderCrop(sheet: Bitmap, transform: SheetTransform, size: Int = CROP_SIZE): Bitmap {
    val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    canvas.drawColor(android.graphics.Color.WHITE)
    canvas.drawBitmap(
        sheet,
        transform.toMatrix(0f, 0f, size.toFloat()),
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
    )
    return out
}

fun inkBitmap(mask: InkMask, color: Int): Bitmap {
    val pixels = IntArray(mask.ink.size) { if (mask.ink[it]) color else 0 }
    return Bitmap.createBitmap(pixels, mask.size, mask.size, Bitmap.Config.ARGB_8888)
}

fun diffBitmap(cells: Array<DiffCell>, size: Int, shared: Int, missing: Int, extra: Int): Bitmap {
    val pixels = IntArray(cells.size) {
        when (cells[it]) {
            DiffCell.NONE -> 0
            DiffCell.SHARED -> shared
            DiffCell.MISSING -> missing
            DiffCell.EXTRA -> extra
        }
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}
