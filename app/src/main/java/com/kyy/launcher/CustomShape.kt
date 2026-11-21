package com.kyy.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Нарисованная пользователем форма иконки. Хранится как замкнутый контур из ~48 точек в долях стороны (0..1),
 * в настройках — строкой «x,y;x,y;…» в промилле. Между точками рисуется гладкая кривая (Catmull-Rom → кубические Безье).
 */
object CustomShapes {
    private const val POINTS = 48

    fun parse(s: String): List<Offset>? {
        if (s.isBlank()) return null
        return try {
            val l = s.split(";").filter { it.isNotBlank() }.map {
                val (x, y) = it.split(",")
                Offset(x.toInt() / 1000f, y.toInt() / 1000f)
            }
            if (l.size >= 3) l else null
        } catch (_: Throwable) { null }
    }

    fun serialize(p: List<Offset>): String =
        p.joinToString(";") { "${(it.x * 1000).roundToInt()},${(it.y * 1000).roundToInt()}" }

    /**
     * Из линии, проведённой пальцем, делает ровный замкнутый контур: замыкает, равномерно размечает по длине,
     * слегка сглаживает дрожь и растягивает так, чтобы форма заполнила весь квадрат иконки (пропорции сохраняются).
     * null — если нарисовано слишком мало или получилась почти прямая.
     */
    fun fit(raw: List<Offset>): List<Offset>? {
        if (raw.size < 8) return null
        val minX0 = raw.minOf { it.x }; val maxX0 = raw.maxOf { it.x }
        val minY0 = raw.minOf { it.y }; val maxY0 = raw.maxOf { it.y }
        val big = max(maxX0 - minX0, maxY0 - minY0)
        val small = min(maxX0 - minX0, maxY0 - minY0)
        if (big < 40f || small < big * 0.1f) return null

        // замыкаем и считаем длину по пути
        val line = raw + raw.first()
        val cum = FloatArray(line.size)
        for (i in 1 until line.size) cum[i] = cum[i - 1] + hypot(line[i].x - line[i - 1].x, line[i].y - line[i - 1].y)
        val total = cum.last()
        if (total <= 0f) return null

        // равномерная разметка по длине
        var seg = 1
        var pts = List(POINTS) { i ->
            val t = total * i / POINTS
            while (seg < line.size - 1 && cum[seg] < t) seg++
            val len = cum[seg] - cum[seg - 1]
            val f = if (len > 0f) ((t - cum[seg - 1]) / len).coerceIn(0f, 1f) else 0f
            Offset(line[seg - 1].x + (line[seg].x - line[seg - 1].x) * f, line[seg - 1].y + (line[seg].y - line[seg - 1].y) * f)
        }
        // сглаживание дрожи пальца: два прохода [1,2,1]/4 по кольцу
        repeat(2) {
            val src = pts
            pts = List(POINTS) { i ->
                val a = src[(i - 1 + POINTS) % POINTS]; val b = src[i]; val c = src[(i + 1) % POINTS]
                Offset((a.x + 2 * b.x + c.x) / 4f, (a.y + 2 * b.y + c.y) / 4f)
            }
        }
        // вписываем в единичный квадрат
        val minX = pts.minOf { it.x }; val maxX = pts.maxOf { it.x }
        val minY = pts.minOf { it.y }; val maxY = pts.maxOf { it.y }
        val w = maxX - minX; val h = maxY - minY
        val k = max(w, h)
        if (k <= 0f) return null
        return pts.map { Offset((it.x - minX) / k + (1f - w / k) / 2f, (it.y - minY) / k + (1f - h / k) / 2f) }
    }

    fun path(p: List<Offset>, w: Float, h: Float): Path {
        val path = Path()
        val n = p.size
        fun pt(i: Int) = p[((i % n) + n) % n]
        path.moveTo(pt(0).x * w, pt(0).y * h)
        for (i in 0 until n) {
            val p0 = pt(i - 1); val p1 = pt(i); val p2 = pt(i + 1); val p3 = pt(i + 2)
            val c1 = p1 + (p2 - p0) / 6f
            val c2 = p2 - (p3 - p1) / 6f
            path.cubicTo(c1.x * w, c1.y * h, c2.x * w, c2.y * h, p2.x * w, p2.y * h)
        }
        path.close()
        return path
    }
}

