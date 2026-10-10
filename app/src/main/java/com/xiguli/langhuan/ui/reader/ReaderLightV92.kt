package com.xiguli.langhuan.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import java.io.File
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * Reader V92 · light and paper.
 *
 * - Brightness: a per-reader window brightness override (ReaderWindowSessionV27 applies and
 *   restores it). [READER_BRIGHTNESS_SYSTEM_V92] keeps the system brightness.
 * - Warm light: a translucent amber layer over the reading surface only; the menu sheet keeps
 *   true colours so the theme tiles stay accurate.
 * - Backdrop: a subtle procedural paper texture or a picture from the gallery drawn under the
 *   text of every page (also during page-turn animations, since it is part of the page paint).
 */

internal const val READER_BRIGHTNESS_SYSTEM_V92 = -1f
internal const val READER_MIN_BRIGHTNESS_V92 = 0.05f

/** Background choices. Stored by [key]; unknown keys fall back to [NONE]. */
internal enum class ReaderBackdropV92(val key: String, val label: String) {
    NONE("none", "素色"),
    XUAN("xuan", "宣纸"),
    LINEN("linen", "麻布"),
    GRID("grid", "稿纸"),
    IMAGE("image", "相册图片"),
    ;

    companion object {
        fun of(key: String?): ReaderBackdropV92 = entries.firstOrNull { it.key == key } ?: NONE
    }
}

/** Everything the page painter needs to draw the backdrop; built once per theme/backdrop. */
internal class ReaderBackdropPaintV92(
    val texture: ShaderBrush? = null,
    val image: ImageBitmap? = null,
    /** Page-coloured veil over a picture so body text keeps its contrast. */
    val veil: Color = Color.Transparent,
)

/** Window brightness override value for a stored setting (-1 = follow the system). */
internal fun readerWindowBrightnessV92(stored: Float): Float =
    if (stored < 0f) android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    else stored.coerceIn(READER_MIN_BRIGHTNESS_V92, 1f)

/** Alpha of the amber warm-light layer; dark themes get less so blacks stay deep. */
internal fun readerWarmLightAlphaV92(warmth: Float, dark: Boolean): Float =
    warmth.coerceIn(0f, 1f) * if (dark) 0.20f else 0.30f

internal val READER_WARM_LIGHT_COLOR_V92 = Color(0xFFFF9636)

/** File holding the gallery picture, already downscaled for the screen. */
internal fun readerBackdropImageFileV92(context: Context): File =
    File(context.filesDir, "reader/backdrop_v92.jpg")

/** Center-crop source rectangle that fills [dst] with an image of size [src]. */
internal fun readerCenterCropV92(src: IntSize, dst: Size): Pair<IntOffset, IntSize> {
    if (src.width <= 0 || src.height <= 0 || dst.width <= 0f || dst.height <= 0f) {
        return IntOffset.Zero to src
    }
    val srcRatio = src.width.toFloat() / src.height
    val dstRatio = dst.width / dst.height
    return if (srcRatio > dstRatio) {
        val w = (src.height * dstRatio).roundToInt().coerceIn(1, src.width)
        IntOffset((src.width - w) / 2, 0) to IntSize(w, src.height)
    } else {
        val h = (src.width / dstRatio).roundToInt().coerceIn(1, src.height)
        IntOffset(0, (src.height - h) / 2) to IntSize(src.width, h)
    }
}

/**
 * Draws the backdrop for a full page of [pageWidth] x [pageHeight], clipped to this draw scope.
 * [offsetY] shifts the page up, so the footer slice of scroll mode lines up with the page.
 */
internal fun DrawScope.drawReaderBackdropV92(
    paint: ReaderBackdropPaintV92,
    pageWidth: Float = size.width,
    pageHeight: Float = size.height,
    offsetY: Float = 0f,
) {
    clipRect(0f, 0f, size.width, size.height) {
        translate(0f, -offsetY) {
            val image = paint.image
            if (image != null) {
                val (srcOffset, srcSize) = readerCenterCropV92(
                    IntSize(image.width, image.height), Size(pageWidth, pageHeight),
                )
                drawImage(
                    image = image,
                    srcOffset = srcOffset,
                    srcSize = srcSize,
                    dstSize = IntSize(pageWidth.roundToInt(), pageHeight.roundToInt()),
                )
                if (paint.veil.alpha > 0f) drawRect(paint.veil, size = Size(pageWidth, pageHeight))
            }
            paint.texture?.let { drawRect(brush = it, size = Size(pageWidth, pageHeight)) }
        }
    }
}

