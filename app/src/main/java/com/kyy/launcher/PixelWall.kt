package com.kyy.launcher

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** Pixel-mode settings. Position/size are fractions of the screen width/height. */
data class PixelParams(
    val shape: Int = 0,
    val bg: Int = 0xFF4A86F7.toInt(),
    val size: Float = 0.78f,   // shape diameter, fraction of screen width
    val cx: Float = 0.5f,      // shape center x, fraction of screen width
    val cy: Float = 0.42f,     // shape center y, fraction of screen height
    val zoom: Float = 1.12f    // photo magnification inside the shape while locked
)

/**
 * Draws the "Pixel" lock screen: color background + shape with the photo inside.
 * e = 0 -> locked (shape), e = 1 -> unlocked (full-screen photo); e is already eased by the caller.
 */
object PixelScene {
    val SHAPE_NAMES = listOf("Круг", "Скруглённый", "Печенье", "Цветок", "Арка", "Капсула")

    // radius of the circle that fits inside each unit shape (used to compute the expand size)
    private val INSCRIBED = floatArrayOf(1f, 1f, 0.68f, 0.56f, 0.8f, 0.6f)
    private val unit = arrayOfNulls<Path>(SHAPE_NAMES.size)
    private val work = Path()
    private val mx = Matrix()
    private val pm = Matrix()
    private val shaderPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var shaderFor: Bitmap? = null
    private var shader: BitmapShader? = null

    private fun polar(f: (Double) -> Double): Path {
        val p = Path()
        val n = 240
        for (i in 0..n) {
            val a = i * 2 * PI / n
            val r = f(a)
            val x = (r * cos(a)).toFloat()
            val y = (r * sin(a)).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        return p
    }

    private fun unitPath(id: Int): Path {
        val i = id.coerceIn(0, SHAPE_NAMES.size - 1)
        unit[i]?.let { return it }
        val p = when (i) {
            0 -> polar { 1.0 }
            1 -> Path().apply {
                addRoundRect(RectF(-1f, -1f, 1f, 1f), 0.55f, 0.55f, Path.Direction.CW)
            }
            2 -> polar { 0.86 + 0.14 * cos(8 * it) }
            3 -> polar { 0.78 + 0.22 * cos(5 * it) }
            4 -> Path().apply {
                addRoundRect(
                    RectF(-0.8f, -1f, 0.8f, 0.8f),
                    floatArrayOf(0.8f, 0.8f, 0.8f, 0.8f, 0.2f, 0.2f, 0.2f, 0.2f),
                    Path.Direction.CW
                )
            }
            else -> Path().apply {
                addRoundRect(RectF(-0.6f, -1f, 0.6f, 1f), 0.6f, 0.6f, Path.Direction.CW)
            }
        }
        unit[i] = p
        return p
    }

    /** Shape scaled to radius r and moved to (cx, cy). The returned Path is reused: use it right away. */
    @Synchronized
    fun shapeInto(id: Int, cx: Float, cy: Float, r: Float): Path {
        mx.reset()
        mx.setScale(r, r)
        mx.postTranslate(cx, cy)
        work.set(unitPath(id))
        work.transform(mx)
        return work
    }

    // Synchronized: the wallpaper renders on its own thread, the editor preview on the UI thread.
    @Synchronized
    fun draw(
        c: Canvas, sw: Float, sh: Float,
        photo: Bitmap, p: PixelParams, e: Float, paint: Paint
    ) {
        val s = max(sw / photo.width, sh / photo.height)
        val l = (sw - photo.width * s) / 2f
        val t = (sh - photo.height * s) / 2f

        if (e >= 0.999f) {
            c.save()
            c.translate(l, t)
            c.scale(s, s)
            c.drawBitmap(photo, 0f, 0f, paint)
            c.restore()
            return
        }

        val cx = p.cx * sw
        val cy = p.cy * sh
        val r0 = p.size * sw / 2f

        val dx = max(cx, sw - cx)
        val dy = max(cy, sh - cy)
        val far = sqrt(dx * dx + dy * dy)
        val ri = INSCRIBED[p.shape.coerceIn(0, INSCRIBED.size - 1)]
        val fEnd = max(1.05f, far / (r0 * ri) * 1.04f)
        val f = exp(ln(fEnd) * e)
        val z = p.zoom + (1f - p.zoom) * e
        // zoom around the screen center so the picture stays put while the shape is moved
        val zx = sw / 2f
        val zy = sh / 2f

        c.drawColor(p.bg)

        // filling the shape with a bitmap shader is much cheaper for the GPU than clipPath
        val path = shapeInto(p.shape, cx, cy, r0 * f)
        if (shaderFor !== photo) {
            shader = BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            shaderFor = photo
        }
        val sd = shader!!
        pm.reset()
        pm.postScale(s, s)
        pm.postTranslate(l, t)
        pm.postTranslate(-zx, -zy)
        pm.postScale(z, z)
        pm.postTranslate(zx, zy)
        sd.setLocalMatrix(pm)
        shaderPaint.shader = sd
        c.drawPath(path, shaderPaint)
    }

    /** Picks a pleasant background color from the photo's dominant hue. */
    fun autoColor(b: Bitmap): Int {
        val w = 48
        val h = maxOf(1, (48f * b.height / b.width).toInt())
        val sm = Bitmap.createScaledBitmap(b, w, h, true)
        val px = IntArray(w * h)
        sm.getPixels(px, 0, w, 0, 0, w, h)
        val hsv = FloatArray(3)
        val wt = FloatArray(24)
        val hs = FloatArray(24)
        val ss = FloatArray(24)
        val vs = FloatArray(24)
        for (c in px) {
            Color.colorToHSV(c, hsv)
            if (hsv[1] > 0.2f && hsv[2] > 0.25f) {
                val k = (hsv[0] / 15f).toInt().coerceIn(0, 23)
                val g = hsv[1] * hsv[2]
                wt[k] += g
                hs[k] += hsv[0] * g
                ss[k] += hsv[1] * g
                vs[k] += hsv[2] * g
            }
        }
        var best = 0
        for (i in 1 until 24) if (wt[i] > wt[best]) best = i
        if (wt[best] < 0.5f) return 0xFF6C8EBF.toInt()
        val hh = hs[best] / wt[best]
        val s = (ss[best] / wt[best]).coerceIn(0.45f, 0.85f)
        val v = (vs[best] / wt[best]).coerceIn(0.6f, 0.95f)
        return Color.HSVToColor(floatArrayOf(hh, s, v))
    }
}

/** Хранилище Pixel-обоев: фото и параметры формы. Читает и лаунчер (редактор), и живые обои (сервис). */
object PixelStore {
    private fun photoFile(c: Context) = File(c.filesDir, "pixel_photo.jpg")

