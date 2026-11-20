package com.venice.agent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.*
import android.widget.TextView
class BubbleService:Service(){private var v:View?=null;override fun onBind(i:Intent?):IBinder?=null;override fun onCreate(){super.onCreate();val w=getSystemService(WINDOW_SERVICE) as WindowManager;val t=TextView(this);t.text="V";t.textSize=22f;t.setTextColor(0xff00aaff.toInt());t.gravity=17;t.setOnClickListener{startActivity(Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))};val type=if(android.os.Build.VERSION.SDK_INT>=26)WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE;val p=WindowManager.LayoutParams(100,100,type,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);p.gravity=Gravity.TOP or Gravity.END;p.y=300;w.addView(t,p);v=t};override fun onDestroy(){v?.let { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) };super.onDestroy()}}
