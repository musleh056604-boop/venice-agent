package com.venice.agent

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.graphics.Color
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity:AppCompatActivity(){
 private lateinit var log:TextView;private lateinit var input:EditText;private lateinit var prefs:Prefs
 override fun onCreate(b:Bundle?){super.onCreate(b);prefs=Prefs(this);Speech.init(this);ui()}
 private fun ui(){val root=LinearLayout(this);root.orientation=LinearLayout.VERTICAL;root.setPadding(24,36,24,20);root.setBackgroundColor(Color.rgb(11,14,20));val title=TextView(this);title.text="Venice Agent\nوكيل عربي للتحكم بالجهاز";title.textSize=22f;title.setTextColor(Color.CYAN);root.addView(title)
  val bar=LinearLayout(this);listOf("الإعدادات" to ::settings,"الصلاحيات" to ::permissions).forEach{val x=Button(this);x.text=it.first;x.setOnClickListener{it.second.invoke()};bar.addView(x)};root.addView(bar)
  log=TextView(this);log.setTextColor(Color.WHITE);val sc=ScrollView(this);sc.addView(log);root.addView(sc,LinearLayout.LayoutParams(-1,0,1f));val row=LinearLayout(this);input=EditText(this);input.hint="اكتب أمراً…";input.setTextColor(Color.WHITE);row.addView(input,LinearLayout.LayoutParams(0,-2,1f));val send=Button(this);send.text="إرسال";send.setOnClickListener{runGoal(input.text.toString())};row.addView(send);root.addView(row);setContentView(root)}
 private fun settings(){val e=EditText(this);e.hint="مفتاح Gemini API";e.setText(prefs.apiKey);e.inputType=129;AlertDialog.Builder(this).setTitle("الإعدادات").setMessage("لا تشارك المفتاح مع أحد").setView(e).setPositiveButton("حفظ"){_,_->prefs.apiKey=e.text.toString().trim();log("تم حفظ المفتاح محلياً")}.setNegativeButton("إلغاء",null).show()}
 private fun permissions(){startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));if(android.os.Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.RECORD_AUDIO),4)}
 private fun runGoal(goal:String){if(goal.isBlank())return;val svc=AgentAccessibilityService.instance;if(svc==null){log("فعّل خدمة إمكانية الوصول أولاً");permissions();return};if(goal.contains("أرسل")||goal.contains("احذف")||goal.contains("اتصل")){AlertDialog.Builder(this).setTitle("تأكيد العملية الحساسة").setMessage(goal).setPositiveButton("تنفيذ"){_,_->execute(goal)}.setNegativeButton("إلغاء",null).show()}else execute(goal)}
 private fun execute(goal:String){log("👤 $goal");if(prefs.apiKey.isBlank()){log("أدخل مفتاح Gemini من الإعدادات");return};CoroutineScope(Dispatchers.IO).launch{val screen=AgentAccessibilityService.instance?.readScreen() ?: "";val answer=callGemini(goal,screen);withContext(Dispatchers.Main){log("🤖 $answer");if(prefs.autoSpeak)Speech.speak(this@MainActivity,answer)}}}
 private fun callGemini(goal:String,screen:String):String{val u=URL("https://generativelanguage.googleapis.com/v1beta/models/${prefs.model}:generateContent?key=${Uri.encode(prefs.apiKey)}");val c=u.openConnection() as HttpURLConnection;c.requestMethod="POST";c.doOutput=true;c.setRequestProperty("Content-Type","application/json");val body=JSONObject().put("contents",JSONArray().put(JSONObject().put("parts",JSONArray().put(JSONObject().put("text","أجب بالعربية باختصار. الأمر: $goal\nالشاشة: $screen")))));c.outputStream.use{it.write(body.toString().toByteArray())};val raw=(if(c.responseCode in 200..299)c.inputStream else c.errorStream).bufferedReader().readText();return runCatching{JSONObject(raw).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")}.getOrElse{"خطأ في Gemini: HTTP ${c.responseCode}"}}
 private fun log(s:String){runOnUiThread{log.append("$s\n")}}
}