    fun prefs(c: Context): SharedPreferences =
        c.getSharedPreferences("pixelwall", Context.MODE_PRIVATE)

    fun params(c: Context): PixelParams {
        val p = prefs(c)
        val d = PixelParams()
        return PixelParams(
            shape = p.getInt("p_shape", d.shape),
            bg = p.getInt("p_bg", d.bg),
            size = p.getFloat("p_size", d.size),
            cx = p.getFloat("p_cx", d.cx),
            cy = p.getFloat("p_cy", d.cy),
            zoom = p.getFloat("p_zoom", d.zoom)
        )
    }

    fun loadPhoto(c: Context): Bitmap? =
        photoFile(c).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }

    fun save(c: Context, photo: Bitmap, pp: PixelParams) {
        FileOutputStream(photoFile(c)).use { photo.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        prefs(c).edit()
            .putInt("p_shape", pp.shape)
            .putInt("p_bg", pp.bg)
            .putFloat("p_size", pp.size)
            .putFloat("p_cx", pp.cx)
            .putFloat("p_cy", pp.cy)
            .putFloat("p_zoom", pp.zoom)
            .putLong("rev", System.currentTimeMillis())
            .apply()
    }

    /** Декодирует фото, чинит поворот и обрезает по центру под пропорции экрана (w/h). */
    fun decodePhoto(c: Context, uri: Uri, aspect: Float): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        c.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > 2400 || bounds.outHeight / sample > 2400) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = c.contentResolver.openInputStream(uri)!!.use {
            BitmapFactory.decodeStream(it, null, opts)
        }!!
        val rot = try {
            c.contentResolver.openInputStream(uri)!!.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }
        } catch (e: Exception) { 0f }
        if (rot != 0f) {
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rot) }, true)
        }
        val cw: Int
        val ch: Int
        if (bmp.width.toFloat() / bmp.height > aspect) {
            ch = bmp.height
            cw = (ch * aspect).toInt()
        } else {
            cw = bmp.width
            ch = (cw / aspect).toInt()
        }
        val cropped = Bitmap.createBitmap(bmp, (bmp.width - cw) / 2, (bmp.height - ch) / 2, cw, ch)
        val maxH = 2000
        return if (ch > maxH) Bitmap.createScaledBitmap(cropped, (maxH * aspect).toInt(), maxH, true) else cropped
    }
}
