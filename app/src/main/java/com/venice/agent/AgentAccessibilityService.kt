package com.venice.agent

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AgentAccessibilityService : AccessibilityService() {
    companion object { @Volatile var instance: AgentAccessibilityService? = null }
    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() { instance = null; super.onDestroy() }
    fun readScreen(): String { val r=rootInActiveWindow ?: return "لا يمكن قراءة الشاشة"; val b=StringBuilder(); walk(r,b); return b.toString().take(6000).ifBlank{"لا نص ظاهر"} }
    private fun walk(n: AccessibilityNodeInfo?, b:String) { if(n==null||b.length>6000)return; val t=n.text?.toString()?.trim(); val d=n.contentDescription?.toString()?.trim(); if(!t.isNullOrBlank()||!d.isNullOrBlank()){val x=Rect();n.getBoundsInScreen(x);b.append((t?:d)).append(" @").append(x.centerX()).append(',').append(x.centerY()).append('\n')}; for(i in 0 until n.childCount) walk(n.getChild(i),b) }
    private fun find(q:String):AccessibilityNodeInfo? { val root=rootInActiveWindow ?: return null; val queue=ArrayDeque<AccessibilityNodeInfo>();queue.add(root);while(queue.isNotEmpty()){val n=queue.removeFirst();if(n.text?.toString()?.contains(q,true)==true||n.contentDescription?.toString()?.contains(q,true)==true)return n;for(i in 0 until n.childCount)n.getChild(i)?.let(queue::add)};return null }
    fun tapText(q:String):String { val n=find(q) ?: return "لم أجد $q"; var p:AccessibilityNodeInfo?=n; repeat(6){ if(p?.isClickable==true)return if(p!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))"تم النقر" else "فشل النقر";p=p?.parent }; val r=Rect();n.getBoundsInScreen(r); tap(r.centerX().toFloat(),r.centerY().toFloat());return "تم النقر" }
    fun typeText(text:String):String { val r=rootInActiveWindow ?: return "لا توجد شاشة"; val q=ArrayDeque<AccessibilityNodeInfo>();q.add(r);while(q.isNotEmpty()){val n=q.removeFirst();if(n.isEditable){val a=Bundle();a.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,text);return if(n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,a))"تمت الكتابة" else "فشلت الكتابة"};for(i in 0 until n.childCount)n.getChild(i)?.let(q::add)};return "لا يوجد حقل نص" }
    fun tap(x:Float,y:Float){val p=Path();p.moveTo(x,y);dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p,0,80)).build(),null,null)}
    fun press(key:String)=when(key){"back"->performGlobalAction(GLOBAL_ACTION_BACK);"home"->performGlobalAction(GLOBAL_ACTION_HOME);"recents"->performGlobalAction(GLOBAL_ACTION_RECENTS);else->false}
}
