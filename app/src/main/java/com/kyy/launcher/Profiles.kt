package com.kyy.launcher

import android.content.Context
import android.content.Intent
import org.json.JSONObject

/**
 * Снимки настроек: экспорт и импорт файлом и именованные профили («Работа», «Дом», «Ночь»).
 * В снимок попадают все настройки лаунчера (цвета, шрифты, иконки, раскладка, папки, док, экран блокировки).
 */
object SettingsIO {
    private const val FILE_APP = "MaterialLauncher"

    /** Привязано к конкретному телефону: при переносе файлом останётся как было (id виджетов, разрешения, приветствие). */
    private val DEVICE = setOf("widgets", "widgetRects", "widgetPages", "qsbId", "qsbAsked", "welcomeDone", "lockFpImage", "clockShapes")

    private fun sp(ctx: Context) = ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    fun snapshot(ctx: Context): JSONObject {
        val out = JSONObject()
        sp(ctx).all.forEach { (k, v) ->
            val e = JSONObject()
            when (v) {
                is Boolean -> e.put("t", "b").put("v", v)
                is Int -> e.put("t", "i").put("v", v)
                is String -> e.put("t", "s").put("v", v)
                else -> return@forEach
            }
            out.put(k, e)
        }
        return JSONObject().put("app", FILE_APP).put("version", 1).put("prefs", out)
    }

    fun isValid(i: JSONObject) = j.optString("app") == FILE_APP && j.optJSONObject("prefs") != null

    /**
     * Заменить настройки снимком. transfer = true — снимок пришёл с другого телефона (файла): виджеты, свои шрифты
     * и картинка отпечатка не переносятся. Возвращает false, если снимок не подошёл.
     */
    fun apply(ctx: Context, snap: JSONObject, transfer: Boolean): Boolean {
        if (!isValid(snap)) return false
        val p = snap.getJSONObject("prefs")
        val sp = sp(ctx)
        val old = sp.all
        val keep = if (transfer) DEVICE else setOf("welcomeDone")
        val e = sp.edit().clear()
        // что привязано к устройству, остаётся как есть
        for (k in keep) when (val v = old[k]) {
            is Boolean -> e.putBoolean(k, v)
            is Int -> e.putInt(k, v)
            is String -> e.putString(k, v)
        }
        val it = p.keys()
        while (it.hasNext()) {
            val k = it.next()
            if (k in keep) continue
            val o = p.optJSONObject(k) ?: continue
            when (o.optString("t")) {
                "b" -> e.putBoolean(k, o.optBoolean("v"))
                "i" -> e.putInt(k, o.optInt("v"))
                "s" -> {
                    val v = o.optString("v")
                    // свой шрифт — файл в памяти приложения; на другом телефоне его нет
                    if (transfer && v.startsWith("custom:")) continue
                    e.putString(k, v)
                }
            }
        }
        return e.commit()
    }

    /** Перезапуск лаунчера, чтобы все настройки прочитались заново. */
    fun restart(ctx: Context) {
        val i = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
        if (i != null) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            ctx.startActivity(i)
        }
        android.os.Process.killProcess(android.os.Process.myPid())
    }
}

/** Именованные профили: каждый хранит полный снимок настроек. */
object Profiles {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("profiles", Context.MODE_PRIVATE)

    fun names(ctx: Context): List<String> =
        sp(ctx).all.keys.filter { it.startsWith("p:") }.map { it.removePrefix("p:") }.sortedBy { it.lowercase() }

    fun active(ctx: Context): String? = sp(ctx).getString("active", null)?.takeIf { sp(ctx).contains("p:$it") }

    /** Сохранить текущие настройки в профиль с таким именем (если есть, перезаписать, и сделать его активным). */
    fun save(ctx: Context, name: String) {
        sp(ctx).edit().putString("p:$name", SettingsIO.snapshot(ctx).toString()).putString("active", name).commit()
    }

    /** Применить профиль. Текущие настройки перед этим уходят в активный профиль, чтобы правки не потерялись. */
    fun apply(ctx: Context, name: String): Boolean {
        val raw = sp(ctx).getString("p:$name", null) ?: return false
        val snap = runCatching { JSONObject(raw) }.getOrNull() ?: return false
        if (!SettingsIO.isValid(snap)) return false
        active(ctx)?.let { cur -> if (cur != name) sp(ctx).edit().putString("p:$cur", SettingsIO.snapshot(ctx).toString()).commit() }
        if (!SettingsIO.apply(ctx, snap, transfer = false)) return false
        sp(ctx).edit().putString("active", name).commit()
        return true
    }

    fun delete(ctx: Context, name: String) {
        val e = sp(ctx).edit().remove("p:$name")
        if (sp(ctx).getString("active", null) == name) e.remove("active")
        e.commit()
    }
}
