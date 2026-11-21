package com.kyy.launcher

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.text.font.FontFamily
import java.io.File

/** Свои шрифты (TTF, OTF): копируются в память приложения, в настройках выбираются как «custom:имя_файла». */
object CustomFonts {
    const val PREFIX = "custom:"
    private const val MAX_BYTES = 25L * 1024 * 1024

    private lateinit var app: Context
    private val cache = HashMap<String, FontFamily>()

    fun init(c: Context) { app = c.applicationContext }

    private fun dir() = File(app.filesDir, "fonts").apply { mkdirs() }

    /** Имена файлов импортированных шрифтов. */
    fun list(): List<String> =
        dir().listFiles()
            ?.filter { it.isFile && (it.extension.equals("ttf", true) || it.extension.equals("otf", true)) }
            ?.map { it.name }?.sortedBy { it.lowercase() } ?: emptyList()

    fun label(file: String) = file.substringBeforeLast('.')

    /** Семейство для Compose или null, если файла нет или он не читается. */
    fun family(file: String): FontFamily? {
        cache[file]?.let { return it }
        val f = File(dir(), file)
        if (!f.exists()) return null
        val tf = try { Typeface.Builder(f).build() } catch (e: Throwable) { null } ?: return null
        return FontFamily(tf).also { cache[file] = it }
    }

    /** Копирует шрифт из выбранного файла. Возвращает имя сохранённого файла или null, если это не шрифт. */
    fun import(uri: Uri): String? {
        val cr = app.contentResolver
        var display = uri.lastPathSegment ?: "font"
        cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0)?.let { n -> display = n }
        }
        val base = display.substringBeforeLast('.')
            .replace(Regex("[^\\p{L}\\p{N} _.-]"), "_").trim().take(40).ifEmpty { "font" }

        val tmp = File(dir(), ".import.tmp")
        var total = 0L
        val input = cr.openInputStream(uri) ?: return null
        input.use { inp ->
            tmp.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = inp.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_BYTES) { tmp.delete(); return null }
                    out.write(buf, 0, n)
                }
            }
        }

        // заголовок TrueType / OpenType / коллекции
        val head = ByteArray(4)
        val got = tmp.inputStream().use { it.read(head) }
        val sig = String(head, Charsets.ISO_8859_1)
        val isTrueType = got == 4 && (head.contentEquals(byteArrayOf(0, 1, 0, 0)) || sig == "true" || sig == "ttcf")
        val isOpenType = got == 4 && sig == "OTTO"
        if (!isTrueType && !isOpenType) { tmp.delete(); return null }

        val name = "$base." + if (isOpenType) "otf" else "ttf"
        val dst = File(dir(), name)
        if (dst.exists()) dst.delete()
        if (!tmp.renameTo(dst)) { tmp.delete(); return null }
        cache.remove(name)
        val ok = try { Typeface.Builder(dst).build() != null } catch (e: Throwable) { false }
        if (!ok) { dst.delete(); return null }
        return name
    }

    fun delete(file: String) {
        cache.remove(file)
        File(dir(), file).delete()
    }
}

/** Дополнительные системные гарнитуры Android для шрифта приложения: id → (подпись, имя семейства в системе). */
object SysFonts {
    private val names = linkedMapOf(
        "light" to ("Лёгкий" to "sans-serif-light"),
        "medium" to ("Средний" to "sans-serif-medium"),
        "black" to ("Чёрный" to "sans-serif-black"),
        "condensed" to ("Узкий" to "sans-serif-condensed"),
        "casual" to ("Casual" to "casual"),
        "cursive" to ("Рукописный" to "cursive"),
        "serifmono" to ("Моно с засечками" to "serif-monospace"),
        "smallcaps" to ("Капитель" to "sans-serif-smallcaps")
    )
    private val cache = HashMap<String, FontFamily?>()

    /** (id, подпись) для выбора в настройках. */
    val list: List<Pair<String, String>> = names.map { it.key to it.value.first }

    /** Семейство по id или null, если такого id нет. Если на телефоне нет такой гарнитуры, Android подставит обычную. */
    fun family(id: String): FontFamily? {
        val n = names[id]?.second ?: return null
        return cache.getOrPut(id) { try { FontFamily(Typeface.create(n, Typeface.NORMAL)) } catch (e: Throwable) { null } }
    }
}

/** Шрифт часов на экране блокировки. family == null — взять шрифт приложения. kind: text / outline (контур) / dots (точки). */
class ClockFont(val id: String, val label: String, val family: FontFamily?, val kind: String = "text")

/** Двенадцать непохожих друг на друга стилей часов (как на Pixel) плюс свои TTF/OTF из CustomFonts. */
object LockClockFonts {
    private fun tf(name: String, style: Int = Typeface.NORMAL): FontFamily? =
        try { FontFamily(Typeface.create(name, style)) } catch (e: Throwable) { null }

    val presets: List<ClockFont> by lazy {
        listOf(
            ClockFont("theme", "Жирный", null),
            ClockFont("ios", "Растяжимый", null, "stretch"),
            ClockFont("thin", "Тонкий", tf("sans-serif-thin")),
            ClockFont("outline", "Контур", tf("sans-serif-black"), "outline"),
            ClockFont("dots", "Точки", null, "dots"),
            ClockFont("serif", "С засечками", tf("serif")),
            ClockFont("serifbold", "Засечки жирные", tf("serif", Typeface.BOLD)),
            ClockFont("condensed", "Узкий", tf("sans-serif-condensed", Typeface.BOLD)),
            ClockFont("rounded", "Скруглённый", tf("sans-serif-rounded") ?: tf("sans-serif-black")),
            ClockFont("mono", "Моно", tf("monospace", Typeface.BOLD)),
            ClockFont("casual", "Casual", tf("casual")),
            ClockFont("cursive", "Рукописный", tf("cursive"))
        )
    }

    fun resolve(id: String): ClockFont {
        if (id.startsWith(CustomFonts.PREFIX)) {
            val file = id.removePrefix(CustomFonts.PREFIX)
            CustomFonts.family(file)?.let { return ClockFont(id, CustomFonts.label(file), it) }
        }
        return presets.firstOrNull { it.id == id } ?: presets.first()
    }
}
