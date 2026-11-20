package com.venice.agent

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {
    private lateinit var logView: TextView
    private lateinit var input: EditText
    private lateinit var prefs: Prefs
    private lateinit var tools: AgentTools
    private val scope = MainScope()
    private var busy = false
    private var activeJob: Job? = null
    private val maxSteps = 12

    override fun onCreate(state: Bundle?) { super.onCreate(state); prefs = Prefs(this); tools = AgentTools(this); Speech.init(this); buildUi() }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 36, 24, 20); setBackgroundColor(Color.rgb(11,14,20)) }
        root.addView(TextView(this).apply { text = "Venice Agent\nوكيل عربي للتحكم بالجهاز"; textSize = 22f; setTextColor(Color.CYAN) })
        root.addView(TextView(this).apply { text = "◉  صورة الوكيل\n   (أفاتار تجريبي — صوت عربي ar-SA)"; textSize = 18f; setTextColor(Color.WHITE); setPadding(0, 18, 0, 18) })
        val bar = LinearLayout(this)
        listOf("الإعدادات" to ::settings, "الصلاحيات" to ::permissions).forEach { pair ->
            bar.addView(Button(this).apply { text = pair.first; setOnClickListener { pair.second() } })
        }
        bar.addView(Button(this).apply { text = "اختبار الصوت"; setOnClickListener { Speech.speak(this@MainActivity, "مرحباً، أنا فينيس") } })
        bar.addView(Button(this).apply { text = "الفقاعة"; setOnClickListener { toggleBubble() } })
        val emergency = Button(this).apply { text = "إيقاف طوارئ"; setTextColor(Color.WHITE); setBackgroundColor(Color.rgb(170, 30, 30)); setOnClickListener { emergencyStop() } }
        bar.addView(emergency)
        root.addView(bar)
        logView = TextView(this).apply { setTextColor(Color.WHITE); textSize = 15f }
        root.addView(ScrollView(this).apply { addView(logView) }, LinearLayout.LayoutParams(-1, 0, 1f))
        val row = LinearLayout(this)
        input = EditText(this).apply { hint = "اكتب أمراً…"; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY) }
        row.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(Button(this).apply { text = "إرسال"; setOnClickListener { runGoal(input.text.toString()) } })
        root.addView(row); setContentView(root)
    }
    private fun settings() { val e = EditText(this).apply { hint = "مفتاح Gemini API"; setText(prefs.apiKey); inputType = 129 }; AlertDialog.Builder(this).setTitle("الإعدادات").setMessage("يحفظ المفتاح محليًا فقط").setView(e).setPositiveButton("حفظ") { _, _ -> prefs.apiKey = e.text.toString().trim(); log("تم حفظ المفتاح") }.setNegativeButton("إلغاء", null).show() }
    private fun permissions() { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 4) }
    private fun runGoal(goal: String) {
        val clean = goal.trim(); if (clean.isEmpty() || busy) return
        if (AgentAccessibilityService.instance == null) { log("فعّل خدمة إمكانية الوصول أولاً"); permissions(); return }
        val sensitive = Regex("أرسل|احذف|اتصل|رسالة|مكالمة|دفع|شراء|كلمة مرور|sms|رقم الهاتف|هاتف|اتصال", RegexOption.IGNORE_CASE).containsMatchIn(clean)
        if (sensitive) AlertDialog.Builder(this).setTitle("تأكيد عملية حساسة").setMessage(clean).setPositiveButton("تنفيذ") { _, _ -> executeGoal(clean) }.setNegativeButton("إلغاء", null).show() else executeGoal(clean)
    }
    private fun executeGoal(goal: String) {
        if (prefs.apiKey.isBlank()) { log("أدخل مفتاح Gemini من الإعدادات"); return }
        busy = true; input.setText(""); log("👤 $goal")
        activeJob = scope.launch { try { val answer = agentLoop(goal); log("🤖 $answer"); if (prefs.autoSpeak && !EmergencyStop.active) Speech.speak(this@MainActivity, answer) } catch (e: Exception) { log("خطأ: ${e.message ?: "غير معروف"}") } finally { busy = false; activeJob = null } }
    }
    private fun toggleBubble() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        startService(Intent(this, BubbleService::class.java))
        log("تم تشغيل الفقاعة")
    }
    private fun emergencyStop() {
        activeJob?.cancel(); activeJob = null; busy = false; EmergencyStop.stop(this); log("تم إيقاف الوكيل والطابور والصوت")
        AlertDialog.Builder(this).setTitle("الوكيل متوقف").setMessage("لن تُنفّذ الأدوات حتى إعادة التفعيل.").setPositiveButton("إعادة تفعيل") { _, _ -> EmergencyStop.resume(); log("تمت إعادة تفعيل الوكيل") }.setNegativeButton("إبقاء الإيقاف", null).show()
    }
    private suspend fun agentLoop(goal: String): String = withContext(Dispatchers.IO) {
        if (EmergencyStop.active) return@withContext "الوكيل متوقف بزر الطوارئ"
        val contents = JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", "نفّذ الأمر بالعربية: $goal"))))
        var finalText = ""; var steps = 0
        while (steps++ < maxSteps) {
            if (EmergencyStop.active) return@withContext "تم الإيقاف الطارئ"
            val screen = AgentAccessibilityService.instance?.readScreen() ?: "خدمة الوصول غير مفعلة"
            val prompt = "أنت وكيل هاتف. استخدم الأدوات لتنفيذ الأمر، ابدأ بقراءة الشاشة وتحقق بعد كل إجراء. الشاشة الحالية:\n$screen"
            val reply = callGemini(contents, prompt)
            val modelParts = reply.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
            contents.put(JSONObject().put("role", "model").put("parts", modelParts))
            var called = false
            val results = JSONArray()
            for (i in 0 until modelParts.length()) {
                val part = modelParts.optJSONObject(i) ?: continue
                part.optString("text").takeIf { it.isNotBlank() }?.let { finalText = it }
                val fc = part.optJSONObject("functionCall") ?: continue
                called = true; val name = fc.optString("name"); val args = fc.optJSONObject("args") ?: JSONObject(); withContext(Dispatchers.Main) { log("• $name") }
                val result = tools.execute(name, args)
                results.put(JSONObject().put("functionResponse", JSONObject().put("name", name).put("response", JSONObject().put("result", result))))
            }
            if (!called) return@withContext finalText.ifBlank { "تم التنفيذ" }
            contents.put(JSONObject().put("role", "user").put("parts", results))
        }
        "توقفت بعد بلوغ حد الخطوات الآمن ($maxSteps)."
    }
    private fun callGemini(contents: JSONArray, instruction: String): JSONObject {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/${prefs.model}:generateContent?key=${java.net.URLEncoder.encode(prefs.apiKey, "UTF-8")}")
        val c = url.openConnection() as HttpURLConnection; c.requestMethod = "POST"; c.connectTimeout = 30000; c.readTimeout = 90000; c.doOutput = true; c.setRequestProperty("Content-Type", "application/json")
        val body = JSONObject().put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", instruction)))).put("contents", contents).put("tools", JSONArray().put(JSONObject().put("functionDeclarations", tools.declarations())))
        c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val raw = (if (c.responseCode in 200..299) c.inputStream else c.errorStream).bufferedReader().use { it.readText() }
        if (c.responseCode !in 200..299) throw IllegalStateException("Gemini HTTP ${c.responseCode}")
        return JSONObject(raw).optJSONArray("candidates")?.optJSONObject(0) ?: JSONObject()
    }
    private fun log(text: String) { runOnUiThread { logView.append("$text\n") } }
}
