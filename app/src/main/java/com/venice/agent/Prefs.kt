package com.venice.agent
import android.content.Context
class Prefs(c:Context){private val p=c.getSharedPreferences("agent",0);var apiKey:String get()=p.getString("key","")!!;set(v){p.edit().putString("key",v).apply()};var model:String get()=p.getString("model","gemini-2.0-flash")!!;set(v){p.edit().putString("model",v).apply()};var autoSpeak:Boolean get()=p.getBoolean("speak",true);set(v){p.edit().putBoolean("speak",v).apply()}}
