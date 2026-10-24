package com.dipayan.nandini

import android.content.Context
import android.media.AudioManager
import org.json.JSONObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SessionDescription
import org.webrtc.SdpObserver
import org.webrtc.PeerConnection.Observer
import org.webrtc.MediaStream
import org.webrtc.DataChannel
import org.webrtc.IceServer
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Separate internet VoIP path. Never attaches to Android SIM/Telecom calls. */
class ArushiVoipClient(
    context: Context,
    private val listener: (String) -> Unit,
    private val onRemoteText: (String) -> Unit
) {
    private val app = context.applicationContext
    private val peerId = UUID.randomUUID().toString().replace("-", "").take(16)
    private val http = OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).build()
    private var socket: WebSocket? = null
    private var factory: PeerConnectionFactory? = null
    private var peer: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var localAudio: AudioTrack? = null
    private var joined = false
    private var roomCode = ""
    private var audioManager: AudioManager? = null
    private var previousAudioMode = AudioManager.MODE_NORMAL
    private var previousSpeaker = false
    private var pendingCandidates = mutableListOf<IceCandidate>()

    fun connect(url: String, room: String, turnUrl: String = "", turnUser: String = "", turnPassword: String = "") {
        close(false)
        if (!(url.startsWith("wss://") || url.startsWith("ws://"))) {
            listener("Error: signaling URL must begin with wss:// (recommended) or ws://.")
            return
        }
        if (!room.matches(Regex("[A-Za-z0-9_-]{4,48}"))) {
            listener("Error: room code must be 4–48 letters, numbers, dashes or underscores.")
            return
        }
        roomCode = room
        try {
            val options = PeerConnectionFactory.InitializationOptions.builder(app).createInitializationOptions()
            PeerConnectionFactory.initialize(options)
            factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
            audioSource = factory?.createAudioSource(MediaConstraints())
            localAudio = factory?.createAudioTrack("ARUSHI_MIC", audioSource)
            localAudio?.setEnabled(true)
            audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            previousAudioMode = audioManager?.mode ?: AudioManager.MODE_NORMAL
            previousSpeaker = audioManager?.isSpeakerphoneOn ?: false
            audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager?.isSpeakerphoneOn = true
            val request = Request.Builder().url(url).build()
            socket = http.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                    webSocket.send(JSONObject().put("type", "join").put("room", roomCode).put("peerId", peerId).toString())
                }
                override fun onMessage(webSocket: WebSocket, text: String) {
                    try { handleSignal(JSONObject(text)) } catch (e: Exception) {
                        listener("Signaling message error: ${e.localizedMessage ?: "invalid message"}")
                    }
                }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                    listener("Signaling connection failed: ${t.localizedMessage ?: "network error"}. Check server URL and internet.")
                }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (joined) listener("Signaling connection closed: $reason")
                }
            })
            listener("Connecting to signaling server…")
        } catch (e: Exception) {
            listener("Could not initialize WebRTC: ${e.localizedMessage ?: e.javaClass.simpleName}")
            close()
        }
    }

    private fun handleSignal(msg: JSONObject) {
        when (msg.optString("type")) {
            "joined" -> {
                joined = true
                listener("Connected to signaling server. Room $roomCode; waiting for the other ARUSHI device…")
            }
            "peer-joined" -> {
                listener("Other participant joined. Starting encrypted WebRTC negotiation…")
                ensurePeer()
                makeOffer()
            }
            "offer" -> {
                ensurePeer()
                val sdp = msg.optString("sdp")
                if (sdp.isBlank()) return listener("Received an empty WebRTC offer.")
                peer?.setRemoteDescription(sdpObserver("Remote offer accepted", onSet = {
                    flushCandidates()
                    makeAnswer()
                }), SessionDescription(SessionDescription.Type.OFFER, sdp))
            }
            "answer" -> {
                val sdp = msg.optString("sdp")
                if (sdp.isBlank()) return listener("Received an empty WebRTC answer.")
                peer?.setRemoteDescription(sdpObserver("Remote answer accepted", onSet = { flushCandidates() }),
                    SessionDescription(SessionDescription.Type.ANSWER, sdp))
            }
            "ice" -> {
                val c = msg.optJSONObject("candidate") ?: return
                val candidate = IceCandidate(c.optString("sdpMid"), c.optInt("sdpMLineIndex"), c.optString("candidate"))
                if (peer?.remoteDescription != null) peer?.addIceCandidate(candidate) else pendingCandidates.add(candidate)
            }
            "peer-left" -> listener("The other participant disconnected.")
            "tts" -> {
                val text = msg.optString("text").take(500)
                if (text.isNotBlank()) onRemoteText(text)
            }
            "error" -> listener("Signaling error: ${msg.optString("message", "unknown server error")}")
        }
    }

    private fun ensurePeer() {
        if (peer != null) return
        val f = factory ?: return
        val iceServers = mutableListOf(
            IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )
        // TURN credentials are supplied by the user; never hard-code a secret in the APK.
        val turn = turnConfig
        if (turn != null) iceServers.add(turn)
        val config = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        peer = f.createPeerConnection(config, object : Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                listener("WebRTC audio connection: ${state.name.lowercase().replace('_', ' ')}")
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) = Unit
            override fun onIceCandidate(candidate: IceCandidate) {
                send(JSONObject().put("type", "ice").put("candidate", JSONObject()
                    .put("sdpMid", candidate.sdpMid)
                    .put("sdpMLineIndex", candidate.sdpMLineIndex)
                    .put("candidate", candidate.sdp)))
            }
            override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) = Unit
            override fun onAddStream(stream: MediaStream) {
                stream.audioTracks.forEach { it.setEnabled(true) }
                listener("Remote audio track received.")
            }
            override fun onRemoveStream(stream: MediaStream) = Unit
            override fun onDataChannel(channel: DataChannel) = Unit
            override fun onRenegotiationNeeded() = Unit
            override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out MediaStream>) {
                (receiver.track() as? AudioTrack)?.setEnabled(true)
                listener("Remote audio track received.")
            }
        })
        if (peer == null) {
            listener("Error: WebRTC could not create a peer connection.")
            return
        }
        localAudio?.let { peer?.addTrack(it, listOf("arushi-audio")) }
    }

    // TURN settings are optional in this prototype. STUN alone cannot traverse every carrier/NAT.
    private var turnConfig: IceServer? = null
    fun setTurnServer(url: String, username: String, password: String) {
        turnConfig = if (url.isBlank()) null else try {
            IceServer.builder(url.trim()).setUsername(username).setPassword(password).createIceServer()
        } catch (_: Exception) { null }
    }

    private fun makeOffer() {
        val p = peer ?: return
        p.createOffer(sdpObserver("Offer created", onCreate = { sdp ->
            p.setLocalDescription(sdpObserver("Offer sent", onSet = {
                send(JSONObject().put("type", "offer").put("sdp", sdp.description))
            }), sdp)
        }), MediaConstraints())
    }

    private fun makeAnswer() {
        val p = peer ?: return
        p.createAnswer(sdpObserver("Answer created", onCreate = { sdp ->
            p.setLocalDescription(sdpObserver("Answer sent", onSet = {
                send(JSONObject().put("type", "answer").put("sdp", sdp.description))
            }), sdp)
        }), MediaConstraints())
    }

    private fun sdpObserver(
        label: String,
        onSet: () -> Unit = {},
        onCreate: (SessionDescription) -> Unit = {}
    ): SdpObserver = object : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription) { onCreate(desc) }
        override fun onSetSuccess() { listener(label); onSet() }
        override fun onCreateFailure(error: String) { listener("WebRTC error: $error") }
        override fun onSetFailure(error: String) { listener("WebRTC setup error: $error") }
    }

    private fun flushCandidates() {
        pendingCandidates.forEach { peer?.addIceCandidate(it) }
        pendingCandidates.clear()
    }

    fun sendAiVoiceText(text: String) {
        val clean = text.trim().take(500)
        if (clean.isBlank()) return
        // The receiving ARUSHI app synthesizes this prompt locally through Android TTS.
        send(JSONObject().put("type", "tts").put("text", clean))
    }

    private fun send(value: JSONObject) {
        if (socket?.send(value.toString()) != true) listener("Signaling message could not be sent.")
    }

    fun hangup() {
        send(JSONObject().put("type", "hangup"))
        close()
        listener("VoIP call ended.")
    }

    fun close(notify: Boolean = true) {
        joined = false
        try { peer?.close(); peer?.dispose() } catch (_: Exception) {}
        peer = null
        try { localAudio?.dispose(); audioSource?.dispose(); factory?.dispose() } catch (_: Exception) {}
        localAudio = null; audioSource = null; factory = null
        pendingCandidates.clear()
        try { socket?.close(1000, "ARUSHI call ended") } catch (_: Exception) {}
        socket = null
        try {
            audioManager?.isSpeakerphoneOn = previousSpeaker
            audioManager?.mode = previousAudioMode
        } catch (_: Exception) {}
        audioManager = null
        if (notify) listener("Disconnected.")
    }
}
