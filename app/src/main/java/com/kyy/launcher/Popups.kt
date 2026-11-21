package com.kyy.launcher

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MenuAction(
    val icon: ImageVector,
    val label: String,
    val danger: Boolean = false,
    val run: () -> Unit
)

data class ShortcutItem(val id: String, val pkg: String, val label: String, val icon: ImageBitmap?)

fun loadShortcuts(ctx: Context, pkg: String): List<ShortcutItem> = runCatching {
    val la = ctx.getSystemService(LauncherApps::class.java)
    val q = LauncherApps.ShortcutQuery().setPackage(pkg).setQueryFlags(
        LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
            LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
            LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
    )
    (la.getShortcuts(q, Process.myUserHandle()) ?: emptyList())
        .filter { it.isEnabled }
        .take(4)
        .map {
            ShortcutItem(
                it.id, pkg, (it.shortLabel ?: "").toString(),
                runCatching { la.getShortcutIconDrawable(it, 0)?.toBitmap(96, 96)?.asImageBitmap() }.getOrNull()
            )
        }
}.getOrDefault(emptyList())

private val shortcutCache = java.util.concurrent.ConcurrentHashMap<String, List<ShortcutItem>>()

/** Грузит ярлыки приложения в фоне заранее (при долгом нажатии), чтобы меню открывалось сразу и готовым. */
fun prefetchShortcuts(ctx: Context, pkg: String) {
    val app = ctx.applicationContext
    Thread { shortcutCache[pkg] = loadShortcuts(app, pkg) }.start()
}

fun startShortcut(ctx: Context, s: ShortcutItem) {
    runCatching {
        ctx.getSystemService(LauncherApps::class.java).startShortcut(s.pkg, s.id, null, null, Process.myUserHandle())
    }
}

/**
 * Плавающее меню как на Google Pixel: растёт из точки нажатия / иконки и так же уходит.
 * Содержимое с первого кадра на месте (размер известен сразу), поэтому меню не «подпрыгивает»;
 * если высота потом меняется (подгрузились ярлык), она меняется плавно.
 */
@Composable
fun FloatingMenu(
    cx: Int, top: Int, bottom: Int, onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val scope = rememberCoroutineScope()
    val p = remember { Animatable(0f) }
    var originX by remember { mutableFloatStateOf(0.5f) }
    var originY by remember { mutableFloatStateOf(1f) }
    var closing by remember { mutableStateOf(false) }
    val dismiss: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch {
                p.animateTo(0f, tween(130, easing = FastOutLinearInEasing))
                onDismiss()
            }
        }
    }
    LaunchedEffect(Unit) { p.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = 700f)) }
    val provider = remember(cx, top, bottom) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect, windowSize: IntSize,
                layoutDirection: LayoutDirection, popupContentSize: IntSize
            ): IntOffset {
                val m = 24
                val x = (cx - popupContentSize.width / 2)
                    .coerceIn(m, (windowSize.width - popupContentSize.width - m).coerceAtLeast(m))
                var y = top - popupContentSize.height - 12
                var above = y >= m
                if (!above) y = bottom + 12
                y = y.coerceIn(m, (windowSize.height - popupContentSize.height - m).coerceAtLeast(m))
                // меню вырастает из якоря: от его нижнего края, если оно над иконкой, и от верхнего, если под ней
                originX = ((cx - x).toFloat() / popupContentSize.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                originY = if (above) 1f else 0f
                return IntOffset(x, y)
            }
        }
    }
    Popup(
        popupPositionProvider = provider,
        onDismissRequest = dismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Surface(
            shape = rc(28), color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 10.dp,
            modifier = Modifier.graphicsLayer {
                val v = p.value
                alpha = v.coerceIn(0f, 1f)
                val sc = (0.8f + 0.2f * v).coerceAtMost(1f)
                scaleX = sc
                scaleY = sc
                transformOrigin = TransformOrigin(originX, originY)
            }
        ) {
            Column(
                Modifier.width(264.dp).animateContentSize(spring(dampingRatio = 1f, stiffness = 500f)).padding(8.dp),
                content = content
            )
        }
    }
}

@Composable
fun MenuRow(
    icon: ImageVector?, text: String, danger: Boolean = false,
    image: ImageBitmap? = null, onClick: () -> Unit
) {
    val s = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(rc(20)).clickable(onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape)
                .background(if (danger) s.errorContainer else s.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            if (image != null) Image(image, null, Modifier.size(28.dp))
            else if (icon != null) Icon(
                icon, null, Modifier.size(22.dp),
                tint = if (danger) s.onErrorContainer else s.onSecondaryContainer
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text, color = if (danger) s.error else s.onSurface,
            fontWeight = FontWeight.Medium, maxLines = 1
        )
    }
}

@Composable
fun AppMenuContent(app: AppInfo, actions: List<MenuAction>, dismiss: () -> Unit) {
    val ctx = LocalContext.current
    // берём заранее загруженные ярлыки; если их нет — грузим в фоне, а не в главном потоке (иначе анимация рвётся)
    val cached = remember(app.pkg) { shortcutCache[app.pkg] }
    val shortcuts by produceState(cached ?: emptyList<ShortcutItem>(), app.pkg) {
        if (cached == null) {
            value = withContext(Dispatchers.Default) { loadShortcuts(ctx, app.pkg) }
                .also { shortcutCache[app.pkg] = it }
        }
    }
    shortcuts.forEach { sc ->
        MenuRow(null, sc.label, image = sc.icon) { dismiss(); startShortcut(ctx, sc) }
    }
    if (shortcuts.isNotEmpty()) {
        HorizontalDivider(Modifier.padding(vertical = 6.dp, horizontal = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
    actions.forEach { a -> MenuRow(a.icon, a.label, a.danger) { dismiss(); a.run() } }
}
