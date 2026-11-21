package com.kyy.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

/** Свой цвет иконок хранится как "rgb:AARRGGBB" (шестнадцатеричное, с прозрачностью). */
const val CUSTOM_COLOR_PREFIX = "rgb:"

fun parseCustomColor(str: String): Int? {
    if (!str.startsWith(CUSTOM_COLOR_PREFIX)) return null
    return str.removePrefix(CUSTOM_COLOR_PREFIX).toLongOrNull(16)?.toInt()
}

fun customColorString(argb: Int): String = CUSTOM_COLOR_PREFIX + String.format("%08X", argb)

/**
 * Окно выбора любого цвета: колесо оттенка, квадрат насыщенности и яркости, ползунок прозрачности
 * и поле с кодом цвета (#RRGGBB).
 */
@Composable
fun ColorPickerDialog(initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val s = MaterialTheme.colorScheme
    val init = remember {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(initial, hsv)
        hsv
    }
    var hue by remember { mutableFloatStateOf(init[0]) }
    var sat by remember { mutableFloatStateOf(init[1]) }
    var value by remember { mutableFloatStateOf(init[2]) }
    var alpha by remember { mutableFloatStateOf(android.graphics.Color.alpha(initial) / 255f) }
    var hexText by remember { mutableStateOf(String.format("%06X", initial and 0xFFFFFF)) }

    fun rgb() = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))
    fun argb() = android.graphics.Color.HSVToColor((alpha * 255f + 0.5f).toInt(), floatArrayOf(hue, sat, value))
    fun hexOf() = String.format("%06X", rgb() and 0xFFFFFF)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Свой цвет иконок") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                WheelAndSquare(hue, sat, value,
                    onHue = { hue = it; hexText = hexOf() },
                    onSV = { a, b -> sat = a; value = b; hexText = hexOf() })
                Spacer(Modifier.height(14.dp))
                // образец с шашечками, чтобы прозрачность была видна
                Box(Modifier.fillMaxWidth().height(40.dp).clip(rc(12)).border(1.dp, s.outlineVariant, rc(12))) {
                    Canvas(Modifier.fillMaxWidth().height(40.dp)) {
                        val cell = 10.dp.toPx()
                        var y = 0f
                        var row = 0
                        while (y < size.height) {
                            var x = 0f
                            var col = row % 2
                            while (x < size.width) {
                                drawRect(
                                    if (col % 2 == 0) Color(0xFFCCCCCC) else Color(0xFFFFFFFF),
                                    Offset(x, y), Size(cell, cell)
                                )
                                x += cell; col++
                            }
                            y += cell; row++
                        }
                        drawRect(Color(argb()))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Прозрачность", fontSize = 13.sp, color = s.onSurfaceVariant, modifier = Modifier.size(width = 100.dp, height = 20.dp))
                    M3Slider(alpha, { alpha = it }, 0f..1f, Modifier.weight(1f))
                    Text("${(alpha * 100).toInt()}%", fontSize = 13.sp, color = s.onSurfaceVariant)
                }
                OutlinedTextField(
                    value = hexText,
                    onValueChange = { t ->
                        val clean = t.removePrefix("#").uppercase().filter { it in "0123456789ABCDEF" }.take(6)
                        hexText = clean
                        if (clean.length == 6) {
                            val c = 0xFF000000.toInt() or clean.toLong(16).toInt()
                            val hsv = FloatArray(3)
                            android.graphics.Color.colorToHSV(c, hsv)
                            hue = hsv[0]; sat = hsv[1]; value = hsv[2]
                        }
                    },
                    prefix = { Text("#") },
                    label = { Text("Код цвета") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { TextButton(onClick = { onPick(argb()) }) { Text("Готово") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

/** Кольцо оттенка снаружи и квадрат «насыщенность × яркость» внутри. */
@Composable
private fun WheelAndSquare(
    hue: Float, sat: Float, value: Float,
    onHue: (Float) -> Unit, onSV: (Float, Float) -> Unit
) {
    val s = MaterialTheme.colorScheme
    val sizeDp = 240.dp
    var mode by remember { mutableStateOf(0) }   // 1 — кольцо, 2 — квадрат
    var px by remember { mutableFloatStateOf(0f) }

    fun geometry(w: Float = px): Triple<Float, Float, Float> {
        val r = w / 2f
        val ring = r * 0.17f            // толщина кольца
        val inner = r - ring
        return Triple(r, ring, inner)
    }

    fun hit(p: Offset) {
        val (r, ring, inner) = geometry()
        val dx = p.x - r
        val dy = p.y - r
        if (mode == 1) {
            var a = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
            if (a < 0f) a += 360f
            onHue(a)
        } else if (mode == 2) {
            val half = inner * 0.66f
            val side = half * 2f
            val left = r - half
            val top = r - half
            onSV(((p.x - left) / side).coerceIn(0f, 1f), 1f - ((p.y - top) / side).coerceIn(0f, 1f))
        }
    }

    fun pickMode(p: Offset) {
        val (r, ring, inner) = geometry()
        val d = hypot(p.x - r, p.y - r)
        mode = if (d >= inner - ring * 0.2f) 1 else 2
    }

    Box(
        Modifier.size(sizeDp)
            .onSizeChanged { px = it.width.toFloat() }
            .pointerInput(Unit) { detectTapGestures { p -> pickMode(p); hit(p) } }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { p -> pickMode(p); hit(p) },
                    onDrag = { change, _ -> change.consume(); hit(change.position) }
                )
            }
    ) {
        Canvas(Modifier.size(sizeDp)) {
            val (r, ring, inner) = geometry(size.width)
            val center = Offset(r, r)
            // кольцо оттенка
            val hues = (0..360 step 30).map { Color(android.graphics.Color.HSVToColor(floatArrayOf(it.toFloat(), 1f, 1f))) }
            drawCircle(
                brush = Brush.sweepGradient(hues, center),
                radius = r - ring / 2f, center = center, style = Stroke(width = ring)
            )
            // квадрат насыщенности и яркости
            val half = inner * 0.66f
            val side = half * 2f
            val tl = Offset(r - half, r - half)
            val pure = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
            drawRect(
                Brush.horizontalGradient(listOf(Color.White, pure), startX = tl.x, endX = tl.x + side),
                tl, Size(side, side)
            )
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = tl.y, endY = tl.y + side),
                tl, Size(side, side)
            )
            // ручка оттенка
            val ang = Math.toRadians(hue.toDouble())
            val hr = r - ring / 2f
            val hc = Offset(r + hr * kotlin.math.cos(ang).toFloat(), r + hr * kotlin.math.sin(ang).toFloat())
            drawCircle(Color.White, ring * 0.46f, hc, style = Stroke(width = 3.dp.toPx()))
            drawCircle(pure, ring * 0.34f, hc)
            // ручка квадрата
            val sc = Offset(tl.x + sat * side, tl.y + (1f - value) * side)
            val cur = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)))
            drawCircle(Color.White, 11.dp.toPx(), sc, style = Stroke(width = 3.dp.toPx()))
            drawCircle(cur, 8.dp.toPx(), sc)
        }
    }
}
