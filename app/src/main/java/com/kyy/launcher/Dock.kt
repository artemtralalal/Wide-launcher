package com.kyy.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/**
 * Док. Тап — запуск; долгое нажатие и отпустить — меню; долгое нажатие и потянуть вбок —
 * перестановка иконок (порядок сохраняется при отпускании).
 */
@Composable
fun DockRow(dock: List<AppInfo>, menuFor: (AppInfo) -> List<MenuAction>) {
    val s = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val pkgs = dock.map { it.pkg }
    var order by remember(pkgs) { mutableStateOf(pkgs) }
    var dragPkg by remember { mutableStateOf<String?>(null) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var rowW by remember { mutableFloatStateOf(1f) }
    var bounds by remember { mutableStateOf<Rect?>(null) }
    var menu by remember { mutableStateOf<Pair<AppInfo, Int>?>(null) }
    val byPkg = remember(dock) { dock.associateBy { it.pkg } }

    // Плашка дока: затемнение и размытие обоев под ней настраиваются
    val ctx = LocalContext.current
    val resumeV = (ctx as? MainActivity)?.resume?.intValue ?: 0
    var plate by remember { mutableStateOf(Rect.Zero) }
    val dim = prefs.dockDim / 100f
    val blur = prefs.dockBlur
    val plateColor = androidx.compose.ui.graphics.lerp(s.surfaceContainer, Color.Black, dim)
        .copy(alpha = (.88f - .38f * blur / 100f + .12f * dim).coerceIn(.3f, .97f))
    Box(
        Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth()
            .onGloballyPositioned { plate = it.boundsInWindow() }
            .then(
                if (prefs.dockBg) Modifier.clip(rc(36)).background(plateColor)
                else Modifier
            )
    ) {
    if (prefs.dockBg) DockBlurBackdrop(Modifier.matchParentSize(), plate.left, plate.top, blur, resumeV)
    Row(
        Modifier.fillMaxWidth()
            .padding(vertical = 8.dp)
            .onGloballyPositioned { rowW = it.size.width.toFloat(); bounds = it.boundsInWindow() }
            .pointerInput(pkgs) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (menu != null) return@awaitEachGesture
                    val n = order.size
                    if (n == 0) return@awaitEachGesture
                    val cw = size.width.toFloat() / n
                    val idx = (down.position.x / cw).toInt().coerceIn(0, n - 1)
                    val app = byPkg[order[idx]] ?: return@awaitEachGesture
                    val slop = viewConfiguration.touchSlop
                    val first = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        var res = 0
                        while (res == 0) {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            val c = ev.changes.firstOrNull { it.id == down.id }
                            res = if (c == null || c.isConsumed || !c.pressed) 1
                            else if ((c.position - down.position).getDistance() > slop) 1
                            else 0
                        }
                        res
                    }
                    if (first != null) return@awaitEachGesture   // обычный тап или свайп — не наше дело
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    prefetchShortcuts(ctx, app.pkg)
                    var dragging = false
                    var released = false
                    var cur = idx
                    try {
                        while (true) {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            val c = ev.changes.firstOrNull { it.id == down.id }
                            ev.changes.forEach { it.consume() }
                            if (c == null) break
                            if (!c.pressed) { released = true; break }
                            if (!dragging && (c.position - down.position).getDistance() > slop) {
                                dragging = true
                                dragPkg = app.pkg
                            }
                            if (dragging) {
                                dragX = c.position.x
                                val target = (c.position.x / cw).toInt().coerceIn(0, n - 1)
                                if (target != cur) {
                                    val l = order.toMutableList()
                                    l.removeAt(cur)
                                    l.add(target, app.pkg)
                                    order = l
                                    cur = target
                                }
                            }
                        }
                        if (released && !dragging) menu = app to idx
                    } finally {
                        if (dragging) prefs.dock = order.joinToString("|")
                        dragPkg = null
                    }
                }
            },
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        order.forEach { pkg ->
            val app = byPkg[pkg] ?: return@forEach
            key(pkg) {
                AppItem(
                    app, false, Color.White, emptyList(),
                    Modifier.weight(1f)
                        .zIndex(if (dragPkg == pkg) 1f else 0f)
                        .graphicsLayer {
                            if (dragPkg == pkg) {
                                translationX = dragX - (order.indexOf(pkg) + 0.5f) * rowW / order.size
                                scaleX = 1.15f
                                scaleY = 1.15f
                            }
                        },
                    prefs.dockSize, managed = true
                )
            }
        }
    }
    }

    menu?.let { (app, idx) ->
        val b = bounds
        if (b != null) {
            val cw = b.width / order.size.coerceAtLeast(1)
            FloatingMenu(
                (b.left + (idx + 0.5f) * cw).roundToInt(), b.top.roundToInt(), b.bottom.roundToInt(),
                { menu = null }
            ) { AppMenuContent(app, menuFor(app)) { menu = null } }
        }
    }
}
