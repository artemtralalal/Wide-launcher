package com.kyy.launcher

import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class Wp(val c1: Int, val c2: Int, val radial: Boolean)

private fun gradientBitmap(w: Int, h: Int, c1: Int, c2: Int, radial: Boolean): Bitmap {
    val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.shader = if (radial) RadialGradient(w * 0.5f, h * 0.35f, h * 0.75f, c2, c1, Shader.TileMode.CLAMP)
    else LinearGradient(0f, 0f, w * 0.6f, h.toFloat(), c1, c2, Shader.TileMode.CLAMP)
    Canvas(b).drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
    return b
}

private fun decodeScaled(a: MainActivity, uri: Uri, maxW: Int, maxH: Int): Bitmap? {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    a.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
    var sample = 1
    while (o.outWidth / (sample * 2) >= maxW && o.outHeight / (sample * 2) >= maxH) sample *= 2
    val o2 = BitmapFactory.Options().apply { inSampleSize = sample }
    val bmp = a.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o2) } ?: return null
    val k = minOf(1f, 4096f / maxOf(bmp.width, bmp.height))
    return if (k < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt(), (bmp.height * k).toInt(), true) else bmp
}

/** Своё меню обоев: фото из галереи, градиенты в цветах темы, выбор — главный экран / блокировка / оба. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperMenu(a: MainActivity, onClose: () -> Unit) {
    val s = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var target by remember { mutableIntStateOf(3) } // 1 — главный, 2 — блокировка, 3 — оба
    var busy by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) } // 0 — обычные обои, 1 — Google Pixel
    val dm = a.resources.displayMetrics
    val w = dm.widthPixels
    val h = dm.heightPixels

    val setWp: (() -> Bitmap?) -> Unit = { make ->
        if (!busy) {
            busy = true
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    try {
                        val bmp = make()
                        if (bmp == null) false
                        else {
                            WallpaperManager.getInstance(a).setBitmap(bmp, null, true, target)
                            true
                        }
                    } catch (_: Throwable) { false }
                }
                busy = false
                Toast.makeText(a, if (ok) "Обои установлены" else "Не удалось установить обои", Toast.LENGTH_SHORT).show()
                if (ok) onClose()
            }
        }
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) setWp { decodeScaled(a, uri, w * 2, h * 2) }
    }

    val list = remember(s) {
        buildList {
            var i = 0
            for (v in Palette.values) {
                val c = Color(v)
                add(Wp(lerp(c, Color.Black, 0.8f).toArgb(), c.toArgb(), i % 2 == 1))
                i++
            }
            add(Wp(s.surface.toArgb(), s.primary.toArgb(), true))
            add(Wp(s.secondary.toArgb(), s.surface.toArgb(), false))
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(.5f)).clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null
        ) { onClose() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            shape = rc(32), color = s.surface,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(if (tab == 1) .94f else .82f).clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null
            ) {}
        ) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(
                    "Обои", style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(top = 22.dp, bottom = 12.dp)
                )
                TabRow(
                    selectedTabIndex = tab, containerColor = Color.Transparent,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Обычные") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Google Pixel") })
                }
                if (tab == 1) {
                    PixelTab(a, onClose)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(3 to "Везде", 1 to "Главный", 2 to "Блокировка").forEach { (v, t) ->
                            ChoiceChip(t, target == v) { target = v }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(
                        onClick = { pick.launch("image/*") }, enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Выбрать из галереи")
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Градиенты", style = MaterialTheme.typography.titleMedium, color = s.onSurface)
                    Spacer(Modifier.height(10.dp))
                    LazyVerticalGrid(
                        GridCells.Fixed(3),
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 12.dp)
                    ) {
                        items(list) { wp ->
                            Box(
                                Modifier.aspectRatio(0.62f).clip(rc(20))
                                    .background(Brush.linearGradient(listOf(Color(wp.c1), Color(wp.c2))))
                                    .clickable { setWp { gradientBitmap(w, h, wp.c1, wp.c2, wp.radial) } }
                            )
                        }
                    }
                    TextButton(
                        onClick = { onClose(); a.openWallpapers() },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) { Text("Системный выбор обоев") }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
