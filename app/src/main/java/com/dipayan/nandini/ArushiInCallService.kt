package com.dipayan.nandini

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.InCallService
import android.telecom.VideoProfile
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Android Telecom integration plus opt-in best-effort voice assistant.
 * Android/OEM call audio routing can block the voice path; this service never claims
 * that generated speech is audible to the remote caller without device testing.
 */
class ArushiInCallService : InCallService() {
    private val activeCallbacks = ConcurrentHashMap<Call, Call.Callback>()
    private val answerTimeouts = ConcurrentHashMap<Call, Runnable>()
    private val incomingCalls = ConcurrentHashMap.newKeySet<Call>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val answerDelayMs = 5_000L

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        ArushiCallSession.attach(call)
        val incoming = call.state == Call.STATE_RINGING ||
            (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q &&
                call.details?.callDirection == Call.Details.DIRECTION_INCOMING)
        if (incoming) incomingCalls.add(call)

        val callback = object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                ArushiCallSession.notifyChanged()
                when (state) {
                    Call.STATE_ACTIVE -> {
                        cancelPendingAutoAnswer(call)
                        if (incomingCalls.contains(call) && ArushiVoiceCallAssistant.isEnabled(this@ArushiInCallService)) {
                            ArushiVoiceCallAssistant.start(
                                this@ArushiInCallService,
                                call.details?.handle?.schemeSpecificPart
                            )
                        }
                    }
                    Call.STATE_DISCONNECTED -> {
                        cancelPendingAutoAnswer(call)
                        ArushiVoiceCallAssistant.stop()
                    }
                }
            }

            override fun onDetailsChanged(call: Call, details: Call.Details) {
                ArushiCallSession.notifyChanged()
            }
        }
        call.registerCallback(callback)
        activeCallbacks[call] = callback

        try {
            startActivity(Intent(this, ArushiCallAssistantActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(ArushiCallAssistantActivity.EXTRA_LAUNCHED_FOR_CALL, true)
            })
        } catch (e: Exception) {
            Log.w("ARUSHI_CALL", "Could not open call UI automatically", e)
        }

        // Give Dipayan five seconds to answer or reject manually. Auto-answer only
        // happens if it remains ringing and the feature is still explicitly enabled.
        if (call.state == Call.STATE_RINGING && ArushiVoiceCallAssistant.isEnabled(this)) {
            val timeout = Runnable {
                answerTimeouts.remove(call)
                if (call.state == Call.STATE_RINGING &&
                    ArushiVoiceCallAssistant.isEnabled(this@ArushiInCallService)) {
                    try {
                        call.answer(VideoProfile.STATE_AUDIO_ONLY)
                    } catch (e: Exception) {
                        Log.e("ARUSHI_CALL", "Delayed auto-answer request failed", e)
                    }
                }
            }
            answerTimeouts[call] = timeout
            mainHandler.postDelayed(timeout, answerDelayMs)
        }
    }

    private fun cancelPendingAutoAnswer(call: Call) {
        answerTimeouts.remove(call)?.let { mainHandler.removeCallbacks(it) }
    }

    override fun onCallRemoved(call: Call) {
        cancelPendingAutoAnswer(call)
        activeCallbacks.remove(call)?.let { call.unregisterCallback(it) }
        incomingCalls.remove(call)
        ArushiCallSession.stopVoiceAssistantIfNoCalls()
        ArushiCallSession.detach(call)
        super.onCallRemoved(call)
    }
}

/** In-process bridge between Telecom's service and ARUSHI's visible call controls. */
object ArushiCallSession {
    @Volatile private var current: Call? = null
    @Volatile private var changeListener: (() -> Unit)? = null

    fun attach(call: Call) {
        current = call
        notifyChanged()
    }

    fun detach(call: Call) {
        if (current === call) current = null
        notifyChanged()
    }

    fun currentCall(): Call? = current
    fun setListener(listener: (() -> Unit)?) { changeListener = listener }
    fun notifyChanged() { changeListener?.invoke() }

    fun answer() {
        current?.let { call ->
            if (call.state == Call.STATE_RINGING) {
                call.answer(VideoProfile.STATE_AUDIO_ONLY)
            }
        }
    }

    fun reject() {
        current?.let { call ->
            if (call.state == Call.STATE_RINGING) call.reject(false, null)
        }
    }

    fun end() { current?.disconnect() }

    fun stopVoiceAssistantIfNoCalls() {
        if (current?.state == Call.STATE_DISCONNECTED || current == null) {
            ArushiVoiceCallAssistant.stop()
        }
    }
}
