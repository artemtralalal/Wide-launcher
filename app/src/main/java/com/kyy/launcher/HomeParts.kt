package com.kyy.launcher

import android.appwidget.AppWidgetHostView
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/** Положение и размер виджета на главном экране, в dp. */
class WRect(val x: Float, val y: Float, val w: Float, val h: Float)

private const val MIN_W = 56f

@Composable
fun PlacedWidget(a: MainActivity, id: Int, r: WRect, maxW: Float, maxH: Float) {
    val s = MaterialTheme.colorScheme
    val dens = LocalDensity.current.density
    val haptic = LocalHapticFeedback.current
    // id < 0 — встроенный виджет (часы), у него нет системного провайдера
    val info = if (id >= 0) (a.wm.getAppWidgetInfo(id) ?: return) else null
    var shapeDlg by remember { mutableStateOf(false) }
    val editing = a.editWidget.intValue == id
    val mw by rememberUpdatedState(maxW)
    val mh by rememberUpdatedState(maxH)

    val move: (Float, Float) -> Unit = { dx, dy ->
        a.rects[id]?.let { c ->
            a.rects[id] = WRect(
                (c.x + dx).coerceIn(0f, maxOf(0f, mw - c.w)),
                (c.y + dy).coerceIn(0f, maxOf(0f, mh - c.h)), c.w, c.h
            )
        }
    }
    // l/t — сдвиг левого/верхнего края, rr/b — правого/нижнего (dp)
    val resize: (Float, Float, Float, Float) -> Unit = { l, t, rr, b ->
        a.rects[id]?.let { c ->
            var x0 = c.x + l; var y0 = c.y + t
            var x1 = c.x + c.w + rr; var y1 = c.y + c.h + b
            if (l != 0f) x0 = x0.coerceIn(0f, maxOf(0f, x1 - MIN_W)) else x1 = x1.coerceIn(x0 + MIN_W, maxOf(x0 + MIN_W, mw))
            if (t != 0f) y0 = y0.coerceIn(0f, maxOf(0f, y1 - MIN_W)) else y1 = y1.coerceIn(y0 + MIN_W, maxOf(y0 + MIN_W, mh))
            a.rects[id] = WRect(x0, y0, x1 - x0, y1 - y0)
        }
    }

    Box(
        Modifier
            .offset { IntOffset((r.x * dens).roundToInt(), (r.y * dens).roundToInt()) }
            .size(r.w.dp, r.h.dp)
            // долгое нажатие ловим до самого виджета (обычные нажатия по виджету работают как раньше)
            .pointerInput(id) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (a.editWidget.intValue == id) return@awaitEachGesture
                    val slop = viewConfiguration.touchSlop
                    val res = withTimeoutOrNull(350L) {
                        var go = true
                        while (go) {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            val c = ev.changes.firstOrNull { it.id == down.id }
                            if (c == null || !c.pressed || (c.position - down.position).getDistance() > slop) go = false
                        }
                        true
                    }
                    if (res == null) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        a.editWidget.intValue = id
                        // можно сразу тащить, не отпуская палец
                        var pressed = true
                        while (pressed) {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            val c = ev.changes.firstOrNull { it.id == down.id }
                            ev.changes.forEach { it.consume() }
                            if (c == null || !c.pressed) pressed = false
                            else {
                                val d = c.position - c.previousPosition
                                move(d.x / dens, d.y / dens)
                            }
                        }
                        a.saveRects()
                    }
                }
            }
    ) {
        if (info != null) AndroidView(
            factory = { c -> a.host.createView(c, id, info) as AppWidgetHostView },
            update = { v ->
                @Suppress("DEPRECATION")
                v.updateAppWidgetSize(null, r.w.toInt(), r.h.toInt(), r.w.toInt(), r.h.toInt())
            },
            modifier = Modifier.fillMaxSize()
        ) else {
            ClockFace(ClockCfg.shape(id), ClockCfg.style(id), Modifier.fillMaxSize())
            if (shapeDlg) ClockStyleDialog(
                ClockCfg.shape(id), ClockCfg.style(id),
                { ClockCfg.setShape(id, it) },
                { st ->
                    ClockCfg.setStyle(id, st)
                    // «Строка» вытянута в ширину, остальные виды квадратные: подгоняем размер виджета
                    a.rects[id]?.let { c ->
                        if (st == "line") {
                            if (ClockCfg.shape(id) == "circle") ClockCfg.setShape(id, "rounded")
                            val w = minOf(maxOf(c.w, 240f), mw)
                            a.rects[id] = WRect(c.x.coerceAtMost(maxOf(0f, mw - w)), c.y, w, 110f)
                        } else if (c.h < 150f || c.w < 150f) {
                            a.rects[id] = WRect(c.x.coerceAtMost(maxOf(0f, mw - 180f)), c.y.coerceAtMost(maxOf(0f, mh - 180f)), 180f, 180f)
                        }
                        a.saveRects()
                    }
                }
            ) { shapeDlg = false }
        }
        if (editing) {
            val shape = rc(14)
            Box(
                Modifier.fillMaxSize()
                    .background(s.primary.copy(alpha = .14f), shape)
                    .border(2.dp, s.primary, shape)
                    .pointerInput(id) {
                        detectDragGestures(onDragEnd = { a.saveRects() }) { change, drag ->
                            change.consume()
                            move(drag.x / dens, drag.y / dens)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (id < 0) {
                    // у часов третья кнопка «Форма», поэтому кнопки столбиком
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        EditPill("Вид", false) { shapeDlg = true }
                        EditPill("Убрать", true) { a.removeWidget(id) }
                        EditPill("Готово", false) { a.editWidget.intValue = NO_WIDGET; a.saveRects() }
                    }
                } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EditPill("Убрать", true) { a.removeWidget(id) }
                    EditPill("Готово", false) { a.editWidget.intValue = NO_WIDGET; a.saveRects() }
                }
                // перенос виджета на соседнюю страницу (справа от последней создаётся новая)
                if ((a.widgetPage[id] ?: 0) > 0) {
                    PageArrow("‹", Modifier.align(Alignment.CenterStart).padding(start = 6.dp)) { a.moveWidgetPage(id, -1) }
                }
                PageArrow("›", Modifier.align(Alignment.CenterEnd).padding(end = 6.dp)) { a.moveWidgetPage(id, 1) }
            }
            ResizeHandle(Modifier.align(Alignment.TopStart), dens, { a.saveRects() }) { dx, dy -> resize(dx, dy, 0f, 0f) }
            ResizeHandle(Modifier.align(Alignment.TopEnd), dens, { a.saveRects() }) { dx, dy -> resize(0f, dy, dx, 0f) }
            ResizeHandle(Modifier.align(Alignment.BottomStart), dens, { a.saveRects() }) { dx, dy -> resize(dx, 0f, 0f, dy) }
            ResizeHandle(Modifier.align(Alignment.BottomEnd), dens, { a.saveRects() }) { dx, dy -> resize(0f, 0f, dx, dy) }
        }
    }
}

