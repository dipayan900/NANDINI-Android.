package com.dipayan.nandini

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import kotlin.concurrent.thread

/**
 * User-started foreground microphone service for offline "Hey NANDINI" spotting.
 * The native decoder is device/runtime sensitive; errors are reported by notification.
 */
class WakeWordService : Service() {
    @Volatile private var running = false
    private var worker: Thread? = null
    private var recorder: AudioRecord? = null
    private var spotter: KeywordSpotter? = null
    private var stream: com.k2fsa.sherpa.onnx.OnlineStream? = null

    companion object {
        const val ACTION_WAKE_DETECTED = "com.dipayan.nandini.ACTION_WAKE_DETECTED"
        private const val CHANNEL_ID = "nandini_wake_word"
        private const val NOTIFICATION_ID = 7701
        private const val SAMPLE_RATE = 16000
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, notification("Listening for “Hey NANDINI” offline"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (running) return START_STICKY
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            updateNotification("Microphone permission is required. Open NANDINI and allow it.")
            stopSelf()
            return START_NOT_STICKY
        }
        running = true
        worker = thread(name = "NANDINI-WakeWord") { listenLoop() }
        return START_STICKY
    }

    private fun listenLoop() {
        var detected = false
        try {
            updateNotification("Loading offline wake-word model…")
            val activeSpotter = KeywordSpotter(
                assetManager = assets,
                config = KeywordSpotterConfig(
                    featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                    modelConfig = OnlineModelConfig(
                        transducer = OnlineTransducerModelConfig(
                            encoder = "encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
                            decoder = "decoder-epoch-12-avg-2-chunk-16-left-64.onnx",
                            joiner = "joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx"
                        ),
                        tokens = "tokens.txt",
                        modelType = "zipformer2",
                        numThreads = 1,
                        provider = "cpu"
                    ),
                    keywordsFile = "keywords.txt",
                    keywordsScore = 1.5f,
                    keywordsThreshold = 0.25f
                )
            )
            spotter = activeSpotter
            val minBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            check(minBuffer > 0) { "AudioRecord buffer setup failed: $minBuffer" }
            val activeRecorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, SAMPLE_RATE / 5 * 2)
            )
            recorder = activeRecorder
            check(activeRecorder.state == AudioRecord.STATE_INITIALIZED) { "Could not initialize microphone" }
            val activeStream = activeSpotter.createStream()
            stream = activeStream
            activeRecorder.startRecording()
            updateNotification("Listening for “Hey NANDINI” — tap notification to open NANDINI")
            val buffer = ShortArray(1600)

            while (running && !Thread.currentThread().isInterrupted) {
                val count = activeRecorder.read(buffer, 0, buffer.size)
                if (count < 0) error("Microphone read failed: $count")
                if (count == 0) continue
                val samples = FloatArray(count) { buffer[it] / 32768.0f }
                activeStream.acceptWaveform(samples, SAMPLE_RATE)
                while (running && activeSpotter.isReady(activeStream)) {
                    activeSpotter.decode(activeStream)
                    val keyword = activeSpotter.getResult(activeStream).keyword
                    if (keyword.isNotBlank()) {
                        activeSpotter.reset(activeStream)
                        detected = true
                        running = false
                        updateNotification("“Hey NANDINI” detected — tap to talk")
                        openNandiniAfterWake()
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("NANDINI_WAKE_SERVICE", "Wake-word listener failed", e)
            updateNotification("Wake-word error: ${e.message ?: e.javaClass.simpleName}. Tap to retry.")
        } finally {
            running = false
            try { recorder?.let { if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) it.stop() } } catch (_: Exception) {}
            try { recorder?.release() } catch (_: Exception) {}
            try { stream?.release() } catch (_: Exception) {}
            try { spotter?.release() } catch (_: Exception) {}
            recorder = null
            stream = null
            spotter = null
            if (!detected) stopSelf()
        }
    }

    private fun openNandiniAfterWake() {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_WAKE_DETECTED
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        // Android may restrict background activity launches. Always provide a notification action as fallback.
        try { startActivity(openIntent) } catch (e: Exception) {
            Log.w("NANDINI_WAKE_SERVICE", "System deferred activity launch; use notification", e)
        }
        val pending = PendingIntent.getActivity(
            this, 7702, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification("“Hey NANDINI” detected — tap to talk", pending))
        stopSelf()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "NANDINI wake word", NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Shows when offline wake-word listening is active or detected" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notification(text: String, action: PendingIntent? = null): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val openApp = PendingIntent.getActivity(
            this, 7703, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        )
        builder.setContentTitle("NANDINI")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(action ?: openApp)
            .setOngoing(running && action == null)
            .setOnlyAlertOnce(true)
        if (action != null) builder.addAction(android.R.drawable.ic_btn_speak_now, "Talk to NANDINI", action)
        return builder.build()
    }

    private fun updateNotification(text: String) {
        try {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, notification(text))
        } catch (e: Exception) {
            Log.w("NANDINI_WAKE_SERVICE", "Could not update notification", e)
        }
    }

    override fun onDestroy() {
        running = false
        try { recorder?.stop() } catch (_: Exception) {}
        worker?.interrupt()
        super.onDestroy()
    }
}