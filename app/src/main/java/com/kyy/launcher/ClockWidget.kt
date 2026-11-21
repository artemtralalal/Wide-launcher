package com.kyy.launcher

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin

/** Настройки встроенных часов: форма и вид хранятся отдельно для каждого виджета (id отрицательные): id:форма:вид;... */
object ClockCfg {
    private class Cfg(val shape: String, val style: String)

    private fun map(): Map<Int, Cfg> = prefs.clockShapes.split(";").mapNotNull {
        val p = it.split(":")
        val id = p.getOrNull(0)?.toIntOrNull()
        val sh = p.getOrNull(1)
        if (id != null && !sh.isNullOrEmpty()) id to Cfg(sh, p.getOrNull(2)?.ifEmpty { null } ?: "analog") else null
    }.toMap()

    private fun save(m: Map<Int, Cfg>) {
        prefs.clockShapes = m.entries.joinToString(";") { "${it.key}:${it.value.shape}:${it.value.style}" }
    }

    fun shape(id: Int): String = map()[id]?.shape ?: "circle"
    fun style(id: Int): String = map()[id]?.style ?: "analog"

    fun setShape(id: Int, shape: String) { save(map().toMutableMap().also { it[id] = Cfg(shape, style(id)) }) }
    fun setStyle(id: Int, style: String) { save(map().toMutableMap().also { it[id] = Cfg(shape(id), style) }) }
    fun remove(id: Int) { save(map().filter { it.key != id }) }
}

val ClockStyles = listOf("analog" to "Стрелки", "stack" to "Столбиком", "line" to "Строка")

/**
 * Аналоговые часы Material You, как виджет на Google Pixel: плашка цвета темы любой формы,
 * крупные цифры 12, 3, 6, 9, толстые часовая и минутная стрелки и тонкая секундная.
 */
@Composable
fun ClockFace(shapeName: String, style: String, modifier: Modifier = Modifier) {
    val s = MaterialTheme.colorScheme
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000L - now % 1000L)
        }
    }
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val sec = cal.get(Calendar.SECOND)
    val min = cal.get(Calendar.MINUTE) + sec / 60f
    val hour = (cal.get(Calendar.HOUR) % 12) + min / 60f
    // секундная стрелка: плавный ход; через 360° и после долгой паузы (экран был выключен) не раскручивается
    val secAnim = remember { Animatable(sec * 6f) }
    LaunchedEffect(now / 1000L) {
        val t = sec * 6f
        when {
            t == 0f && secAnim.value > 300f -> { secAnim.animateTo(360f, tween(300, easing = FastOutSlowInEasing)); secAnim.snapTo(0f) }
            kotlin.math.abs(secAnim.value - t) > 30f -> secAnim.snapTo(t)
            else -> secAnim.animateTo(t, tween(300, easing = FastOutSlowInEasing))
        }
    }
    val secAngle = secAnim.value
    val tm = rememberTextMeasurer()
    val fontFamily = MaterialTheme.typography.headlineLarge.fontFamily
    val plate = s.primaryContainer
    val digits = s.primary.copy(alpha = .38f)
    val hands = s.primary
    val secColor = s.onSurface

    if (style == "stack" || style == "line") {
        DigitalClock(shapeName, style, cal, now, plate, modifier)
        return
    }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight)
        Box(Modifier.size(side).clip(shapeOf(shapeName)).background(plate)) {
            Canvas(Modifier.fillMaxSize()) {
                val d = size.minDimension
                val c = Offset(size.width / 2f, size.height / 2f)
                val fontPx = d * 0.32f
                val style = TextStyle(
                    color = digits, fontSize = fontPx.toSp(), fontWeight = FontWeight.Black,
                    fontFamily = fontFamily, textAlign = TextAlign.Center
                )
                val r = d * 0.30f
                listOf("12" to Offset(0f, -r), "3" to Offset(r, 0f), "6" to Offset(0f, r), "9" to Offset(-r, 0f)).forEach { (t, o) ->
                    val m = tm.measure(AnnotatedString(t), style)
                    val cx = c.x + o.x
                    val cy = c.y + o.y
                    // по вертикали центрируем высоту заглавных, а не всю строку
                    val baseline = cy + fontPx * 0.36f
                    drawText(m, topLeft = Offset(cx - m.size.width / 2f, baseline - m.firstBaseline))
                }
                fun hand(deg: Float, from: Float, len: Float, w: Float, color: androidx.compose.ui.graphics.Color) {
                    val rad = Math.toRadians(deg.toDouble())
                    val dx = sin(rad).toFloat()
                    val dy = -cos(rad).toFloat()
                    drawLine(color, Offset(c.x + dx * from, c.y + dy * from), Offset(c.x + dx * len, c.y + dy * len), w, StrokeCap.Round)
                }
                hand(hour * 30f, 0f, d * 0.22f, d * 0.095f, hands)
                hand(min * 6f, 0f, d * 0.37f, d * 0.075f, hands)
                hand(secAngle, -d * 0.09f, d * 0.40f, d * 0.016f, secColor)
                drawCircle(hands, d * 0.045f, c)
                drawCircle(secColor, d * 0.018f, c)
            }
        }
    }
}

