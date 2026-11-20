package com.venice.agent

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

class AgentTools(private val ctx: Context) {
    private val memory = Memory(ctx)
    private val svc get() = AgentAccessibilityService.instance
    private fun prop(type: String, description: String) = JSONObject().put("type", type).put("description", description)

    fun declarations(): JSONArray = JSONArray().apply {
        put(fn("read_screen", "اقرأ النصوص والأزرار الحالية", JSONObject()))
        put(fn("tap_text", "انقر على عنصر بالنص", JSONObject().put("query", prop("string", "النص")), "query"))
        put(fn("type_text", "اكتب في الحقل الحالي", JSONObject().put("text", prop("string", "النص")), "text"))
        put(fn("press", "اضغط back أو home أو recents", JSONObject().put("key", prop("string", "المفتاح")), "key"))
        put(fn("open_app", "افتح تطبيقًا بالاسم", JSONObject().put("name", prop("string", "اسم التطبيق")), "name"))
        put(fn("speak", "انطق نصًا", JSONObject().put("text", prop("string", "النص")), "text"))
        put(fn("save_skill", "احفظ خطوات المهمة", JSONObject().put("name", prop("string", "الاسم")).put("steps", prop("string", "JSON للخطوات")), "name", "steps"))
        put(fn("run_skill", "شغّل مهارة محفوظة", JSONObject().put("name", prop("string", "الاسم")), "name"))
    }
    private fun fn(name: String, desc: String, props: JSONObject, vararg required: String) = JSONObject().put("name", name).put("description", desc).put("parameters", JSONObject().put("type", "OBJECT").put("properties", props).put("required", JSONArray(required)))

    suspend fun execute(name: String, args: JSONObject): String = when (name) {
        "read_screen" -> svc?.readScreen() ?: "خدمة الوصول غير مفعلة"
        "tap_text" -> svc?.tapText(args.optString("query")) ?: "خدمة الوصول غير مفعلة"
        "type_text" -> svc?.typeText(args.optString("text")) ?: "خدمة الوصول غير مفعلة"
        "press" -> svc?.press(args.optString("key"))?.toString() ?: "خدمة الوصول غير مفعلة"
        "open_app" -> openApp(args.optString("name"))
        "speak" -> { Speech.speak(ctx, args.optString("text")); "تم النطق" }
        "save_skill" -> { memory.save(args.optString("name"), JSONArray(args.optString("steps", "[]"))); "تم حفظ المهارة" }
        "run_skill" -> runSkill(args.optString("name"))
        else -> "أداة غير معروفة"
    }
    private fun openApp(name: String): String {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val info = pm.queryIntentActivities(intent, 0).firstOrNull { it.loadLabel(pm).toString().contains(name, true) } ?: return "لم أجد التطبيق $name"
        val launch = pm.getLaunchIntentForPackage(info.activityInfo.packageName) ?: return "تعذر فتح التطبيق"
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(launch); return "تم فتح ${info.loadLabel(pm)}"
    }
    private suspend fun runSkill(name: String): String {
        val steps = memory.load(name) ?: return "لا توجد مهارة بهذا الاسم"
        val out = StringBuilder()
        for (i in 0 until steps.length()) { val step = steps.getJSONObject(i); out.append(execute(step.optString("tool"), step.optJSONObject("args") ?: JSONObject())).append('\n'); delay(350) }
        return out.toString()
    }
}
