package com.kantu.phone

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class SpeechController(context: Context) : TextToSpeech.OnInitListener {
    private val prefs = context.applicationContext.getSharedPreferences("kantu_prefs", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var pendingText: String? = null
    private var pendingOnDone: (() -> Unit)? = null
    private var onDoneOnce: (() -> Unit)? = null
    private val _chineseUnavailable = MutableStateFlow(false)
    val chineseUnavailable = _chineseUnavailable.asStateFlow()

    @set:JvmName("assignTtsVolume")
    var volume: Float = prefs.getFloat("tts_volume", 1f).coerceIn(0f, 1f)
        set(value) {
            field = value.coerceIn(0f, 1f)
            prefs.edit().putFloat("tts_volume", field).apply()
        }

    fun setVolume(v: Float) {
        volume = v.coerceIn(0f, 1f)
    }

    override fun onInit(status: Int) {
        val tts = engine ?: return
        if (status != TextToSpeech.SUCCESS) {
            _chineseUnavailable.value = true
            return
        }
        tts.setSpeechRate(0.8f)
        val languageResult = tts.setLanguage(Locale.CHINA)
        _chineseUnavailable.value = languageResult == TextToSpeech.LANG_MISSING_DATA ||
            languageResult == TextToSpeech.LANG_NOT_SUPPORTED
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) = finish(success = true)
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finish(success = false)
            override fun onError(utteranceId: String?, errorCode: Int) = finish(success = false)
        })
        ready = true
        pendingText?.let { text ->
            val cb = pendingOnDone
            pendingText = null
            pendingOnDone = null
            speak(text, cb)
        }
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (!ready) {
            pendingText = text
            pendingOnDone = onDone
            return
        }
        onDoneOnce = onDone
        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
        }
        val result = engine?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "kantu-${System.nanoTime()}")
        if (result != TextToSpeech.SUCCESS) finish(success = false)
    }

    private fun finish(success: Boolean) {
        main.post {
            val cb = onDoneOnce
            onDoneOnce = null
            if (success) {
                main.postDelayed({ cb?.invoke() }, 220)
            } else {
                cb?.invoke()
            }
        }
    }

    fun shutdown() {
        onDoneOnce = null
        pendingOnDone = null
        engine?.stop()
        engine?.shutdown()
        engine = null
        ready = false
    }
}
