package com.kyy.launcher

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

private val Greetings = listOf(
    "Привет", "Hello", "Hola", "Bonjour", "Hallo", "Ciao", "Olá", "こんにちは", "안녕하세요", "你好",
    "مرحبا", "Merhaba", "Hej", "नमस्ते", "Γεια σου", "Cześć", "שלום", "Xin chào", "สวัสดี", "Привіт",
    "Salam", "Ahoj", "Sawubona", "Halo"
)

private fun blobShapes(): List<RoundedPolygon> = listOf(
    RoundedPolygon.star(10, 1f, 0.87f, CornerRounding(0.55f)),
    RoundedPolygon.star(4, 1f, 0.6f, CornerRounding(0.5f)),
    RoundedPolygon(6, rounding = CornerRounding(0.25f)),
    RoundedPolygon.star(8, 1f, 0.72f, cornerRounding(0.4f)),
    RoundedPolygon(5, rounding = CornerRounding(0.3f)),
    RoundedPolygon.star(12, 1f, 0.9f, cornerRounding(0.6f)),
    RoundedPolygon.star(6, 1f, 0.65f, CornerRounding(0.5f)),
    RoundedPolygon(3, rounding = CornerRounding(0.4f)),
    RoundedPolygon.star(5, 1f, 0.55f, CornerRounding(0.45f)),
    RoundedPolygon.star(7, 1f, 0.8f, CornerRounding(0.5f))
)

/** Фигура плавно перетекает в следующую, а цвет — в следующий цвет. [shift] разводит фазы двух фигур. */
private fun DrawScope.drawBlob(
    morphs: List<Morph>, colors: List<Color>, t: Float, shift: Int,
    center: Offset, radius: Float, rotation: Float
) {
    val st = t / 4.5f + shift
    val i = floor(st).toInt()
    val f = st - i
    val e = f * f * (3f - 2f * f)
    val morph = morphs[i % morphs.size]
    val color = lerpColor(colors[i % colors.size], colors[(i + 1) % colors.size], e)
    val path = Path()
    var first = true
    for (c in morph.asCubics(e)) {
        if (first) {
            path.moveTo(center.x + c.anchor0X * radius, center.y + c.anchor0Y * radius)
            first = false
        }
        path.cubicTo(
            center.x + c.control0X * radius, center.y + c.control0Y * radius,
            center.x + c.control1X * radius, center.y + c.control1Y * radius,
            center.x + c.anchor1X * radius, center.y + c.anchor1Y * radius
        )
    }
    path.close()
    withTransform({ rotate(rotation, center) }) { drawPath(path, color) }
}

/** Экран первого запуска: плывущие фигуры, меняющие цвет, и «привет» на разных языках. */
@Composable
fun WelcomeScreen(onDone: () -> Unit) {
    val sc = MaterialTheme.colorScheme
    val blobColors = listOf(sc.primaryContainer, sc.tertiaryContainer, sc.secondaryContainer)
    val shapes = remember { blobShapes() }
    val morphs = remember { shapes.indices.map { Morph(shapes[it], shapes[(it + 1) % shapes.size]) } }

    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { n ->
                if (last != 0L) time += (n - last) / 1_000_000_000f
                last = n
            }
        }
    }
    val idx by remember { derivedStateOf { (time / 3.2f).toInt() % Greetings.size } }

    Box(
        Modifier.fillMaxSize().background(sc.surface)
            .pointerInput(Unit) { detectTapGestures { } }   // не пропускаем касания на рабочий стол под приветствием
    ) {
        // Фигуры целиком помещаются в экран: центр всегда отступает от краёв на радиус плюс запас на дрейф
        Canvas(Modifier.fillMaxSize()) {
            val t = time
            val w = size.width
            val h = size.height
            fun cx(frac: Float, r: Float, drift: Float): Float =
                (w * frac + w * drift).coerceIn(r + w * 0.02f, w - r - w * 0.02f)
            val r1 = w * 0.17f
            val r2 = w * 0.36f
            val r3 = w * 0.10f
            drawBlob(
                morphs, blobColors, t, 0,
                Offset(cx(0.70f, r1, 0.04f * sin(t * 0.35f)), h * (0.13f + 0.015f * cos(t * 0.5f))),
                r1, t * 6f
            )
            drawBlob(
                morphs, blobColors, t, 3,
                Offset(cx(0.50f, r2, 0.03f * cos(t * 0.3f)), h * (0.68f + 0.015f * sin(t * 0.4f))),
                r2, -t * 4f
            )
            drawBlob(
                morphs, blobColors, t, 6,
                Offset(cx(0.20f, r3, 0.03f * sin(t * 0.45f)), h * (0.30f + 0.015f * cos(t * 0.35f))),
                r3, t * 9f
            )
        }

        // название сверху
        Row(
            Modifier.align(Alignment.TopCenter).systemBarsPadding().padding(top = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Wipe Launcher", color = sc.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(10.dp))
            Text(
                "beta", color = sc.onSecondaryContainer, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(CircleShape).background(sc.secondaryContainer)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }

        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(top = 96.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Фиксированная область: слова разных письменностей не двигают друг друга и не прыгают по высоте
            Crossfade(
                targetState = idx,
                modifier = Modifier.fillMaxWidth().height(100.dp),
                animationSpec = tween(900),
                label = "hello"
            ) { i ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        Greetings[i], color = sc.onSurface, fontSize = 64.sp, lineHeight = 72.sp,
                        fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, maxLines = 1
                    )
                }
            }
        }

        // Невидимый прогрев: все надписи раскладываются заранее, чтобы системные шрифты
        // для китайского, арабского и др. не подгружались прямо во время анимации
        Column(Modifier.requiredSize(1.dp).graphicsLayer { alpha = 0f }) {
            Greetings.forEach { Text(it, fontSize = 64.sp, lineHeight = 72.sp, maxLines = 1) }
        }

        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().systemBarsPadding().padding(bottom = 28.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.fillMaxWidth(0.72f).height(60.dp).clip(CircleShape).background(sc.primary)
                    .clickable { onDone() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, "Далее", Modifier.size(28.dp), tint = sc.onPrimary)
            }
        }
    }
}