class SmoothPathShape(private val pts: List<Offset>) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(CustomShapes.path(pts, size.width, size.height))
}

private var customKey = ""
private var customShape: Shape = CircleShape

/** Своя форма из настроек; пока ничего не нарисовано — круг. Разбор строки запоминается до её смены. */
@Synchronized
fun customShapeOf(str: String): Shape {
    if (str != customKey) {
        customKey = str
        customShape = CustomShapes.parse(str)?.let { SmoothPathShape(it) } ?: CircleShape
    }
    return customShape
}

/** Окно, где форму иконки рисуют пальцем. Контур замыкается сам, форму можно перерисовать сколько угодно раз. */
@Composable
fun ShapeDrawDialog(onDismiss: () -> Unit) {
    val s = MaterialTheme.colorScheme
    val raw = remember { mutableStateListOf<Offset>() }
    var fitted by remember { mutableStateOf(CustomShapes.parse(prefs.customShape)) }
    var tooSmall by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = rc(28), color = s.surfaceContainerHigh) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Своя форма иконки", style = MaterialTheme.typography.titleLarge, color = s.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (tooSmall) "Получилось слишком мало или почти прямая линия. Обведите фигуру покрупнее."
                    else "Обведите контур пальцем: линия сама замкнётся и сгладится. Форма растянется на весь размер иконки.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (tooSmall) s.error else s.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                val primary = s.primary
                val guide = s.outlineVariant
                Canvas(
                    Modifier.fillMaxWidth().aspectRatio(1f).clip(rc(24)).background(s.surfaceContainerLow)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { o -> raw.clear(); raw.add(o); fitted = null; tooSmall = false },
                                onDrag = { change, _ -> change.consume(); raw.add(change.position) },
                                onDragEnd = {
                                    fitted = CustomShapes.fit(raw.toList())
                                    tooSmall = fitted == null
                                    raw.clear()
                                },
                                onDragCancel = { raw.clear() }
                            )
                        }
                ) {
                    // направляющие: центр и рамка, в которую форма впишется
                    val inset = size.width * 0.1f
                    drawLine(guide, Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), 1.dp.toPx())
                    drawLine(guide, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), 1.dp.toPx())
                    val f = fitted
                    if (f != null) {
                        val side = size.width - inset * 2f
                        val p = CustomShapes.path(f, side, side)
                        p.translate(Offset(inset, inset))
                        drawPath(p, primary, style = Fill)
                    }
                    if (raw.size > 1) {
                        val line = Path().apply {
                            moveTo(raw[0].x, raw[0].y)
                            for (i in 1 until raw.size) lineTo(raw[i].x, raw[i].y)
                        }
                        drawPath(line, primary, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val f = fitted
                    Box(
                        Modifier.size(56.dp).clip(if (f != null) SmoothPathShape(f) else CircleShape)
                            .background(if (f != null) s.primaryContainer else s.surfaceContainerHighest),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Star, null, tint = if (f != null) s.onPrimaryContainer else s.onSurfaceVariant)
                    }
                    Text("Так будет выглядеть иконка", style = MaterialTheme.typography.bodySmall, color = s.onSurfaceVariant)
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { raw.clear(); fitted = null; tooSmall = false }) { Text("Очистить") }
                    TextButton(onClick = onDismiss) { Text("Отмена") }
                    Button(
                        enabled = fitted != null,
                        onClick = {
                            fitted?.let {
                                prefs.customShape = CustomShapes.serialize(it)
                                prefs.iconShape = "custom"
                            }
                            onDismiss()
                        }
                    ) { Text("Готово") }
                }
            }
        }
    }
}
