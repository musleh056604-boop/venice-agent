package com.venice.agent
import android.content.Context
import org.json.JSONArray
class Memory(c:Context){private val p=c.getSharedPreferences("skills",0);fun save(n:String,s:JSONArray){p.edit().putString(n,s.toString()).apply()};fun load(n:String)=p.getString(n,null)?.let{JSONArray(it)};fun list=p.all.keys.joinToString("\n")}
