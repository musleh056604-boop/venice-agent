package com.venice.agent
import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale
object Speech{private var t:TextToSpeech?=null;fun init(c:Context){if(t==null)t=TextToSpeech(c){if(it==TextToSpeech.SUCCESS)t?.language=Locale("ar")}};fun speak(c:Context,s:String){init(c);t?.speak(s,TextToSpeech.QUEUE_FLUSH,null,"agent")}}