@Composable
private fun PageArrow(text: String, modifier: Modifier, onClick: () -> Unit) {
    val s = MaterialTheme.colorScheme
    Box(
        modifier.size(40.dp).clip(CircleShape).background(s.primary).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = s.onPrimary)
    }
}

@Composable
private fun EditPill(text: String, danger: Boolean, onClick: () -> Unit) {
    val s = MaterialTheme.colorScheme
    Text(
        text, fontSize = 14.sp, fontWeight = FontWeight.Bold,
        color = if (danger) s.onError else s.onPrimary,
        modifier = Modifier.clip(CircleShape)
            .background(if (danger) s.error else s.primary)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun ResizeHandle(modifier: Modifier, dens: Float, onEnd: () -> Unit, onDrag: (Float, Float) -> Unit) {
    val s = MaterialTheme.colorScheme
    Box(
        modifier.size(40.dp).pointerInput(Unit) {
            detectDragGestures(onDragEnd = onEnd) { change, drag ->
                change.consume()
                onDrag(drag.x / dens, drag.y / dens)
            }
        },
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(20.dp).clip(CircleShape).background(s.primary).border(2.dp, Color.White, CircleShape))
    }
}

@Composable
fun HostedWidget(
    a: MainActivity, id: Int, modifier: Modifier = Modifier,
    minHeight: Dp = 48.dp
) {
    val info = a.wm.getAppWidgetInfo(id) ?: return
    BoxWithConstraints(modifier) {
        val dens = LocalDensity.current
        // высота = заявленная виджетом (минимум по ячейкам), но не меньше запаса, чтобы содержимое не обрезалось
        val h = maxOf(with(dens) { maxOf(info.minHeight, info.minResizeHeight).toDp() }, minHeight)
        val w = maxWidth
        AndroidView(
            factory = { c -> a.host.createView(c, id, info) as AppWidgetHostView },
            update = { v ->
                @Suppress("DEPRECATION")
                v.updateAppWidgetSize(null, w.value.toInt(), h.value.toInt(), w.value.toInt(), h.value.toInt())
            },
            modifier = Modifier.fillMaxWidth().height(h)
        )
    }
}

/**
 * Строка поиска под доком. Стиль «Google» — настоящий виджет Google (его внутренний фон рисует само приложение Google,
 * поэтому у него регулируется только прозрачность всего виджета). Стиль «Свой» — строка, которую рисует лаунчер:
 * прозрачность, размытие, цвет и Liquid Glass, как у папок.
 */
@Composable
fun QsbWidget(a: MainActivity) {
    val id = a.qsb.intValue
    val custom = prefs.qsbStyle == "custom"
    if (!custom && id != -1 && a.wm.getAppWidgetInfo(id) != null) {
        val alpha = 1f - prefs.qsbTransp.coerceIn(0, 100) / 100f
        key(id) {
            HostedWidget(
                a, id,
                Modifier.fillMaxWidth().padding(horizontal = 12.dp).graphicsLayer { this.alpha = alpha },
                minHeight = 72.dp
            )
        }
    } else {
        val s = MaterialTheme.colorScheme
        val glass = prefs.qsbPlate == "glass"
        val on = plateOn(glass, prefs.qsbTransp, prefs.qsbColor, s.surfaceContainerHigh, s.onSurfaceVariant)
        GlassPlate(
            rc(28), s.surfaceContainerHigh, glass, prefs.qsbTransp, prefs.qsbBlur, prefs.qsbColor,
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth().height(56.dp).clickable { a.openSearch() },
            Alignment.CenterStart
        ) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search, null, tint = on)
                Spacer(Modifier.width(14.dp))
                Text("Поиск в Google", color = on, fontSize = 16.sp)
            }
        }
    }
}
