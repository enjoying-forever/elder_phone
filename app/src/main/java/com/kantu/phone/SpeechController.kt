package com.kantu.phone

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * Future-facing speech surface so a different engine can replace [SpeechController]
 * without changing call sites that only need announce / stop / shutdown.
 */
interface VoiceAnnouncer {
    val chineseUnavailable: StateFlow<Boolean>
    val volume: Float
    fun setVolume(v: Float)
    fun speak(text: String, onDone: (() -> Unit)? = null)
    fun speakForCall(text: String, onDone: (() -> Unit)? = null)
    fun stop()
    fun shutdown()
}

class SpeechController(context: Context) :
    TextToSpeech.OnInitListener,
    DefaultLifecycleObserver,
    VoiceAnnouncer {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())
    private val utteranceSeq = AtomicLong(0)
    private var engine: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var pendingText: String? = null
    private var pendingOnDone: (() -> Unit)? = null
    private var pendingLoud = false
    private var currentUtteranceId: String? = null
    private var currentOnDone: (() -> Unit)? = null
    private var delayedUtteranceId: String? = null
    private var delayedDone: Runnable? = null
    private var lifecycleOwner: LifecycleOwner? = (context as? LifecycleOwner)?.also { owner ->
        owner.lifecycle.addObserver(this)
    }

    private val _chineseUnavailable = MutableStateFlow(false)
    override val chineseUnavailable = _chineseUnavailable.asStateFlow()

    /**
     * App-persisted TTS gain, independent of [android.media.AudioManager] stream volume.
     *
     * Android limitation: [TextToSpeech.Engine.KEY_PARAM_VOLUME] only scales the utterance
     * in the range 0..1 relative to the *current* stream volume selected by audio attributes.
     * It cannot raise speech above the system stream, and this controller never calls
     * [android.media.AudioManager.setStreamVolume] / adjustStreamVolume so we do not
     * mutate global media / accessibility volume. USAGE_ALARM / FLAG_AUDIBILITY_ENFORCED
     * are also avoided because they can bypass DND and user volume caps.
     */
    @set:JvmName("assignTtsVolume")
    override var volume: Float = prefs.getFloat(PREF_VOLUME, 1f).coerceIn(MIN_VOLUME, 1f)
        set(value) {
            field = value.coerceIn(MIN_VOLUME, 1f)
            prefs.edit().putFloat(PREF_VOLUME, field).apply()
        }

    override fun setVolume(v: Float) {
        volume = v
    }

    override fun onInit(status: Int) {
        main.post { handleInit(status) }
    }

    private fun handleInit(status: Int) {
        val tts = engine ?: return
        if (status != TextToSpeech.SUCCESS) {
            _chineseUnavailable.value = true
            dropPendingWithoutCallback()
            return
        }
        tts.setSpeechRate(0.8f)
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        val languageResult = tts.setLanguage(Locale.CHINA)
        _chineseUnavailable.value = languageResult == TextToSpeech.LANG_MISSING_DATA ||
            languageResult == TextToSpeech.LANG_NOT_SUPPORTED
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                main.post { completeSuccess(utteranceId) }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                main.post { completeFailure(utteranceId) }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                main.post { completeFailure(utteranceId) }
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                main.post { completeFailure(utteranceId) }
            }
        })
        ready = true
        val text = pendingText
        val cb = pendingOnDone
        val loud = pendingLoud
        pendingText = null
        pendingOnDone = null
        pendingLoud = false
        if (text != null) speakInternal(text, cb, loud)
    }

    override fun speak(text: String, onDone: (() -> Unit)?) {
        enqueue(text, onDone, loud = false)
    }

    override fun speakForCall(text: String, onDone: (() -> Unit)?) {
        enqueue(text, onDone, loud = true)
    }

    // Kotlin trailing-lambda overloads so SpeechController.speak(text) { } and speak(text) keep compiling.
    fun speak(text: String) = speak(text, null)

    fun speakForCall(text: String) = speakForCall(text, null)

    private fun enqueue(text: String, onDone: (() -> Unit)?, loud: Boolean) {
        cancelCallbacks()
        if (!ready || engine == null) {
            pendingText = text
            pendingOnDone = onDone
            pendingLoud = loud
            return
        }
        speakInternal(text, onDone, loud)
    }

    private fun speakInternal(text: String, onDone: (() -> Unit)?, loud: Boolean) {
        val tts = engine ?: return
        val utteranceId = "kantu-${utteranceSeq.incrementAndGet()}"
        currentUtteranceId = utteranceId
        currentOnDone = onDone
        val params = Bundle().apply {
            // Per-utterance gain only. Call prompts always use 1f; other speech uses persisted volume.
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, if (loud) 1f else volume)
        }
        val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            currentUtteranceId = null
            currentOnDone = null
        }
    }

    private fun completeSuccess(utteranceId: String?) {
        if (utteranceId == null || utteranceId != currentUtteranceId) return
        val cb = currentOnDone
        currentOnDone = null
        currentUtteranceId = null
        if (cb == null) return
        val runnable = Runnable {
            if (delayedUtteranceId != utteranceId) return@Runnable
            delayedDone = null
            delayedUtteranceId = null
            cb.invoke()
        }
        delayedDone = runnable
        delayedUtteranceId = utteranceId
        main.postDelayed(runnable, DONE_DELAY_MS)
    }

    private fun completeFailure(utteranceId: String?) {
        if (utteranceId == delayedUtteranceId) dropDelayed()
        if (utteranceId != null && utteranceId != currentUtteranceId) return
        currentUtteranceId = null
        currentOnDone = null
    }

    private fun dropDelayed() {
        delayedDone?.let { main.removeCallbacks(it) }
        delayedDone = null
        delayedUtteranceId = null
    }

    private fun cancelCallbacks() {
        dropDelayed()
        currentOnDone = null
        currentUtteranceId = null
        pendingText = null
        pendingOnDone = null
        pendingLoud = false
    }

    private fun dropPendingWithoutCallback() {
        pendingText = null
        pendingOnDone = null
        pendingLoud = false
    }

    override fun stop() {
        cancelCallbacks()
        engine?.stop()
    }

    override fun shutdown() {
        stop()
        lifecycleOwner?.lifecycle?.removeObserver(this)
        lifecycleOwner = null
        ready = false
        engine?.shutdown()
        engine = null
    }

    override fun onStop(owner: LifecycleOwner) {
        stop()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        shutdown()
    }

    private companion object {
        const val PREFS = "kantu_prefs"
        const val PREF_VOLUME = "tts_volume"
        const val MIN_VOLUME = 0.3f
        const val DONE_DELAY_MS = 220L
    }
}