/** Builds a seamless texture tile tinted with the theme's text colour. Pure CPU, ~1 ms. */
internal fun readerTextureTileV92(backdrop: ReaderBackdropV92, ink: Color, dark: Boolean): ImageBitmap? {
    val sizePx = when (backdrop) {
        ReaderBackdropV92.XUAN -> 256
        ReaderBackdropV92.LINEN -> 96
        ReaderBackdropV92.GRID -> 64
        else -> return null
    }
    val bitmap = android.graphics.Bitmap.createBitmap(sizePx, sizePx, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    val strength = if (dark) 0.55f else 1f
    fun ink(alpha: Float): Int = ink.copy(alpha = (alpha * strength).coerceIn(0f, 1f)).toArgb()
    val random = Random(backdrop.ordinal * 7919 + 17)
    when (backdrop) {
        ReaderBackdropV92.XUAN -> {
            // Rice paper: fine speckles plus a few long, soft fibres.
            paint.style = android.graphics.Paint.Style.FILL
            repeat(900) {
                paint.color = ink(0.025f + random.nextFloat() * 0.04f)
                canvas.drawCircle(random.nextFloat() * sizePx, random.nextFloat() * sizePx, 0.5f + random.nextFloat() * 0.7f, paint)
            }
            paint.style = android.graphics.Paint.Style.STROKE
            paint.strokeCap = android.graphics.Paint.Cap.ROUND
            repeat(46) {
                paint.color = ink(0.035f + random.nextFloat() * 0.05f)
                paint.strokeWidth = 0.6f + random.nextFloat() * 0.8f
                val x = random.nextFloat() * sizePx
                val y = random.nextFloat() * sizePx
                val length = 8f + random.nextFloat() * 26f
                val angle = random.nextFloat() * Math.PI.toFloat()
                val dx = kotlin.math.cos(angle) * length
                val dy = kotlin.math.sin(angle) * length
                // Draw at every wrapped position so the tile repeats without seams.
                for (ox in intArrayOf(-sizePx, 0, sizePx)) for (oy in intArrayOf(-sizePx, 0, sizePx)) {
                    canvas.drawLine(x + ox, y + oy, x + ox + dx, y + oy + dy, paint)
                }
            }
        }
        ReaderBackdropV92.LINEN -> {
            // Woven cloth: faint crossing threads with uneven weight.
            paint.style = android.graphics.Paint.Style.FILL
            var y = 0
            while (y < sizePx) {
                paint.color = ink(0.018f + random.nextFloat() * 0.035f)
                canvas.drawRect(0f, y.toFloat(), sizePx.toFloat(), y + 1f, paint)
                y += 3
            }
            var x = 1
            while (x < sizePx) {
                paint.color = ink(0.015f + random.nextFloat() * 0.03f)
                canvas.drawRect(x.toFloat(), 0f, x + 1f, sizePx.toFloat(), paint)
                x += 3
            }
        }
        ReaderBackdropV92.GRID -> {
            // Manuscript paper: a light square grid, one line per tile edge.
            paint.style = android.graphics.Paint.Style.FILL
            paint.color = ink(0.07f)
            canvas.drawRect(0f, 0f, sizePx.toFloat(), 1.2f, paint)
            canvas.drawRect(0f, 0f, 1.2f, sizePx.toFloat(), paint)
        }
        ReaderBackdropV92.NONE, ReaderBackdropV92.IMAGE -> Unit
    }
    return bitmap.asImageBitmap()
}

/**
 * The backdrop paint for the current settings and theme, or null for a plain page. The texture
 * tile is cached per theme; the gallery picture is decoded on IO and swaps in when ready.
 */
@Composable
internal fun rememberReaderBackdropPaintV92(settings: ReaderSettingsV30, theme: ReaderThemeV30): ReaderBackdropPaintV92? {
    val context = LocalContext.current
    val backdrop = ReaderBackdropV92.of(settings.backdrop)
    val imageVersion = settings.backdropImageVersion
    val image by produceState<ImageBitmap?>(initialValue = null, backdrop, imageVersion) {
        value = if (backdrop != ReaderBackdropV92.IMAGE) null else withContext(Dispatchers.IO) {
            runCatching {
                val file = readerBackdropImageFileV92(context)
                if (file.exists()) BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() else null
            }.getOrNull()
        }
    }
    return remember(backdrop, theme.key, theme.text, theme.page, image) {
        when (backdrop) {
            ReaderBackdropV92.NONE -> null
            ReaderBackdropV92.IMAGE -> image?.let {
                ReaderBackdropPaintV92(image = it, veil = theme.page.copy(alpha = if (theme.dark) 0.55f else 0.42f))
            }
            else -> readerTextureTileV92(backdrop, theme.text, theme.dark)?.let { tile ->
                ReaderBackdropPaintV92(texture = ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated)))
            }
        }
    }
}

