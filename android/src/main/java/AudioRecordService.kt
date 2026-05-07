package com.plugin.audio_recorder_android

import android.app.*
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat

private const val TAG = "AudioRecordService"
private const val CHANNEL_ID = "audio_record_service_channel"
private const val NOTIFICATION_ID = 101

class AudioRecordService : Service() {
    private var recorder: MediaRecorder? = null
    private val handler = Handler(Looper.getMainLooper())
    private var amplitudeTicker: Runnable? = null

    companion object {
        var isRecording = false
            private set
        var currentFilePath: String? = null
            private set
        var lastAmplitude: Int = 0
            private set

        fun getStatus(): Map<String, Any?> {
            return mapOf(
                "success" to true,
                "isRecording" to isRecording,
                "currentPath" to currentFilePath,
                "maxAmplitude" to lastAmplitude
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate called")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.v(TAG, "onStartCommand received action: $action")

        when (action) {
            "START" -> {
                val fileName = intent.getStringExtra("fileName")
                val format = intent.getIntExtra("format", MediaRecorder.OutputFormat.MPEG_4)
                val encoder = intent.getIntExtra("encoder", MediaRecorder.AudioEncoder.AAC)
                Log.d(TAG, "Processing START request - File: $fileName, Format: $format, Encoder: $encoder")
                startRecording(fileName, format, encoder)
            }
            "STOP" -> {
                Log.d(TAG, "Processing STOP request")
                stopRecording()
            }
            "PAUSE" -> {
                Log.d(TAG, "Processing PAUSE request")
                pauseRecording()
            }
            "RESUME" -> {
                Log.d(TAG, "Processing RESUME request")
                resumeRecording()
            }
            else -> Log.w(TAG, "Unknown action received: $action")
        }
        return START_NOT_STICKY
    }
    private fun startRecording(fileName: String?, format: Int, encoder: Int) {
        if (isRecording) {
            Log.w(TAG, "startRecording: Already recording, ignoring request.")
            return
        }
        if (fileName == null) {
            Log.e(TAG, "startRecording: fileName is null, cannot start.")
            return
        }

        currentFilePath = fileName
        createNotificationChannel()

        try {
            val notification = buildNotification("Recording...")
            Log.d(TAG, "Starting foreground service with microphone type")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            Log.d(TAG, "Initializing MediaRecorder...")
            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(format)
                setAudioEncoder(encoder)
                setOutputFile(fileName)

                Log.v(TAG, "MediaRecorder: prepare() starting")
                prepare()
                Log.v(TAG, "MediaRecorder: start() starting")
                start()
            }

            isRecording = true
            startAmplitudeUpdates()
            Log.i(TAG, "Successfully started recording: $fileName")
        } catch (e: Exception) {
            Log.e(TAG, "FATAL: Failed to start recording: ${e.stackTraceToString()}")
            isRecording = false
            stopSelf()
        }
    }
    private fun startAmplitudeUpdates() {
        Log.v(TAG, "Starting amplitude ticker (100ms interval)")
        amplitudeTicker = object : Runnable {
            override fun run() {
                if (isRecording) {
                    lastAmplitude = try {
                        recorder?.maxAmplitude ?: 0
                    } catch (e: Exception) {
                        Log.v(TAG, "Failed to get maxAmplitude: ${e.message}")
                        0
                    }
                    handler.postDelayed(this, 100)
                }
            }
        }
        handler.post(amplitudeTicker!!)
    }
    private fun stopRecording() {
        Log.d(TAG, "stopRecording called")
        try {
            isRecording = false
            amplitudeTicker?.let {
                Log.v(TAG, "Removing amplitude callbacks")
                handler.removeCallbacks(it)
            }
            lastAmplitude = 0

            recorder?.apply {
                Log.v(TAG, "Recorder stop() and release()")
                stop()
                reset()
                release()
            }
            Log.i(TAG, "Recording stopped and resources released.")
        } catch (e: Exception) {
            Log.e(TAG, "Error during recorder shutdown: ${e.message}")
        } finally {
            recorder = null
            Log.v(TAG, "Removing foreground notification")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()
        }
    }
    private fun pauseRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isRecording) {
            try {
                recorder?.pause()
                updateNotification("Recording Paused")
                Log.i(TAG, "Recording paused state reached.")
            } catch (e: Exception) {
                Log.e(TAG, "Pause operation failed: ${e.message}")
            }
        } else {
            Log.w(TAG, "Pause requested but not supported or not recording (SDK: ${Build.VERSION.SDK_INT})")
        }
    }
    private fun resumeRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isRecording) {
            try {
                recorder?.resume()
                updateNotification("Recording...")
                Log.i(TAG, "Recording resumed state reached.")
            } catch (e: Exception) {
                Log.e(TAG, "Resume operation failed: ${e.message}")
            }
        } else {
            Log.w(TAG, "Resume requested but not supported or not recording")
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Audio Recorder")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val notification = buildNotification(contentText)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID, "Audio Recording Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy called")
        if (isRecording) {
            Log.w(TAG, "Service destroyed while recording! Emergency stop.")
            stopRecording()
        }
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? {
        Log.v(TAG, "onBind called")
        return null
    }
}