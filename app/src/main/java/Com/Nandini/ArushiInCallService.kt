package com.dipayan.nandini

import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Android Telecom integration plus opt-in best-effort voice assistant.
 * Android/OEM call audio routing can block the voice path; the app reports status honestly.
 */
class ArushiInCallService : InCallService() {
    private val activeCallbacks = ConcurrentHashMap<Call, Call.Callback>()

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        ArushiCallSession.attach(call)
        val callback = object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                ArushiCallSession.notifyChanged()
                when (state) {
                    Call.STATE_ACTIVE -> {
                        if (ArushiVoiceCallAssistant.isEnabled(this@ArushiInCallService)) {
                            val number = call.details?.handle?.schemeSpecificPart
                            ArushiVoiceCallAssistant.start(this@ArushiInCallService, number)
                        }
                    }
                    Call.STATE_DISCONNECTED -> ArushiVoiceCallAssistant.stop()
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

        // Auto-answer is explicitly opt-in from ARUSHI's settings screen.
        if (call.state == Call.STATE_RINGING && ArushiVoiceCallAssistant.isEnabled(this)) {
            try {
                call.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY)
            } catch (e: Exception) {
                Log.e("ARUSHI_CALL", "Auto-answer request failed", e)
            }
        } else if (call.state == Call.STATE_ACTIVE && ArushiVoiceCallAssistant.isEnabled(this)) {
            ArushiVoiceCallAssistant.start(this, call.details?.handle?.schemeSpecificPart)
        }
    }

    override fun onCallRemoved(call: Call) {
        activeCallbacks.remove(call)?.let { call.unregisterCallback(it) }
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
            if (call.state == Call.STATE_RINGING) call.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY)
        }
    }
    fun reject() {
        current?.let { call ->
            if (call.state == Call.STATE_RINGING) call.reject(false, null)
        }
    }
    fun end() { current?.disconnect() }

    fun stopVoiceAssistantIfNoCalls() {
        if (current?.state == Call.STATE_DISCONNECTED || current == null) ArushiVoiceCallAssistant.stop()
    }
}