/** Copies and downsizes a picked picture into app storage. Returns false on any failure. */
internal suspend fun importReaderBackdropImageV92(context: Context, uri: Uri, maxEdgePx: Int = 1600): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching false
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdgePx) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: return@runCatching false
            val target = readerBackdropImageFileV92(context)
            target.parentFile?.mkdirs()
            val temp = File(target.parentFile, target.name + ".tmp")
            temp.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, it) }
            bitmap.recycle()
            temp.renameTo(target) || run { temp.copyTo(target, overwrite = true); temp.delete(); true }
        }.getOrDefault(false)
    }

/* -------------------------------------------------------------------------- */
/*                         Settings sheet section (V92)                       */
/* -------------------------------------------------------------------------- */

internal const val READER_LIGHT_SECTION_LABEL_V92 = "亮度与护眼"
internal const val READER_BACKDROP_SECTION_LABEL_V92 = "背景纹理"

@Composable
internal fun ReaderLightAndBackdropSectionV92(settings: ReaderSettingsV30) {
    val t = LocalLanghuanUiTokens.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importError by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            if (importReaderBackdropImageV92(context, uri)) {
                importError = null
                settings.backdropImageVersion = System.currentTimeMillis()
                settings.backdrop = ReaderBackdropV92.IMAGE.key
            } else {
                importError = "无法读取这张图片，请换一张试试"
            }
        }
    }
    val theme = readerThemeV30(settings.theme)

    Text(
        text = READER_LIGHT_SECTION_LABEL_V92,
        style = MaterialTheme.typography.labelLarge,
        color = t.secondaryForeground,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(t.space2))
    val followSystem = settings.brightness < 0f
    ReaderLightSliderRowV92(
        label = "亮度",
        valueLabel = if (followSystem) "系统" else "${(settings.brightness * 100).roundToInt()}%",
        value = if (followSystem) 0.6f else settings.brightness,
        range = READER_MIN_BRIGHTNESS_V92..1f,
        onValue = { settings.brightness = it },
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(t.space2),
    ) {
        ReaderLightChipV92(
            text = "跟随系统亮度",
            selected = followSystem,
            modifier = Modifier.weight(1f),
            onClick = {
                settings.brightness = if (followSystem) 0.6f else READER_BRIGHTNESS_SYSTEM_V92
            },
        )
        ReaderLightChipV92(
            text = if (settings.warmth > 0f) "关闭暖光" else "开启暖光",
            selected = settings.warmth > 0f,
            modifier = Modifier.weight(1f),
            onClick = { settings.warmth = if (settings.warmth > 0f) 0f else 0.4f },
        )
    }
    Spacer(Modifier.height(t.space2))
    ReaderLightSliderRowV92(
        label = "暖光",
        valueLabel = if (settings.warmth <= 0f) "关" else "${(settings.warmth * 100).roundToInt()}%",
        value = settings.warmth,
        range = 0f..1f,
        onValue = { settings.warmth = if (it < 0.03f) 0f else it },
    )
    Text(
        text = "暖光会给正文罩上一层琥珀色，夜里读书更柔和；亮度只在阅读时生效。",
        style = MaterialTheme.typography.bodySmall,
        color = t.mutedForeground,
    )

    Spacer(Modifier.height(t.space5))
    Text(
        text = READER_BACKDROP_SECTION_LABEL_V92,
        style = MaterialTheme.typography.labelLarge,
        color = t.secondaryForeground,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(t.space2))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(t.space2),
    ) {
        ReaderBackdropV92.entries.forEach { option ->
            ReaderBackdropTileV92(
                option = option,
                theme = theme,
                selected = ReaderBackdropV92.of(settings.backdrop) == option,
                modifier = Modifier.weight(1f),
                onClick = {
                    if (option == ReaderBackdropV92.IMAGE &&
                        (!readerBackdropImageFileV92(context).exists() || settings.backdrop == option.key)
                    ) {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    } else {
                        settings.backdrop = option.key
                    }
                },
            )
        }
    }
    Spacer(Modifier.height(t.space2))
    Text(
        text = importError ?: if (settings.backdrop == ReaderBackdropV92.IMAGE.key) {
            "再次点「相册图片」可以更换图片。"
        } else {
            "纹理很淡，不影响正文清晰度。"
        },
        style = MaterialTheme.typography.bodySmall,
        color = if (importError != null) t.destructive else t.mutedForeground,
    )
}