/** Цифровые часы: «столбиком» (часы над минутами, как на экране блокировки Pixel) и «строка» (время и дата). */
@Composable
private fun DigitalClock(
    shapeName: String, style: String, cal: Calendar, now: Long,
    plate: androidx.compose.ui.graphics.Color, modifier: Modifier
) {
    val s = MaterialTheme.colorScheme
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val h24 = android.text.format.DateFormat.is24HourFormat(ctx)
    val hh = if (h24) cal.get(Calendar.HOUR_OF_DAY) else (cal.get(Calendar.HOUR).let { if (it == 0) 12 else it })
    val hs = "%02d".format(hh)
    val ms = "%02d".format(cal.get(Calendar.MINUTE))
    val fam = MaterialTheme.typography.headlineLarge.fontFamily
    val day = remember(now / 60000L) {
        java.text.SimpleDateFormat("EEE, d MMMM", java.util.Locale.getDefault()).format(java.util.Date(now))
    }
    fun tight(fs: androidx.compose.ui.unit.TextUnit, color: androidx.compose.ui.graphics.Color) = TextStyle(
        color = color, fontSize = fs, fontWeight = FontWeight.Black, fontFamily = fam, textAlign = TextAlign.Center,
        lineHeight = (fs.value * 0.88f).sp,
        lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
            androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
            androidx.compose.ui.text.style.LineHeightStyle.Trim.Both
        )
    )
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        if (style == "stack") {
            val side = minOf(maxWidth, maxHeight)
            val fs = (side.value * 0.40f).sp
            Box(Modifier.size(side).clip(shapeOf(shapeName)).background(plate), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(hs, style = tight(fs, s.primary))
                    Text(ms, style = tight(fs, s.onPrimaryContainer.copy(alpha = .55f)))
                }
            }
        } else {
            val fs = minOf(maxHeight.value * 0.46f, maxWidth.value * 0.23f).sp
            Box(Modifier.fillMaxSize().clip(shapeOf(shapeName)).background(plate), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$hs:$ms", style = tight(fs, s.primary))
                    Spacer(Modifier.height(4.dp))
                    Text(day, fontSize = (fs.value * 0.26f).coerceIn(11f, 18f).sp, color = s.onPrimaryContainer, maxLines = 1)
                }
            }
        }
    }
}

/** Окно настройки часов: вид (стрелки / столбиком / строка) и форма. */
@Composable
fun ClockStyleDialog(
    shape: String, style: String, onShape: (String) -> Unit, onStyle: (String) -> Unit, onClose: () -> Unit
) {
    val s = MaterialTheme.colorScheme
    val shapes = ShapeNames.filter { it.first != "custom" || prefs.customShape.isNotEmpty() }
    Dialog(onDismissRequest = onClose) {
        Surface(shape = rc(28), color = s.surfaceContainerHigh) {
            Column(Modifier.padding(20.dp)) {
                Text("Вид часов", style = MaterialTheme.typography.headlineSmall, color = s.onSurface)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ClockStyles.forEach { (id, name) ->
                        val on = id == style
                        Text(
                            name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                            color = if (on) s.onPrimary else s.onSurfaceVariant,
                            modifier = Modifier.weight(1f).clip(rc(50)).background(if (on) s.primary else s.surfaceContainerHighest)
                                .clickable { onStyle(id) }.padding(vertical = 10.dp)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Форма", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = s.primary)
                Spacer(Modifier.height(8.dp))
                LazyVerticalGrid(
                    GridCells.Fixed(4), Modifier.heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(shapes) { (id, name) ->
                        Column(
                            Modifier.clip(rc(16)).clickable { onShape(id) }.padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                Modifier.size(56.dp).clip(shapeOf(id))
                                    .background(if (id == shape) s.primary else s.primaryContainer)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(name, fontSize = 11.sp, maxLines = 1, color = s.onSurfaceVariant, textAlign = TextAlign.Center)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onClose) { Text("Готово") }
                }
            }
        }
    }
}
