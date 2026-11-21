package com.kyy.launcher

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Открывает системное окно установки наших живых обоев (с запасными вариантами для разных прошивок). */
private fun openPixelWallpaper(ctx: Context) {
    try {
        ctx.startActivity(
            Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(ctx, PixelWallpaperService::class.java)
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        try {
            ctx.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e2: Exception) {
            Toast.makeText(ctx, "Открой «Обои» в настройках и выбери «Google Pixel» в живых обоях", Toast.LENGTH_LONG).show()
        }
    }
}

/** Вкладка «Google Pixel»: фото внутри формы на цветном фоне, при разблокировке форма раскрывается на весь экран. */
@Composable
fun ColumnScope.PixelTab(a: MainActivity, onClose: () -> Unit) {
    val s = MaterialTheme.colorScheme
    val dm = a.resources.displayMetrics
    val aspect = dm.widthPixels.toFloat() / dm.heightPixels
    val scope = rememberCoroutineScope()

    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var pp by remember { mutableStateOf(PixelStore.params(a)) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        photo = withContext(Dispatchers.IO) { try { PixelStore.loadPhoto(a) } catch (_: Throwable) { null } }
        loaded = true
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val b = try {
                    withContext(Dispatchers.Default) { PixelStore.decodePhoto(a, uri, aspect) }
                } catch (_: Throwable) { null }
                if (b == null) {
                    Toast.makeText(a, "Не удалось открыть фото", Toast.LENGTH_LONG).show()
                } else {
                    val auto = withContext(Dispatchers.Default) { PixelScene.autoColor(b) }
                    pp = pp.copy(bg = auto)
                    photo = b
                }
            }
        }
    }
    val pick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    val apply = {
        val ph = photo
        val params = pp
        if (ph != null && !busy) {
            busy = true
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    try { PixelStore.save(a, ph, params); true } catch (_: Throwable) { false }
                }
                busy = false
                if (!ok) {
                    Toast.makeText(a, "Не удалось сохранить", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(
                        a, "В окне установки выбери «Главный экран и экран блокировки». Нажми на превью — форма раскроется",
                        Toast.LENGTH_LONG
                    ).show()
                    openPixelWallpaper(a)
                    onClose()
                }
            }
        }
        Unit
    }

    val p = photo
    if (p == null) {
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (loaded) {
                Text(
                    "Фото в форме на экране блокировки. При разблокировке форма плавно раскрывается на весь экран.",
                    style = MaterialTheme.typography.bodyMedium, color = s.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                FilledTonalButton(onClick = pick, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Выбрать фото")
                }
            }
        }
        return
    }

    val auto = remember(p) { PixelScene.autoColor(p) }
    val presets = remember {
        listOf(0xFF4A86F7, 0xFF2EC4B6, 0xFFFFC24B, 0xFFF2709C, 0xFF9B7BFF, 0xFFE8E4DC, 0xFF1F2430).map { it.toInt() }
    }
    val swatches = listOf(auto) + presets
    val curPp by rememberUpdatedState(pp)

    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                Modifier.height(340.dp).aspectRatio(aspect, matchHeightConstraintsFirst = true).clip(rc(24))
            ) {
                PixelPreview(
                    p, pp,
                    onMove = { dx, dy ->
                        pp = curPp.copy(
                            cx = (curPp.cx + dx).coerceIn(0.1f, 0.9f),
                            cy = (curPp.cy + dy).coerceIn(0.1f, 0.9f)
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                )
                // подсказка, где будут системные часы: их рисует система поверх обоев
                Text(
                    "12:30",
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp),
                    color = Color.White.copy(alpha = 0.5f), fontSize = 40.sp, fontWeight = FontWeight.Bold
                )
            }
        }
        Text(
            "Перетащи форму пальцем", style = MaterialTheme.typography.labelSmall,
            color = s.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp)
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PixelScene.SHAPE_NAMES.forEachIndexed { i, name ->
                ChoiceChip(name, pp.shape == i) { pp = pp.copy(shape = i) }
            }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Фон", style = MaterialTheme.typography.labelMedium)
            swatches.forEachIndexed { i, col ->
                val sel = pp.bg == col
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(Color(col))
                        .border(if (sel) 3.dp else 1.dp, if (sel) s.primary else s.outlineVariant, CircleShape)
                        .clickable { pp = pp.copy(bg = col) }
                ) {
                    if (i == 0) {
                        Text(
                            "A", Modifier.align(Alignment.Center), color = Color.White,
                            fontSize = 14.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        Text("Размер формы", style = MaterialTheme.typography.labelMedium)
        M3Slider(pp.size, { pp = pp.copy(size = it) }, 0.4f..1.0f)
        Text("Приближение фото в форме", style = MaterialTheme.typography.labelMedium)
        M3Slider(pp.zoom, { pp = pp.copy(zoom = it) }, 1f..1.6f)
        TextButton(onClick = pick, modifier = Modifier.align(Alignment.End)) { Text("Другое фото") }
    }
    Spacer(Modifier.height(8.dp))
    Button(onClick = { apply() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
        Text("Установить обои")
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun PixelPreview(photo: Bitmap, pp: PixelParams, onMove: (Float, Float) -> Unit, modifier: Modifier) {
    val paint = remember { Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG) }
    val move by rememberUpdatedState(onMove)
    Canvas(
        modifier.pointerInput(Unit) {
            detectDragGestures { change, drag ->
                change.consume()
                move(drag.x / size.width, drag.y / size.height)
            }
        }
    ) {
        drawIntoCanvas { cv ->
            val n = cv.nativeCanvas
            n.save()
            n.clipRect(0f, 0f, size.width, size.height)
            PixelScene.draw(n, size.width, size.height, photo, pp, 0f, paint)
            n.restore()
        }
    }
}