@Composable
private fun ReaderBackdropTileV92(
    option: ReaderBackdropV92,
    theme: ReaderThemeV30,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusMd)
    val tile = remember(option, theme.key) {
        readerTextureTileV92(option, theme.text, theme.dark)?.let {
            ShaderBrush(ImageShader(it, TileMode.Repeated, TileMode.Repeated))
        }
    }
    Column(
        modifier = modifier
            .clip(shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = "背景：${option.label}"
                this.selected = selected
                if (selected) stateDescription = "已选择"
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.9f)
                .clip(shape)
                .background(theme.page)
                .border(width = if (selected) 2.dp else 1.dp, color = if (selected) t.primary else t.border, shape = shape),
            contentAlignment = Alignment.Center,
        ) {
            if (tile != null) Canvas(Modifier.fillMaxSize()) { drawRect(brush = tile) }
            if (option == ReaderBackdropV92.IMAGE) {
                Icon(Icons.Rounded.Image, contentDescription = null, tint = theme.secondary, modifier = Modifier.size(20.dp))
            } else {
                Text("文", color = theme.text, style = MaterialTheme.typography.titleMedium)
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(3.dp)
                        .size(16.dp)
                        .background(color = t.primary, shape = CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = t.card, modifier = Modifier.size(11.dp))
                }
            }
        }
        Spacer(Modifier.height(t.space1))
        Text(
            text = option.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) t.primary else t.secondaryForeground,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ReaderLightChipV92(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusMd)
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(color = if (selected) t.accent else t.card, shape = shape)
            .border(width = 1.dp, color = if (selected) t.primary else t.border, shape = shape)
            .clickable(role = Role.Switch, onClick = onClick)
            .semantics { stateDescription = if (selected) "已开启" else "已关闭" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) t.accentForeground else t.secondaryForeground,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun ReaderLightSliderRowV92(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValue: (Float) -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(54.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = t.secondaryForeground,
        )
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = { raw -> onValue(((raw * 100f).roundToInt() / 100f).coerceIn(range.start, range.endInclusive)) },
            valueRange = range,
            modifier = Modifier.weight(1f).semantics { contentDescription = "$label 调节" },
            colors = SliderDefaults.colors(
                thumbColor = t.primary,
                activeTrackColor = t.primary,
                inactiveTrackColor = t.border,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
        Text(
            text = valueLabel,
            modifier = Modifier.width(58.dp),
            style = MaterialTheme.typography.labelMedium,
            color = t.mutedForeground,
            textAlign = TextAlign.End,
        )
    }
}

/** Warm-light layer for the reading surface. Draw-only: it never takes touches. */
@Composable
internal fun BoxScope.ReaderWarmLightOverlayV92(warmth: Float, dark: Boolean) {
    val alpha = readerWarmLightAlphaV92(warmth, dark)
    if (alpha <= 0f) return
    Canvas(Modifier.matchParentSize()) {
        drawRect(color = READER_WARM_LIGHT_COLOR_V92, topLeft = Offset.Zero, size = size, alpha = alpha)
    }
}
