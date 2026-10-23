package com.dipayan.nandini

import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService
import android.util.Log

/**
 * User-approved Android Telecom integration.
 * This does not bypass OEM or Android cellular-audio restrictions.
 */
class ArushiInCallService : InCallService() {
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        ArushiCallSession.attach(call)
        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                ArushiCallSession.notifyChanged()
            }
            override fun onDetailsChanged(call: Call, details: Call.Details) {
                ArushiCallSession.notifyChanged()
            }
        })
        try {
            startActivity(Intent(this, ArushiCallAssistantActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(ArushiCallAssistantActivity.EXTRA_LAUNCHED_FOR_CALL, true)
            })
        } catch (e: Exception) {
            Log.w("ARUSHI_CALL", "Could not open call UI automatically", e)
        }
    }

    override fun onCallRemoved(call: Call) {
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

    fun answer() { current?.let { call ->
        if (call.state == Call.STATE_RINGING) call.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY)
    } }
    fun reject() { current?.let { call ->
        if (call.state == Call.STATE_RINGING) call.reject(false, null)
    } }
    fun end() { current?.disconnect() }
}
