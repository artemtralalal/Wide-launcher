package com.kyy.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.abs

val PageEffects = listOf(
    "none" to "Обычное",
    "cube" to "Куб",
    "carousel" to "Карусель",
    "fade" to "Затухание",
    "zoom" to "Масштаб",
    "flip" to "Переворот"
)

/**
 * Эффект листания страниц рабочего стола. o — положение страницы относительно текущей:
 * 0 — страница на месте, ±1 — полностью ушла влево или вправо. Когда листание закончилось, o = 0 и ничего не меняется.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.pageEffect(pager: PagerState, page: Int): Modifier = graphicsLayer {
    val effect = prefs.pageEffect
    if (effect == "none") return@graphicsLayer
    val o = ((pager.currentPage - page) + pager.currentPageOffsetFraction).coerceIn(-1f, 1f)
    if (o == 0f) return@graphicsLayer
    val a = abs(o)
    cameraDistance = 14f * density
    when (effect) {
        "cube" -> {
            transformOrigin = TransformOrigin(if (o > 0) 1f else 0f, .5f)
            rotationY = -90f * o
            alpha = if (a >= .98f) 0f else 1f
        }
        "carousel" -> {
            transformOrigin = TransformOrigin(.5f, .5f)
            rotationY = -35f * o
            val sc = 1f - .18f * a
            scaleX = sc; scaleY = sc
            alpha = 1f - .5f * a
        }
        "fade" -> alpha = 1f - a
        "zoom" -> {
            val sc = 1f - .3f * a
            scaleX = sc; scaleY = sc
            alpha = 1f - .6f * a
        }
        "flip" -> {
            transformOrigin = TransformOrigin(.5f, .5f)
            rotationY = -180f * o
            alpha = if (a > .5f) 0f else 1f
        }
    }
}
