package com.venice.agent

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

object Speech {
    private var tts: TextToSpeech? = null
    @Volatile private var ready = false

    fun init(context: Context) {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                val saudi = Locale("ar", "SA")
                val result = tts?.setLanguage(saudi) ?: TextToSpeech.ERROR
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.language = Locale("ar")
                }
                tts?.setSpeechRate(0.98f)
            }
        }
    }

    fun speak(context: Context, text: String) {
        init(context)
        if (!ready || text.isBlank()) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "venice-agent")
    }

    fun stop() { tts?.stop() }
    fun shutdown() { tts?.stop(); tts?.shutdown(); tts = null; ready = false }
}

object EmergencyStop {
    @Volatile var active: Boolean = false
        private set

    fun stop(context: Context) { active = true; Speech.stop() }
    fun resume() { active = false }
}
